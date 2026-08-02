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

package priv.seventeen.artist.overture.core.component

import org.bukkit.configuration.ConfigurationSection
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.overture.api.data.ItemDataLimits
import priv.seventeen.artist.overture.api.data.ItemDataNode
import java.math.BigDecimal
import java.math.BigInteger

internal class ItemDataValidationException(
    val dataPath: String,
    message: String
) : IllegalArgumentException(message)

/** YAML / public tree / Asteroid ItemTag 间唯一的受限转换边界。 */
internal object ItemDataCodec {
    fun fromConfiguration(section: ConfigurationSection, sourcePath: String): ItemDataNode.Compound {
        val state = ReadState()
        val node = readSection(section, sourcePath, 0, state)
        validate(node, sourcePath, requireHomogeneousLists = false)
        return node
    }

    fun fromTag(tag: ItemTag): ItemDataNode.Compound =
        readTag(tag, "data", 0, ReadState()).also {
            validate(it, "data", requireHomogeneousLists = true)
        }

    fun fromTagData(data: ItemTagData): ItemDataNode =
        readTagData(data, "data", 0, ReadState()).also {
            validate(it, "data", requireHomogeneousLists = true)
        }

    fun toTag(compound: ItemDataNode.Compound): ItemTag {
        validate(compound, "data", requireHomogeneousLists = true)
        return ItemTag().also { target ->
            compound.values.forEach { (key, value) -> target[key] = toTagDataUnchecked(value) }
        }
    }

    fun toTagData(node: ItemDataNode): ItemTagData {
        validate(node, "data", requireHomogeneousLists = true)
        return toTagDataUnchecked(node)
    }

    fun validateForStorage(node: ItemDataNode, path: String) {
        validate(node, path, requireHomogeneousLists = true)
    }

    private fun readSection(
        section: ConfigurationSection,
        path: String,
        depth: Int,
        state: ReadState
    ): ItemDataNode.Compound {
        state.visit(path, depth)
        val values = linkedMapOf<String, ItemDataNode>()
        for (key in section.getKeys(false)) {
            val childPath = "$path.$key"
            val raw = section.get(key)
                ?: throw ItemDataValidationException(childPath, "组件数据不允许 null")
            values[key] = readValue(raw, childPath, depth + 1, state)
        }
        return ItemDataNode.Compound(values)
    }

    private fun readValue(raw: Any, path: String, depth: Int, state: ReadState): ItemDataNode {
        state.visit(path, depth)
        return when (raw) {
            is ConfigurationSection -> {
                val values = linkedMapOf<String, ItemDataNode>()
                for (key in raw.getKeys(false)) {
                    val child = raw.get(key)
                        ?: throw ItemDataValidationException("$path.$key", "组件数据不允许 null")
                    values[key] = readValue(child, "$path.$key", depth + 1, state)
                }
                ItemDataNode.Compound(values)
            }
            is Map<*, *> -> {
                val values = linkedMapOf<String, ItemDataNode>()
                raw.forEach { (key, value) ->
                    val textKey = key as? String
                        ?: throw ItemDataValidationException(path, "组件 mapping 的键必须是字符串")
                    if (value == null) {
                        throw ItemDataValidationException("$path.$textKey", "组件数据不允许 null")
                    }
                    values[textKey] = readValue(value, "$path.$textKey", depth + 1, state)
                }
                ItemDataNode.Compound(values)
            }
            is List<*> -> {
                if (raw.size > ItemDataLimits.MAX_LIST_LENGTH) {
                    throw ItemDataValidationException(path, "组件列表超过 ${ItemDataLimits.MAX_LIST_LENGTH} 项")
                }
                ItemDataNode.ListNode(raw.mapIndexed { index, value ->
                    readValue(
                        value ?: throw ItemDataValidationException("$path[$index]", "组件数据不允许 null"),
                        "$path[$index]",
                        depth + 1,
                        state
                    )
                })
            }
            is Boolean -> ItemDataNode.Bool(raw)
            is Byte, is Short, is Int, is Long -> ItemDataNode.Integer((raw as Number).toLong())
            is BigInteger -> ItemDataNode.Integer(
                try {
                    raw.longValueExact()
                } catch (_: ArithmeticException) {
                    throw ItemDataValidationException(path, "整数超出 Long 范围")
                }
            )
            is Float, is Double -> ItemDataNode.Decimal((raw as Number).toDouble().checkedFinite(path))
            is BigDecimal -> {
                val stripped = raw.stripTrailingZeros()
                if (stripped.scale() <= 0) {
                    ItemDataNode.Integer(
                        try {
                            stripped.longValueExact()
                        } catch (_: ArithmeticException) {
                            throw ItemDataValidationException(path, "整数超出 Long 范围")
                        }
                    )
                } else {
                    ItemDataNode.Decimal(raw.toDouble().checkedFinite(path))
                }
            }
            is String -> {
                if (raw.length > ItemDataLimits.MAX_STRING_LENGTH) {
                    throw ItemDataValidationException(path, "字符串超过 ${ItemDataLimits.MAX_STRING_LENGTH} 字符")
                }
                ItemDataNode.Text(raw)
            }
            else -> throw ItemDataValidationException(path, "不支持的组件类型 ${raw.javaClass.name}")
        }
    }

