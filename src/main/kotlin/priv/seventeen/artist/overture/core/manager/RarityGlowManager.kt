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

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Item
import org.bukkit.scoreboard.Team
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.overture.OvertureConfig
import priv.seventeen.artist.overture.core.item.ItemStream
import java.io.File

/**
 * 品质发光管理器
 *
 * 利用 Scoreboard Team 颜色控制掉落物的发光轮廓颜色。
 * 每个品质对应一个 Team，启动时一次性创建，运行期只做 entry 增删，开销极低。
 *
 * Team 命名规则: "ot_rarity_<tier_lowercase>"，前缀便于 reload/启动时精准清理残留。
 */
object RarityGlowManager {

    private const val TEAM_PREFIX = "ot_rarity_"

    /** tier → Team */
    private val teams = mutableMapOf<String, Team>()

    /** uuid → tier，用于 O(1) removeGlow，避免遍历所有 Team */
    private val entityTier = mutableMapOf<String, String>()

    /**
     * 从 rarity.yml 加载品质定义，注册对应的 Scoreboard Team。
     * 插件启动和 reload 时调用。
     *
     * rarity.yml 格式:
     * ```yaml
     * COMMON:
     *   color: WHITE
     * LEGENDARY:
     *   color: GOLD
     * ```
     */
    fun load(rarityFile: File) {
        if (!rarityFile.exists()) {
            writeDefaults(rarityFile)
        }

        val yaml = YamlConfiguration.loadConfiguration(rarityFile)
        val tiers = mutableMapOf<String, RarityTier>()

        for (tier in yaml.getKeys(false)) {
            val color = yaml.getString("$tier.color") ?: continue
            tiers[tier.uppercase()] = RarityTier(color)
        }

        if (!OvertureConfig.instance.rarity.enabled) {
            clearExistingGlows()
            unregisterTeams()
            return
        }
        clearExistingGlows()
        reload(tiers)
        rescanExistingDrops()
    }

    /**
     * 根据解析好的 tier map 注册/更新 Scoreboard Team。
     * 内部先清理旧数据，再重建。
     */
    private fun reload(tiers: Map<String, RarityTier>) {
        val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard

        // 卸载旧 Team，清空索引
        teams.values.forEach { runCatching { it.unregister() } }
        teams.clear()
        entityTier.clear()

        for ((tier, cfg) in tiers) {
            val teamName = "$TEAM_PREFIX${tier.lowercase()}"
            val color = runCatching { ChatColor.valueOf(cfg.color.uppercase()) }.getOrElse {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.rarity-color-invalid",
                        "tier" to tier,
                        "color" to cfg.color
                    )
                )
                ChatColor.WHITE
            }
            // reload 后 Team 已被 unregister，直接重新注册
            val team = scoreboard.registerNewTeam(teamName)
            team.color = color
            teams[tier] = team
        }

        BlinkLog.info(
            LanguageManager.text(
                "console.rarity-loaded",
                "count" to teams.size,
                "tiers" to teams.keys.joinToString()
            )
        )
    }

    /**
     * 对掉落物应用发光效果。
     * 若物品不含 rarity 数据或品质未在 rarity.yml 中配置，则静默跳过。
     */
    fun applyGlow(entity: Item) {
        if (!OvertureConfig.instance.rarity.enabled) return
        val tier = ItemStream(entity.itemStack).overtureData.getString("rarity") ?: return
        val team = teams[tier] ?: return
        val uuid = entity.uniqueId.toString()
        team.addEntry(uuid)
        entityTier[uuid] = tier
        entity.isGlowing = true
    }

    /**
     * 移除掉落物的发光 entry（拾取、消失时调用）。
     * 通过反向索引 O(1) 定位 Team，无需遍历。
     */
    fun removeGlow(entity: Item) {
        val uuid = entity.uniqueId.toString()
        val tier = entityTier.remove(uuid) ?: return
        teams[tier]?.removeEntry(uuid)
        entity.isGlowing = false
    }

    /**
     * 插件禁用时调用：卸载所有 Team 并清空索引。
     */
    fun cleanup() {
        clearExistingGlows()
        unregisterTeams()
    }

    private fun unregisterTeams() {
        teams.values.forEach { runCatching { it.unregister() } }
        teams.clear()
        entityTier.clear()
    }

    private fun clearExistingGlows() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.filterIsInstance<Item>().forEach { item ->
                val stream = ItemStream(item.itemStack)
                if (stream.isOverture) item.isGlowing = false
            }
        }
    }

    /**
     * 插件首次启动时调用：清理上次非正常关闭残留的同前缀 Team。
     * 只需在 onEnable 前调用一次，reload 不需要调用。
     */
    fun cleanupStale() {
        val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
        scoreboard.teams
            .filter { it.name.startsWith(TEAM_PREFIX) }
            .forEach { runCatching { it.unregister() } }
    }

    /**
     * 启动 / reload 后补扫世界中已存在的掉落物。
     *
     * 重启服务器时，掉落物实体会保留 glowing 状态，但 Scoreboard Team entry 不会自动恢复，
     * 这会导致客户端看到默认黑色描边。这里延迟 1 tick 重新把 Overture 掉落物加入对应 Team。
     * 只在 load() 后执行一次，不做持续扫描。
     */
    private fun rescanExistingDrops() {
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            var count = 0
            Bukkit.getWorlds().forEach { world ->
                world.entities.filterIsInstance<Item>().forEach entityLoop@{ item ->
                    val stream = ItemStream(item.itemStack)
                    if (!stream.isOverture) return@entityLoop
                    applyGlow(item)
                    count++
                }
            }
            if (count > 0) {
                BlinkLog.info(LanguageManager.text("console.rarity-reapplied", "count" to count))
            }
        }, 1L)
    }

    /** 当前已注册的品质，供调试使用 */
    fun registeredTiers(): Set<String> = teams.keys.toSet()

    // ==================== 内部工具 ====================

    private fun writeDefaults(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            # Overture 品质发光配置
            # color 填写 Bukkit ChatColor 枚举名（大小写不敏感）
            # 完整颜色列表: WHITE, BLACK, DARK_GRAY, GRAY,
            #   RED, DARK_RED, GOLD, YELLOW,
            #   GREEN, DARK_GREEN, AQUA, DARK_AQUA,
            #   BLUE, DARK_BLUE, LIGHT_PURPLE, DARK_PURPLE
            
            COMMON:
              color: WHITE
            
            UNCOMMON:
              color: GREEN
            
            RARE:
              color: AQUA
            
            EPIC:
              color: BLUE
            
            LEGENDARY:
              color: GOLD
            """.trimIndent()
        )
        BlinkLog.info(LanguageManager.text("console.rarity-default-created"))
    }
}

/** rarity.yml 单条品质数据 */
data class RarityTier(val color: String)
