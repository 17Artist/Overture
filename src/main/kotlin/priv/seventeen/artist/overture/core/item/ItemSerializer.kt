package priv.seventeen.artist.overture.core.item

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.overture.core.manager.ItemManager

/**
 * 物品序列化器
 * ItemStack ↔ JSON 格式
 */
object ItemSerializer {

    private val gson = Gson()

    /**
     * 序列化 ItemStack 为 JSON
     *
     * Overture 物品格式:
     * ```json
     * {
     *   "id": "diamond_sword_1",
     *   "amount": 1,
     *   "data": { "durability": {"t": 3, "v": 100} },
     *   "unique": { ... }
     * }
     * ```
     *
     * 原版物品格式:
     * ```json
     * {
     *   "id": "minecraft:diamond_sword",
     *   "amount": 1,
     *   "nbt": "..."
     * }
     * ```
     */
    fun serialize(itemStack: ItemStack): String {
        val stream = ItemStream(itemStack)
        val json = JsonObject()

        if (stream.isOverture) {
            json.addProperty("id", stream.overtureId)
            json.addProperty("amount", itemStack.amount)

            // 序列化活跃数据（类型化 JSON，可无损恢复）
            val data = stream.overtureData
            if (!data.isEmpty()) {
                json.add("data", tagToJson(data))
            }

            // 序列化唯一数据
            val unique = stream.overtureUnique
            if (!unique.isEmpty()) {
                val uniqueJson = JsonObject()
                if (unique.containsKey("uuid")) uniqueJson.addProperty("uuid", unique.getString("uuid"))
                if (unique.containsKey("player")) uniqueJson.addProperty("player", unique.getString("player"))
                if (unique.containsKey("date")) uniqueJson.addProperty("date", unique.getLong("date"))
                if (unique.containsKey("date-formatted")) uniqueJson.addProperty("date-formatted", unique.getString("date-formatted"))
                json.add("unique", uniqueJson)
            }
        } else {
            json.addProperty("id", "minecraft:${itemStack.type.name.lowercase()}")
            json.addProperty("amount", itemStack.amount)
            // 使用 Asteroid 序列化原版物品
            try {
                val nmsJson = AsteroidAPI.getItemStackNMS().item2Json(itemStack)
                json.addProperty("nbt", nmsJson)
            } catch (_: Exception) {
            }
        }

        return gson.toJson(json)
    }

    /**
     * 从 JSON 反序列化为 ItemStack
     */
    fun deserialize(json: String): ItemStack? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val id = obj.get("id")?.asString ?: return null
            val amount = obj.get("amount")?.asInt ?: 1

