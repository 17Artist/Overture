package priv.seventeen.artist.overture.command

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.blink.command.BlinkCommand
import priv.seventeen.artist.blink.command.BlinkCommandRegistrar
import priv.seventeen.artist.blink.command.SenderType
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.core.item.ItemSerializer
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.LoaderManager
import priv.seventeen.artist.overture.core.manager.UpdateManager
import priv.seventeen.artist.overture.feature.ItemMenu
import priv.seventeen.artist.overture.util.ColorUtil

/**
 * Overture 命令注册
 */
object OvertureCommand {

    @Awake(LifeCycle.ENABLE)
    fun register() {
        BlinkCommandRegistrar.register(
            bukkitPlugin,
            BlinkCommand("overture", "ot", "oi")
                .command("list", "浏览物品列表", sender = SenderType.PLAYER) { ctx ->
                    val group = if (ctx.size > 0) {
                        LoaderManager.getGroup(ctx.arg(0))
                    } else null
                    ItemMenu.open(ctx.player!!, group)
                }
                .command("give", "发放物品", args = arrayOf("item", "?player", "?amount"), sender = SenderType.OP) { ctx ->
                    val itemId = ctx.arg(0)
                    val targetName = if (ctx.size > 1) ctx.arg(1) else null
                    val amount = ctx.argInt(2, 1)

                    val target = if (targetName != null) {
                        Bukkit.getPlayer(targetName)
                    } else {
                        ctx.player
                    }

                    if (target == null) {
                        ctx.reply(ColorUtil.colored("&c玩家不存在"))
                        return@command
                    }

                    if (ItemManager.getItem(itemId) == null) {
                        ctx.reply(ColorUtil.colored("&c物品不存在: $itemId"))
                        return@command
                    }

                    val success = ItemManager.give(target, itemId, amount)
                    if (success) {
                        ctx.reply(ColorUtil.colored("&a已发放 ${amount}x $itemId 给 ${target.name}"))
                    } else {
                        ctx.reply(ColorUtil.colored("&c发放失败"))
                    }
                }
                .command("get", "获取物品", args = arrayOf("item", "?amount"), sender = SenderType.PLAYER) { ctx ->
                    val itemId = ctx.arg(0)
                    val amount = ctx.argInt(1, 1)

                    if (ItemManager.getItem(itemId) == null) {
                        ctx.reply(ColorUtil.colored("&c物品不存在: $itemId"))
                        return@command
                    }

                    val success = ItemManager.give(ctx.player!!, itemId, amount)
                    if (success) {
                        ctx.reply(ColorUtil.colored("&a已获取 ${amount}x $itemId"))
                    } else {
                        ctx.reply(ColorUtil.colored("&c获取失败"))
                    }
                }
                .command("rebuild", "重构手持物品", sender = SenderType.PLAYER) { ctx ->
                    val player = ctx.player!!
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(ColorUtil.colored("&c请手持物品"))
                        return@command
                    }

                    val stream = ItemStream(item)
                    if (!stream.isOverture) {
                        ctx.reply(ColorUtil.colored("&c非 Overture 物品"))
                        return@command
                    }

                    val itemDef = ItemManager.getItem(stream.overtureId ?: "") ?: run {
                        ctx.reply(ColorUtil.colored("&c物品定义不存在"))
                        return@command
                    }

                    // 强制重构（不检查版本）
                    val rebuilt = itemDef.build(player, stream)
                    val result = rebuilt.toItemStack(player)
                    player.inventory.setItemInMainHand(result)
                    ctx.reply(ColorUtil.colored("&a物品已重构"))
                }
                .command("serialize", "序列化手持物品", sender = SenderType.PLAYER) { ctx ->
                    val player = ctx.player!!
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(ColorUtil.colored("&c请手持物品"))
                        return@command
                    }

                    val json = ItemSerializer.serialize(item)
                    ctx.reply(ColorUtil.colored("&a序列化结果:"))
                    ctx.reply(json)
                }
                .command("info", "查看物品信息", sender = SenderType.PLAYER) { ctx ->
                    val player = ctx.player!!
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(ColorUtil.colored("&c请手持物品"))
                        return@command
                    }

                    val stream = ItemStream(item)
                    if (!stream.isOverture) {
                        ctx.reply(ColorUtil.colored("&c非 Overture 物品"))
                        return@command
                    }

                    ctx.reply(ColorUtil.colored("&a--- 物品信息 ---"))
                    ctx.reply(ColorUtil.colored("&7ID: &f${stream.overtureId}"))
                    ctx.reply(ColorUtil.colored("&7版本: &f${stream.version}"))
                    ctx.reply(ColorUtil.colored("&7数据: &f${stream.overtureData}"))
                    val unique = stream.overtureUnique
                    if (!unique.isEmpty()) {
                        ctx.reply(ColorUtil.colored("&7唯一: &fUUID=${unique.getString("uuid")}"))
                        ctx.reply(ColorUtil.colored("&7  玩家: &f${unique.getString("player")}"))
                        ctx.reply(ColorUtil.colored("&7  时间: &f${unique.getString("date-formatted")}"))
                    }
                }
                .command("reload", "重载配置", sender = SenderType.OP) { ctx ->
                    priv.seventeen.artist.overture.OvertureConfig.reload()
                    priv.seventeen.artist.overture.Overture.applyConfig()
                    OvertureAPI.reload()
                    ctx.reply(ColorUtil.colored("&a配置已重载"))
                }
                .tabComplete("item") { ItemManager.getItemIds() }
                .tabComplete("player") { Bukkit.getOnlinePlayers().map { it.name } }
        )
    }
}
