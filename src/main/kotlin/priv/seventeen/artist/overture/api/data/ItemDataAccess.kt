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

package priv.seventeen.artist.overture.api.data

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

/** Overture 物品数据的不可变快照。 */
interface ItemDataView {
    val itemId: String?

    /** 读取 `overture.data.<namespace>.definition.<key>`。 */
    fun component(key: NamespacedKey): ItemDataNode.Compound?

    /** 读取 `overture.data.<namespace>`，包含 schema、definition 与 instance。 */
    fun namespace(namespace: String): ItemDataNode.Compound?
}

/** 物品修改回调中使用的数据编辑器；路径相对 `overture.data`。 */
interface MutableItemData {
    fun get(path: String): ItemDataNode?
    fun put(path: String, value: ItemDataNode)
    fun remove(path: String): Boolean
}

/** Java/Kotlin 均可直接用 lambda 实现的修改函数。 */
fun interface ItemDataMutation {
    fun mutate(data: MutableItemData)
}

sealed interface ItemMutationResult {
    data class Success(val itemStack: ItemStack) : ItemMutationResult
    data class Failure(val reason: String, val cause: Throwable? = null) : ItemMutationResult
}