            if (id.startsWith("minecraft:")) {
                // 原版物品
                val nbt = obj.get("nbt")?.asString
                if (nbt != null) {
                    val item = AsteroidAPI.getItemStackNMS().json2Item(nbt)
                    item.amount = amount
                    return item
                }
                val material = org.bukkit.Material.matchMaterial(id.removePrefix("minecraft:").uppercase())
                    ?: return null
                return ItemStack(material, amount)
            } else {
                // Overture 物品
                val item = ItemManager.generate(id) ?: return null
                item.amount = amount

                val tag = ItemTag.fromItemStack(item)
                val root = tag.getCompound(ItemKey.ROOT)
                var dirty = false

                // 恢复活跃数据（覆盖模板生成的默认值）
                val dataElement = obj.get("data")
                if (dataElement != null && dataElement.isJsonObject) {
                    val dataTag = jsonToTag(dataElement.asJsonObject)
                    val existing = root.getCompound(ItemKey.DATA)
                    existing.putAll(dataTag)
                    root.putCompound(ItemKey.DATA, existing)
                    dirty = true
                }

                // 恢复唯一数据
                val uniqueObj = obj.getAsJsonObject("unique")
                if (uniqueObj != null) {
                    val uniqueTag = root.getCompound(ItemKey.UNIQUE)
                    uniqueObj.get("uuid")?.asString?.let { uniqueTag.putString("uuid", it) }
                    uniqueObj.get("player")?.asString?.let { uniqueTag.putString("player", it) }
                    uniqueObj.get("date")?.asLong?.let { uniqueTag.putLong("date", it) }
                    uniqueObj.get("date-formatted")?.asString?.let { uniqueTag.putString("date-formatted", it) }
                    root.putCompound(ItemKey.UNIQUE, uniqueTag)
                    dirty = true
                }

                if (dirty) {
                    tag.putCompound(ItemKey.ROOT, root)
                    return tag.saveTo(item)
                }

                return item
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ItemTag → 类型化 JSON
     * 每个条目编码为 {"t": 类型ID, "v": 值}，保证反序列化时类型无损
     */
    private fun tagToJson(tag: ItemTag): JsonObject {
        val json = JsonObject()
        for ((key, data) in tag) {
            json.add(key, dataToJson(data))
        }
        return json
    }

    private fun dataToJson(data: ItemTagData): JsonObject {
        val json = JsonObject()
        json.addProperty("t", data.type.id())
        when (data.type) {
            ItemTagType.BYTE -> json.addProperty("v", data.asByte())
            ItemTagType.SHORT -> json.addProperty("v", data.asShort())
            ItemTagType.INT -> json.addProperty("v", data.asInt())
            ItemTagType.LONG -> json.addProperty("v", data.asLong())
            ItemTagType.FLOAT -> json.addProperty("v", data.asFloat())
            ItemTagType.DOUBLE -> json.addProperty("v", data.asDouble())
            ItemTagType.STRING -> json.addProperty("v", data.asString())
            ItemTagType.BYTE_ARRAY -> json.add("v", JsonArray().apply { data.asByteArray().forEach { add(it) } })
            ItemTagType.INT_ARRAY -> json.add("v", JsonArray().apply { data.asIntArray().forEach { add(it) } })
            ItemTagType.LONG_ARRAY -> json.add("v", JsonArray().apply { data.asLongArray().forEach { add(it) } })
            ItemTagType.LIST -> json.add("v", JsonArray().apply { data.asList().forEach { add(dataToJson(it)) } })
            ItemTagType.COMPOUND -> json.add("v", tagToJson(data.asCompound()))
            else -> json.addProperty("v", data.asString())
        }
        return json
    }

    /**
     * 类型化 JSON → ItemTag
     */
    private fun jsonToTag(json: JsonObject): ItemTag {
        val tag = ItemTag()
        for ((key, element) in json.entrySet()) {
            if (!element.isJsonObject) continue
            jsonToData(element.asJsonObject)?.let { tag[key] = it }
        }
        return tag
    }

    private fun jsonToData(json: JsonObject): ItemTagData? {
        val typeId = json.get("t")?.asByte ?: return null
        val value = json.get("v") ?: return null
        return when (ItemTagType.fromId(typeId)) {
            ItemTagType.BYTE -> ItemTagData.of(value.asByte)
            ItemTagType.SHORT -> ItemTagData.of(value.asShort)
            ItemTagType.INT -> ItemTagData.of(value.asInt)
            ItemTagType.LONG -> ItemTagData.of(value.asLong)
            ItemTagType.FLOAT -> ItemTagData.of(value.asFloat)
            ItemTagType.DOUBLE -> ItemTagData.of(value.asDouble)
            ItemTagType.STRING -> ItemTagData.of(value.asString)
            ItemTagType.BYTE_ARRAY -> ItemTagData.of(ByteArray(value.asJsonArray.size()) { value.asJsonArray[it].asByte })
            ItemTagType.INT_ARRAY -> ItemTagData.of(IntArray(value.asJsonArray.size()) { value.asJsonArray[it].asInt })
            ItemTagType.LONG_ARRAY -> ItemTagData.of(LongArray(value.asJsonArray.size()) { value.asJsonArray[it].asLong })
            ItemTagType.LIST -> ItemTagList(value.asJsonArray.mapNotNull { if (it.isJsonObject) jsonToData(it.asJsonObject) else null })
            ItemTagType.COMPOUND -> ItemTagData.of(jsonToTag(value.asJsonObject))
            else -> null
        }
    }
}
