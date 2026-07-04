package priv.seventeen.artist.overture

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.overture.core.action.AriaRegistry
import priv.seventeen.artist.overture.core.manager.DisplayManager
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.LoaderManager
import priv.seventeen.artist.overture.core.manager.RarityGlowManager
import priv.seventeen.artist.overture.core.manager.YamlItemProvider
import priv.seventeen.artist.overture.feature.ItemAsyncTick
import priv.seventeen.artist.overture.feature.ItemDurability
import java.io.File

/**
 * Overture 插件入口
 */
object Overture {

    @Awake(LifeCycle.LOAD)
    fun onLoad() {
        BlinkLog.info("Overture 物品库加载中...")

        // 注册 Aria 内置函数
        AriaRegistry.register()
    }

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        val dataFolder = bukkitPlugin.dataFolder
        dataFolder.mkdirs()
        File(dataFolder, "items").mkdirs()
        File(dataFolder, "displays").mkdirs()

        // 加载配置（BlinkConfig）
        OvertureConfig.load()
        applyConfig()

        // 清理上次非正常关闭残留的 Team，仅首次启动需要
        RarityGlowManager.cleanupStale()

        // 加载品质发光
        RarityGlowManager.load(File(dataFolder, "rarity.yml"))

        // 注册默认物品提供者
        ItemManager.registerProvider(YamlItemProvider(dataFolder))

        // 加载展示方案
        LoaderManager.loadDisplays(File(dataFolder, "displays"))

        // 加载物品
        ItemManager.reload()

        // 检测可选依赖（触发 lazy 初始化，日志在此时打印）
        priv.seventeen.artist.overture.hook.ArcartXHook.enabled

        BlinkLog.success("已加载 §b${ItemManager.getItems().size} §f个物品, §b${DisplayManager.getDisplayCount()} §f个展示方案")
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        RarityGlowManager.cleanup()
        BlinkLog.info("Overture 已禁用")
    }

    /**
     * 将配置应用到各模块
     */
    fun applyConfig() {
        val config = OvertureConfig.instance
        ItemAsyncTick.period = config.update.asyncTickPeriod
        ItemDurability.displayFormat = config.durability.display
        ItemDurability.symbolFull = config.durability.displaySymbol.full
        ItemDurability.symbolEmpty = config.durability.displaySymbol.empty
        ItemDurability.scale = config.durability.scale
    }
}
