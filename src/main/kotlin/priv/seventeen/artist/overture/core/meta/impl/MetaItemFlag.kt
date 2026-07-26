package priv.seventeen.artist.overture.core.meta.impl

import com.google.common.collect.LinkedHashMultimap
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey

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

    override fun buildMeta(itemMeta: ItemMeta) {
        flags.forEach { itemMeta.addItemFlags(it) }
    }

    override fun buildRelease(itemStack: ItemStack, itemMeta: ItemMeta) {
        if (ItemFlag.HIDE_ATTRIBUTES in flags) {
            materializeDefaultAttributes(itemStack, itemMeta)
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        flags.forEach { itemMeta.removeItemFlags(it) }
    }

    /**
     * 把材质的默认属性显式写入 ItemMeta（仅在物品没有任何显式属性修饰符时）
     *
     * 1.20.5 起 HideFlags NBT 被组件取代，CraftBukkit 对 HIDE_ATTRIBUTES 的处理分两代：
     * - 1.20.5 ~ 1.21.4：写 `attribute_modifiers` 组件并置 `show_in_tooltip=false`。
     *   物品没有显式修饰符时写出的是空列表，而空列表会覆盖掉材质自带的默认属性 —— 结果
     *   护甲值不是被隐藏而是直接归零，部分端上表现为整行属性依旧显示。
     * - 1.21.5 起：改用 `tooltip_display` 组件的 hidden_components，隐藏与数值互不影响。
     *
     * 先把默认属性显式化，两代实现就都会走「非空修饰符 + 不显示」的路径：
     * 1.20.4 及以下显式 AttributeModifiers 本就覆盖默认值，数值同样等价，因此全版本安全。
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
