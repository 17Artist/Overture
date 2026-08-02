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

package priv.seventeen.artist.overture.command

import org.bukkit.Bukkit
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.event.server.TabCompleteEvent
import priv.seventeen.artist.blink.command.BlinkCommand
import priv.seventeen.artist.blink.command.BlinkCommandRegistrar
import priv.seventeen.artist.blink.command.SenderType
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.overture.Overture
import priv.seventeen.artist.overture.OvertureConfig
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.core.item.ItemSerializer
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.LoaderManager
import priv.seventeen.artist.overture.core.display.RenderEntryRegistry
import priv.seventeen.artist.overture.core.action.TriggerRegistry
import priv.seventeen.artist.overture.core.behavior.ItemBehaviorRegistry
import priv.seventeen.artist.overture.core.diagnostic.BuildDiagnosticsStore
import priv.seventeen.artist.overture.core.mapper.MapperFunction
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.core.meta.MetaRegistry
import priv.seventeen.artist.overture.feature.ItemMenu

/**
 * Overture 命令注册
 */
object OvertureCommand {

    private const val FALLBACK_PREFIX = "overture"
    private val commandLabels = setOf("overture", "ot", "oi")

    private fun message(path: String, vararg placeholders: Pair<String, Any?>): String =
        LanguageManager.text(path, *placeholders)

    private fun word(path: String): String = LanguageManager.raw(path)

