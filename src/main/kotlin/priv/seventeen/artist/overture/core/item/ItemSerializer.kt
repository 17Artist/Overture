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

package priv.seventeen.artist.overture.core.item

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.overture.core.manager.ItemManager

/**
 * Overture 物品 JSON schema v2。
 *
 * 未知根字段会被视为可忽略扩展；结构深度、
 * 节点数、字符串及数组长度均有限制，避免不可信外部 JSON 造成内存或递归攻击。
 */
object ItemSerializer {
    const val CURRENT_SCHEMA = 2
    const val MAX_JSON_LENGTH = 1_048_576
    const val MAX_DEPTH = 32
    const val MAX_NODES = 16_384
    const val MAX_STRING_LENGTH = 32_767
    const val MAX_ARRAY_LENGTH = 65_536
    const val MAX_ITEM_ID_LENGTH = 256
    const val MAX_AMOUNT = 127

    private val gson = Gson()

    fun serialize(itemStack: ItemStack): String {
        val stream = ItemStream(itemStack)
        val json = JsonObject()
        json.addProperty("schema", CURRENT_SCHEMA)

        if (stream.isOverture) {
            json.addProperty("kind", "overture")
            json.addProperty("id", stream.overtureId)
            json.addProperty("amount", itemStack.amount.coerceIn(1, MAX_AMOUNT))

            val data = stream.overtureData
            if (!data.isEmpty()) json.add("data", encodeTag(data, 0))

            val unique = stream.overtureUnique
            if (!unique.isEmpty()) {
                val uniqueJson = JsonObject()
                putUniqueString(uniqueJson, unique, ItemKey.UNIQUE_UUID)
                putUniqueString(uniqueJson, unique, ItemKey.UNIQUE_PLAYER)
                if (unique.containsKey(ItemKey.UNIQUE_DATE)) {
                    uniqueJson.addProperty(ItemKey.UNIQUE_DATE, unique.getLong(ItemKey.UNIQUE_DATE))
                }
                putUniqueString(uniqueJson, unique, ItemKey.UNIQUE_DATE_FORMATTED)
                json.add("unique", uniqueJson)
            }
        } else {
            json.addProperty("kind", "minecraft")
            json.addProperty("id", "minecraft:${itemStack.type.name.lowercase()}")
            json.addProperty("amount", itemStack.amount.coerceIn(1, MAX_AMOUNT))
            try {
                json.addProperty("nbt", AsteroidAPI.getItemStackNMS().item2Json(itemStack))
            } catch (_: Exception) {
            }
        }

        val result = gson.toJson(json)
        require(result.length <= MAX_JSON_LENGTH) {
            "序列化结果超过 $MAX_JSON_LENGTH 字符限制"
        }
        return result
    }

    /** 容错入口：无效输入返回 null。 */
    fun deserialize(json: String): ItemStack? =
        try {
            deserializeStrict(json)
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        }

    /**
     * 严格入口：仅接受当前 schema；无效结构或超限数据会抛出 [ItemSerializationException]。
     */
    fun deserializeStrict(json: String): ItemStack {
        if (json.length > MAX_JSON_LENGTH) {
            throw ItemSerializationException("JSON 超过 $MAX_JSON_LENGTH 字符限制")
        }
        val parsed = try {
            JsonParser.parseString(json)
        } catch (error: Throwable) {
            throw ItemSerializationException("JSON 解析失败: ${error.message}", error)
        }
        validateLimits(parsed)
        if (!parsed.isJsonObject) throw ItemSerializationException("JSON 根节点必须是对象")

        val root = requireCurrentSchema(parsed.asJsonObject)
        val id = root.get("id")?.asString
            ?: throw ItemSerializationException("缺少 id")
        if (id.isBlank() || id.length > MAX_ITEM_ID_LENGTH) {
            throw ItemSerializationException("id 为空或超过 $MAX_ITEM_ID_LENGTH 字符")
        }
        val amount = root.get("amount")?.asInt
            ?: throw ItemSerializationException("缺少 amount")
        if (amount !in 1..MAX_AMOUNT) {
            throw ItemSerializationException("amount 必须在 1..$MAX_AMOUNT")
        }
        val kind = root.get("kind")?.asString
            ?: throw ItemSerializationException("缺少 kind")

        return when (kind) {
            "minecraft" -> deserializeMinecraft(root, id, amount)
            "overture" -> deserializeOverture(root, id, amount)
            else -> throw ItemSerializationException("未知 kind: $kind")
        }
    }

