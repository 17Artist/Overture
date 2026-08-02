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

package priv.seventeen.artist.overture.core.behavior

import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.api.behavior.ItemBehaviorContext
import priv.seventeen.artist.overture.api.behavior.ItemBehaviorSignal
import priv.seventeen.artist.overture.core.component.ItemDataService
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem
import java.util.concurrent.TimeUnit

object ItemBehaviorDispatcher {
    private const val SOFT_BUDGET_MILLIS = 5L

    data class Result(
        val executed: Int,
        val stopped: Boolean
    )

    fun dispatch(
        item: OvertureItem,
        trigger: TriggerKey,
        player: Player?,
        stream: ItemStream,
        event: Event?,
        variables: Map<String, Any>
    ): Result {
        var executed = 0
        var stopped = false
        for (binding in item.behaviors) {
            val registered = ItemBehaviorRegistry.resolve(binding.key) ?: continue
            if (!registered.owner.isEnabled) continue

            val started = System.nanoTime()
            val result = try {
                registered.value.onTrigger(
                    ItemBehaviorContext(
                        trigger,
                        player,
                        item.id,
                        stream.sourceItem.clone(),
                        ItemDataService.mutable(stream),
                        event,
                        item.eventVars + variables,
                        binding.options,
                        !Bukkit.isPrimaryThread()
                    )
                )
            } catch (error: Throwable) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.behavior-run-failed",
                        "key" to binding.key,
                        "owner" to registered.owner.name,
                        "trigger" to trigger,
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                )
                continue
            }
            val elapsed = System.nanoTime() - started
            stream.extensionTimings["behavior:${binding.key}@$trigger"] = elapsed
            if (elapsed > TimeUnit.MILLISECONDS.toNanos(SOFT_BUDGET_MILLIS)) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.behavior-slow",
                        "key" to binding.key,
                        "elapsed" to "%.2f".format(elapsed / 1_000_000.0),
                        "budget" to SOFT_BUDGET_MILLIS
                    )
                )
            }

            executed++
            stream.actionTrace += "behavior:${binding.key}@$trigger"
            if (result.changed) stream.signals += ItemSignal.ITEM_CHANGED
            stream.signals += result.signals.mapTo(linkedSetOf(), ::toInternalSignal)
            if (result.cancelEvent && event is Cancellable) event.isCancelled = true
            if (result.stopPropagation) {
                stopped = true
                break
            }
        }
        return Result(executed, stopped)
    }

    private fun toInternalSignal(signal: ItemBehaviorSignal): ItemSignal = when (signal) {
        ItemBehaviorSignal.DURABILITY_CHANGED -> ItemSignal.DURABILITY_CHANGED
        ItemBehaviorSignal.ITEM_CHANGED -> ItemSignal.ITEM_CHANGED
        ItemBehaviorSignal.DURABILITY_DESTROYED -> ItemSignal.DURABILITY_DESTROYED
    }
}
