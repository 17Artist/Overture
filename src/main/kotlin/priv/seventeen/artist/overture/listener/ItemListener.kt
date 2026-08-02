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
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.OvertureConfig
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.core.action.ItemActionDispatcher
import priv.seventeen.artist.overture.core.action.OvertureTriggers
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.UpdateManager
import priv.seventeen.artist.overture.core.meta.impl.MetaDurability
import priv.seventeen.artist.overture.core.diagnostic.BuildDiagnosticsStore
import priv.seventeen.artist.overture.feature.ItemCooldown

/**
 * 物品交互事件监听器
 */
object ItemListener {

    // Paper 会把“原版没有可执行动作”的空中交互预先标记为 cancelled；
    // 自定义物品（例如 STICK）仍必须收到该事件，否则右键动作永远不会触发。
    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        val trigger = when {
            event.action.name.contains("LEFT") -> OvertureTriggers.ON_LEFT_CLICK
            event.action.name.contains("RIGHT") -> OvertureTriggers.ON_RIGHT_CLICK
            else -> return
        }

        if (!dispatchAction(itemDef, trigger, event.player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            setHandItem(event.player, event.hand ?: EquipmentSlot.HAND, rebuildItem(event.player, stream))
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val item = if (event.hand == EquipmentSlot.OFF_HAND) {
            event.player.inventory.itemInOffHand
        } else {
            event.player.inventory.itemInMainHand
        }
        if (item.type.isAir) return

        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        // 耐久耗尽时阻止使用
        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        if (!dispatchAction(itemDef, OvertureTriggers.ON_RIGHT_CLICK_ENTITY, event.player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            setHandItem(event.player, event.hand, rebuildItem(event.player, stream))
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) return

        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        // 耐久耗尽时阻止攻击
        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        if (!dispatchAction(itemDef, OvertureTriggers.ON_ATTACK, player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            setHandItem(player, EquipmentSlot.HAND, rebuildItem(player, stream))
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        if (!dispatchAction(itemDef, OvertureTriggers.ON_DROP, event.player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            val result = rebuildItem(event.player, stream)
            if (result == null) {
                event.itemDrop.remove()
            } else {
                event.itemDrop.itemStack = result
            }
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        dispatchAction(itemDef, OvertureTriggers.ON_PICK, player, stream, event)

        if (event.isCancelled) {
            if (stream.signals.isNotEmpty()) {
                val result = rebuildItem(player, stream)
                if (result == null) {
                    event.item.remove()
                } else {
                    event.item.itemStack = result
                }
            }
            return
        }

        var current = if (stream.signals.isNotEmpty()) {
            rebuildItem(player, stream) ?: run {
                event.isCancelled = true
                event.item.remove()
                return
            }
        } else {
            item
        }

        val updated = if (OvertureConfig.instance.update.checkOnPickup) {
            UpdateManager.checkUpdate(player, current)
        } else {
            null
        }
        if (updated != null) {
            current = updated
        }
        event.item.itemStack = current
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        if (!dispatchAction(itemDef, OvertureTriggers.ON_CONSUME, event.player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            event.setItem(rebuildItem(event.player, stream) ?: ItemStack(Material.AIR))
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val item = event.player.inventory.itemInMainHand
        if (item.type.isAir) return

        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        // 耐久耗尽时阻止破坏方块
        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        if (!dispatchAction(itemDef, OvertureTriggers.ON_BLOCK_BREAK, event.player, stream, event)) return

        if (stream.signals.isNotEmpty()) {
            setHandItem(event.player, EquipmentSlot.HAND, rebuildItem(event.player, stream))
        }
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val offhandResult = executeSwapAction(
            event.player,
            event.offHandItem,
            OvertureTriggers.ON_SWAP_TO_OFFHAND,
            event
        )
        val mainhandResult = executeSwapAction(
            event.player,
            event.mainHandItem,
            OvertureTriggers.ON_SWAP_TO_MAINHAND,
            event
        )

        if (event.isCancelled) {
            // 交换被脚本取消时，事件字段不会落入背包；把动作修改写回原槽位。
            if (offhandResult.changed) setHandItem(event.player, EquipmentSlot.HAND, offhandResult.item)
            if (mainhandResult.changed) setHandItem(event.player, EquipmentSlot.OFF_HAND, mainhandResult.item)
        } else {
            if (offhandResult.changed) event.offHandItem = offhandResult.item
            if (mainhandResult.changed) event.mainHandItem = mainhandResult.item
        }
    }

    @AutoListener
    fun onJoin(event: PlayerJoinEvent) {
        if (!OvertureConfig.instance.update.checkOnJoin) return
        Bukkit.getScheduler().runTaskLater(
            bukkitPlugin, Runnable {
                UpdateManager.checkInventory(event.player)
            }, 20L
        )
    }

    @AutoListener
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (!OvertureConfig.instance.update.checkOnJoin) return
        UpdateManager.checkInventory(event.player)
    }

    @AutoListener(ignoreCancelled = true)
    fun onHeldItemChange(event: PlayerItemHeldEvent) {
        if (!OvertureConfig.instance.update.checkOnSwitch) return
        val item = event.player.inventory.getItem(event.newSlot) ?: return
        if (item.type.isAir) return
        val updated = UpdateManager.checkUpdate(event.player, item) ?: return
        event.player.inventory.setItem(event.newSlot, updated)
    }

    @AutoListener
    fun onQuit(event: PlayerQuitEvent) {
        ItemCooldown.clear(event.player)
        BuildDiagnosticsStore.removePlayer(event.player.uniqueId)
    }

    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        // 有自定义耐久时拦截原版耐久损耗
        if (itemDef.metaList.any { it.key == "durability" }) {
            event.isCancelled = true
            // 触发 on_damage 脚本
            dispatchAction(itemDef, OvertureTriggers.ON_DAMAGE, event.player, stream, event)
            if (stream.signals.isNotEmpty()) {
                replaceMatchingInventoryItem(event.player, item, rebuildItem(event.player, stream))
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查物品耐久是否已耗尽
     * @return true 表示耐久已耗尽，应阻止使用
     */
    private fun isDurabilityDepleted(itemDef: OvertureItem, stream: ItemStream): Boolean {
        val hasDurability = itemDef.metaList.any { it.key == "durability" }
        if (!hasDurability) return false
        val current = stream.overtureData.getInt("durability_current")
        return current <= 0
    }

    private fun dispatchAction(
        item: OvertureItem,
        trigger: TriggerKey,
        player: Player?,
        stream: ItemStream,
        event: Event?
    ): Boolean = ItemActionDispatcher.dispatch(
        item,
        trigger,
        player,
        stream,
        event
    ).executed

    private fun rebuildItem(player: Player, stream: ItemStream): ItemStack? {
        if (stream.sourceItem.amount <= 0) return null

        if (stream.signals.contains(ItemSignal.DURABILITY_DESTROYED)) {
            return getDestroyResult(player, stream)
        }

        val itemDef = ItemManager.getItem(stream.overtureId ?: return null) ?: return null
        val rebuilt = itemDef.build(player, stream)
        if (rebuilt.buildCancelled) return stream.sourceItem
        return rebuilt.toItemStack(player)
    }

    private fun getDestroyResult(player: Player, stream: ItemStream): ItemStack? {
        val itemDef = ItemManager.getItem(stream.overtureId ?: return null) ?: return null
        dispatchAction(itemDef, OvertureTriggers.ON_ITEM_BREAK, player, stream, null)
        val durabilityMeta = itemDef.metaList.filterIsInstance<MetaDurability>().firstOrNull()

        val remains = durabilityMeta?.getRemainsItem()
        if (remains == null) {
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
        }
        return remains
    }

    private fun setHandItem(player: Player, hand: EquipmentSlot, item: ItemStack?) {
        val value = item ?: ItemStack(Material.AIR)
        if (hand == EquipmentSlot.OFF_HAND) {
            player.inventory.setItemInOffHand(value)
        } else {
            player.inventory.setItemInMainHand(value)
        }
    }

    private fun executeSwapAction(
        player: Player,
        item: ItemStack?,
        trigger: TriggerKey,
        event: PlayerSwapHandItemsEvent
    ): SwapResult {
        if (item == null || item.type.isAir) return SwapResult(item, false)
        val stream = ItemStream(item)
        if (!stream.isOverture) return SwapResult(item, false)
        val itemDef = ItemManager.getItem(stream.overtureId ?: return SwapResult(item, false))
            ?: return SwapResult(item, false)
        dispatchAction(itemDef, trigger, player, stream, event)

        var result = if (stream.signals.isNotEmpty()) rebuildItem(player, stream) else item
        var changed = stream.signals.isNotEmpty()
        if (OvertureConfig.instance.update.checkOnSwitch && result != null) {
            val updated = UpdateManager.checkUpdate(player, result)
            if (updated != null) {
                result = updated
                changed = true
            }
        }
        return SwapResult(result, changed)
    }

    private fun replaceMatchingInventoryItem(player: Player, original: ItemStack, replacement: ItemStack?) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) === original) {
                inventory.setItem(slot, replacement ?: ItemStack(Material.AIR))
                return
            }
        }

        // 部分服务端事件提供 ItemStack 副本；优先回写当前主手，避免误改相同物品的其他槽位。
        if (inventory.itemInMainHand.isSimilar(original)) {
            setHandItem(player, EquipmentSlot.HAND, replacement)
        } else if (inventory.itemInOffHand.isSimilar(original)) {
            setHandItem(player, EquipmentSlot.OFF_HAND, replacement)
        }
    }

    private data class SwapResult(
        val item: ItemStack?,
        val changed: Boolean
    )
}