    private fun readTag(tag: ItemTag, path: String, depth: Int, state: ReadState): ItemDataNode.Compound {
        state.visit(path, depth)
        return readTagEntries(tag, path, depth, state)
    }

    private fun readTagEntries(
        tag: ItemTag,
        path: String,
        depth: Int,
        state: ReadState
    ): ItemDataNode.Compound = ItemDataNode.Compound(
        tag.entries.associateTo(linkedMapOf()) { (key, value) ->
            key to readTagData(value, "$path.$key", depth + 1, state)
        }
    )

    private fun readTagData(data: ItemTagData, path: String, depth: Int, state: ReadState): ItemDataNode {
        state.visit(path, depth)
        return when (data.type) {
            ItemTagType.BYTE -> ItemDataNode.Bool(data.asBoolean())
            ItemTagType.SHORT -> ItemDataNode.Integer(data.asShort().toLong())
            ItemTagType.INT -> ItemDataNode.Integer(data.asInt().toLong())
            ItemTagType.LONG -> ItemDataNode.Integer(data.asLong())
            ItemTagType.FLOAT -> ItemDataNode.Decimal(data.asFloat().toDouble().checkedFinite(path))
            ItemTagType.DOUBLE -> ItemDataNode.Decimal(data.asDouble().checkedFinite(path))
            ItemTagType.STRING -> ItemDataNode.Text(checkedString(data.asString(), path))
            ItemTagType.BYTE_ARRAY -> {
                val values = data.asByteArray()
                checkListLength(values.size, path)
                ItemDataNode.ListNode(values.map { ItemDataNode.Integer(it.toLong()) })
            }
            ItemTagType.INT_ARRAY -> {
                val values = data.asIntArray()
                checkListLength(values.size, path)
                ItemDataNode.ListNode(values.map { ItemDataNode.Integer(it.toLong()) })
            }
            ItemTagType.LONG_ARRAY -> {
                val values = data.asLongArray()
                checkListLength(values.size, path)
                ItemDataNode.ListNode(values.map { ItemDataNode.Integer(it) })
            }
            ItemTagType.LIST -> {
                val values = data.asList()
                checkListLength(values.size, path)
                ItemDataNode.ListNode(values.mapIndexed { index, child ->
                    readTagData(child, "$path[$index]", depth + 1, state)
                })
            }
            ItemTagType.COMPOUND -> readTagEntries(data.asCompound(), path, depth, state)
            ItemTagType.END, null -> throw ItemDataValidationException(path, "组件数据不支持 END/null NBT")
        }
    }

    private fun toTagDataUnchecked(node: ItemDataNode): ItemTagData = when (node) {
        is ItemDataNode.Bool -> ItemTagData.ofBoolean(node.value)
        is ItemDataNode.Integer -> ItemTagData.of(node.value)
        is ItemDataNode.Decimal -> ItemTagData.of(node.value)
        is ItemDataNode.Text -> ItemTagData.of(node.value)
        is ItemDataNode.Compound -> ItemTagData.of(ItemTag().also { tag ->
            node.values.forEach { (key, value) -> tag[key] = toTagDataUnchecked(value) }
        })
        is ItemDataNode.ListNode -> ItemTagData.of(ItemTagList().also { list ->
            node.values.forEach { list.add(toTagDataUnchecked(it)) }
        })
    }

