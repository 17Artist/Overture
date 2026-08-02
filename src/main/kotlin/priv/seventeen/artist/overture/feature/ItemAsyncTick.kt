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

package priv.seventeen.artist.overture.feature

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.overture.Overture
import priv.seventeen.artist.overture.core.manager.UpdateManager

/**
 * 异步 Tick 定时任务
 * 定期检查在线玩家背包中的物品更新
 */
object ItemAsyncTick {

    var period: Long = 100L
        private set

    private var task: BukkitTask? = null

    fun configure(newPeriod: Long) {
        period = newPeriod.coerceAtLeast(1L)
        if (task != null) {
            start()
        }
    }

    @Awake(LifeCycle.ENABLE, priority = 100)
    fun start() {
        stop()
        if (!Overture.ready) return

        task = Bukkit.getScheduler().runTaskTimer(bukkitPlugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                try {
                    UpdateManager.checkInventory(player)
                } catch (e: Exception) {
                    BlinkLog.warn(
                        LanguageManager.text(
                            "console.player-update-failed",
                            "player" to player.name,
                            "error" to (e.message ?: e.javaClass.simpleName)
                        )
                    )
                }
            }
        }, period, period)
    }

    @Awake(LifeCycle.DISABLE, priority = -100)
    fun stop() {
        task?.cancel()
        task = null
    }
}
