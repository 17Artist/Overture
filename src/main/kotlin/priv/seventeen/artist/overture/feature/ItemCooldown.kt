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

import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * 物品冷却系统
 */
object ItemCooldown {

    // 玩家UUID -> (冷却键 -> 开始时间)
    private val cooldowns = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    /**
     * 检查冷却是否结束
     * @param player 玩家
     * @param key 冷却键
     * @param duration 冷却时长（毫秒）
     * @return true = 冷却已结束（可以使用）
     */
    fun check(player: Player, key: String, duration: Long): Boolean {
        val playerKey = player.uniqueId.toString()
        val map = cooldowns[playerKey] ?: return true
        val startTime = map[key] ?: return true
        return System.currentTimeMillis() - startTime >= duration
    }

    /**
     * 设置冷却开始时间
     */
    fun set(player: Player, key: String) {
        val playerKey = player.uniqueId.toString()
        cooldowns.getOrPut(playerKey) { ConcurrentHashMap() }[key] = System.currentTimeMillis()
    }

    /**
     * 获取剩余冷却时间（毫秒）
     * @param duration 冷却总时长（毫秒），不传则返回已经过的时间
     * @return 剩余时间，0 表示冷却已结束或无冷却
     */
    fun remaining(player: Player, key: String, duration: Long = 0L): Long {
        val playerKey = player.uniqueId.toString()
        val map = cooldowns[playerKey] ?: return 0
        val startTime = map[key] ?: return 0
        val elapsed = System.currentTimeMillis() - startTime
        return (duration - elapsed).coerceAtLeast(0)
    }

    /**
     * 清除玩家所有冷却
     */
    fun clear(player: Player) {
        cooldowns.remove(player.uniqueId.toString())
    }

    /**
     * 清除所有冷却数据
     */
    fun clearAll() {
        cooldowns.clear()
    }
}
