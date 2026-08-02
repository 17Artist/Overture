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

package priv.seventeen.artist.overture.api.behavior

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.api.data.MutableItemData

data class BehaviorBinding(
    val key: NamespacedKey,
    val options: Map<String, Any?> = emptyMap()
)

data class ItemBehaviorContext(
    val trigger: TriggerKey,
    val player: Player?,
    val itemId: String,
    /** 当前物品的只读 clone；实例数据修改必须通过 [data] 完成。 */
    val itemStack: ItemStack,
    val data: MutableItemData,
    val event: Event?,
    val variables: Map<String, Any>,
    val options: Map<String, Any?>,
    val asynchronous: Boolean
)

/** Behavior 可返回的稳定信号，不暴露 Overture 内部 ItemStream 类型。 */
enum class ItemBehaviorSignal {
    DURABILITY_CHANGED,
    ITEM_CHANGED,
    DURABILITY_DESTROYED
}

data class ItemBehaviorResult(
    val changed: Boolean = false,
    val cancelEvent: Boolean = false,
    val stopPropagation: Boolean = false,
    val signals: Set<ItemBehaviorSignal> = emptySet()
) {
    companion object {
        @JvmField
        val PASS = ItemBehaviorResult()

        @JvmField
        val CHANGED = ItemBehaviorResult(changed = true)
    }
}

fun interface ItemBehavior {
    fun onTrigger(context: ItemBehaviorContext): ItemBehaviorResult
}
