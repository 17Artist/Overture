package priv.seventeen.artist.overture.core.action

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import priv.seventeen.artist.aria.callable.CallableManager
import priv.seventeen.artist.aria.callable.InvocationData
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.aria.interop.JavaObjectMirror
import priv.seventeen.artist.aria.value.*
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.feature.ItemCooldown
import priv.seventeen.artist.overture.util.ColorUtil

/**
 * Aria 内置函数注册
 * 注册所有 Overture 提供的 Aria 命名空间函数
 */
object AriaRegistry {

    fun register() {
        registerItemFunctions()
        registerCooldownFunctions()
        registerPotionFunctions()
        registerGlobalFunctions()
    }

    // ==================== item 命名空间 ====================

    private fun registerItemFunctions() {
        val manager = CallableManager.INSTANCE

        // item.damage(n) — 扣除耐久
        manager.registerStaticFunction("item", "damage") { data ->
            val stream = getStream(data)
            val amount = data.get(0).numberValue().toInt()
            if (stream != null) {
                val current = stream.getData("durability_current")?.asInt() ?: 0
                val newValue = (current - amount).coerceAtLeast(0)
                stream.setData("durability_current", ItemTagData.of(newValue))
                stream.signals.add(ItemSignal.DURABILITY_CHANGED)
                if (newValue <= 0) {
                    stream.signals.add(ItemSignal.DURABILITY_DESTROYED)
                }
            }
            NoneValue.NONE
        }

        // item.repair(n) — 恢复耐久
        manager.registerStaticFunction("item", "repair") { data ->
            val stream = getStream(data)
            val amount = data.get(0).numberValue().toInt()
            if (stream != null) {
                val current = stream.getData("durability_current")?.asInt() ?: 0
                val max = stream.getData("durability")?.asInt() ?: 0
                val newValue = (current + amount).coerceIn(0, max)
                stream.setData("durability_current", ItemTagData.of(newValue))
                stream.signals.add(ItemSignal.DURABILITY_CHANGED)
            }
            NoneValue.NONE
        }

        // item.durability() — 获取当前耐久
        manager.registerStaticFunction("item", "durability") { data ->
            val stream = getStream(data)
            NumberValue(stream?.getData("durability_current")?.asInt()?.toDouble() ?: 0.0)
        }

        // item.maxDurability() — 获取最大耐久
        manager.registerStaticFunction("item", "maxDurability") { data ->
            val stream = getStream(data)
            NumberValue(stream?.getData("durability")?.asInt()?.toDouble() ?: 0.0)
        }

        // item.consume(n?) — 消耗物品
        manager.registerStaticFunction("item", "consume") { data ->
            val stream = getStream(data)
            val amount = if (data.argCount() > 0) data.get(0).numberValue().toInt() else 1
            if (stream != null) {
                val item = stream.sourceItem
                item.amount = (item.amount - amount).coerceAtLeast(0)
            }
            NoneValue.NONE
        }

        // item.data(key) — 读取数据
        manager.registerStaticFunction("item", "data") { data ->
            val stream = getStream(data)
            val key = data.get(0).stringValue()
            if (data.argCount() > 1) {
                // item.data(key, value) — 写入数据
                val value = data.get(1)
                if (stream != null) {
                    val tagData = when {
                        value is NumberValue -> ItemTagData.of(value.numberValue().toInt())
                        value is StringValue -> ItemTagData.of(value.stringValue())
                        value is BooleanValue -> ItemTagData.ofBoolean(value.booleanValue())
                        else -> ItemTagData.of(value.stringValue())
                    }
                    stream.setData(key, tagData)
                    // 写入数据后自动标记需要 rebuild，确保修改被保存
                    stream.signals.add(ItemSignal.DURABILITY_CHANGED)
                }
                NoneValue.NONE
            } else {
                // 读取
                val tagData = stream?.getData(key)
                if (tagData != null) {
                    when (tagData.type) {
                        ItemTagType.INT,
                        ItemTagType.DOUBLE,
                        ItemTagType.FLOAT,
                        ItemTagType.LONG,
                        ItemTagType.SHORT,
                        ItemTagType.BYTE -> NumberValue(tagData.asDouble())
                        ItemTagType.STRING -> StringValue(tagData.asString())
                        else -> StringValue(tagData.asString())
                    }
                } else {
                    NoneValue.NONE
                }
            }
        }

        // item.removeData(key) — 删除数据
        manager.registerStaticFunction("item", "removeData") { data ->
            val stream = getStream(data)
            val key = data.get(0).stringValue()
            if (stream != null) {
                stream.removeData(key)
                stream.signals.add(ItemSignal.DURABILITY_CHANGED)
            }
            NoneValue.NONE
        }

        // item.update() — 标记需要更新
        manager.registerStaticFunction("item", "update") { data ->
            val stream = getStream(data)
            stream?.signals?.add(ItemSignal.DURABILITY_CHANGED)
            NoneValue.NONE
        }

        // item.id() — 获取物品 ID
        manager.registerStaticFunction("item", "id") { data ->
            val stream = getStream(data)
            StringValue(stream?.overtureId ?: "")
        }

        // item.amount() — 获取物品数量
        manager.registerStaticFunction("item", "amount") { data ->
            val stream = getStream(data)
            NumberValue(stream?.sourceItem?.amount?.toDouble() ?: 0.0)
        }

        // item.uses() — 获取剩余使用次数
        manager.registerStaticFunction("item", "uses") { data ->
            val stream = getStream(data)
            NumberValue(stream?.getData("uses")?.asInt()?.toDouble() ?: 0.0)
        }

        // item.use(n?) — 消耗使用次数，默认 1。次数耗尽时自动消耗物品
        manager.registerStaticFunction("item", "use") { data ->
            val stream = getStream(data)
            val amount = if (data.argCount() > 0) data.get(0).numberValue().toInt() else 1
            if (stream != null) {
                val current = stream.getData("uses")?.asInt() ?: 0
                if (current > 0) {
                    val newValue = (current - amount).coerceAtLeast(0)
                    stream.setData("uses", ItemTagData.of(newValue))
                    stream.signals.add(ItemSignal.DURABILITY_CHANGED)
                    if (newValue <= 0) {
                        stream.sourceItem.amount = 0
                    }
                }
            }
            NoneValue.NONE
        }
    }

