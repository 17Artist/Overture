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

package priv.seventeen.artist.overture.api.render

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataView

/**
 * 自定义展示条目的只读快照上下文。
 *
 * [itemStack] 与 [data] 均基于当前物品的 clone，修改不会回写实际物品。
 */
data class RenderEntryContext @JvmOverloads constructor(
    val key: NamespacedKey,
    val player: Player?,
    val itemStack: ItemStack,
    val displayId: String,
    val data: ItemDataView = OvertureAPI.readItemData(itemStack)
)

fun interface RenderEntryRenderer {
    /**
     * 返回展示文本。`<namespace:key>` 名称变量使用第一行，
     * `<namespace:key...>` Lore 变量展开全部行。
     */
    fun onRender(context: RenderEntryContext): List<String>
}
