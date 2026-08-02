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

package priv.seventeen.artist.overture.api

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.action.ExternalTriggerResult
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.api.render.RenderEntryRenderer
import priv.seventeen.artist.overture.api.reload.ReloadReport
import priv.seventeen.artist.overture.api.behavior.ItemBehavior
import priv.seventeen.artist.overture.api.component.ItemComponentCodec
import priv.seventeen.artist.overture.api.data.ItemDataMutation
import priv.seventeen.artist.overture.api.data.ItemDataView
import priv.seventeen.artist.overture.api.data.ItemMutationResult
import priv.seventeen.artist.overture.core.behavior.ItemBehaviorRegistry
import priv.seventeen.artist.overture.core.component.ItemComponentRegistry
import priv.seventeen.artist.overture.core.component.ItemDataService
import priv.seventeen.artist.overture.core.mapper.MapperFunction
import priv.seventeen.artist.overture.core.mapper.MapperHandler
import priv.seventeen.artist.overture.core.meta.MetaRegistry
import priv.seventeen.artist.overture.core.action.ExternalTriggerDispatcher
import priv.seventeen.artist.overture.core.action.TriggerRegistry
import priv.seventeen.artist.overture.core.display.RenderEntryRegistry
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.overture.core.item.ItemSerializer
import priv.seventeen.artist.overture.core.manager.DropLabelManager
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.RarityGlowManager
import java.io.File

/**
 * Overture 公共 API
 */
object OvertureAPI {

    /**
     * 获取所有物品 ID
     */
    @JvmStatic
    fun getItemIds(): List<String> = ItemManager.getItemIds()

    /**
     * 生成物品
     */
    @JvmStatic
    @JvmOverloads
    fun generateItem(id: String, player: Player? = null): ItemStack? = ItemManager.generate(id, player)

    /**
     * 获取物品模板展示名（缓存，无需 build）
     * 返回的是模板默认名：不含玩家条件展示与实例数据变量
     * 适用于列表、日志、消息等只读展示场景
     */
    @JvmStatic
    fun getItemName(id: String): String? = ItemManager.getItem(id)?.templateName

    /**
     * 获取物品模板描述（缓存，无需 build）
     */
    @JvmStatic
    fun getItemLore(id: String): List<String>? = ItemManager.getItem(id)?.templateLore

    /**
     * 获取物品模板副本（缓存 clone，无需重新 build）
     * 仅用于展示场景（菜单图标等），不要直接发放给玩家：
     * unique 物品的 UUID、随机词条等实例数据不会重新生成
     */
    @JvmStatic
    fun getTemplateItem(id: String): ItemStack? = ItemManager.getItem(id)?.templateItemStack()

    /**
     * 判断是否为 Overture 物品
     */
    @JvmStatic
    fun isOvertureItem(item: ItemStack): Boolean = ItemManager.isOvertureItem(item)

    /**
     * 获取物品 ID
     */
    @JvmStatic
    fun getOvertureId(item: ItemStack): String? = ItemManager.getOvertureId(item)

    /**
     * 序列化物品为 JSON
     */
    @JvmStatic
    fun serialize(item: ItemStack): String = ItemSerializer.serialize(item)

    /**
     * 从 JSON 反序列化物品
     */
    @JvmStatic
    fun deserialize(json: String): ItemStack? = ItemSerializer.deserialize(json)

    /**
     * 注册带所有权的物品 provider。
     */
    @JvmStatic
    @JvmOverloads
    fun registerProvider(
        owner: Plugin,
        key: NamespacedKey,
        provider: ItemProvider,
        priority: Int = provider.priority
    ): RegistrationHandle = ItemManager.registerProvider(owner, key, provider, priority)

    /**
     * 注册 `<namespace:key...>` 自定义 Lore 渲染条目。
     *
     * key 必须由 owner 创建；同键最高优先级生效，相同优先级冲突会被拒绝。
     */
    @JvmStatic
    @JvmOverloads
    fun registerRenderEntry(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        renderer: RenderEntryRenderer
    ): RegistrationHandle = RenderEntryRegistry.register(owner, key, priority, renderer)

