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

import org.bukkit.entity.Player
import org.bukkit.event.Event
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.core.behavior.ItemBehaviorDispatcher
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem

/**
 * Behavior 与 Aria 的统一动作出口。Behavior 先执行；stopPropagation 可阻止后续 Aria。
 */
object ItemActionDispatcher {
    data class Result(
        val behaviorCount: Int,
        val ariaExecuted: Boolean,
        val stopped: Boolean,
        val actionOrigin: String?
    ) {
        val executed: Boolean get() = behaviorCount > 0 || ariaExecuted
    }

    fun dispatch(
        item: OvertureItem,
        trigger: TriggerKey,
        player: Player?,
        stream: ItemStream,
        event: Event? = null,
        variables: Map<String, Any> = emptyMap()
    ): Result {
        val behaviorResult = ItemBehaviorDispatcher.dispatch(
            item,
            trigger,
            player,
            stream,
            event,
            variables
        )
        if (behaviorResult.stopped) {
            return Result(behaviorResult.executed, false, true, null)
        }

        val action = ActionResolver.resolve(item, trigger)
            ?: return Result(behaviorResult.executed, false, false, null)
        val ariaExecuted = ActionExecutor.execute(
            action.action,
            player,
            stream,
            event
        )
        if (ariaExecuted) {
            stream.actionTrace += "aria:${action.origin}@$trigger"
        }
        return Result(behaviorResult.executed, ariaExecuted, false, action.origin)
    }
}
