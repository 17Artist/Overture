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

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.NamespacedKey
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.core.meta.MetaState

/**
 * 属性修饰符 Meta
 *
 * 配置格式:
 * ```yaml
 * attribute:
 *   mainhand:
 *     generic_attack_damage: "+7"
 *     generic_attack_speed: "+10%"
 *   offhand:
 *     generic_armor: "+2~5"
 * ```
 *
 * 支持:
 * - "+n" 固定加成 (operation=0, ADD_VALUE)
 * - "+n%" 百分比加成 (operation=1, ADD_MULTIPLIED_BASE)
 * - "+min~max" 区间随机
 */
@MetaKey("attribute")
class MetaAttribute(
    private val section: ConfigurationSection?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "attribute"

    private val modifiers: List<AttributeEntry> = parseModifiers()
    private var cleanupModifiers: List<BuiltAttribute> = emptyList()

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        val existing = MetaState.getStrings(compound, key)
        if (existing.isNotEmpty() && !signals.contains(ItemSignal.UPDATE_CHECKED)) return

        val built = modifiers.mapIndexed { index, entry ->
            BuiltAttribute(
                entry.attribute,
                entry.computeAmount(),
                entry.operation.coerceIn(0, 2),
                entry.slot,
                modifierId(entry, index)
            )
        }
        MetaState.putStrings(compound, key, built.map { it.encode() })
    }

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        cleanupModifiers = MetaState.getStrings(compound, key).mapNotNull(BuiltAttribute::decode)
    }

    override fun buildMeta(itemMeta: ItemMeta) {
        writeModifiers(
            itemMeta,
            modifiers.mapIndexed { index, entry ->
                BuiltAttribute(
                    entry.attribute,
                    entry.computeAmount(),
                    entry.operation.coerceIn(0, 2),
                    entry.slot,
                    modifierId(entry, index)
                )
            }
        )
    }

    override fun buildMeta(itemMeta: ItemMeta, compound: ItemTag) {
        val built = MetaState.getStrings(compound, key).mapNotNull { BuiltAttribute.decode(it) }
        if (built.isEmpty()) {
            buildMeta(itemMeta)
        } else {
            writeModifiers(itemMeta, built)
        }
    }

    private fun writeModifiers(itemMeta: ItemMeta, built: List<BuiltAttribute>) {
        val nms = AsteroidAPI.getAttributeItemNMS()
        for (entry in built) {
            try {
                // 重建会沿用原 ItemMeta；同一 Overture 标识必须先替换再写入，
                // 否则 Bukkit 会因相同 UUID/NamespacedKey 已存在而拒绝注册。
                removeOwnedModifier(itemMeta, entry.modifierId)
                nms.addModifier(
                    itemMeta,
                    resolveRuntimeAttribute(entry.attribute),
                    entry.modifierId,
                    entry.amount,
                    entry.operation,
                    entry.slot
                )
            } catch (e: Exception) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.attribute-write-failed",
                        "attribute" to entry.attribute,
                        "error" to (e.message ?: e.javaClass.simpleName)
                    )
                )
            }
        }
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        cleanupModifiers = MetaState.getStrings(compound, key).mapNotNull(BuiltAttribute::decode)
        MetaState.remove(compound, key)
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        for (entry in cleanupModifiers) {
            removeOwnedModifier(itemMeta, entry.modifierId)
        }
    }

    internal fun removeOwnedModifier(itemMeta: ItemMeta, modifierId: String): Int {
        val allModifiers = itemMeta.attributeModifiers ?: return 0
        var removed = 0
        for ((attribute, modifier) in allModifiers.entries().toList()) {
            if (modifierIdentifier(modifier) == modifierId &&
                itemMeta.removeAttributeModifier(attribute, modifier)
            ) {
                removed++
            }
        }
        return removed
    }

    private fun modifierIdentifier(modifier: AttributeModifier): String? {
        val key = runCatching {
            modifier.javaClass.getMethod("getKey").invoke(modifier)?.toString()
        }.getOrNull()
        if (key != null) return key
        return runCatching {
            modifier.javaClass.getMethod("getName").invoke(modifier)?.toString()
        }.getOrNull()
    }

    private fun parseModifiers(): List<AttributeEntry> {
        val result = mutableListOf<AttributeEntry>()
        section ?: return result

        for (slotKey in section.getKeys(false)) {
            val slotSection = section.getConfigurationSection(slotKey) ?: continue
            val slot = normalizeSlot(slotKey)
            if (slot == null && slotKey.lowercase() !in setOf("any", "all")) {
                BlinkLog.warn(LanguageManager.text("console.attribute-slot-invalid", "slot" to slotKey))
                continue
            }

            for (attrKey in slotSection.getKeys(false)) {
                val valueStr = slotSection.getString(attrKey) ?: continue
                val entry = parseAttributeValue(attrKey, valueStr, slot)
                if (entry != null) {
                    result.add(entry)
                }
            }
        }
        return result
    }

    private fun parseAttributeValue(attribute: String, value: String, slot: String?): AttributeEntry? {
        val cleanValue = value.removePrefix("+")

        // 百分比: "10%"  → operation=1 (ADD_MULTIPLIED_BASE)
        if (cleanValue.endsWith("%")) {
            val num = cleanValue.removeSuffix("%").toDoubleOrNull() ?: return null
            return AttributeEntry(attribute, num / 100.0, 1, slot)
        }

        // 区间随机: "2~5" → operation=0 (ADD_VALUE)
        if (cleanValue.contains("~")) {
            val parts = cleanValue.split("~")
            if (parts.size == 2) {
                val min = parts[0].toDoubleOrNull() ?: return null
                val max = parts[1].toDoubleOrNull() ?: return null
                return AttributeEntry(attribute, minOf(min, max), 0, slot, maxOf(min, max))
            }
        }

        // 固定值: "7" → operation=0 (ADD_VALUE)
        val num = cleanValue.toDoubleOrNull() ?: return null
        return AttributeEntry(attribute, num, 0, slot)
    }

    /**
     * 标准化槽位名称，与 Asteroid IAttributeItemNMS 约定一致
     */
    private fun normalizeSlot(slot: String): String? {
        return when (slot.lowercase()) {
            "mainhand", "main_hand" -> "hand"
            "offhand", "off_hand" -> "offhand"
            "head", "helmet" -> "head"
            "chest", "chestplate" -> "chest"
            "legs", "leggings" -> "legs"
            "feet", "boots" -> "feet"
            "any", "all" -> null  // null = 所有槽位
            else -> null
        }
    }

    private fun modifierId(entry: AttributeEntry, index: Int): String {
        val key = "${entry.attribute}_${entry.slot ?: "any"}_$index"
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "_")
        return "overture:$key"
    }

    /**
     * 1.21+ 的属性注册键去掉了部分 `generic_` / `generic.` 前缀，而旧版
     * Asteroid 仍接受枚举式名称。通过运行时 Registry 探测选出真实存在的键，
     * 在 1.18 等旧版上则保留原值交给 Asteroid 的兼容层处理。
     */
    private fun resolveRuntimeAttribute(attribute: String): String {
        return ATTRIBUTE_ALIASES.computeIfAbsent(attribute) { raw ->
            val path = raw.removePrefix("minecraft:").lowercase()
            val candidates = linkedSetOf(
                if (raw.contains(':')) raw.lowercase() else "minecraft:$path",
                "minecraft:${path.replace('.', '_')}"
            )
            val firstUnderscore = path.indexOf('_')
            if (firstUnderscore > 0) {
                candidates.add(
                    "minecraft:${path.substring(0, firstUnderscore)}.${path.substring(firstUnderscore + 1)}"
                )
            }
            if (path.startsWith("generic_")) {
                candidates.add("minecraft:${path.removePrefix("generic_")}")
            }
            if (path.startsWith("generic.")) {
                candidates.add("minecraft:${path.removePrefix("generic.").replace('.', '_')}")
            }

            val registry = runCatching {
                Class.forName("org.bukkit.Registry").getField("ATTRIBUTE").get(null)
            }.getOrNull() ?: return@computeIfAbsent raw
            val getMethod = registry.javaClass.methods.firstOrNull {
                it.name == "get" && it.parameterCount == 1 &&
                    it.parameterTypes[0].name == NamespacedKey::class.java.name
            } ?: return@computeIfAbsent raw

            candidates.firstOrNull { candidate ->
                val key = NamespacedKey.fromString(candidate) ?: return@firstOrNull false
                runCatching { getMethod.invoke(registry, key) != null }.getOrDefault(false)
            } ?: raw
        }
    }

    data class AttributeEntry(
        val attribute: String,
        val amount: Double,
        /** 0=ADD_VALUE, 1=ADD_MULTIPLIED_BASE, 2=ADD_MULTIPLIED_TOTAL */
        val operation: Int,
        val slot: String?,
        val maxAmount: Double? = null
    ) {
        fun computeAmount(): Double {
            return if (maxAmount != null) {
                amount + Math.random() * (maxAmount - amount)
            } else {
                amount
            }
        }
    }

    private data class BuiltAttribute(
        val attribute: String,
        val amount: Double,
        val operation: Int,
        val slot: String?,
        val modifierId: String
    ) {
        fun encode(): String {
            return listOf(attribute, amount.toString(), operation.toString(), slot.orEmpty(), modifierId)
                .joinToString(SEPARATOR)
        }

        companion object {
            private const val SEPARATOR = "\u001f"

            fun decode(encoded: String): BuiltAttribute? {
                val parts = encoded.split(SEPARATOR)
                if (parts.size != 5) return null
                return BuiltAttribute(
                    attribute = parts[0],
                    amount = parts[1].toDoubleOrNull() ?: return null,
                    operation = parts[2].toIntOrNull()?.coerceIn(0, 2) ?: return null,
                    slot = parts[3].ifEmpty { null },
                    modifierId = parts[4]
                )
            }
        }
    }

    companion object {
        private val ATTRIBUTE_ALIASES = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
