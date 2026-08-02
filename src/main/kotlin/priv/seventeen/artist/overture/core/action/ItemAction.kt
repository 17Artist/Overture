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

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.action.TriggerKey

/**
 * 物品动作定义
 * 封装一个触发器对应的 Aria 脚本
 */
class ItemAction(
    val name: String,
    val trigger: TriggerKey,
    val script: String,
    val cancelEvent: Boolean = false
) {
    /** 预编译的 Aria 脚本 */
    var compiled: AriaCompiledRoutine? = null
        private set

    /**
     * 编译脚本（延迟到 Aria 引擎初始化之后调用）
     */
    fun compile(): ItemAction {
        compiled = try {
            Aria.compile(name, script)
        } catch (e: Exception) {
            BlinkLog.error(
                LanguageManager.text(
                    "console.aria-action-compile-failed",
                    "name" to name,
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            )
            null
        }
        return this
    }
}
