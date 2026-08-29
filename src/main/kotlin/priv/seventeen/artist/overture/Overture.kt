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

package priv.seventeen.artist.overture

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.blink.nms.AsteroidManager
import priv.seventeen.artist.blink.script.AriaScriptManager
import org.bukkit.NamespacedKey
import priv.seventeen.artist.overture.core.action.AriaRegistry
import priv.seventeen.artist.overture.core.manager.DisplayManager
import priv.seventeen.artist.overture.core.manager.DropLabelManager
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.LoaderManager
import priv.seventeen.artist.overture.core.manager.RarityGlowManager
import priv.seventeen.artist.overture.core.manager.YamlItemProvider
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.resource.DefaultResourceInstaller
import priv.seventeen.artist.overture.feature.ItemAsyncTick
import priv.seventeen.artist.overture.feature.ItemCooldown
import priv.seventeen.artist.overture.feature.ItemDurability
import priv.seventeen.artist.overture.core.diagnostic.BuildDiagnosticsStore
import java.io.File

/**
 * Overture 插件入口
 */
object Overture {

    @Volatile
    var ready: Boolean = false
        private set

    @Awake(LifeCycle.LOAD, priority = -100)
    fun onLoad() {
        reloadLanguage()
        BlinkLog.info(LanguageManager.text("console.lifecycle.loading"))

        if (!AriaScriptManager.isAvailable) {
            BlinkLog.error(LanguageManager.text("console.lifecycle.missing-aria"))
            return
        }
        if (!AsteroidManager.isAvailable) {
            BlinkLog.error(LanguageManager.text("console.lifecycle.missing-asteroid"))
            return
        }

        AriaRegistry.register()
        ready = true
    }

    @Awake(LifeCycle.ENABLE, priority = -100)
    fun onEnable() {
        if (!ready) {
            BlinkLog.error(LanguageManager.text("console.lifecycle.runtime-unavailable"))
            Bukkit.getPluginManager().disablePlugin(bukkitPlugin)
            return
        }

        val dataFolder = bukkitPlugin.dataFolder
        val defaultResources = try {
            DefaultResourceInstaller.install(dataFolder, bukkitPlugin::getResource)
        } catch (error: Exception) {
            BlinkLog.error(
                LanguageManager.text(
                    "console.lifecycle.default-files-failed",
                    "error" to (error.message ?: error.javaClass.simpleName)
                )
            )
            Bukkit.getPluginManager().disablePlugin(bukkitPlugin)
            return
        }
        reloadLanguage()
        if (defaultResources.copied.isNotEmpty()) {
            BlinkLog.success(
                LanguageManager.text(
                    "console.lifecycle.default-files-created",
                    "count" to defaultResources.copied.size
                )
            )
        }

        // 加载配置（BlinkConfig）
        OvertureConfig.load()
        applyConfig()

        // 清理上次非正常关闭残留的 Team，仅首次启动需要
        RarityGlowManager.cleanupStale()

        // 加载品质发光
        RarityGlowManager.load(File(dataFolder, "rarity.yml"))

        // 注册默认物品提供者
        ItemManager.registerProvider(
            bukkitPlugin,
            NamespacedKey(bukkitPlugin, "yaml"),
            YamlItemProvider(dataFolder)
        )

        // YamlItemProvider.reload() 会同时重载展示方案，避免重复扫描 displays。
        val reloadReport = ItemManager.reloadWithReport()
        if (!reloadReport.success) {
            BlinkLog.error(LanguageManager.text("console.lifecycle.initial-load-failed"))
            reloadReport.issues.take(20).forEach { issue ->
                BlinkLog.error(LanguageManager.text(
                    "console.lifecycle.initial-load-issue",
                    "severity" to issue.severity.name,
                    "source" to issue.source,
                    "item" to (issue.itemId ?: "-"),
                    "path" to (issue.path ?: "-"),
                    "component" to (issue.component ?: "-"),
                    "message" to issue.message
                ))
            }
            org.bukkit.Bukkit.getPluginManager().disablePlugin(bukkitPlugin)
            return
        }

        // 掉落标签解析依赖已加载的物品定义，必须在首次物品重载成功后恢复。
        DropLabelManager.load(File(dataFolder, "drop-labels.yml"))
        DropLabelManager.init()

        BlinkLog.success(
            LanguageManager.text(
                "console.lifecycle.enabled",
                "items" to ItemManager.getItems().size,
                "displays" to DisplayManager.getDisplayCount()
            )
        )
    }

    @Awake(LifeCycle.DISABLE, priority = 100)
    fun onDisable() {
        RarityGlowManager.cleanup()
        DropLabelManager.cleanup()
        ItemCooldown.clearAll()
        BuildDiagnosticsStore.clear()
        ItemManager.clear()
        ready = false
        BlinkLog.info(LanguageManager.text("console.lifecycle.disabled"))
    }

    /**
     * 将配置应用到各模块
     */
    fun applyConfig() {
        val config = OvertureConfig.instance
        ItemAsyncTick.configure(config.update.asyncTickPeriod)
        ItemDurability.displayFormat = config.durability.display
        ItemDurability.symbolFull = config.durability.displaySymbol.full
        ItemDurability.symbolEmpty = config.durability.displaySymbol.empty
        ItemDurability.scale = config.durability.scale.coerceAtLeast(1)
    }

    fun reloadLanguage(): Boolean {
        val result = LanguageManager.load(
            File(bukkitPlugin.dataFolder, "language.yml"),
            bukkitPlugin::getResource
        )
        if (!result.success) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.language-load-failed",
                    "error" to result.error
                )
            )
        }
        return result.success
    }
}
