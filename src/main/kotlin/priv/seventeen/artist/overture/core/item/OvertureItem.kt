package priv.seventeen.artist.overture.core.item

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.overture.api.event.ItemBuildEvent
import priv.seventeen.artist.overture.core.action.ActionTrigger
import priv.seventeen.artist.overture.core.action.ItemAction
import priv.seventeen.artist.overture.core.group.ItemGroup
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaRegistry
import priv.seventeen.artist.overture.util.ColorUtil
import priv.seventeen.artist.overture.util.HashUtil
import priv.seventeen.artist.overture.util.Translator

/**
 * 物品定义
 * 从 YAML 配置加载的物品模板
 */
class OvertureItem(
    /** 物品唯一标识 */
    val id: String,
    /** 原始配置节 */
    val config: ConfigurationSection
) {

    /** 引用的展示方案 ID */
    val display: String? = config.getString("display")

    /** 材质 */
    val material: Material = Material.matchMaterial(config.getString("icon", "STONE")!!.removeSuffix("!!").uppercase())
        ?: Material.STONE

    /** 材质是否锁定 */
    val iconLocked: Boolean = config.contains("icon!!") || config.getString("icon", "")!!.endsWith("!!")

    /** 名称变量 */
    val nameVars: Map<String, String> = loadNameVars()

    /** 名称是否锁定 */
    val nameLocked: Boolean = config.contains("name!!")

    /** 描述变量 */
    val loreVars: Map<String, MutableList<String>> = loadLoreVars()

    /** 描述是否锁定 */
    val loreLocked: Boolean = config.contains("lore!!")

    /** 活跃数据翻译结果 */
    val dataResult: Translator.TranslateResult? = config.getConfigurationSection("data")?.let { Translator.fromSection(it) }

    /** 锁定数据映射 */
    val lockedData: Map<String, ItemTagData?> = dataResult?.lockedData ?: emptyMap()

    /** data-mapper 配置 */
    val dataMapper: Map<String, String> = loadDataMapper()

    /** Meta 列表 */
    val metaList: List<Meta> = loadMeta()

    /** 事件动作映射 */
    val actions: Map<ActionTrigger, ItemAction> by lazy { loadActions() }

    /** 引用的事件模型 ID 列表 */
    val modelIds: List<String> = config.getStringList("event.from")

    /** 事件变量 */
    val eventVars: Map<String, Any> = loadEventVars()

    /** 版本签名 (SHA-1) */
    val version: String = computeVersion()

    /** 所属分组 */
    var group: ItemGroup? = null

    /**
     * 构建物品（首次生成）
     */
    fun build(player: Player?): ItemStreamGenerated {
        val item = ItemStack(material)
        val stream = ItemStreamGenerated(
            item,
            nameVars.toMutableMap(),
            loreVars.mapValues { it.value.toMutableList() }.toMutableMap()
        )

        // 写入 ID
        val root = stream.getOrCreateRoot()
        root.putString(ItemKey.ID, id)

        // 写入活跃数据
        dataResult?.tag?.let { dataTag ->
            root.putCompound(ItemKey.DATA, dataTag.deepClone())
        }

        // 写入锁定数据
        applyLockedData(stream)

        // 触发 Pre 事件
        val preEvent = ItemBuildEvent.Pre(player, id, stream)
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) return stream

        // 写入版本签名
        stream.setVersion(version)

        // 触发 Post 事件（Meta build/drop 在此处理）
        val postEvent = ItemBuildEvent.Post(player, id, stream)
        Bukkit.getPluginManager().callEvent(postEvent)

        return stream
    }

    /**
     * 构建物品（基于已有 ItemStream 更新）
     */
    fun build(player: Player?, existingStream: ItemStream): ItemStreamGenerated {
        val stream = ItemStreamGenerated(
            existingStream.sourceItem,
            nameVars.toMutableMap(),
            loreVars.mapValues { it.value.toMutableList() }.toMutableMap()
        )

        // 继承信号
        stream.signals.addAll(existingStream.signals)

        // 写入锁定数据（强制覆盖）
        applyLockedData(stream)

        // 触发 Pre 事件
        val preEvent = ItemBuildEvent.Pre(player, id, stream)
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) return stream

        // 写入版本签名
        stream.setVersion(version)

        // 触发 Post 事件
        val postEvent = ItemBuildEvent.Post(player, id, stream)
        Bukkit.getPluginManager().callEvent(postEvent)

        return stream
    }

    /**
     * 快速生成 ItemStack（用于菜单展示等）
     */
    fun buildItemStack(player: Player? = null): ItemStack {
        return build(player).toItemStack(player)
    }

    /**
     * 应用锁定数据
     */
    private fun applyLockedData(stream: ItemStream) {
        for ((path, data) in lockedData) {
            if (data != null) {
                stream.setData(path, data)
            }
        }
    }

    /**
     * 计算版本签名
     */
    private fun computeVersion(): String {
        return HashUtil.sha1(config.getValues(true).toString())
    }

    private fun loadNameVars(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val section = config.getConfigurationSection("name")
            ?: config.getConfigurationSection("name!!")
        section?.getKeys(false)?.forEach { key ->
            result[key] = ColorUtil.colored(section.getString(key, "")!!)
        }
        return result
    }

    private fun loadLoreVars(): Map<String, MutableList<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        val section = config.getConfigurationSection("lore")
            ?: config.getConfigurationSection("lore!!")
        section?.getKeys(false)?.forEach { key ->
            val value = section.get(key)
            when (value) {
                is List<*> -> result[key] = value.filterIsInstance<String>().map { ColorUtil.colored(it) }.toMutableList()
                is String -> result[key] = mutableListOf(ColorUtil.colored(value))
                else -> result[key] = mutableListOf()
            }
        }
        return result
    }

    private fun loadDataMapper(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val section = config.getConfigurationSection("data-mapper") ?: return result
        section.getKeys(false).forEach { key ->
            result[key] = section.getString(key, "")!!
        }
        return result
    }

    private fun loadMeta(): List<Meta> {
        val section = config.getConfigurationSection("meta") ?: return emptyList()
        val result = mutableListOf<Meta>()
        for (key in section.getKeys(false)) {
            val cleanKey = key.removeSuffix("!!")
            val locked = key.endsWith("!!")
            val metaSection = section.getConfigurationSection(key)
            val metaValue = section.get(key)
            val meta = MetaRegistry.create(cleanKey, metaSection, metaValue, locked)
            if (meta != null) {
                result.add(meta)
            }
        }
        return result
    }

    private fun loadActions(): Map<ActionTrigger, ItemAction> {
        val result = mutableMapOf<ActionTrigger, ItemAction>()
        val eventSection = config.getConfigurationSection("event") ?: return result

        for (key in eventSection.getKeys(false)) {
            if (key == "from" || key == "data") continue
            val cleanKey = key.removeSuffix("!!")
            val cancelEvent = key.endsWith("!!")
            val trigger = ActionTrigger.fromKey(cleanKey) ?: continue
            val script = eventSection.getString(key) ?: continue
            result[trigger] = ItemAction(trigger, script, cancelEvent).compile()
        }
        return result
    }

    private fun loadEventVars(): Map<String, Any> {
        val section = config.getConfigurationSection("event.data") ?: return emptyMap()
        val result = mutableMapOf<String, Any>()
        section.getKeys(true).forEach { key ->
            val value = section.get(key)
            if (value != null && value !is ConfigurationSection) {
                result[key] = value
            }
        }
        return result
    }

    override fun toString(): String = "OvertureItem(id=$id)"
}