    @Awake(LifeCycle.ENABLE)
    fun register() {
        BlinkCommandRegistrar.register(
            bukkitPlugin,
            BlinkCommand("overture", "ot", "oi")
                .command("list", message("command.description.list"), args = arrayOf("?group"), sender = SenderType.OP) { ctx ->
                    val player = ctx.player ?: return@command ctx.reply(message("command.player-only"))
                    val group = if (ctx.size > 0) {
                        LoaderManager.getGroup(ctx.arg(0))
                    } else null
                    ItemMenu.open(player, group)
                }
                .command("give", message("command.description.give"), args = arrayOf("item", "?player", "?amount"), sender = SenderType.OP) { ctx ->
                    val itemId = ctx.arg(0)
                    val targetName = if (ctx.size > 1) ctx.arg(1) else null
                    val amount = ctx.argInt(2, 1)

                    val target = if (targetName != null) {
                        Bukkit.getPlayer(targetName)
                    } else {
                        ctx.player
                    }

                    if (target == null) {
                        ctx.reply(message("command.player-not-found", "player" to (targetName ?: word("common.unknown"))))
                        return@command
                    }

                    if (ItemManager.getItem(itemId) == null) {
                        ctx.reply(message("command.item-not-found", "item" to itemId))
                        return@command
                    }

                    val success = ItemManager.give(target, itemId, amount)
                    if (success) {
                        ctx.reply(message("command.give.success", "amount" to amount, "item" to itemId, "player" to target.name))
                    } else {
                        ctx.reply(message("command.give.failed"))
                    }
                }
                .command("get", message("command.description.get"), args = arrayOf("item", "?amount"), sender = SenderType.OP) { ctx ->
                    val player = ctx.player ?: return@command ctx.reply(message("command.player-only"))
                    val itemId = ctx.arg(0)
                    val amount = ctx.argInt(1, 1)

                    if (ItemManager.getItem(itemId) == null) {
                        ctx.reply(message("command.item-not-found", "item" to itemId))
                        return@command
                    }

                    val success = ItemManager.give(player, itemId, amount)
                    if (success) {
                        ctx.reply(message("command.get.success", "amount" to amount, "item" to itemId))
                    } else {
                        ctx.reply(message("command.get.failed"))
                    }
                }
                .command("rebuild", message("command.description.rebuild"), sender = SenderType.OP) { ctx ->
                    val player = ctx.player ?: return@command ctx.reply(message("command.player-only"))
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(message("command.hold-item"))
                        return@command
                    }

                    val stream = ItemStream(item)
                    if (!stream.isOverture) {
                        ctx.reply(message("command.not-overture-item"))
                        return@command
                    }

                    val itemDef = ItemManager.getItem(stream.overtureId ?: "") ?: run {
                        ctx.reply(message("command.definition-not-found", "item" to stream.overtureId.orEmpty()))
                        return@command
                    }

                    // 按当前定义直接重新生成
                    val rebuilt = itemDef.build(player, stream)
                    if (rebuilt.buildCancelled) {
                        ctx.reply(message("command.rebuild.cancelled"))
                        return@command
                    }
                    val result = rebuilt.toItemStack(player)
                    player.inventory.setItemInMainHand(result)
                    ctx.reply(message("command.rebuild.success"))
                }
                .command("serialize", message("command.description.serialize"), sender = SenderType.OP) { ctx ->
                    val player = ctx.player ?: return@command ctx.reply(message("command.player-only"))
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(message("command.hold-item"))
                        return@command
                    }

                    val json = ItemSerializer.serialize(item)
                    ctx.reply(message("command.serialize.header"))
                    ctx.reply(json)
                }
                .command("info", message("command.description.info"), sender = SenderType.OP) { ctx ->
                    val player = ctx.player ?: return@command ctx.reply(message("command.player-only"))
                    val item = player.inventory.itemInMainHand
                    if (item.type.isAir) {
                        ctx.reply(message("command.hold-item"))
                        return@command
                    }

                    val stream = ItemStream(item)
                    if (!stream.isOverture) {
                        ctx.reply(message("command.not-overture-item"))
                        return@command
                    }

                    ctx.reply(message("command.info.header"))
                    ctx.reply(message("command.info.id", "value" to stream.overtureId))
                    ctx.reply(message("command.info.version", "value" to stream.version))
                    ctx.reply(message("command.info.data", "value" to stream.overtureData))
                    val unique = stream.overtureUnique
                    if (!unique.isEmpty()) {
                        ctx.reply(message("command.info.unique", "value" to unique.getString("uuid")))
                        ctx.reply(message("command.info.owner", "value" to unique.getString("player")))
                        ctx.reply(message("command.info.created-at", "value" to unique.getString("date-formatted")))
                    }
                }
                .command(
                    "diagnostics",
                    message("command.description.diagnostics"),
                    args = arrayOf("?diagnostic_target"),
                    sender = SenderType.OP
                ) { ctx ->
                    when (if (ctx.size > 0) ctx.arg(0).lowercase() else "components") {
                        "components" -> {
                            ctx.reply(message("command.diagnostics.components-header"))
                            val lines = ItemManager.componentRegistrations()
                            if (lines.isEmpty()) {
                                ctx.reply(message("command.diagnostics.empty"))
                            } else {
                                lines.take(80).forEach { ctx.reply(message("command.diagnostics.entry", "value" to it)) }
                            }
                        }
                        else -> ctx.reply(message("command.diagnostics.usage"))
                    }
                }
                .command(
                    "inspect",
                    message("command.description.inspect"),
                    args = arrayOf("?inspect_target"),
                    sender = SenderType.OP
                ) { ctx ->
                    val target = if (ctx.size > 0) ctx.arg(0) else null
                    when (target?.lowercase()) {
                        "reload" -> {
                            val report = ItemManager.lastReloadReport
                            val status = if (report.success) {
                                "&a${word("common.success")}"
                            } else {
                                "&c${word("common.failed")}"
                            }
                            ctx.reply(message("command.inspect.reload-header"))
                            ctx.reply(
                                message(
                                    "command.inspect.reload-summary",
                                    "status" to status,
                                    "duration" to report.durationMillis,
                                    "items" to report.itemCount,
                                    "models" to report.modelCount
                                )
                            )
                            ctx.reply(
                                message(
                                    "command.inspect.reload-problems",
                                    "errors" to report.errorCount,
                                    "warnings" to report.warningCount,
                                    "conflicts" to report.conflicts.size
                                )
                            )
                            report.issues.take(20).forEach { issue ->
                                ctx.reply(
                                    message(
                                        "command.inspect.issue",
                                        "color" to if (issue.severity.name == "ERROR") "&c" else "&e",
                                        "source" to issue.source,
                                        "item" to (issue.itemId ?: "-"),
                                        "path" to (issue.path ?: "-"),
                                        "component" to (issue.component ?: "-"),
                                        "owner" to (issue.owner ?: "-"),
                                        "message" to issue.message
                                    )
                                )
                            }
                            report.conflicts.take(20).forEach { conflict ->
                                ctx.reply(
                                    message(
                                        "command.inspect.reload-conflict",
                                        "type" to conflict.resourceType,
                                        "id" to conflict.resourceId,
                                        "previous" to conflict.previous.providerKey,
                                        "candidate" to conflict.candidate.providerKey,
                                        "winner" to (conflict.winner?.providerKey ?: word("common.rejected"))
                                    )
                                )
                            }
                            return@command
                        }
                        "registries" -> {
                            ctx.reply(message("command.diagnostics.registries-header"))
                            val lines = buildList {
                                addAll(ItemManager.providerRegistrations().map { "provider $it" })
                                addAll(ItemManager.componentRegistrations().map { "component $it" })
                                addAll(RenderEntryRegistry.registrations().map { "render $it" })
                                addAll(TriggerRegistry.registrations().map { "trigger $it" })
                                addAll(ItemBehaviorRegistry.registrations().map { "behavior $it" })
                                addAll(MapperFunction.registrations().map { "mapper $it" })
                                addAll(MetaRegistry.registrations().map { "meta $it" })
                            }
                            if (lines.isEmpty()) {
                                ctx.reply(message("command.diagnostics.empty"))
                            } else {
                                lines.take(80).forEach { ctx.reply(message("command.diagnostics.entry", "value" to it)) }
                            }
                            return@command
                        }
                    }

                    val player = ctx.player
                    val heldStream = player?.inventory?.itemInMainHand
                        ?.takeUnless { it.type.isAir }
                        ?.let(::ItemStream)
                    val itemId = target ?: heldStream?.overtureId
                    if (itemId == null) {
                        ctx.reply(message("command.inspect.usage"))
                        return@command
                    }
                    val item = ItemManager.getItem(itemId)
                    if (item == null) {
                        ctx.reply(message("command.definition-not-found", "item" to itemId))
                        return@command
                    }

                    val none = word("common.none")
                    val unknown = word("common.unknown")
                    val unavailable = word("common.not-available")
                    val stream = heldStream?.takeIf { it.overtureId == itemId }
                    val origin = ItemManager.getItemOrigin(itemId)
                    ctx.reply(message("command.inspect.item-header", "item" to itemId))
                    ctx.reply(
                        message(
                            "command.inspect.origin",
                            "provider" to (origin?.providerKey ?: unknown),
                            "owner" to (origin?.owner ?: unknown),
                            "priority" to (origin?.priority ?: 0)
                        )
                    )
                    ctx.reply(
                        message(
                            "command.inspect.version",
                            "definition" to item.version,
                            "stack" to (stream?.version ?: unavailable),
                            "outdated" to (
                                stream?.let {
                                    word(if (it.version != item.version) "common.yes" else "common.no")
                                } ?: unavailable
                            )
                        )
                    )
                    ctx.reply(
                        message(
                            "command.inspect.meta",
                            "value" to item.metaList.joinToString {
                                "${it.key}${if (it.locked) "!!" else ""}"
                            }.ifEmpty { none }
                        )
                    )
                    ctx.reply(message("command.inspect.display", "value" to (item.display ?: none)))
                    ctx.reply(
                        message(
                            "command.inspect.components",
                            "value" to item.componentKeys.joinToString().ifEmpty { none }
                        )
                    )
                    ctx.reply(
                        message(
                            "command.inspect.behaviors",
                            "value" to item.behaviors.joinToString { it.key.toString() }.ifEmpty { none }
                        )
                    )
                    ctx.reply(
                        message(
                            "command.inspect.actions",
                            "value" to item.actions.keys.joinToString().ifEmpty { none }
                        )
                    )
                    ctx.reply(
                        message(
                            "command.inspect.models",
                            "value" to item.modelIds.joinToString().ifEmpty { none }
                        )
                    )

                    ItemManager.lastReloadReport.conflicts
                        .filter { it.resourceType == "item" && it.resourceId == itemId }
                        .forEach { conflict ->
                            ctx.reply(
                                message(
                                    "command.inspect.item-conflict",
                                    "previous" to conflict.previous.providerKey,
                                    "candidate" to conflict.candidate.providerKey,
                                    "winner" to (conflict.winner?.providerKey ?: word("common.rejected"))
                                )
                            )
                        }

                    val diagnostics = BuildDiagnosticsStore.get(player, itemId)
                    if (diagnostics == null) {
                        ctx.reply(message("command.inspect.no-build-record"))
                    } else {
                        ctx.reply(
                            message(
                                "command.inspect.action-trace",
                                "value" to diagnostics.actionTrace.joinToString(" -> ").ifEmpty { none }
                            )
                        )
                        ctx.reply(
                            message(
                                "command.inspect.timings",
                                "value" to diagnostics.timingsNanos.entries.joinToString {
                                    "${it.key}=${"%.3f".format(it.value / 1_000_000.0)}ms"
                                }.ifEmpty { none }
                            )
                        )
                    }
                }
                .command("reload", message("command.description.reload"), sender = SenderType.OP) { ctx ->
                    OvertureConfig.reload()
                    Overture.applyConfig()
                    val languageLoaded = Overture.reloadLanguage()
                    val report = OvertureAPI.reloadWithReport()
                    if (!languageLoaded) {
                        ctx.reply(message("command.reload.language-failed"))
                    }
                    if (report.success) {
                        ctx.reply(
                            message(
                                "command.reload.success",
                                "items" to report.itemCount,
                                "models" to report.modelCount,
                                "warnings" to report.warningCount
                            )
                        )
                    } else {
                        ctx.reply(message("command.reload.failed", "errors" to report.errorCount))
                    }
                }
                .tabComplete("item") { ItemManager.getItemIds() }
                .tabComplete("player") { Bukkit.getOnlinePlayers().map { it.name } }
                .tabComplete("group") { LoaderManager.getGroups().keys.toList() }
                .tabComplete("inspect_target") {
                    listOf("reload", "registries") + ItemManager.getItemIds()
                }
                .tabComplete("diagnostic_target") { listOf("components") }
        )
    }

    @AutoListener(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        if (event.player.isOp || !isOvertureCommandInput(event.message)) return
        event.isCancelled = true
        event.player.sendMessage(message("command.op-only"))
    }

    @AutoListener
    fun onPlayerCommandSend(event: PlayerCommandSendEvent) {
        if (event.player.isOp) return
        event.commands.removeIf(::isOvertureCommandInput)
    }

    @AutoListener(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onTabComplete(event: TabCompleteEvent) {
        val player = event.sender as? org.bukkit.entity.Player ?: return
        if (player.isOp || !isOvertureCommandInput(event.buffer)) return
        event.completions = emptyList()
    }

    internal fun isOvertureCommandInput(input: String): Boolean {
        val label = input.trimStart().removePrefix("/").substringBefore(' ').lowercase()
        val separator = label.indexOf(':')
        if (separator < 0) return label in commandLabels
        return label.substring(0, separator) == FALLBACK_PREFIX &&
            label.substring(separator + 1) in commandLabels
    }
}
