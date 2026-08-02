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

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.core.registry.OwnedRegistry

/**
 * 内建及第三方动作触发器。
 */
object TriggerRegistry {
    data class Definition(val description: String)

    private val registry = OwnedRegistry<Definition>("action-trigger")
    private val builtInsByConfigKey: Map<String, TriggerKey> =
        OvertureTriggers.ALL.associateBy(TriggerKey::value)

    fun register(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        description: String = ""
    ): RegistrationHandle = registry.register(owner, key, priority, Definition(description))

    fun resolve(configKey: String): TriggerKey? {
        val normalized = configKey.lowercase()
        builtInsByConfigKey[normalized]?.let { return it }
        if (':' !in normalized) return null
        val key = NamespacedKey.fromString(normalized) ?: return null
        // 显式 namespaced key 在 provider 加载阶段先保留；第三方 Trigger 可在运行期注册，
        // 不能因首次快照的注册时序把配置动作永久丢掉。
        return TriggerKey(key)
    }

    fun isKnown(key: TriggerKey): Boolean =
        key in OvertureTriggers.ALL || registry.active(key.namespacedKey) != null

    fun registrations(): List<String> =
        OvertureTriggers.ALL.map { "$it owner=Overture built-in" } +
            registry.infos().map {
                "${it.key} owner=${it.ownerName} priority=${it.priority} active=${it.active}"
            }
}

object OvertureTriggers {
    private fun key(value: String) =
        TriggerKey(requireNotNull(NamespacedKey.fromString("overture:$value")))

    @JvmField val ON_LEFT_CLICK = key("on_left_click")
    @JvmField val ON_RIGHT_CLICK = key("on_right_click")
    @JvmField val ON_RIGHT_CLICK_ENTITY = key("on_right_click_entity")
    @JvmField val ON_ATTACK = key("on_attack")
    @JvmField val ON_DAMAGE = key("on_damage")
    @JvmField val ON_CONSUME = key("on_consume")
    @JvmField val ON_DROP = key("on_drop")
    @JvmField val ON_PICK = key("on_pick")
    @JvmField val ON_BLOCK_BREAK = key("on_block_break")
    @JvmField val ON_ITEM_BREAK = key("on_item_break")
    @JvmField val ON_SWAP_TO_OFFHAND = key("on_swap_to_offhand")
    @JvmField val ON_SWAP_TO_MAINHAND = key("on_swap_to_mainhand")
    @JvmField val ON_BUILD = key("on_build")
    @JvmField val ON_RELEASE = key("on_release")
    @JvmField val ON_RELEASE_DISPLAY = key("on_release_display")

    @JvmField
    val ALL: Set<TriggerKey> = linkedSetOf(
        ON_LEFT_CLICK,
        ON_RIGHT_CLICK,
        ON_RIGHT_CLICK_ENTITY,
        ON_ATTACK,
        ON_DAMAGE,
        ON_CONSUME,
        ON_DROP,
        ON_PICK,
        ON_BLOCK_BREAK,
        ON_ITEM_BREAK,
        ON_SWAP_TO_OFFHAND,
        ON_SWAP_TO_MAINHAND,
        ON_BUILD,
        ON_RELEASE,
        ON_RELEASE_DISPLAY
    )
}
