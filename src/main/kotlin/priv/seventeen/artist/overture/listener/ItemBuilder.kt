/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.overture.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.api.event.ItemBuildEvent
import priv.seventeen.artist.overture.api.event.ItemReleaseEvent
import priv.seventeen.artist.overture.core.action.ActionExecutor
import priv.seventeen.artist.overture.core.action.ItemActionDispatcher
import priv.seventeen.artist.overture.core.action.OvertureTriggers
import priv.seventeen.artist.overture.core.display.ConditionalDisplay
import priv.seventeen.artist.overture.core.display.Display
import priv.seventeen.artist.overture.core.display.RenderEntryRegistry
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.ItemStreamGenerated
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.manager.DisplayManager
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.mapper.DataMapper
import priv.seventeen.artist.overture.core.meta.MetaRegistry

/**
 * 物品构建事件处理器
 * 负责 Meta 的 build/drop 协调和 Display 构建
 */
object ItemBuilder {

    @AutoListener
    fun onBuildPre(event: ItemBuildEvent.Pre) {
        if (ItemSignal.TEMPLATE in event.stream.signals) return
        val itemDef = ItemManager.getItem(event.itemId) ?: return
        ItemActionDispatcher.dispatch(
            itemDef,
            OvertureTriggers.ON_BUILD,
            event.player,
            event.stream,
            event
        )
    }

