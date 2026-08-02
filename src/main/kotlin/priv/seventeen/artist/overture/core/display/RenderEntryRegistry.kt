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

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.api.render.RenderEntryContext
import priv.seventeen.artist.overture.api.render.RenderEntryRenderer
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.registry.OwnedRegistry
import priv.seventeen.artist.overture.util.ColorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 自定义展示条目注册表：`<namespace:key>` 可用于名称，`<namespace:key...>` 可用于 Lore。
 */
object RenderEntryRegistry {
    private const val SOFT_BUDGET_MILLIS = 5L
    private const val MAX_LINES = 128
    private const val MAX_LINE_LENGTH = 2048

    private val registry = OwnedRegistry<RenderEntryRenderer>("render-entry")
    private val warnedAsync = ConcurrentHashMap.newKeySet<NamespacedKey>()

    fun register(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        renderer: RenderEntryRenderer
    ): RegistrationHandle = registry.register(owner, key, priority, renderer)

    fun render(
        requestedEntries: Set<String>,
        player: Player?,
        stream: ItemStream,
        displayId: String,
        timings: MutableMap<String, Long>
    ): Map<String, List<String>> {
        if (requestedEntries.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, List<String>>()

        for (rawKey in requestedEntries.sorted()) {
            val key = NamespacedKey.fromString(rawKey) ?: continue
            val entry = registry.active(key) ?: continue
            if (!Bukkit.isPrimaryThread()) {
                if (warnedAsync.add(key)) {
                    BlinkLog.warn(
                        LanguageManager.text("console.render-async-skipped", "key" to key)
                    )
                }
                continue
            }
            if (!entry.owner.isEnabled) continue

            val snapshot = stream.sourceItem.clone()
            val context = RenderEntryContext(
                key = key,
                player = player,
                itemStack = snapshot,
                displayId = displayId
            )
            val started = System.nanoTime()
            val lines = try {
                entry.value.onRender(context)
            } catch (error: Throwable) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.render-failed",
                        "key" to key,
                        "owner" to entry.owner.name,
                        "item" to (stream.overtureId ?: LanguageManager.raw("common.unknown")),
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                )
                emptyList()
            }
            val elapsed = System.nanoTime() - started
            timings[key.toString()] = elapsed
            if (elapsed > TimeUnit.MILLISECONDS.toNanos(SOFT_BUDGET_MILLIS)) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.render-slow",
                        "key" to key,
                        "elapsed" to "%.2f".format(elapsed / 1_000_000.0),
                        "budget" to SOFT_BUDGET_MILLIS
                    )
                )
            }

            result[rawKey] = try {
                lines
                    .filterNotNull()
                    .take(MAX_LINES)
                    .map { line -> ColorUtil.colored(line.take(MAX_LINE_LENGTH)) }
            } catch (error: Throwable) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.render-invalid-result",
                        "key" to key,
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                )
                emptyList()
            }
        }
        return result
    }

    fun registrations(): List<String> = registry.infos().map {
        "${it.key} owner=${it.ownerName} priority=${it.priority} active=${it.active}"
    }
}