    private fun deserializeMinecraft(json: JsonObject, id: String, amount: Int): ItemStack {
        if (!id.startsWith("minecraft:")) {
            throw ItemSerializationException("minecraft kind 的 id 必须以 minecraft: 开头")
        }
        val nbt = json.get("nbt")?.asString
        if (nbt != null) {
            if (nbt.length > MAX_JSON_LENGTH) throw ItemSerializationException("nbt 字段过长")
            return try {
                AsteroidAPI.getItemStackNMS().json2Item(nbt).also { it.amount = amount }
            } catch (error: Throwable) {
                throw ItemSerializationException("Asteroid 无法反序列化原版物品: ${error.message}", error)
            }
        }
        val material = Material.getMaterial(id.removePrefix("minecraft:").uppercase())
            ?: throw ItemSerializationException("未知原版材质: $id")
        return ItemStack(material, amount)
    }

    private fun deserializeOverture(json: JsonObject, id: String, amount: Int): ItemStack {
        if (id.startsWith("minecraft:")) {
            throw ItemSerializationException("overture kind 不能使用 minecraft: id")
        }
        val definition = ItemManager.getItem(id)
            ?: throw ItemSerializationException("Overture 物品不存在: $id")
        val stream = definition.buildRestored(null) { restored ->
            val root = restored.getOrCreateRoot()
            json.get("data")?.let { dataElement ->
                if (!dataElement.isJsonObject) {
                    throw ItemSerializationException("data 必须是对象")
                }
                val existing = root.getCompound(ItemKey.DATA)
                existing.putAll(decodeTag(dataElement.asJsonObject, 0))
                root.putCompound(ItemKey.DATA, existing)
            }

            json.get("unique")?.let { uniqueElement ->
                if (!uniqueElement.isJsonObject) {
                    throw ItemSerializationException("unique 必须是对象")
                }
                val uniqueObj = uniqueElement.asJsonObject
                val uniqueTag = root.getCompound(ItemKey.UNIQUE)
                uniqueObj.get(ItemKey.UNIQUE_UUID)?.asString?.let {
                    uniqueTag.putString(ItemKey.UNIQUE_UUID, it)
                }
                uniqueObj.get(ItemKey.UNIQUE_PLAYER)?.asString?.let {
                    uniqueTag.putString(ItemKey.UNIQUE_PLAYER, it)
                }
                uniqueObj.get(ItemKey.UNIQUE_DATE)?.asLong?.let {
                    uniqueTag.putLong(ItemKey.UNIQUE_DATE, it)
                }
                uniqueObj.get(ItemKey.UNIQUE_DATE_FORMATTED)?.asString?.let {
                    uniqueTag.putString(ItemKey.UNIQUE_DATE_FORMATTED, it)
                }
                root.putCompound(ItemKey.UNIQUE, uniqueTag)
            }
        }
        if (stream.buildCancelled) {
            throw ItemSerializationException("Overture 物品构建被取消: $id")
        }
        return stream.toItemStack(null).also { it.amount = amount }
    }

    private fun requireCurrentSchema(source: JsonObject): JsonObject {
        val version = source.get("schema")?.let {
            runCatching { it.asInt }.getOrNull()
        } ?: throw ItemSerializationException("缺少或无效 schema")
        if (version != CURRENT_SCHEMA) {
            throw ItemSerializationException("不支持 schema $version，当前仅支持 $CURRENT_SCHEMA")
        }
        return source
    }

