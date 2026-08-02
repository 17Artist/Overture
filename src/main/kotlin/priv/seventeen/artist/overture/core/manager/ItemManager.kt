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
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.api.ItemProvider
import priv.seventeen.artist.overture.api.event.ItemGiveEvent
import priv.seventeen.artist.overture.api.event.PluginReloadEvent
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.api.reload.ReloadConflict
import priv.seventeen.artist.overture.api.reload.ReloadIssue
import priv.seventeen.artist.overture.api.reload.ReloadIssueSeverity
import priv.seventeen.artist.overture.api.reload.ReloadReport
import priv.seventeen.artist.overture.api.reload.ResourceOrigin
import priv.seventeen.artist.overture.core.component.ItemComponentRegistry
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.model.ItemModel
import priv.seventeen.artist.overture.core.registry.OwnedRegistry

/**
 * 物品管理器。
 *
 * 重载会先完整构建并校验候选内容，再一次性替换当前状态；任一 provider、脚本或展示
 * 校验失败时继续使用当前内容，并恢复 YAML loader 的分组与展示状态。
 */
object ItemManager {

    private data class State(
        val items: Map<String, OvertureItem> = emptyMap(),
        val models: Map<String, ItemModel> = emptyMap(),
        val itemOrigins: Map<String, ResourceOrigin> = emptyMap(),
        val modelOrigins: Map<String, ResourceOrigin> = emptyMap()
    )

    private data class ProviderRegistration(
        val provider: ItemProvider,
        val priority: Int
    )

    private data class ReloadContext(
        val items: MutableMap<String, OvertureItem> = linkedMapOf(),
        val models: MutableMap<String, ItemModel> = linkedMapOf(),
        val itemOrigins: MutableMap<String, ResourceOrigin> = linkedMapOf(),
        val modelOrigins: MutableMap<String, ResourceOrigin> = linkedMapOf(),
        val issues: MutableList<ReloadIssue> = mutableListOf(),
        val conflicts: MutableList<ReloadConflict> = mutableListOf(),
        var currentOrigin: ResourceOrigin? = null
    )

    private class ReloadAbort(message: String) : IllegalStateException(message)

    @Volatile
    private var state = State()

    private val providerRegistry = OwnedRegistry<ProviderRegistration>("item-provider")
    private val reloadContext = ThreadLocal<ReloadContext?>()

    @Volatile
    var lastReloadReport: ReloadReport = ReloadReport(
        success = true,
        startedAtMillis = 0L,
        durationMillis = 0L,
        itemCount = 0,
        modelCount = 0,
        issues = emptyList(),
        conflicts = emptyList(),
        rolledBack = false
    )
        private set

    /**
     * Loader 在当前事务中登记事件模型。
     */
    @Synchronized
    fun registerModel(model: ItemModel) {
        val context = reloadContext.get()
        if (context != null) {
            val origin = context.currentOrigin
                ?: ResourceOrigin("manual", "manual", "Overture", 0)
            putModelCandidate(context, model.id, model, origin)
            return
        }

        val current = state
        state = current.copy(
            models = current.models + (model.id to model),
            modelOrigins = current.modelOrigins + (
                model.id to ResourceOrigin("manual", "manual", "Overture", 0)
                )
        )
    }

    fun registerProvider(
        owner: Plugin,
        key: NamespacedKey,
        provider: ItemProvider,
        priority: Int = provider.priority
    ): RegistrationHandle =
        providerRegistry.register(owner, key, priority, ProviderRegistration(provider, priority))

    fun getItem(id: String): OvertureItem? = state.items[id]

    fun getItems(): Map<String, OvertureItem> = state.items.toMap()

    fun getItemIds(): List<String> = state.items.keys.toList()

    fun getModel(id: String): ItemModel? = state.models[id]

    fun getItemOrigin(id: String): ResourceOrigin? = state.itemOrigins[id]

    fun getModelOrigin(id: String): ResourceOrigin? = state.modelOrigins[id]

    fun providerRegistrations(): List<String> = providerRegistry.infos().map {
        "${it.key} owner=${it.ownerName} priority=${it.priority} active=${it.active}"
    }

    fun componentRegistrations(): List<String> = ItemComponentRegistry.registrations()

