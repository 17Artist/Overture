package priv.seventeen.artist.overture.core.item

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.overture.api.event.ItemReleaseEvent
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaRegistry

/**
 * 物品流 — 运行时物品实例
 * 所有物品操作的中间载体，包装 ItemStack + NBT 数据
 */
open class ItemStream(val sourceItem: ItemStack) {

    /** 从 ItemStack 读取的完整 NBT */
    val sourceTag: ItemTag = ItemTag.fromItemStack(sourceItem)

    /** overture 根节点 */
    val sourceCompound: ItemTag
        get() = sourceTag.getCompound(ItemKey.ROOT)

    /** 是否为原版物品（无 overture 节点） */
    val isVanilla: Boolean
        get() = !sourceTag.containsKey(ItemKey.ROOT)

    /** 是否为 Overture 物品 */
    val isOverture: Boolean
        get() = sourceTag.containsKey(ItemKey.ROOT)

    /** 物品 ID */
    val overtureId: String?
        get() = if (isOverture) sourceCompound.getString(ItemKey.ID) else null

    /** 活跃数据节点 */
    val overtureData: ItemTag
        get() = sourceCompound.getCompound(ItemKey.DATA)

    /** 唯一数据节点 */
    val overtureUnique: ItemTag
        get() = sourceCompound.getCompound(ItemKey.UNIQUE)

    /** 当前版本签名 */
    val version: String?
        get() = if (isOverture) sourceCompound.getString(ItemKey.VERSION) else null

    /** Meta 历史记录 */
    val metaHistory: List<String>
        get() {
            val list = sourceCompound.getTagList(ItemKey.META_HISTORY)
            return (0 until list.size).map { list[it].asString() }
        }

    /** 信号集合 */
    val signals: MutableSet<ItemSignal> = mutableSetOf()

    /**
     * 检查物品是否过时
     */
    fun isOutdated(item: OvertureItem): Boolean {
        return version != item.version
    }

    /**
     * 获取需要 drop 的 Meta 列表
     * 对比当前物品定义的 meta 和 NBT 中存储的 meta 历史
     */
    fun getDropMeta(currentMeta: List<Meta>): List<String> {
        val currentKeys = currentMeta.map { it.key }.toSet()
        return metaHistory.filter { it !in currentKeys }
    }

    /**
     * 设置活跃数据
     */
    fun setData(key: String, data: ItemTagData) {
        val root = getOrCreateRoot()
        val dataTag = root.getCompound(ItemKey.DATA)
        dataTag.putDeep(key, data)
        root.putCompound(ItemKey.DATA, dataTag)
        // 确保修改传播回 sourceTag
        sourceTag.putCompound(ItemKey.ROOT, root)
    }

    /**
     * 获取活跃数据
     */
    fun getData(key: String): ItemTagData? {
        return overtureData.getDeep(key)
    }

    /**
     * 删除活跃数据
     */
    fun removeData(key: String) {
        val root = getOrCreateRoot()
        val dataTag = root.getCompound(ItemKey.DATA)
        dataTag.removeDeep(key)
        root.putCompound(ItemKey.DATA, dataTag)
        // 确保修改传播回 sourceTag
        sourceTag.putCompound(ItemKey.ROOT, root)
    }

    /**
     * 设置版本签名
     */
    fun setVersion(version: String) {
        getOrCreateRoot().putString(ItemKey.VERSION, version)
    }

    /**
     * 设置 Meta 历史
     */
    fun setMetaHistory(history: List<String>) {
        val list = ItemTagList()
        history.forEach { list.add(ItemTagData.of(it)) }
        getOrCreateRoot().putList(ItemKey.META_HISTORY, list)
    }

    /**
     * 保存 NBT 回 ItemStack（仅写入 NBT，不触发事件）
     */
    fun save(): ItemStack {
        return sourceTag.saveTo(sourceItem)
    }

    /**
     * 完整释放为 ItemStack（触发 Release 事件链）
     * 这是生成物品的标准出口
     *
     * 流程：
     * 1. 触发 Release 事件 → Meta 写入 ItemMeta，Display 构建 name/lore
     * 2. setItemMeta → Bukkit 层数据（display/enchant/attribute）写入 ItemStack
     * 3. 从 ItemStack 重新读取完整 NBT（包含 Bukkit 刚写入的 display 等）
     * 4. 把 overture 节点合并到这份最新 NBT 上
     * 5. saveTo → 最终 ItemStack 同时包含 display 和 overture 数据
     */
    fun toItemStack(player: Player? = null): ItemStack {
        // 获取 ItemMeta
        val itemMeta = sourceItem.itemMeta ?: return sourceTag.saveTo(sourceItem)

        // 1. 触发 Release 事件（Meta buildMeta + Display 构建）
        val releaseEvent = ItemReleaseEvent.Release(player, this, itemMeta)
        Bukkit.getPluginManager().callEvent(releaseEvent)

        // 2. 写回 ItemMeta（display/lore/enchant/attribute 等 Bukkit 层数据）
        sourceItem.itemMeta = itemMeta

        // 3. 从 setItemMeta 后的 ItemStack 重新读取完整 NBT
        //    此时包含 Bukkit 写入的 display.Name, display.Lore, Enchantments 等
        val freshTag = ItemTag.fromItemStack(sourceItem)

        // 4. 把 overture 节点合并到最新 NBT 上
        val overtureCompound = sourceTag.getCompound(ItemKey.ROOT)
        freshTag.putCompound(ItemKey.ROOT, overtureCompound)

        // 5. saveTo 写入最终 ItemStack（同时包含 display 和 overture）
        val result = freshTag.saveTo(sourceItem)

        // 触发 Final 事件
        val finalEvent = ItemReleaseEvent.Final(player, this, result)
        Bukkit.getPluginManager().callEvent(finalEvent)

        return finalEvent.itemStack
    }

    /**
     * 获取或创建 overture 根节点
     */
    fun getOrCreateRoot(): ItemTag {
        if (!sourceTag.containsKey(ItemKey.ROOT)) {
            sourceTag.putCompound(ItemKey.ROOT, ItemTag())
        }
        return sourceTag.getCompound(ItemKey.ROOT)
    }
}
