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

package priv.seventeen.artist.overture.core.registry

import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.blink.event.AutoListener
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 统一清理所有由第三方插件持有的扩展注册。
 */
object ExtensionRegistryHub {
    private val registries = Collections.newSetFromMap(
        IdentityHashMap<OwnerCleanupRegistry, Boolean>()
    )

    internal fun attach(registry: OwnerCleanupRegistry) {
        synchronized(registries) {
            registries += registry
        }
    }

    fun unregisterOwner(owner: Plugin): Int {
        val removed = synchronized(registries) {
            registries.sumOf { it.unregisterOwner(owner) }
        }
        if (removed > 0) {
            BlinkLog.info(
                LanguageManager.text(
                    "console.extensions-unregistered",
                    "owner" to owner.name,
                    "count" to removed
                )
            )
        }
        return removed
    }

    @AutoListener
    fun onPluginDisable(event: PluginDisableEvent) {
        unregisterOwner(event.plugin)
    }
}