    @AutoListener
    fun onBuildPost(event: ItemBuildEvent.Post) {
        val stream = event.stream
        val itemDef = ItemManager.getItem(event.itemId) ?: return
        val compound = stream.sourceCompound

        // 1. Drop 阶段：移除已删除的 Meta
        val dropMetaKeys = stream.getDropMeta(itemDef.metaList)
        for (metaKey in dropMetaKeys) {
            val oldMeta = MetaRegistry.create(metaKey, null, null, false)
            if (oldMeta != null) {
                oldMeta.drop(event.player, compound, stream.sourceTag)
                stream.droppedMeta.add(oldMeta)
            }
        }

        // 2. Build 阶段：构建当前 Meta
        val isUpdateCheck = stream.signals.contains(ItemSignal.UPDATE_CHECKED)
        val sourceTag = stream.sourceTag
        for (meta in itemDef.metaList.sortedBy { it.priority }) {
            if (isUpdateCheck && !meta.locked) continue
            try {
                if (isUpdateCheck) {
                    meta.prepareRebuild(compound, sourceTag)
                }
                meta.build(event.player, compound, sourceTag, stream.signals)
            } catch (e: Throwable) {
                // 单个 Meta 失败（例如某版本缺少对应 NMS 桥接）不能中断整条构建链
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.meta-build-failed",
                        "item" to event.itemId,
                        "meta" to meta.key,
                        "error" to (e.message ?: e.javaClass.simpleName)
                    )
                )
            }
        }

        // 3. 记录 Meta 历史
        stream.setMetaHistory(itemDef.metaList.map { it.key })
    }

    @AutoListener
    fun onRelease(event: ItemReleaseEvent.Release) {
        val stream = event.stream
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        if (ItemSignal.TEMPLATE !in stream.signals) {
            ItemActionDispatcher.dispatch(
                itemDef,
                OvertureTriggers.ON_RELEASE,
                event.player,
                stream,
                event
            )
        }

        // 清理由定义更新遗留、但新定义已删除的 Bukkit ItemMeta。
        for (dropped in stream.droppedMeta) {
            dropped.dropMeta(event.itemMeta)
        }

        // Meta buildMeta 阶段
        val forceLocked = stream.signals.contains(ItemSignal.UPDATE_CHECKED)
        for (meta in itemDef.metaList.sortedBy { it.priority }) {
            if (forceLocked && !meta.locked) continue
            try {
                if (forceLocked) {
                    meta.dropMeta(event.itemMeta)
                }
                meta.buildMeta(event.itemMeta, stream.sourceCompound)
                meta.buildRelease(stream.sourceItem, event.itemMeta)
            } catch (e: Throwable) {
                // 同上：避免一个 Meta 抛异常导致后续 Meta 与 Display 全部不生效
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.item-meta-write-failed",
                        "item" to itemDef.id,
                        "meta" to meta.key,
                        "error" to (e.message ?: e.javaClass.simpleName)
                    )
                )
            }
        }

        // 耐久同步（从 stream 的 sourceTag 读取数据，因为此时 NBT 尚未 saveTo）
        val durMeta = itemDef.metaList.filterIsInstance<priv.seventeen.artist.overture.core.meta.impl.MetaDurability>().firstOrNull()
        if (durMeta != null && (!forceLocked || durMeta.locked)) {
            val data = stream.sourceCompound.getCompound("data")
            val current = data.getInt("durability_current")
            val maxDur = data.getInt("durability")
            val maxItemDur = stream.sourceItem.type.maxDurability.toInt()
            durMeta.syncDurability(event.itemMeta, current, maxDur, maxItemDur)
        }

        // Display 构建（仅 Generated 流）
        if (stream is ItemStreamGenerated) {
            buildDisplay(event.player, stream, itemDef, event.itemMeta)
        }
    }

    @AutoListener(priority = EventPriority.LOW)
    fun onReleaseDisplay(event: ItemReleaseEvent.Display) {
        if (ItemSignal.TEMPLATE in event.stream.signals) return
        val itemDef = ItemManager.getItem(event.stream.overtureId ?: return) ?: return
        ItemActionDispatcher.dispatch(
            itemDef,
            OvertureTriggers.ON_RELEASE_DISPLAY,
            event.player,
            event.stream,
            event
        )
    }

    private fun buildDisplay(
        player: Player?,
        stream: ItemStreamGenerated,
        itemDef: OvertureItem,
        itemMeta: ItemMeta
    ) {
        var displayId = itemDef.display

        // 触发 SelectDisplay 事件
        val selectEvent = ItemReleaseEvent.SelectDisplay(player, stream, displayId)
        Bukkit.getPluginManager().callEvent(selectEvent)
        displayId = selectEvent.displayId

        if (displayId == null) {
            clearLockedDisplay(stream, itemDef, itemMeta)
            return
        }

        // 解析展示方案（可能是条件展示）
        val resolved = DisplayManager.resolve(displayId)
        val display: Display? = when (resolved) {
            is ConditionalDisplay -> {
                val targetId = resolved.evaluate(player, stream) { routine, p, s ->
                    ActionExecutor.evaluateCondition(routine, p, s)
                }
                targetId?.let { DisplayManager.getDisplay(it) }
            }
            is Display -> resolved
            else -> null
        }

        if (display == null) {
            clearLockedDisplay(stream, itemDef, itemMeta)
            return
        }

        // Data-Mapper 注入变量
        val mappedVars = DataMapper.map(itemDef.dataMapper, stream)
        for ((key, value) in mappedVars) {
            stream.addVariable(key, value)
        }

        // 触发 Display 事件
        val displayEvent = ItemReleaseEvent.Display(player, stream, stream.nameVars, stream.loreVars)
        Bukkit.getPluginManager().callEvent(displayEvent)

        // 第三方自定义条目：名称取第一行，Lore 保留完整列表；同一个 key 只执行一次回调。
        val nameEntries = display.structureName.variableNames
        val loreEntries = display.structureLore.listVariableNames
        val renderedEntries = RenderEntryRegistry.render(
            nameEntries + loreEntries,
            player,
            stream,
            displayId,
            stream.renderTimings
        )
        renderedEntries.forEach { (key, lines) ->
            if (key in nameEntries) {
                lines.firstOrNull()?.let { stream.addName(key, it) }
            }
            if (key in loreEntries) {
                stream.addLore(key, lines)
            }
        }

        // 构建展示
        val product = display.build(stream.nameVars, stream.loreVars.mapValues { it.value.toList() })

        // 普通交互重建必须刷新名称/Lore；只有版本更新检查才按 !! 锁定语义
        // 保留未锁定字段。过去仅判断 updating，导致数据已更新而展示永久停留在旧值。
        if (shouldOverwriteDisplay(stream.signals, itemDef.nameLocked)) {
            itemMeta.setDisplayName(product.name)
        }
        if (shouldOverwriteDisplay(stream.signals, itemDef.loreLocked)) {
            itemMeta.lore = product.lore
        }
    }

    internal fun shouldOverwriteDisplay(signals: Set<ItemSignal>, locked: Boolean): Boolean =
        ItemSignal.UPDATE_CHECKED !in signals || locked

    private fun clearLockedDisplay(
        stream: ItemStreamGenerated,
        itemDef: OvertureItem,
        itemMeta: ItemMeta
    ) {
        if (!stream.updating) return
        if (itemDef.nameLocked) itemMeta.setDisplayName(null)
        if (itemDef.loreLocked) itemMeta.lore = null
    }
}
