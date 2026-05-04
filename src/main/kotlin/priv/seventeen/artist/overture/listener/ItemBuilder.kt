package priv.seventeen.artist.overture.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.api.event.ItemBuildEvent
import priv.seventeen.artist.overture.api.event.ItemReleaseEvent
import priv.seventeen.artist.overture.core.action.ActionExecutor
import priv.seventeen.artist.overture.core.display.ConditionalDisplay
import priv.seventeen.artist.overture.core.display.Display
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
    fun onBuildPost(event: ItemBuildEvent.Post) {
        val stream = event.stream
        val itemDef = ItemManager.getItem(event.itemId) ?: return
        val compound = stream.sourceCompound

        // 1. Drop 阶段：移除已删除的 Meta
        val dropMetaKeys = stream.getDropMeta(itemDef.metaList)
        for (metaKey in dropMetaKeys) {
            val oldMeta = MetaRegistry.create(metaKey, null, null, false)
            oldMeta?.drop(event.player, compound)
        }

        // 2. Build 阶段：构建当前 Meta
        val isUpdateCheck = stream.signals.contains(ItemSignal.UPDATE_CHECKED)
        val sourceTag = stream.sourceTag
        for (meta in itemDef.metaList.sortedBy { it.priority }) {
            if (isUpdateCheck && !meta.locked) continue
            meta.build(event.player, compound, sourceTag, stream.signals)
        }

        // 3. 记录 Meta 历史
        stream.setMetaHistory(itemDef.metaList.map { it.key })
    }

    @AutoListener
    fun onRelease(event: ItemReleaseEvent.Release) {
        val stream = event.stream
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        // Meta buildMeta 阶段
        for (meta in itemDef.metaList.sortedBy { it.priority }) {
            meta.buildMeta(event.itemMeta)
            meta.buildRelease(stream.sourceItem, event.itemMeta)
        }

        // 耐久同步（从 stream 的 sourceTag 读取数据，因为此时 NBT 尚未 saveTo）
        val durMeta = itemDef.metaList.filterIsInstance<priv.seventeen.artist.overture.core.meta.impl.MetaDurability>().firstOrNull()
        if (durMeta != null) {
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

        if (displayId == null) return

        // 解析展示方案（可能是条件展示）
        val resolved = DisplayManager.resolve(displayId)
        val display: Display? = when (resolved) {
            is ConditionalDisplay -> {
                val targetId = resolved.evaluate(player, stream) { expr, p, s ->
                    ActionExecutor.evaluateCondition(expr, p, s)
                }
                targetId?.let { DisplayManager.getDisplay(it) }
            }
            is Display -> resolved
            else -> null
        }

        if (display == null) return

        // Data-Mapper 注入变量
        val mappedVars = DataMapper.map(itemDef.dataMapper, stream)
        for ((key, value) in mappedVars) {
            stream.addVariable(key, value)
        }

        // 触发 Display 事件
        val displayEvent = ItemReleaseEvent.Display(player, stream, stream.nameVars, stream.loreVars)
        Bukkit.getPluginManager().callEvent(displayEvent)

        // 构建展示
        val product = display.build(stream.nameVars, stream.loreVars.mapValues { it.value.toList() })

        // 写入 ItemMeta
        if (product.name != null) {
            itemMeta.setDisplayName(product.name)
        }
        itemMeta.lore = product.lore
    }
}