    private fun validate(
        root: ItemDataNode,
        rootPath: String,
        requireHomogeneousLists: Boolean
    ) {
        val queue = ArrayDeque<Triple<ItemDataNode, Int, String>>()
        queue += Triple(root, 0, rootPath)
        var nodes = 0
        while (queue.isNotEmpty()) {
            val (node, depth, path) = queue.removeFirst()
            nodes++
            if (nodes > ItemDataLimits.MAX_NODES) {
                throw ItemDataValidationException(path, "组件节点超过 ${ItemDataLimits.MAX_NODES}")
            }
            if (depth > ItemDataLimits.MAX_DEPTH) {
                throw ItemDataValidationException(path, "组件深度超过 ${ItemDataLimits.MAX_DEPTH}")
            }
            when (node) {
                is ItemDataNode.Compound -> node.values.forEach { (key, value) ->
                    if (key.isBlank()) throw ItemDataValidationException(path, "组件键不能为空")
                    if (key.length > ItemDataLimits.MAX_STRING_LENGTH) {
                        throw ItemDataValidationException(path, "组件键超过 ${ItemDataLimits.MAX_STRING_LENGTH} 字符")
                    }
                    queue += Triple(value, depth + 1, "$path.$key")
                }
                is ItemDataNode.ListNode -> {
                    if (node.values.size > ItemDataLimits.MAX_LIST_LENGTH) {
                        throw ItemDataValidationException(path, "组件列表超过 ${ItemDataLimits.MAX_LIST_LENGTH} 项")
                    }
                    if (requireHomogeneousLists && node.values.map(::storageKind).distinct().size > 1) {
                        throw ItemDataValidationException(path, "NBT 列表中的组件节点类型必须一致")
                    }
                    node.values.forEachIndexed { index, value ->
                        queue += Triple(value, depth + 1, "$path[$index]")
                    }
                }
                is ItemDataNode.Text -> if (node.value.length > ItemDataLimits.MAX_STRING_LENGTH) {
                    throw ItemDataValidationException(path, "字符串超过 ${ItemDataLimits.MAX_STRING_LENGTH} 字符")
                }
                is ItemDataNode.Decimal -> if (!node.value.isFinite()) {
                    throw ItemDataValidationException(path, "组件小数必须是有限值")
                }
                else -> Unit
            }
        }
    }

    private fun storageKind(node: ItemDataNode): String = when (node) {
        is ItemDataNode.Bool -> "byte"
        is ItemDataNode.Integer -> "long"
        is ItemDataNode.Decimal -> "double"
        is ItemDataNode.Text -> "string"
        is ItemDataNode.Compound -> "compound"
        is ItemDataNode.ListNode -> "list"
    }

    private fun checkedString(value: String, path: String): String {
        if (value.length > ItemDataLimits.MAX_STRING_LENGTH) {
            throw ItemDataValidationException(path, "字符串超过 ${ItemDataLimits.MAX_STRING_LENGTH} 字符")
        }
        return value
    }

    private fun checkListLength(size: Int, path: String) {
        if (size > ItemDataLimits.MAX_LIST_LENGTH) {
            throw ItemDataValidationException(path, "组件列表超过 ${ItemDataLimits.MAX_LIST_LENGTH} 项")
        }
    }

    private fun Double.checkedFinite(path: String): Double {
        if (!isFinite()) throw ItemDataValidationException(path, "组件小数必须是有限值")
        return this
    }

    private class ReadState {
        private var nodes = 0

        fun visit(path: String, depth: Int) {
            nodes++
            if (nodes > ItemDataLimits.MAX_NODES) {
                throw ItemDataValidationException(path, "组件节点超过 ${ItemDataLimits.MAX_NODES}")
            }
            if (depth > ItemDataLimits.MAX_DEPTH) {
                throw ItemDataValidationException(path, "组件深度超过 ${ItemDataLimits.MAX_DEPTH}")
            }
        }
    }
}