    /**
     * 注册第三方动作触发键，物品配置可在 event 下使用完整的 namespace:key。
     */
    @JvmStatic
    @JvmOverloads
    fun registerTrigger(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        description: String = ""
    ): RegistrationHandle = TriggerRegistry.register(owner, key, priority, description)

    /**
     * 注册类型安全 ItemBehavior。物品通过 behaviors 下的 namespace:key 显式绑定。
     */
    @JvmStatic
    @JvmOverloads
    fun registerBehavior(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        behavior: ItemBehavior
    ): RegistrationHandle = ItemBehaviorRegistry.register(owner, key, priority, behavior)

    @JvmStatic
    @JvmOverloads
    fun registerMapperFunction(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        handler: MapperHandler
    ): RegistrationHandle = MapperFunction.register(owner, key, priority, handler)

    @JvmStatic
    @JvmOverloads
    fun registerMeta(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        factory: MetaRegistry.MetaFactory
    ): RegistrationHandle = MetaRegistry.register(owner, key, priority, factory)

    /** 在 owner 的 namespace 下注册可校验物品组件。可在插件 LOAD/onLoad 阶段调用。 */
    @JvmStatic
    fun registerItemComponent(
        owner: Plugin,
        key: NamespacedKey,
        codec: ItemComponentCodec
    ): RegistrationHandle = ItemComponentRegistry.register(owner, key, 0, codec)

    /** 在 owner 的 namespace 下注册带优先级的可校验物品组件。 */
    @JvmStatic
    fun registerItemComponent(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int,
        codec: ItemComponentCodec
    ): RegistrationHandle = ItemComponentRegistry.register(owner, key, priority, codec)

    /** 读取不依赖 ItemStream、Asteroid 或 YAML 类型的物品数据快照。 */
    @JvmStatic
    fun readItemData(item: ItemStack): ItemDataView = ItemDataService.read(item)

    /** 在 clone 上修改数据并按当前定义重建；成功结果必须由调用方写回。 */
    @JvmStatic
    fun mutateItem(item: ItemStack, mutation: ItemDataMutation): ItemMutationResult =
        ItemDataService.mutate(item, null, mutation)

    /** 在 clone 上修改数据并使用玩家上下文重建；成功结果必须由调用方写回。 */
    @JvmStatic
    fun mutateItem(
        item: ItemStack,
        player: Player?,
        mutation: ItemDataMutation
    ): ItemMutationResult = ItemDataService.mutate(item, player, mutation)

    /** 按当前模板重建 clone；成功结果必须由调用方写回。 */
    @JvmStatic
    fun rebuildItem(item: ItemStack): ItemMutationResult = ItemDataService.rebuild(item, null)

    /** 按当前模板和玩家上下文重建 clone；成功结果必须由调用方写回。 */
    @JvmStatic
    fun rebuildItem(item: ItemStack, player: Player?): ItemMutationResult =
        ItemDataService.rebuild(item, player)

    /**
     * 从第三方 Bukkit 事件显式触发物品动作。
     *
     * 调用方仍持有事件监听器，并负责将返回的 ItemStack 写回正确槽位或实体。
     */
    @JvmStatic
    @JvmOverloads
    fun dispatchExternalTrigger(
        trigger: TriggerKey,
        player: Player?,
        itemStack: ItemStack,
        event: Event? = null,
        variables: Map<String, Any> = emptyMap()
    ): ExternalTriggerResult =
        ExternalTriggerDispatcher.dispatch(trigger, player, itemStack, event, variables)

    /**
     * 重载并返回完整校验结果。
     */
    @JvmStatic
    fun reloadWithReport(): ReloadReport {
        val report = ItemManager.reloadWithReport()
        if (report.success) {
            RarityGlowManager.load(File(bukkitPlugin.dataFolder, "rarity.yml"))
            DropLabelManager.load(File(bukkitPlugin.dataFolder, "drop-labels.yml"))
        }
        return report
    }
}
