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

package priv.seventeen.artist.overture.util

import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.asteroid.item.ItemTagType
import org.bukkit.configuration.ConfigurationSection

/**
 * YAML ConfigurationSection ↔ ItemTag 双向转换器
 *
 * 类型推断规则:
 * - "10s" → Short
 * - "10L" → Long
 * - "10f" → Float
 * - "10b" → Byte
 * - 10 → Int
 * - 10.0 → Double
 * - true/false → Byte(1/0)
 * - [...] → ItemTagList
 * - {...} → ItemTag (Compound)
 * - key!! → 去掉后缀，标记为 locked
 */
object Translator {

    /**
     * 将 ConfigurationSection 转换为 ItemTag
     * @return Pair<ItemTag, Map<String, ItemTagData?>> (转换结果, locked 数据映射)
     */
    fun fromSection(section: ConfigurationSection, lockAll: Boolean = false): TranslateResult {
        val tag = ItemTag()
        val lockedData = mutableMapOf<String, ItemTagData?>()
        translateSection(section, tag, "", lockedData)
        if (lockAll) {
            tag.forEach { (key, value) -> lockedData[key] = value.clone() }
        }
        return TranslateResult(tag, lockedData)
    }

    /**
     * 将 ConfigurationSection 转换为 ItemTag（不收集 locked 数据）
     */
    fun toItemTag(section: ConfigurationSection): ItemTag {
        val tag = ItemTag()
        for (key in section.getKeys(false)) {
            val cleanKey = key.removeSuffix("!!")
            val value = section.get(key) ?: continue
            tag.put(cleanKey, toItemTagData(value))
        }
        return tag
    }

    /**
     * 将任意值转换为 ItemTagData
     */
    fun toItemTagData(value: Any): ItemTagData {
        return when (value) {
            is Boolean -> ItemTagData.ofBoolean(value)
            is Int -> ItemTagData.of(value)
            is Long -> ItemTagData.of(value)
            is Double -> ItemTagData.of(value)
            is Float -> ItemTagData.of(value)
            is Short -> ItemTagData.of(value)
            is Byte -> ItemTagData.of(value)
            is String -> parseString(value)
            is List<*> -> {
                val list = ItemTagList()
                value.filterNotNull().forEach { list.add(toItemTagData(it)) }
                ItemTagData.of(list)
            }
            is ConfigurationSection -> ItemTagData.of(toItemTag(value))
            is Map<*, *> -> {
                val compound = ItemTag()
                value.forEach { (k, v) ->
                    if (k != null && v != null) {
                        compound.put(k.toString().removeSuffix("!!"), toItemTagData(v))
                    }
                }
                ItemTagData.of(compound)
            }
            else -> ItemTagData.of(value.toString())
        }
    }

    /**
     * 解析字符串类型标注
     */
    private fun parseString(value: String): ItemTagData {
        // Short: "10s"
        if (value.endsWith("s") && value.length > 1) {
            value.dropLast(1).toShortOrNull()?.let { return ItemTagData.of(it) }
        }
        // Long: "10L"
        if (value.endsWith("L") && value.length > 1) {
            value.dropLast(1).toLongOrNull()?.let { return ItemTagData.of(it) }
        }
        // Float: "10f"
        if (value.endsWith("f") && value.length > 1) {
            value.dropLast(1).toFloatOrNull()?.let { return ItemTagData.of(it) }
        }
        // Byte: "10b"
        if (value.endsWith("b") && value.length > 1) {
            value.dropLast(1).toByteOrNull()?.let { return ItemTagData.of(it) }
        }
        return ItemTagData.of(value)
    }

    private fun translateSection(
        section: ConfigurationSection,
        tag: ItemTag,
        prefix: String,
        lockedData: MutableMap<String, ItemTagData?>
    ) {
        for (key in section.getKeys(false)) {
            val isLocked = key.endsWith("!!")
            val cleanKey = key.removeSuffix("!!")
            val fullPath = if (prefix.isEmpty()) cleanKey else "$prefix.$cleanKey"
            val value = section.get(key) ?: continue

            if (value is ConfigurationSection) {
                val subTag = ItemTag()
                translateSection(value, subTag, fullPath, lockedData)
                tag.put(cleanKey, ItemTagData.of(subTag))
                if (isLocked) {
                    lockedData[fullPath] = ItemTagData.of(subTag)
                }
            } else {
                val data = toItemTagData(value)
                tag.put(cleanKey, data)
                if (isLocked) {
                    lockedData[fullPath] = data
                }
            }
        }
    }

    /**
     * 将 ItemTag 递归展平为 Map<String, Any>
     * 用于 data-mapper 变量引用
     */
    fun flatten(tag: ItemTag, prefix: String = ""): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((key, data) in tag) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (data.type) {
                ItemTagType.COMPOUND -> {
                    result.putAll(flatten(data.asCompound(), fullKey))
                }
                ItemTagType.INT -> result[fullKey] = data.asInt()
                ItemTagType.DOUBLE -> result[fullKey] = data.asDouble()
                ItemTagType.FLOAT -> result[fullKey] = data.asFloat()
                ItemTagType.LONG -> result[fullKey] = data.asLong()
                ItemTagType.SHORT -> result[fullKey] = data.asShort()
                ItemTagType.BYTE -> result[fullKey] = data.asByte()
                ItemTagType.STRING -> result[fullKey] = data.asString()
                else -> result[fullKey] = data.asString()
            }
        }
        return result
    }

    data class TranslateResult(
        val tag: ItemTag,
        val lockedData: Map<String, ItemTagData?>
    )
}
