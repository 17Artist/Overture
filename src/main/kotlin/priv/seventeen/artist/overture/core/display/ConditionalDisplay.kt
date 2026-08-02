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

package priv.seventeen.artist.overture.core.display

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.item.ItemStream

/**
 * 条件展示
 * 根据 Aria 表达式判断结果选择不同的展示方案
 */
class ConditionalDisplay(
    val id: String,
    val config: ConfigurationSection
) {

    /** 条件列表 */
    val conditions: List<DisplayCondition> = loadConditions()

    /** 默认展示方案 ID */
    val defaultDisplay: String? = config.getString("default")

    /**
     * 评估条件，返回应使用的展示方案 ID
     * @param player 当前玩家
     * @param stream 物品流
     * @param evaluator Aria 表达式求值器
     */
    fun evaluate(
        player: Player?,
        stream: ItemStream,
        evaluator: (AriaCompiledRoutine, Player?, ItemStream) -> Boolean
    ): String? {
        for (condition in conditions) {
            val routine = condition.compiled ?: continue
            if (evaluator(routine, player, stream)) {
                return condition.displayId
            }
        }
        return defaultDisplay
    }

    private fun loadConditions(): List<DisplayCondition> {
        val list = config.getMapList("conditions")
        return list.mapIndexedNotNull { index, map ->
            val condition = map["condition"]?.toString() ?: return@mapIndexedNotNull null
            val display = map["display"]?.toString() ?: return@mapIndexedNotNull null
            val routine = try {
                Aria.compile("$id.condition.$index", "return $condition")
            } catch (e: Exception) {
                BlinkLog.error(
                    LanguageManager.text(
                        "console.conditional-display-compile-failed",
                        "id" to id,
                        "index" to index,
                        "error" to (e.message ?: e.javaClass.simpleName)
                    )
                )
                null
            }
            DisplayCondition(condition, display, routine)
        }
    }

    data class DisplayCondition(
        val expression: String,
        val displayId: String,
        val compiled: AriaCompiledRoutine?
    )
}