    // ==================== cooldown 命名空间 ====================

    private fun registerCooldownFunctions() {
        val manager = CallableManager.INSTANCE

        // cooldown.check(key, ms) — 检查冷却是否结束
        manager.registerStaticFunction("cooldown", "check") { data ->
            val player = getPlayer(data)
            val key = data.get(0).stringValue()
            val ms = data.get(1).numberValue().toLong()
            if (player != null) {
                BooleanValue.of(ItemCooldown.check(player, key, ms))
            } else {
                BooleanValue.of(false)
            }
        }

        // cooldown.set(key) — 设置冷却
        manager.registerStaticFunction("cooldown", "set") { data ->
            val player = getPlayer(data)
            val key = data.get(0).stringValue()
            if (player != null) {
                ItemCooldown.set(player, key)
            }
            NoneValue.NONE
        }

        // cooldown.time(key, ms) — 获取剩余冷却时间
        manager.registerStaticFunction("cooldown", "time") { data ->
            val player = getPlayer(data)
            val key = data.get(0).stringValue()
            val duration = data.get(1).numberValue().toLong()
            if (player != null) {
                NumberValue(ItemCooldown.remaining(player, key, duration).toDouble())
            } else {
                NumberValue(0.0)
            }
        }
    }

    // ==================== potion 命名空间 ====================

