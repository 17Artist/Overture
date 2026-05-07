package priv.seventeen.artist.overture.listener

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.core.action.ActionExecutor
import priv.seventeen.artist.overture.core.action.ActionTrigger
import priv.seventeen.artist.overture.core.action.ItemAction
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.UpdateManager
import priv.seventeen.artist.overture.core.meta.impl.MetaDurability
import priv.seventeen.artist.overture.feature.ItemCooldown

/**
 * 物品交互事件监听器
 */
object ItemListener {

    @AutoListener(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
//        BlinkLog.detail("[Listener] onInteract 触发: action=${event.action}, hasItem=${event.item != null}")
        val item = event.item ?: return
        val stream = ItemStream(item)
//        BlinkLog.detail("[Listener] isOverture=${stream.isOverture}, id=${stream.overtureId}")
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: run {
//            BlinkLog.detail("[Listener] 物品定义未找到: ${stream.overtureId}")
            return
        }

        // 耐久耗尽时阻止使用
        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        val trigger = when {
            event.action.name.contains("LEFT") -> ActionTrigger.ON_LEFT_CLICK
            event.action.name.contains("RIGHT") -> ActionTrigger.ON_RIGHT_CLICK
            else -> return
        }

        val action = resolveAction(itemDef, trigger)
//        BlinkLog.detail("[Listener] trigger=$trigger, action=${action != null}, compiled=${action?.compiled != null}")
        if (action == null) return
        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)

        if (stream.signals.isNotEmpty()) {
            BlinkLog.info("[onInteract] signals=${stream.signals}, triggering rebuild")
            rebuildItem(event.player, stream)
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.player.inventory.itemInMainHand
        if (item.type.isAir) return

        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return

        // 耐久耗尽时阻止使用
        if (isDurabilityDepleted(itemDef, stream)) {
            event.isCancelled = true
            return
        }

        val action = resolveAction(itemDef, ActionTrigger.ON_RIGHT_CLICK_ENTITY) ?: return
        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)

        if (stream.signals.isNotEmpty()) {
            rebuildItem(event.player, stream)
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onAttack(event: org.bukkit.event.entity.EntityDamageEvent) {
        if (event !is EntityDamageByEntityEvent) return
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

        val action = resolveAction(itemDef, ActionTrigger.ON_ATTACK) ?: return
        ActionExecutor.execute(action, player, stream, event, itemDef.eventVars)

        if (stream.signals.isNotEmpty()) {
            rebuildItem(player, stream)
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val action = resolveAction(itemDef, ActionTrigger.ON_DROP) ?: return
        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)

        if (stream.signals.isNotEmpty() && !event.isCancelled) {
            // Drop 场景特殊：物品已脱离背包，需要直接操作掉落物
            stream.save()
            val itemDef2 = ItemManager.getItem(stream.overtureId ?: return) ?: return
            val rebuilt = itemDef2.build(event.player, stream)
            val result = rebuilt.toItemStack(event.player)
            event.itemDrop.itemStack = result
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val action = resolveAction(itemDef, ActionTrigger.ON_PICK)
        if (action != null) {
            ActionExecutor.execute(action, player, stream, event, itemDef.eventVars)
        }

        // 事件被取消时不再执行后续逻辑
        if (event.isCancelled) return

        // 拾取时检查更新
        val updated = UpdateManager.checkUpdate(player, item)
        if (updated != null) {
            event.item.itemStack = updated
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val action = resolveAction(itemDef, ActionTrigger.ON_CONSUME) ?: return
        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)
    }

    @AutoListener(priority = EventPriority.HIGH)
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

        val action = resolveAction(itemDef, ActionTrigger.ON_BLOCK_BREAK) ?: return
        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)

        if (stream.signals.isNotEmpty()) {
            rebuildItem(event.player, stream)
        }
    }

