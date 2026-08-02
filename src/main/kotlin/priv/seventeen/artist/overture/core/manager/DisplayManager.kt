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

package priv.seventeen.artist.overture.core.manager

import org.bukkit.configuration.ConfigurationSection
import priv.seventeen.artist.overture.core.display.ConditionalDisplay
import priv.seventeen.artist.overture.core.display.Display

/**
 * 展示方案管理器。
 *
 * provider 重载期间写入线程本地 staging；正常 resolve 始终读取已提交快照，
 * 校验成功后才交换，避免异步生成线程看到半加载展示表。
 */
object DisplayManager {
    private data class State(
        val displays: Map<String, Display> = emptyMap(),
        val conditionalDisplays: Map<String, ConditionalDisplay> = emptyMap()
    )

    private data class Staging(
        val displays: MutableMap<String, Display>,
        val conditionalDisplays: MutableMap<String, ConditionalDisplay>
    )

    @Volatile
    private var state = State()
    private val staging = ThreadLocal<Staging?>()

    @Synchronized
    fun register(display: Display) {
        val transaction = staging.get()
        if (transaction != null) {
            transaction.displays[display.id] = display
        } else {
            state = state.copy(displays = state.displays + (display.id to display))
        }
    }

    @Synchronized
    fun registerConditional(display: ConditionalDisplay) {
        val transaction = staging.get()
        if (transaction != null) {
            transaction.conditionalDisplays[display.id] = display
        } else {
            state = state.copy(
                conditionalDisplays = state.conditionalDisplays + (display.id to display)
            )
        }
    }

    fun getDisplay(id: String): Display? = state.displays[id]

    fun getConditionalDisplay(id: String): ConditionalDisplay? =
        state.conditionalDisplays[id]

    fun resolve(id: String): Any? =
        state.conditionalDisplays[id] ?: state.displays[id]

    fun loadFromSection(id: String, section: ConfigurationSection) {
        if (section.contains("conditions")) {
            registerConditional(ConditionalDisplay(id, section))
        } else {
            register(Display(id, section))
        }
    }

    @Synchronized
    fun reload() {
        val transaction = staging.get()
        if (transaction != null) {
            transaction.displays.clear()
            transaction.conditionalDisplays.clear()
        } else {
            state = State()
        }
    }

    fun getDisplayIds(): List<String> =
        (state.displays.keys + state.conditionalDisplays.keys).toList()

    fun getDisplayCount(): Int =
        state.displays.size + state.conditionalDisplays.size

    internal fun beginReload() {
        check(staging.get() == null) { "Display reload transaction already active" }
        val current = state
        staging.set(
            Staging(
                current.displays.toMutableMap(),
                current.conditionalDisplays.toMutableMap()
            )
        )
    }

    @Synchronized
    internal fun commitReload() {
        val transaction = staging.get()
            ?: throw IllegalStateException("Display reload transaction not active")
        state = State(
            transaction.displays.toMap(),
            transaction.conditionalDisplays.toMap()
        )
        staging.remove()
    }

    internal fun rollbackReload() {
        staging.remove()
    }

    internal fun validationErrors(): List<String> {
        val displays = staging.get()?.conditionalDisplays ?: state.conditionalDisplays
        return displays.values.flatMap { display ->
            display.conditions.mapIndexedNotNull { index, condition ->
                if (condition.compiled == null) "${display.id}.conditions[$index] 编译失败" else null
            }
        }
    }
}
