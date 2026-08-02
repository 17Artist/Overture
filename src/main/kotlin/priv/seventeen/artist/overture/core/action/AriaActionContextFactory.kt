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
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.context.Context
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.aria.value.StoreOnlyValue
import priv.seventeen.artist.overture.core.item.ItemStream

/**
 * 创建一次性 Aria 动作上下文。
 *
 * 每次执行都创建独立的 LocalStorage/ScopeStack，只注入当前 item 与可选 player；
 */
internal object AriaActionContextFactory {
    internal val ITEM_KEY: VariableKey = VariableKey.of("item")
    internal val PLAYER_KEY: VariableKey = VariableKey.of("player")

    fun create(stream: ItemStream, player: Player?): Context =
        createWithValues(stream, player)

    internal fun createWithValues(item: Any, player: Any?): Context {
        val context = Aria.createContext()
        context.forceSetLocalValue(ITEM_KEY, StoreOnlyValue(item))
        if (player != null) {
            context.forceSetLocalValue(PLAYER_KEY, StoreOnlyValue(player))
        }
        return context
    }
}