    fun generate(id: String, player: Player? = null): ItemStack? {
        val item = state.items[id] ?: return null
        val stream = item.build(player)
        if (stream.buildCancelled) return null
        return stream.toItemStack(player)
    }

    fun give(player: Player, id: String, amount: Int = 1): Boolean {
        if (amount <= 0) return false
        val itemStack = generate(id, player) ?: return false
        itemStack.amount = amount

        val event = ItemGiveEvent(player, id, itemStack)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false

        val remaining = player.inventory.addItem(event.itemStack)
        remaining.values.forEach { overflow ->
            player.world.dropItemNaturally(player.location, overflow)
        }
        return true
    }

    fun read(itemStack: ItemStack): ItemStream = ItemStream(itemStack)

    fun isOvertureItem(itemStack: ItemStack): Boolean =
        ItemTag.fromItemStack(itemStack).containsKey("overture")

    fun getOvertureId(itemStack: ItemStack): String? {
        val tag = ItemTag.fromItemStack(itemStack)
        if (!tag.containsKey("overture")) return null
        return tag.getCompound("overture").getString("id")
    }

    @Synchronized
    fun reloadWithReport(): ReloadReport {
        val startedAt = System.currentTimeMillis()
        if (!Bukkit.isPrimaryThread()) {
            return finishFailedReport(
                startedAt,
                listOf(ReloadIssue(ReloadIssueSeverity.ERROR, "reload", "重载只能在服务端主线程执行")),
                emptyList()
            )
        }

        val oldState = state
        val oldGroups = LoaderManager.captureGroups()
        val context = ReloadContext()
        reloadContext.set(context)
        DisplayManager.beginReload()

        try {
            val providers = providerRegistry.activeEntries()
                .sortedWith(
                    compareBy<OwnedRegistry.ActiveEntry<ProviderRegistration>> { it.value.priority }
                        .thenBy { it.key.toString() }
                )
            if (providers.isEmpty()) {
                throw ReloadAbort("没有已注册的物品 provider")
            }

            for (entry in providers) {
                val registration = entry.value
                val provider = registration.provider
                val origin = ResourceOrigin(
                    providerKey = entry.key.toString(),
                    providerId = provider.id,
                    owner = entry.owner.name,
                    priority = registration.priority
                )
                context.currentOrigin = origin

                try {
                    provider.reload()
                    val loaded = provider.load()
                    for ((declaredId, item) in loaded) {
                        if (declaredId != item.id) {
                            throw ReloadAbort(
                                "provider ${provider.id} 返回键 $declaredId，但物品定义 ID 为 ${item.id}"
                            )
                        }
                        context.issues += item.componentCompilation.issues
                        val failedActions = item.actions.values.filter { it.compiled == null }
                        if (failedActions.isNotEmpty()) {
                            throw ReloadAbort(
                                "物品 ${item.id} 有 ${failedActions.size} 个 Aria 动作编译失败"
                            )
                        }
                        putItemCandidate(context, declaredId, item, origin)
                    }
                    BlinkLog.info(
                        LanguageManager.text(
                            "console.item-provider-loaded",
                            "provider" to provider.id,
                            "count" to loaded.size
                        )
                    )
                } catch (error: ReloadAbort) {
                    throw error
                } catch (error: Throwable) {
                    throw ReloadAbort("物品 provider ${provider.id} 加载失败: ${error.message}")
                }
            }
            context.currentOrigin = null

            context.issues += LoaderManager.validationIssues()
            context.issues += ItemComponentRegistry.validationIssues()
            DisplayManager.validationErrors().forEach {
                context.issues += ReloadIssue(ReloadIssueSeverity.ERROR, "display", it)
            }
            context.models.forEach { (id, model) ->
                if (model.actions.values.any { it.compiled == null }) {
                    context.issues += ReloadIssue(
                        ReloadIssueSeverity.ERROR,
                        "model:$id",
                        "存在 Aria 动作编译失败"
                    )
                }
            }
            if (context.issues.any { it.severity == ReloadIssueSeverity.ERROR }) {
                throw ReloadAbort("重载校验失败")
            }

            val newState = State(
                items = context.items.toMap(),
                models = context.models.toMap(),
                itemOrigins = context.itemOrigins.toMap(),
                modelOrigins = context.modelOrigins.toMap()
            )
            DisplayManager.commitReload()
            state = newState
            val report = ReloadReport(
                success = true,
                startedAtMillis = startedAt,
                durationMillis = System.currentTimeMillis() - startedAt,
                itemCount = context.items.size,
                modelCount = context.models.size,
                issues = context.issues.toList(),
                conflicts = context.conflicts.toList(),
                rolledBack = false
            )
            lastReloadReport = report
            BlinkLog.success(
                LanguageManager.text(
                    "console.reload-complete",
                    "items" to report.itemCount,
                    "models" to report.modelCount,
                    "conflicts" to report.conflicts.size
                )
            )
            Bukkit.getPluginManager().callEvent(PluginReloadEvent(report))
            return report
        } catch (error: Throwable) {
            state = oldState
            DisplayManager.rollbackReload()
            LoaderManager.restoreGroups(oldGroups)
            context.issues += ReloadIssue(
                ReloadIssueSeverity.ERROR,
                context.currentOrigin?.providerKey ?: "reload",
                error.message ?: error.javaClass.simpleName
            )
            val report = finishFailedReport(startedAt, context.issues, context.conflicts)
            BlinkLog.error(
                LanguageManager.text(
                    "console.reload-failed",
                    "error" to (error.message ?: error.javaClass.simpleName)
                )
            )
            Bukkit.getPluginManager().callEvent(PluginReloadEvent(report))
            return report
        } finally {
            reloadContext.remove()
        }
    }