    private fun encodeTag(tag: ItemTag, depth: Int): JsonObject {
        if (depth > MAX_DEPTH) throw ItemSerializationException("ItemTag 超过最大深度 $MAX_DEPTH")
        val result = JsonObject()
        for ((key, value) in tag) {
            if (key.length > MAX_STRING_LENGTH) throw ItemSerializationException("ItemTag key 过长")
            result.add(key, encodeData(value, depth + 1))
        }
        return result
    }

    private fun encodeData(data: ItemTagData, depth: Int): JsonObject {
        if (depth > MAX_DEPTH) throw ItemSerializationException("ItemTag 超过最大深度 $MAX_DEPTH")
        val result = JsonObject()
        val type = data.type ?: throw ItemSerializationException("ItemTagData 缺少类型")
        result.addProperty("type", type.name)
        val value: JsonElement = when (type) {
            ItemTagType.END -> JsonNull.INSTANCE
            ItemTagType.BYTE -> gson.toJsonTree(data.asByte())
            ItemTagType.SHORT -> gson.toJsonTree(data.asShort())
            ItemTagType.INT -> gson.toJsonTree(data.asInt())
            ItemTagType.LONG -> gson.toJsonTree(data.asLong())
            ItemTagType.FLOAT -> gson.toJsonTree(data.asFloat())
            ItemTagType.DOUBLE -> gson.toJsonTree(data.asDouble())
            ItemTagType.STRING -> gson.toJsonTree(data.asString().checkedString())
            ItemTagType.BYTE_ARRAY -> JsonArray().also { array ->
                data.asByteArray().checkedArraySize().forEach { array.add(it.toInt()) }
            }
            ItemTagType.INT_ARRAY -> JsonArray().also { array ->
                data.asIntArray().checkedArraySize().forEach(array::add)
            }
            ItemTagType.LONG_ARRAY -> JsonArray().also { array ->
                data.asLongArray().checkedArraySize().forEach(array::add)
            }
            ItemTagType.LIST -> JsonArray().also { array ->
                val list = data.asList()
                if (list.size > MAX_ARRAY_LENGTH) throw ItemSerializationException("ItemTag 列表过长")
                list.forEach { array.add(encodeData(it, depth + 1)) }
            }
            ItemTagType.COMPOUND -> encodeTag(data.asCompound(), depth + 1)
        }
        result.add("value", value)
        return result
    }

    private fun decodeTag(json: JsonObject, depth: Int): ItemTag {
        if (depth > MAX_DEPTH) throw ItemSerializationException("ItemTag 超过最大深度 $MAX_DEPTH")
        val result = ItemTag()
        for ((key, value) in json.entrySet()) {
            result.put(key, decodeData(value, depth + 1))
        }
        return result
    }

