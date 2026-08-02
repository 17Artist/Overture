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

package priv.seventeen.artist.overture.core.action

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.action.ExternalTriggerResult
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.manager.ItemManager

/**
 * 外部 Bukkit 事件到 Overture 动作的显式桥接。
 *
 * Overture 不替第三方接管监听器；调用方拥有事件并负责把返回物品写回正确位置。
 */
object ExternalTriggerDispatcher {
    fun dispatch(
        trigger: TriggerKey,
        player: Player?,
        itemStack: ItemStack,
        event: Event? = null,
        variables: Map<String, Any> = emptyMap()
    ): ExternalTriggerResult {
        if (!Bukkit.isPrimaryThread()) {
            return unchanged(trigger, itemStack, "外部触发器只能在服务端主线程调用")
        }
        if (!TriggerRegistry.isKnown(trigger)) {
            return unchanged(trigger, itemStack, "触发器 $trigger 未注册")
        }

        val stream = ItemStream(itemStack)
        val itemId = stream.overtureId
            ?: return unchanged(trigger, itemStack, "不是 Overture 物品")
        val item = ItemManager.getItem(itemId)
            ?: return unchanged(trigger, itemStack, "物品定义 $itemId 不存在")
        val dispatch = ItemActionDispatcher.dispatch(
            item,
            trigger,
            player,
            stream,
            event,
            variables
        )
        if (!dispatch.executed) {
            return unchanged(trigger, itemStack, "物品 $itemId 未配置触发器 $trigger")
        }
        if (stream.signals.isEmpty()) {
            return ExternalTriggerResult(
                trigger,
                true,
                false,
                itemStack,
                stream.signals.toSet()
            )
        }

        val result = if (stream.sourceItem.amount <= 0) {
            ItemStack(Material.AIR)
        } else {
            val rebuilt = item.build(player, stream)
            if (rebuilt.buildCancelled) itemStack else rebuilt.toItemStack(player)
        }
        return ExternalTriggerResult(
            trigger,
            true,
            true,
            result,
            stream.signals.toSet()
        )
    }

    private fun unchanged(
        trigger: TriggerKey,
        itemStack: ItemStack,
        diagnostic: String
    ) = ExternalTriggerResult(
        trigger,
        false,
        false,
        itemStack,
        emptySet(),
        diagnostic
    )
}
