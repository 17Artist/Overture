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
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.item.ItemStream

/** 负责创建请求级 Aria 上下文并执行物品脚本。 */
object ActionExecutor {

    /** 执行物品动作；action 的 !! 标记负责自动取消 Bukkit 事件。 */
    fun execute(
        action: ItemAction,
        player: Player?,
        stream: ItemStream,
        event: Event? = null
    ): Boolean {
        val compiled = action.compiled ?: return false

        if (action.cancelEvent && event is Cancellable) {
            event.isCancelled = true
        }

        return try {
            compiled.execute(AriaActionContextFactory.create(stream, player))
            true
        } catch (e: Exception) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.action-run-failed",
                    "trigger" to action.trigger,
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            )
            false
        }
    }


    /** 执行已预编译的条件表达式。 */
    fun evaluateCondition(
        routine: AriaCompiledRoutine,
        player: Player?,
        stream: ItemStream
    ): Boolean {
        return try {
            routine.execute(AriaActionContextFactory.create(stream, player)).booleanValue()
        } catch (e: Exception) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.display-condition-failed",
                    "name" to routine.name,
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            )
            false
        }
    }
}
