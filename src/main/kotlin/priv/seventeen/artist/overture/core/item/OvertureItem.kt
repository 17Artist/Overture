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

package priv.seventeen.artist.overture.core.item

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.event.ItemBuildEvent
import priv.seventeen.artist.overture.core.action.ItemAction
import priv.seventeen.artist.overture.core.action.TriggerRegistry
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.api.behavior.BehaviorBinding
import priv.seventeen.artist.overture.core.component.ComponentStateMerger
import priv.seventeen.artist.overture.core.component.ItemComponentRegistry
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
class OvertureItem @JvmOverloads constructor(
    /** 物品唯一标识 */
    val id: String,
    /** 原始配置节 */
    val config: ConfigurationSection,
    /** 可定位到 provider 文件的来源。 */
    val source: String = "provider"
) {

    /** 引用的展示方案 ID */
    val display: String? = config.getString("display")

    /** 材质 */
    private val materialName: String = config.getString("icon!!")
        ?: config.getString("icon", "STONE")
        ?: "STONE"

    val material: Material = Material.getMaterial(materialName.uppercase())
        ?: Material.STONE

    /** 材质是否锁定 */
    val iconLocked: Boolean = config.contains("icon!!")

    /** 名称变量 */
    val nameVars: Map<String, String> = loadNameVars()

    /** 名称是否锁定 */
    val nameLocked: Boolean = config.contains("name!!")

    /** 描述变量 */
    val loreVars: Map<String, MutableList<String>> = loadLoreVars()

    /** 描述是否锁定 */
    val loreLocked: Boolean = config.contains("lore!!")

    /** 根级 data!! 会锁定整组数据；data 中仍可对单个键使用 !!。 */
    private val dataRootLocked: Boolean = config.contains("data!!")

    /** 活跃数据翻译结果 */
    val dataResult: Translator.TranslateResult? = (
        config.getConfigurationSection("data!!") ?: config.getConfigurationSection("data")
    )?.let { Translator.fromSection(it, lockAll = dataRootLocked) }

    /** 锁定数据映射 */
    val lockedData: Map<String, ItemTagData?> = dataResult?.lockedData ?: emptyMap()

    /** 在候选快照阶段编译完成的第三方组件定义与问题。 */
    internal val componentCompilation = ItemComponentRegistry.compile(id, source, config)

    val componentKeys: Set<String> = componentCompilation.namespaces.flatMapTo(linkedSetOf()) {
        (namespace, value) -> value.definitions.keys.map { "$namespace:$it" }
    }

    /** data-mapper 配置 */
    val dataMapper: Map<String, String> = loadDataMapper()

    /** Meta 列表 */
    val metaList: List<Meta> = loadMeta()

    /** 事件动作映射 */
    val actions: Map<TriggerKey, ItemAction> by lazy { loadActions() }

    /** 引用的事件模型 ID 列表 */
    val modelIds: List<String> = config.getStringList("event.from")

    /** 事件变量 */
    val eventVars: Map<String, Any> = loadEventVars()

    /** 第三方类型安全行为绑定。 */
    val behaviors: List<BehaviorBinding> = loadBehaviors()

    /** 版本签名 (SHA-1) */
    val version: String = computeVersion()

    /** 所属分组 */
    var group: ItemGroup? = null

    /**
     * 模板物品缓存
     * 以 player = null 完整构建一次，仅用于展示读取（菜单图标、名称查询等）
     * reload 后 OvertureItem 对象整体重建，缓存自然失效
     */
    private val templateItem: ItemStack? by lazy { buildInternal(null, template = true).toItemStackOrNull(null) }

    /** 模板展示名（缓存，不含玩家条件展示与实例数据变量） */
    val templateName: String? by lazy {
        templateItem?.itemMeta?.takeIf { it.hasDisplayName() }?.displayName
    }

    /** 模板描述（缓存，不含玩家条件展示与实例数据变量） */
    val templateLore: List<String> by lazy {
        templateItem?.itemMeta?.lore ?: emptyList()
    }

    /**
     * 获取模板物品副本
     * 仅用于展示场景（菜单图标等），不要直接发放给玩家：
     * unique 物品的 UUID、随机词条等实例数据不会重新生成
     */
    fun templateItemStack(): ItemStack? = templateItem?.clone()

    /**
     * 构建物品（首次生成）
     */
    fun build(player: Player?): ItemStreamGenerated = buildInternal(player, template = false)

    private fun buildInternal(player: Player?, template: Boolean): ItemStreamGenerated {
        val stream = createInitialStream(template)
        return finishBuild(player, stream)
    }

    /**
     * 从序列化数据恢复实例。先载入当前模板默认值，再注入持久化数据，最后重新应用
     * 当前锁定数据和组件 definition，确保实例状态与当前展示定义同时生效。
     */
    internal fun buildRestored(
        player: Player?,
        restore: (ItemStreamGenerated) -> Unit
    ): ItemStreamGenerated {
        val stream = createInitialStream(template = false)
        restore(stream)
        applyLockedData(stream)
        ComponentStateMerger.apply(stream, componentCompilation, updating = true)
        return finishBuild(player, stream)
    }

    private fun createInitialStream(template: Boolean): ItemStreamGenerated {
        val item = ItemStack(material)
        val stream = ItemStreamGenerated(
            item,
            nameVars.toMutableMap(),
            loreVars.mapValues { it.value.toMutableList() }.toMutableMap(),
            updating = false
        )
        if (template) stream.signals += ItemSignal.TEMPLATE

        // 写入 ID
        val root = stream.getOrCreateRoot()
        root.putString(ItemKey.ID, id)

        // 写入活跃数据
        dataResult?.tag?.let { dataTag ->
            root.putCompound(ItemKey.DATA, dataTag.deepClone())
        }

        // 写入锁定数据
        applyLockedData(stream)

        // 组件 definition 是当前模板的权威值；新物品没有需要继承的 instance。
        ComponentStateMerger.apply(stream, componentCompilation, updating = false)
        return stream
    }

    private fun finishBuild(player: Player?, stream: ItemStreamGenerated): ItemStreamGenerated {

        // 触发 Pre 事件
        val preEvent = ItemBuildEvent.Pre(player, id, stream)
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) {
            stream.buildCancelled = true
            return stream
        }

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
        // 先将内存中的 NBT 修改写回 sourceItem，确保新 stream 读到最新数据
        val savedItem = existingStream.save()
        if (iconLocked) {
            savedItem.type = material
        }

        val stream = ItemStreamGenerated(
            savedItem,
            nameVars.toMutableMap(),
            loreVars.mapValues { it.value.toMutableList() }.toMutableMap(),
            updating = true
        )

        // 继承信号
        stream.signals.addAll(existingStream.signals)
        stream.actionTrace.addAll(existingStream.actionTrace)
        stream.extensionTimings.putAll(existingStream.extensionTimings)

        // 写入锁定数据（强制覆盖）
        applyLockedData(stream)

        // 完整覆盖 definition，同时保留已有 namespace.instance。
        ComponentStateMerger.apply(stream, componentCompilation, updating = true)

        // 触发 Pre 事件
        val preEvent = ItemBuildEvent.Pre(player, id, stream)
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) {
            stream.buildCancelled = true
            return stream
        }

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
    fun buildItemStack(player: Player? = null): ItemStack? = build(player).toItemStackOrNull(player)

    private fun ItemStreamGenerated.toItemStackOrNull(player: Player?): ItemStack? =
        if (buildCancelled) null else toItemStack(player)

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
        val canonical = config.getValues(true)
            .entries
            .sortedBy { it.key }
            .joinToString("\n") { (key, value) -> "$key=${canonicalValue(value)}" }
        return HashUtil.sha1(canonical)
    }

    private fun canonicalValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { canonicalValue(it) }
            is Map<*, *> -> value.entries
                .sortedBy { it.key.toString() }
                .joinToString(prefix = "{", postfix = "}") { "${it.key}=${canonicalValue(it.value)}" }
            else -> value.toString()
        }
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
        val rootLocked = config.contains("meta!!")
        val section = config.getConfigurationSection("meta!!")
            ?: config.getConfigurationSection("meta")
            ?: return emptyList()
        val result = mutableListOf<Meta>()
        for (key in section.getKeys(false)) {
            val cleanKey = key.removeSuffix("!!")
            val locked = rootLocked || key.endsWith("!!")
            val metaSection = section.getConfigurationSection(key)
            val metaValue = section.get(key)
            val meta = MetaRegistry.create(cleanKey, metaSection, metaValue, locked)
            if (meta != null) {
                result.add(meta)
            } else {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.item-unknown-meta",
                        "item" to id,
                        "key" to key,
                        "available" to MetaRegistry.getRegisteredKeys().sorted().joinToString(", ")
                    )
                )
            }
        }
        return result
    }

    private fun loadActions(): Map<TriggerKey, ItemAction> {
        val result = mutableMapOf<TriggerKey, ItemAction>()
        val eventSection = config.getConfigurationSection("event") ?: return result

        for (key in eventSection.getKeys(false)) {
            if (key == "from" || key == "data") continue
            val cleanKey = key.removeSuffix("!!")
            val cancelEvent = key.endsWith("!!")
            val trigger = TriggerRegistry.resolve(cleanKey)
            if (trigger == null) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.item-unknown-event",
                        "item" to id,
                        "key" to cleanKey
                    )
                )
                continue
            }
            val script = eventSection.getString(key) ?: continue
            val routineName = trigger.toString().replace(':', '.')
            result[trigger] = ItemAction("$id.event.$routineName", trigger, script, cancelEvent).compile()
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

    private fun loadBehaviors(): List<BehaviorBinding> {
        val raw = config.get("behaviors")
        if (raw is List<*>) {
            return raw.mapNotNull { value ->
                NamespacedKey.fromString(value?.toString()?.lowercase() ?: return@mapNotNull null)
                    ?.let { BehaviorBinding(it) }
            }
        }

        val section = config.getConfigurationSection("behaviors") ?: return emptyList()
        return section.getKeys(false).mapNotNull { rawKey ->
            val key = NamespacedKey.fromString(rawKey.lowercase())
            if (key == null) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.item-invalid-behavior",
                        "item" to id,
                        "key" to rawKey
                    )
                )
                return@mapNotNull null
            }
            val options = section.getConfigurationSection(rawKey)
                ?.getValues(true)
                ?.mapValues { it.value }
                ?: emptyMap()
            BehaviorBinding(key, options)
        }
    }

    override fun toString(): String = "OvertureItem(id=$id)"
}
