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

package priv.seventeen.artist.overture.core.meta.impl

import com.google.common.collect.LinkedHashMultimap
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.core.meta.MetaState

/**
 * 物品标志 Meta
 *
 * 配置格式:
 * ```yaml
 * item_flag:
 *   - HIDE_ATTRIBUTES
 *   - HIDE_ENCHANTS
 * ```
 */
@MetaKey("item_flag")
class MetaItemFlag(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "item_flag"

    /**
     * 最后执行
     * 隐藏标记必须在 attribute / enchantment 等 Meta 写完之后再打，
     * 否则无法判断物品最终是否带有显式属性修饰符
     */
    override val priority: Int = 100

    val flags: List<ItemFlag> = parseFlags()
    private var cleanupFlags: List<ItemFlag> = emptyList()

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        MetaState.putStrings(compound, key, flags.map { it.name })
    }

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        cleanupFlags = MetaState.getStrings(compound, key).mapNotNull {
            runCatching { ItemFlag.valueOf(it) }.getOrNull()
        }
    }

    override fun buildMeta(itemMeta: ItemMeta) {
        flags.forEach { itemMeta.addItemFlags(it) }
    }

    override fun buildRelease(itemStack: ItemStack, itemMeta: ItemMeta) {
        if (ItemFlag.HIDE_ATTRIBUTES in flags) {
            materializeDefaultAttributes(itemStack, itemMeta)
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        cleanupFlags.forEach { itemMeta.removeItemFlags(it) }
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        cleanupFlags = MetaState.getStrings(compound, key).mapNotNull {
            runCatching { ItemFlag.valueOf(it) }.getOrNull()
        }
        MetaState.remove(compound, key)
    }

    /**
     * 把材质的默认属性显式写入 ItemMeta（仅在物品没有任何显式属性修饰符时）
     */
    private fun materializeDefaultAttributes(itemStack: ItemStack, itemMeta: ItemMeta) {
        // 已有显式修饰符（例如 attribute Meta 写入的）时不介入，避免改变实际数值
        if (itemMeta.hasAttributeModifiers()) return

        val defaults = LinkedHashMultimap.create<Attribute, AttributeModifier>()
        for (slot in EquipmentSlot.values()) {
            val slotDefaults = try {
                itemStack.type.getDefaultAttributeModifiers(slot)
            } catch (_: Throwable) {
                // 高版本新增的槽位在部分端上可能不受支持
                continue
            }
            for ((attribute, modifier) in slotDefaults.entries()) {
                defaults.put(attribute, modifier)
            }
        }

        if (defaults.isEmpty) return
        itemMeta.setAttributeModifiers(defaults)
    }

    private fun parseFlags(): List<ItemFlag> {
        val list = when (value) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> listOf(value)
            else -> return emptyList()
        }
        return list.mapNotNull { name ->
            try {
                ItemFlag.valueOf(name.uppercase())
            } catch (_: Exception) {
                null
            }
        }
    }
}