    private fun decodeData(json: JsonElement, depth: Int): ItemTagData {
        if (depth > MAX_DEPTH) throw ItemSerializationException("ItemTag 超过最大深度 $MAX_DEPTH")
        if (!json.isJsonObject) throw ItemSerializationException("ItemTagData 必须是对象")
        val obj = json.asJsonObject

        val typeName = obj.get("type")?.asString
            ?: throw ItemSerializationException("ItemTagData 缺少 type")
        val type = runCatching { ItemTagType.valueOf(typeName) }.getOrNull()
            ?: throw ItemSerializationException("未知 ItemTag 类型: $typeName")
        val value = obj.get("value")
            ?: throw ItemSerializationException("ItemTagData 缺少 value")

        return try {
            when (type) {
                ItemTagType.END -> ItemTagData.ofEnd()
                ItemTagType.BYTE -> ItemTagData.of(value.asByte)
                ItemTagType.SHORT -> ItemTagData.of(value.asShort)
                ItemTagType.INT -> ItemTagData.of(value.asInt)
                ItemTagType.LONG -> ItemTagData.of(value.asLong)
                ItemTagType.FLOAT -> ItemTagData.of(value.asFloat)
                ItemTagType.DOUBLE -> ItemTagData.of(value.asDouble)
                ItemTagType.STRING -> ItemTagData.of(value.asString.checkedString())
                ItemTagType.BYTE_ARRAY -> ItemTagData.of(
                    value.asJsonArray.checkedArray().map { it.asInt.toByte() }.toByteArray()
                )
                ItemTagType.INT_ARRAY -> ItemTagData.of(
                    value.asJsonArray.checkedArray().map { it.asInt }.toIntArray()
                )
                ItemTagType.LONG_ARRAY -> ItemTagData.of(
                    value.asJsonArray.checkedArray().map { it.asLong }.toLongArray()
                )
                ItemTagType.LIST -> {
                    val list = ItemTagList()
                    value.asJsonArray.checkedArray().forEach {
                        list.add(decodeData(it, depth + 1))
                    }
                    ItemTagData.of(list)
                }
                ItemTagType.COMPOUND -> ItemTagData.of(decodeTag(value.asJsonObject, depth + 1))
                else -> throw ItemSerializationException("不支持 ItemTag 类型: $type")
            }
        } catch (error: ItemSerializationException) {
            throw error
        } catch (error: Throwable) {
            throw ItemSerializationException("ItemTag $type 值无效: ${error.message}", error)
        }
    }

    private fun validateLimits(root: JsonElement) {
        val queue = ArrayDeque<Pair<JsonElement, Int>>()
        queue += root to 0
        var nodes = 0
        while (queue.isNotEmpty()) {
            val (element, depth) = queue.removeFirst()
            nodes++
            if (nodes > MAX_NODES) throw ItemSerializationException("JSON 节点超过 $MAX_NODES")
            if (depth > MAX_DEPTH) throw ItemSerializationException("JSON 深度超过 $MAX_DEPTH")
            when {
                element.isJsonObject -> {
                    element.asJsonObject.entrySet().forEach { (key, value) ->
                        if (key.length > MAX_STRING_LENGTH) {
                            throw ItemSerializationException("JSON key 过长")
                        }
                        queue += value to (depth + 1)
                    }
                }
                element.isJsonArray -> {
                    val array = element.asJsonArray
                    if (array.size() > MAX_ARRAY_LENGTH) {
                        throw ItemSerializationException("JSON 数组超过 $MAX_ARRAY_LENGTH")
                    }
                    array.forEach { queue += it to (depth + 1) }
                }
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    element.asString.checkedString()
                }
            }
        }
    }

    private fun putUniqueString(target: JsonObject, source: ItemTag, key: String) {
        if (source.containsKey(key)) target.addProperty(key, source.getString(key).checkedString())
    }

    private fun String.checkedString(): String {
        if (length > MAX_STRING_LENGTH) throw ItemSerializationException("字符串超过 $MAX_STRING_LENGTH")
        return this
    }

    private fun ByteArray.checkedArraySize(): ByteArray {
        if (size > MAX_ARRAY_LENGTH) throw ItemSerializationException("byte 数组超过 $MAX_ARRAY_LENGTH")
        return this
    }

    private fun IntArray.checkedArraySize(): IntArray {
        if (size > MAX_ARRAY_LENGTH) throw ItemSerializationException("int 数组超过 $MAX_ARRAY_LENGTH")
        return this
    }

    private fun LongArray.checkedArraySize(): LongArray {
        if (size > MAX_ARRAY_LENGTH) throw ItemSerializationException("long 数组超过 $MAX_ARRAY_LENGTH")
        return this
    }

    private fun JsonArray.checkedArray(): JsonArray {
        if (size() > MAX_ARRAY_LENGTH) throw ItemSerializationException("数组超过 $MAX_ARRAY_LENGTH")
        return this
    }
}

class ItemSerializationException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