    private fun registerPotionFunctions() {
        val manager = CallableManager.INSTANCE

        // potion.give(type, duration, amplifier)
        manager.registerStaticFunction("potion", "give") { data ->
            val player = getPlayer(data)
            val typeName = data.get(0).stringValue()
            val duration = data.get(1).numberValue().toInt()
            val amplifier = if (data.argCount() > 2) data.get(2).numberValue().toInt() else 0
            if (player != null) {
                val type = resolvePotionType(typeName)
                if (type != null) {
                    player.addPotionEffect(PotionEffect(type, duration, amplifier))
                }
            }
            NoneValue.NONE
        }

        // potion.remove(type)
        manager.registerStaticFunction("potion", "remove") { data ->
            val player = getPlayer(data)
            val typeName = data.get(0).stringValue()
            if (player != null) {
                val type = resolvePotionType(typeName)
                if (type != null) {
                    player.removePotionEffect(type)
                }
            }
            NoneValue.NONE
        }

        // potion.clear()
        manager.registerStaticFunction("potion", "clear") { data ->
            val player = getPlayer(data)
            player?.activePotionEffects?.forEach { player.removePotionEffect(it.type) }
            NoneValue.NONE
        }
    }

    // ==================== 全局函数 ====================

    private fun registerGlobalFunctions() {
        val manager = CallableManager.INSTANCE

        // cancel() — 取消事件
        manager.registerStaticFunction("", "cancel") { data ->
            val event = data.context.globalStorage
                .getGlobalVariable(VariableKey.of("event")).value
            val obj = unwrapJava(event)
            if (obj is Cancellable) {
                obj.isCancelled = true
            }
            NoneValue.NONE
        }

        // sendMessage(msg) — 发送消息
        manager.registerStaticFunction("", "sendMessage") { data ->
            val player = getPlayer(data)
            val msg = data.get(0).stringValue()
            player?.sendMessage(ColorUtil.colored(msg))
            NoneValue.NONE
        }

        // playSound(sound, volume?, pitch?)
        manager.registerStaticFunction("", "playSound") { data ->
            val player = getPlayer(data)
            val soundName = data.get(0).stringValue()
            val volume = if (data.argCount() > 1) data.get(1).numberValue().toFloat() else 1.0f
            val pitch = if (data.argCount() > 2) data.get(2).numberValue().toFloat() else 1.0f
            if (player != null) {
                try {
                    val sound = Sound.valueOf(soundName.uppercase())
                    player.playSound(player.location, sound, volume, pitch)
                } catch (_: Exception) {
                }
            }
            NoneValue.NONE
        }

        // runCommand(cmd) — 以玩家身份执行命令
        manager.registerStaticFunction("", "runCommand") { data ->
            val player = getPlayer(data)
            val cmd = data.get(0).stringValue()
            player?.performCommand(cmd)
            NoneValue.NONE
        }

        // broadcast(msg) — 全服广播
        manager.registerStaticFunction("", "broadcast") { data ->
            val msg = data.get(0).stringValue()
            Bukkit.broadcastMessage(ColorUtil.colored(msg))
            NoneValue.NONE
        }
    }

    // ==================== 辅助方法 ====================

    private fun unwrapJava(value: IValue<*>): Any? {
        if (value !is ObjectValue<*>) return null
        val jvm = value.jvmValue()
        return if (jvm is JavaObjectMirror) jvm.javaObject else jvm
    }

    private fun getPlayer(data: InvocationData): Player? {
        val value = data.context.globalStorage
            .getGlobalVariable(VariableKey.of("player")).value
        return unwrapJava(value) as? Player
    }

    private fun getStream(data: InvocationData): ItemStream? {
        val value = data.context.globalStorage
            .getGlobalVariable(VariableKey.of("item")).value
        return unwrapJava(value) as? ItemStream
    }

    @Suppress("DEPRECATION")
    private fun resolvePotionType(name: String): PotionEffectType? {
        return PotionEffectType.getByName(name.uppercase())
    }
}
