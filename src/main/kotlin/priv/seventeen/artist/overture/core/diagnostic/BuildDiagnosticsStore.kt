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

package priv.seventeen.artist.overture.core.diagnostic

import org.bukkit.entity.Player
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.ItemStreamGenerated
import priv.seventeen.artist.overture.core.item.ItemSignal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BuildDiagnostics(
    val itemId: String,
    val timestampMillis: Long,
    val actionTrace: List<String>,
    val timingsNanos: Map<String, Long>
)

/**
 * 最近一次构建诊断。只保存轻量字符串/数字，不持有 Player、ItemStack 或插件回调。
 */
object BuildDiagnosticsStore {
    private data class PlayerItemKey(val playerId: UUID, val itemId: String)

    private val byPlayer = ConcurrentHashMap<PlayerItemKey, BuildDiagnostics>()
    private val byItem = ConcurrentHashMap<String, BuildDiagnostics>()

    fun record(player: Player?, stream: ItemStream) {
        if (ItemSignal.TEMPLATE in stream.signals) return
        val itemId = stream.overtureId ?: return
        val timings = linkedMapOf<String, Long>()
        timings.putAll(stream.extensionTimings)
        if (stream is ItemStreamGenerated) {
            stream.renderTimings.forEach { (key, value) ->
                timings["render:$key"] = value
            }
        }
        val diagnostics = BuildDiagnostics(
            itemId,
            System.currentTimeMillis(),
            stream.actionTrace.toList(),
            timings
        )
        byItem[itemId] = diagnostics
        if (player != null) byPlayer[PlayerItemKey(player.uniqueId, itemId)] = diagnostics
    }

    fun get(player: Player?, itemId: String): BuildDiagnostics? =
        player?.let { byPlayer[PlayerItemKey(it.uniqueId, itemId)] } ?: byItem[itemId]

    fun removePlayer(playerId: UUID) {
        byPlayer.keys.removeIf { it.playerId == playerId }
    }

    fun clear() {
        byPlayer.clear()
        byItem.clear()
    }
}