    @Synchronized
    fun clear() {
        state = State()
        providerRegistry.clear()
    }

    private fun putItemCandidate(
        context: ReloadContext,
        id: String,
        item: OvertureItem,
        origin: ResourceOrigin
    ) {
        val previous = context.itemOrigins[id]
        if (previous == null) {
            context.items[id] = item
            context.itemOrigins[id] = origin
            return
        }
        val winner = resolveConflict(context, "item", id, previous, origin)
        if (winner == origin) {
            context.items[id] = item
            context.itemOrigins[id] = origin
        }
    }

    private fun putModelCandidate(
        context: ReloadContext,
        id: String,
        model: ItemModel,
        origin: ResourceOrigin
    ) {
        val previous = context.modelOrigins[id]
        if (previous == null) {
            context.models[id] = model
            context.modelOrigins[id] = origin
            return
        }
        val winner = resolveConflict(context, "model", id, previous, origin)
        if (winner == origin) {
            context.models[id] = model
            context.modelOrigins[id] = origin
        }
    }

    private fun resolveConflict(
        context: ReloadContext,
        resourceType: String,
        resourceId: String,
        previous: ResourceOrigin,
        candidate: ResourceOrigin
    ): ResourceOrigin {
        if (previous.priority == candidate.priority) {
            context.conflicts += ReloadConflict(
                resourceType,
                resourceId,
                previous,
                candidate,
                null,
                "REJECT_EQUAL_PRIORITY"
            )
            throw ReloadAbort(
                "$resourceType ID $resourceId 冲突：${previous.providerKey} 与 ${candidate.providerKey} 优先级相同"
            )
        }

        val winner = if (candidate.priority > previous.priority) candidate else previous
        context.conflicts += ReloadConflict(
            resourceType,
            resourceId,
            previous,
            candidate,
            winner,
            "HIGHER_PRIORITY_WINS"
        )
        context.issues += ReloadIssue(
            ReloadIssueSeverity.WARNING,
            "$resourceType:$resourceId",
            "来源冲突，按优先级选择 ${winner.providerKey}"
        )
        return winner
    }

    private fun finishFailedReport(
        startedAt: Long,
        issues: List<ReloadIssue>,
        conflicts: List<ReloadConflict>
    ): ReloadReport {
        val current = state
        return ReloadReport(
            success = false,
            startedAtMillis = startedAt,
            durationMillis = System.currentTimeMillis() - startedAt,
            itemCount = current.items.size,
            modelCount = current.models.size,
            issues = issues.toList(),
            conflicts = conflicts.toList(),
            rolledBack = true
        ).also { lastReloadReport = it }
    }
}
