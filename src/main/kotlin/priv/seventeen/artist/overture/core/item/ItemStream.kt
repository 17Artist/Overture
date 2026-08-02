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

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.overture.api.event.ItemReleaseEvent
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaRegistry
import priv.seventeen.artist.overture.core.diagnostic.BuildDiagnosticsStore

/**
 * 物品流 — 运行时物品实例
 * 所有物品操作的中间载体，包装 ItemStack + NBT 数据
 */
open class ItemStream(sourceItem: ItemStack) {

    var sourceItem: ItemStack = sourceItem
        private set

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

    /** 本次更新中已从定义移除的 Meta 实例，供 Release 阶段清理 Bukkit ItemMeta。 */
    val droppedMeta: MutableList<Meta> = mutableListOf()

    /** 第三方 Behavior 等扩展在本次处理中的耗时（纳秒）。 */
    val extensionTimings: MutableMap<String, Long> = linkedMapOf()

    /** 本次处理实际经过的 Behavior/Aria 动作链。 */
    val actionTrace: MutableList<String> = mutableListOf()

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
        // 获取或创建 overture 根节点
        if (!sourceTag.containsKey(ItemKey.ROOT)) {
            sourceTag.putCompound(ItemKey.ROOT, ItemTag())
        }
        val root = sourceTag.getCompound(ItemKey.ROOT)

        // 获取或创建 data 节点
        if (!root.containsKey(ItemKey.DATA)) {
            root.putCompound(ItemKey.DATA, ItemTag())
        }
        val dataTag = root.getCompound(ItemKey.DATA)

        // 写入数据
        if (key.contains('.')) {
            dataTag.putDeep(key, data)
        } else {
            dataTag.put(key, data)
        }

        // 逐层写回确保修改传播
        root.putCompound(ItemKey.DATA, dataTag)
        sourceTag.putCompound(ItemKey.ROOT, root)
    }

    /**
     * 获取活跃数据
     */
    fun getData(key: String): ItemTagData? {
        if (!sourceTag.containsKey(ItemKey.ROOT)) return null
        val root = sourceTag.getCompound(ItemKey.ROOT)
        if (!root.containsKey(ItemKey.DATA)) return null
        val dataTag = root.getCompound(ItemKey.DATA)
        return if (key.contains('.')) {
            dataTag.getDeep(key)
        } else {
            if (dataTag.containsKey(key)) dataTag[key] else null
        }
    }

    /**
     * 删除活跃数据
     */
    fun removeData(key: String) {
        if (!sourceTag.containsKey(ItemKey.ROOT)) return
        val root = sourceTag.getCompound(ItemKey.ROOT)
        if (!root.containsKey(ItemKey.DATA)) return
        val dataTag = root.getCompound(ItemKey.DATA)
        if (key.contains('.')) {
            dataTag.removeDeep(key)
        } else {
            dataTag.remove(key)
        }
        root.putCompound(ItemKey.DATA, dataTag)
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
        sourceItem = sourceTag.saveTo(sourceItem)
        return sourceItem
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
        // 先应用 sourceTag 中的 native/overture 修改。Asteroid 1.2.2 的 saveTo
        // 可能返回新 ItemStack，必须接住返回值。
        val taggedItem = save()
        val itemMeta = taggedItem.itemMeta ?: return taggedItem

        // 1. 触发 Release 事件（Meta buildMeta + Display 构建）
        val releaseEvent = ItemReleaseEvent.Release(player, this, itemMeta)
        Bukkit.getPluginManager().callEvent(releaseEvent)

        // 2. 写回 ItemMeta（display/lore/enchant/attribute 等 Bukkit 层数据）
        taggedItem.itemMeta = itemMeta

        // 3. 从 setItemMeta 后的 ItemStack 重新读取完整 NBT
        //    此时包含 Bukkit 写入的 display.Name, display.Lore, Enchantments 等
        val freshTag = ItemTag.fromItemStack(taggedItem)

        // 4. Release 动作可能在 save() 之后修改 overture 实例数据，只合并该命名空间。
        mergeOvertureStateAfterRelease(freshTag, sourceTag)

        // 5. saveTo 写入最终 ItemStack（同时包含最新 Bukkit Meta 和 overture 数据）
        val result = freshTag.saveTo(taggedItem)

        // 触发 Final 事件
        val finalEvent = ItemReleaseEvent.Final(player, this, result)
        Bukkit.getPluginManager().callEvent(finalEvent)

        sourceItem = finalEvent.itemStack
        BuildDiagnosticsStore.record(player, this)
        return sourceItem
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
internal fun mergeOvertureStateAfterRelease(freshTag: ItemTag, releaseTag: ItemTag) {
    val overture = releaseTag[ItemKey.ROOT] ?: return
    freshTag[ItemKey.ROOT] = overture.clone()
}