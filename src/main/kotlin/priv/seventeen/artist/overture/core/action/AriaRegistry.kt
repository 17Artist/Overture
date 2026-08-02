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

package priv.seventeen.artist.overture.core.action

import org.bukkit.entity.Player
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.aria.callable.CallableManager
import priv.seventeen.artist.aria.callable.InvocationData
import priv.seventeen.artist.aria.value.BooleanValue
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.NoneValue
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.aria.value.StringValue
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.feature.ItemCooldown

/**
 * 注册 Overture 自有的 Aria 对象函数。
 */
object AriaRegistry {

    @Volatile
    private var registered = false

    private val functionClasses = listOf(
        OvertureItemAriaFunctions::class.java,
        OverturePlayerAriaFunctions::class.java
    )

    @Synchronized
    fun register() {
        if (registered) return

        val manager = CallableManager.INSTANCE
        functionClasses.forEach { functionClass ->
            manager.registerObjectFunction(functionClass)
            BlinkLog.info(
                LanguageManager.text(
                    "console.aria-class-registered",
                    "class" to functionClass.simpleName
                )
            )
        }
        registered = true
    }
}

/** val.item（ItemStream）上的 Overture 物品函数。 */
object OvertureItemAriaFunctions {

    @JvmStatic
    @AriaInvokeHandler(value = "damage", target = ItemStream::class)
    fun damage(data: InvocationData): IValue<*> {
        val stream = target(data)
        val amount = optionalPositiveInt(data, 0, 0)
        val current = stream.getData("durability_current")?.asInt() ?: 0
        val newValue = (current - amount).coerceAtLeast(0)
        stream.setData("durability_current", ItemTagData.of(newValue))
        stream.signals.add(ItemSignal.DURABILITY_CHANGED)
        if (newValue <= 0) stream.signals.add(ItemSignal.DURABILITY_DESTROYED)
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "repair", target = ItemStream::class)
    fun repair(data: InvocationData): IValue<*> {
        val stream = target(data)
        val amount = optionalPositiveInt(data, 0, 0)
        val current = stream.getData("durability_current")?.asInt() ?: 0
        val max = (stream.getData("durability")?.asInt() ?: 0).coerceAtLeast(0)
        stream.setData("durability_current", ItemTagData.of((current + amount).coerceIn(0, max)))
        stream.signals.add(ItemSignal.DURABILITY_CHANGED)
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "durability", target = ItemStream::class)
    fun durability(data: InvocationData): IValue<*> =
        NumberValue(target(data).getData("durability_current")?.asInt()?.toDouble() ?: 0.0)

    @JvmStatic
    @AriaInvokeHandler(value = "maxDurability", target = ItemStream::class)
    fun maxDurability(data: InvocationData): IValue<*> =
        NumberValue(target(data).getData("durability")?.asInt()?.toDouble() ?: 0.0)

    @JvmStatic
    @AriaInvokeHandler(value = "consume", target = ItemStream::class)
    fun consume(data: InvocationData): IValue<*> {
        val stream = target(data)
        val amount = optionalPositiveInt(data, 0, 1)
        stream.sourceItem.amount = (stream.sourceItem.amount - amount).coerceAtLeast(0)
        stream.signals.add(ItemSignal.ITEM_CHANGED)
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "data", target = ItemStream::class)
    fun itemData(data: InvocationData): IValue<*> {
        val stream = target(data)
        val key = data.get(0).stringValue()
        if (data.argCount() > 1) {
            val value = data.get(1)
            val tagData = when (value) {
                is NumberValue -> ItemTagData.of(value.numberValue().toInt())
                is StringValue -> ItemTagData.of(value.stringValue())
                is BooleanValue -> ItemTagData.ofBoolean(value.booleanValue())
                else -> ItemTagData.of(value.stringValue())
            }
            stream.setData(key, tagData)
            stream.signals.add(ItemSignal.ITEM_CHANGED)
            return NoneValue.NONE
        }

        val tagData = stream.getData(key) ?: return NoneValue.NONE
        return when (tagData.type) {
            ItemTagType.INT,
            ItemTagType.DOUBLE,
            ItemTagType.FLOAT,
            ItemTagType.LONG,
            ItemTagType.SHORT,
            ItemTagType.BYTE -> NumberValue(tagData.asDouble())
            ItemTagType.STRING -> StringValue(tagData.asString())
            else -> StringValue(tagData.asString())
        }
    }

    @JvmStatic
    @AriaInvokeHandler(value = "removeData", target = ItemStream::class)
    fun removeData(data: InvocationData): IValue<*> {
        val stream = target(data)
        stream.removeData(data.get(0).stringValue())
        stream.signals.add(ItemSignal.ITEM_CHANGED)
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "update", target = ItemStream::class)
    fun update(data: InvocationData): IValue<*> {
        target(data).signals.add(ItemSignal.ITEM_CHANGED)
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "id", target = ItemStream::class)
    fun id(data: InvocationData): IValue<*> = StringValue(target(data).overtureId ?: "")

    @JvmStatic
    @AriaInvokeHandler(value = "amount", target = ItemStream::class)
    fun amount(data: InvocationData): IValue<*> = NumberValue(target(data).sourceItem.amount.toDouble())

    @JvmStatic
    @AriaInvokeHandler(value = "uses", target = ItemStream::class)
    fun uses(data: InvocationData): IValue<*> =
        NumberValue(target(data).getData("uses")?.asInt()?.toDouble() ?: 0.0)

    @JvmStatic
    @AriaInvokeHandler(value = "use", target = ItemStream::class)
    fun use(data: InvocationData): IValue<*> {
        val stream = target(data)
        val amount = optionalPositiveInt(data, 0, 1)
        val current = stream.getData("uses")?.asInt() ?: 0
        if (current > 0) {
            val newValue = (current - amount).coerceAtLeast(0)
            stream.setData("uses", ItemTagData.of(newValue))
            stream.signals.add(ItemSignal.ITEM_CHANGED)
            if (newValue <= 0) stream.sourceItem.amount = 0
        }
        return NoneValue.NONE
    }

    private fun target(data: InvocationData): ItemStream = data.target as ItemStream

    private fun optionalPositiveInt(data: InvocationData, index: Int, defaultValue: Int): Int =
        if (data.argCount() > index) data.get(index).numberValue().toInt().coerceAtLeast(0) else defaultValue
}

/** val.player（Player）上的 Overture 物品冷却函数。 */
object OverturePlayerAriaFunctions {

    @JvmStatic
    @AriaInvokeHandler(value = "checkItemCooldown", target = Player::class)
    fun checkItemCooldown(data: InvocationData): IValue<*> {
        val player = data.target as Player
        val key = data.get(0).stringValue()
        val duration = data.get(1).numberValue().toLong().coerceAtLeast(0L)
        return BooleanValue.of(ItemCooldown.check(player, key, duration))
    }

    @JvmStatic
    @AriaInvokeHandler(value = "setItemCooldown", target = Player::class)
    fun setItemCooldown(data: InvocationData): IValue<*> {
        ItemCooldown.set(data.target as Player, data.get(0).stringValue())
        return NoneValue.NONE
    }

    @JvmStatic
    @AriaInvokeHandler(value = "itemCooldownRemaining", target = Player::class)
    fun itemCooldownRemaining(data: InvocationData): IValue<*> {
        val player = data.target as Player
        val key = data.get(0).stringValue()
        val duration = data.get(1).numberValue().toLong().coerceAtLeast(0L)
        return NumberValue(ItemCooldown.remaining(player, key, duration).toDouble())
    }
}
