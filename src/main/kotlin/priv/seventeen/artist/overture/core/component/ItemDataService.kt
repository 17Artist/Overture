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

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.data.ItemDataMutation
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.data.ItemDataView
import priv.seventeen.artist.overture.api.data.ItemMutationResult
import priv.seventeen.artist.overture.api.data.MutableItemData
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.manager.ItemManager

internal object ItemDataService {
    /** 为内部扩展调度创建受路径与数据上限约束的编辑器。 */
    fun mutable(stream: ItemStream): MutableItemData = MutableView(stream)

    fun read(item: ItemStack): ItemDataView {
        val stream = ItemStream(item.clone())
        if (!stream.isOverture) return SnapshotView(null, ItemDataNode.Compound(emptyMap()))
        val data = try {
            ItemDataCodec.fromTag(stream.overtureData.deepClone())
        } catch (_: Throwable) {
            ItemDataNode.Compound(emptyMap())
        }
        return SnapshotView(stream.overtureId, data)
    }

    fun mutate(
        item: ItemStack,
        player: Player?,
        mutation: ItemDataMutation
    ): ItemMutationResult {
        val working = item.clone()
        val stream = try {
            ItemStream(working)
        } catch (error: Throwable) {
            return ItemMutationResult.Failure("无法读取物品数据: ${error.message}", error)
        }
        if (!stream.isOverture) return ItemMutationResult.Failure("不是 Overture 物品")
        val itemId = stream.overtureId ?: return ItemMutationResult.Failure("Overture 物品缺少 id")
        val definition = ItemManager.getItem(itemId)
            ?: return ItemMutationResult.Failure("物品定义不存在: $itemId")

        try {
            mutation.mutate(mutable(stream))
            val rebuilt = definition.build(player, stream)
            if (rebuilt.buildCancelled) {
                return ItemMutationResult.Failure("物品重建被 ItemBuildEvent.Pre 取消")
            }
            return ItemMutationResult.Success(rebuilt.toItemStack(player))
        } catch (error: Throwable) {
            return ItemMutationResult.Failure(
                "物品修改失败: ${error.message ?: error.javaClass.simpleName}",
                error
            )
        }
    }

    fun rebuild(item: ItemStack, player: Player?): ItemMutationResult {
        val working = item.clone()
        val stream = try {
            ItemStream(working)
        } catch (error: Throwable) {
            return ItemMutationResult.Failure("无法读取物品数据: ${error.message}", error)
        }
        if (!stream.isOverture) return ItemMutationResult.Failure("不是 Overture 物品")
        val itemId = stream.overtureId ?: return ItemMutationResult.Failure("Overture 物品缺少 id")
        val definition = ItemManager.getItem(itemId)
            ?: return ItemMutationResult.Failure("物品定义不存在: $itemId")
        return try {
            val rebuilt = definition.build(player, stream)
            if (rebuilt.buildCancelled) {
                ItemMutationResult.Failure("物品重建被 ItemBuildEvent.Pre 取消")
            } else {
                ItemMutationResult.Success(rebuilt.toItemStack(player))
            }
        } catch (error: Throwable) {
            ItemMutationResult.Failure("物品重建失败: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private class SnapshotView(
        override val itemId: String?,
        private val data: ItemDataNode.Compound
    ) : ItemDataView {
        override fun component(key: NamespacedKey): ItemDataNode.Compound? {
            val namespace = namespace(key.namespace) ?: return null
            val definition = namespace.values["definition"] as? ItemDataNode.Compound ?: return null
            return definition.values[key.key] as? ItemDataNode.Compound
        }

        override fun namespace(namespace: String): ItemDataNode.Compound? =
            data.values[namespace] as? ItemDataNode.Compound
    }

    private class MutableView(private val stream: ItemStream) : MutableItemData {
        override fun get(path: String): ItemDataNode? {
            validatePath(path)
            val value = stream.getData(path) ?: return null
            return ItemDataCodec.fromTagData(value)
        }

        override fun put(path: String, value: ItemDataNode) {
            validatePath(path)
            ItemDataCodec.validateForStorage(value, "data.$path")
            stream.setData(path, ItemDataCodec.toTagData(value))
        }

        override fun remove(path: String): Boolean {
            validatePath(path)
            if (stream.getData(path) == null) return false
            stream.removeData(path)
            return true
        }

        private fun validatePath(path: String) {
            val parts = path.split('.')
            require(parts.isNotEmpty() && parts.size <= 32 && parts.none { it.isBlank() }) {
                "数据路径必须由 1..32 个非空段组成"
            }
            require(parts.all { it.length <= 32_767 }) { "数据路径段过长" }
        }
    }
}