    @AutoListener(priority = EventPriority.HIGH)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        // 主手 → 副手: offHandItem 是交换后副手将持有的物品（即原来在主手的物品）
        val toOffhand = event.offHandItem
        if (toOffhand != null && !toOffhand.type.isAir) {
            val stream = ItemStream(toOffhand)
            if (stream.isOverture) {
                val itemDef = stream.overtureId?.let { ItemManager.getItem(it) }
                if (itemDef != null) {
                    val action = resolveAction(itemDef, ActionTrigger.ON_SWAP_TO_OFFHAND)
                    if (action != null) {
                        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)
                    }
                }
            }
        }

        // 副手 → 主手: mainHandItem 是交换后主手将持有的物品（即原来在副手的物品）
        val toMainhand = event.mainHandItem
        if (toMainhand != null && !toMainhand.type.isAir) {
            val stream = ItemStream(toMainhand)
            if (stream.isOverture) {
                val itemDef = stream.overtureId?.let { ItemManager.getItem(it) }
                if (itemDef != null) {
                    val action = resolveAction(itemDef, ActionTrigger.ON_SWAP_TO_MAINHAND)
                    if (action != null) {
                        ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)
                    }
                }
            }
        }
    }

    @AutoListener
    fun onJoin(event: PlayerJoinEvent) {
        // 玩家加入时检查背包更新
        Bukkit.getScheduler().runTaskLater(
            bukkitPlugin, Runnable {
                UpdateManager.checkInventory(event.player)
            }, 20L
        )
    }

    @AutoListener
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        UpdateManager.checkInventory(event.player)
    }

    @AutoListener
    fun onQuit(event: PlayerQuitEvent) {
        ItemCooldown.clear(event.player)
    }

    @AutoListener(priority = EventPriority.MONITOR)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        val stream = ItemStream(item)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        // 有自定义耐久时拦截原版耐久损耗
        if (itemDef.metaList.any { it.key == "durability" }) {
            event.isCancelled = true
            // 触发 on_damage 脚本
            val action = resolveAction(itemDef, ActionTrigger.ON_DAMAGE)
            if (action != null) {
                ActionExecutor.execute(action, event.player, stream, event, itemDef.eventVars)
                if (stream.signals.isNotEmpty()) {
                    rebuildItem(event.player, stream)
                }
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

    private fun resolveAction(
        item: OvertureItem,
        trigger: ActionTrigger
    ): ItemAction? {
        // 优先物品自身的动作
        item.actions[trigger]?.let { return it }
        // 其次从模型中查找
        for (modelId in item.modelIds) {
            val model = ItemManager.getModel(modelId) ?: continue
            model.actions[trigger]?.let { return it }
        }
        return null
    }

    private fun rebuildItem(player: Player, stream: ItemStream) {
        if (stream.signals.contains(ItemSignal.DURABILITY_DESTROYED)) {
            // 物品损坏处理
            handleItemDestroy(player, stream)
            return
        }

        // 1. 将脚本中的数据修改写回 sourceTag → sourceItem
        stream.save()
        BlinkLog.info("[rebuildItem] saved stream, overtureId=${stream.overtureId}")

        // 2. 完整重构：基于当前数据重新构建物品
        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val rebuilt = itemDef.build(player, stream)
        val result = rebuilt.toItemStack(player)
        BlinkLog.info("[rebuildItem] rebuilt complete, setting to main hand")

        // 3. 显式写回玩家主手（不依赖引用，确保生效）
        player.inventory.setItemInMainHand(result)
    }

    private fun handleItemDestroy(player: Player, stream: ItemStream) {
        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val durabilityMeta = itemDef.metaList.filterIsInstance<MetaDurability>().firstOrNull()

        val remains = durabilityMeta?.getRemainsItem()
        if (remains != null) {
            player.inventory.setItemInMainHand(remains)
        } else {
            // 播放破碎效果
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            player.inventory.setItemInMainHand(null)
        }
    }
}
