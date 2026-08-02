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

package priv.seventeen.artist.overture.core.component

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.component.ComponentDecodeContext
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.component.ComponentIssue
import priv.seventeen.artist.overture.api.component.ItemComponentCodec
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.api.reload.ReloadIssue
import priv.seventeen.artist.overture.api.reload.ReloadIssueSeverity
import priv.seventeen.artist.overture.core.registry.OwnedRegistry

internal data class CompiledComponentNamespace(
    val schemaVersion: Int,
    val definitions: Map<String, ItemDataNode.Compound>
)

internal data class ComponentCompilation(
    val namespaces: Map<String, CompiledComponentNamespace> = emptyMap(),
    val issues: List<ReloadIssue> = emptyList()
)

/** 组件 codec 的 owned registry 与候选快照编译器。 */
internal object ItemComponentRegistry {
    private val registry = OwnedRegistry<ItemComponentCodec>("item-component")

    fun register(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int,
        codec: ItemComponentCodec
    ): RegistrationHandle {
        require(codec.schemaVersion > 0) { "组件 $key 的 schemaVersion 必须是正整数" }
        val handle = registry.register(owner, key, priority, codec)
        val versions = activeEntries(key.namespace).map { it.value.schemaVersion }.distinct()
        if (versions.size > 1) {
            handle.unregister()
            throw IllegalArgumentException(
                "组件 namespace ${key.namespace} 的 active codec schemaVersion 不一致: ${versions.sorted()}"
            )
        }
        return handle
    }

    fun compile(
        itemId: String,
        source: String,
        config: ConfigurationSection
    ): ComponentCompilation {
        if (!config.contains("components")) return ComponentCompilation()
        val rootPath = "items.$itemId.components"
        val root = config.getConfigurationSection("components")
            ?: return ComponentCompilation(
                issues = listOf(
                    issue(source, itemId, rootPath, null, null, "components 必须是 mapping")
                )
            )

        val issues = mutableListOf<ReloadIssue>()
        val definitions = linkedMapOf<String, MutableMap<String, ItemDataNode.Compound>>()
        val schemas = linkedMapOf<String, Int>()

        for (rawKey in root.getKeys(false)) {
            val componentPath = "$rootPath.$rawKey"
            if (':' !in rawKey) {
                issues += issue(
                    source,
                    itemId,
                    componentPath,
                    rawKey,
                    null,
                    "组件键必须是完整 namespace:key"
                )
                continue
            }
            val key = NamespacedKey.fromString(rawKey)
            if (key == null || key.toString() != rawKey) {
                issues += issue(source, itemId, componentPath, rawKey, null, "组件键不是合法的 namespace:key")
                continue
            }
            val entry = registry.active(key)
            if (entry == null) {
                issues += issue(source, itemId, componentPath, rawKey, null, "组件未注册")
                continue
            }
            val componentSection = root.getConfigurationSection(rawKey)
            if (componentSection == null) {
                issues += issue(
                    source,
                    itemId,
                    componentPath,
                    rawKey,
                    entry.owner.name,
                    "组件根值必须是 mapping"
                )
                continue
            }

            val sourceNode = try {
                ItemDataCodec.fromConfiguration(componentSection, componentPath)
            } catch (error: ItemDataValidationException) {
                issues += issue(
                    source,
                    itemId,
                    error.dataPath,
                    rawKey,
                    entry.owner.name,
                    error.message ?: "组件数据无效"
                )
                continue
            }

            val result = try {
                val decoded: ComponentDecodeResult? = entry.value.decode(
                    ComponentDecodeContext(key, itemId, sourceNode, componentPath)
                )
                requireNotNull(decoded) { "codec 返回 null" }
            } catch (error: Throwable) {
                issues += issue(
                    source,
                    itemId,
                    componentPath,
                    rawKey,
                    entry.owner.name,
                    "codec 执行失败: ${error.message ?: error.javaClass.simpleName}"
                )
                continue
            }

            when (result) {
                is ComponentDecodeResult.Success -> {
                    try {
                        ItemDataCodec.validateForStorage(result.definition, componentPath)
                    } catch (error: ItemDataValidationException) {
                        issues += issue(
                            source,
                            itemId,
                            error.dataPath,
                            rawKey,
                            entry.owner.name,
                            error.message ?: "codec 返回了不可存储的数据"
                        )
                        continue
                    }
                    val previousSchema = schemas.putIfAbsent(key.namespace, entry.value.schemaVersion)
                    if (previousSchema != null && previousSchema != entry.value.schemaVersion) {
                        issues += issue(
                            source,
                            itemId,
                            componentPath,
                            rawKey,
                            entry.owner.name,
                            "namespace ${key.namespace} 的 schemaVersion 不一致: " +
                                "$previousSchema/${entry.value.schemaVersion}"
                        )
                        continue
                    }
                    definitions.getOrPut(key.namespace) { linkedMapOf() }[key.key] = result.definition
                    result.warnings.forEach { warning ->
                        issues += codecIssue(
                            ReloadIssueSeverity.WARNING,
                            source,
                            itemId,
                            componentPath,
                            rawKey,
                            entry.owner.name,
                            warning
                        )
                    }
                }
                is ComponentDecodeResult.Failure -> {
                    val failures = result.issues.ifEmpty {
                        listOf(ComponentIssue(message = "codec 拒绝组件，但没有提供原因"))
                    }
                    failures.forEach { failure ->
                        issues += codecIssue(
                            ReloadIssueSeverity.ERROR,
                            source,
                            itemId,
                            componentPath,
                            rawKey,
                            entry.owner.name,
                            failure
                        )
                    }
                }
            }
        }

        return ComponentCompilation(
            namespaces = definitions.mapValues { (namespace, values) ->
                CompiledComponentNamespace(
                    schemaVersion = requireNotNull(schemas[namespace]),
                    definitions = values.toMap()
                )
            },
            issues = issues
        )
    }

    fun validationIssues(): List<ReloadIssue> = registry.activeEntries()
        .groupBy { it.key.namespace }
        .mapNotNull { (namespace, entries) ->
            val versions = entries.map { it.value.schemaVersion }.distinct().sorted()
            if (versions.size <= 1) return@mapNotNull null
            ReloadIssue(
                severity = ReloadIssueSeverity.ERROR,
                source = "component-registry",
                message = "namespace $namespace 的 active codec schemaVersion 不一致: $versions",
                path = "components.$namespace",
                owner = entries.joinToString(",") { it.owner.name }
            )
        }

    fun activeNamespaces(): Set<String> = registry.activeEntries().mapTo(linkedSetOf()) { it.key.namespace }

    fun registrations(): List<String> = registry.infos().map { info ->
        val schema = registry.active(info.key)?.value?.schemaVersion
        "${info.key} owner=${info.ownerName} priority=${info.priority} active=${info.active} schema=${schema ?: "-"}"
    }

    private fun activeEntries(namespace: String) =
        registry.activeEntries().filter { it.key.namespace == namespace }

    private fun codecIssue(
        severity: ReloadIssueSeverity,
        source: String,
        itemId: String,
        componentPath: String,
        component: String,
        owner: String,
        componentIssue: ComponentIssue
    ): ReloadIssue {
        val path = if (componentIssue.path.isBlank()) {
            componentPath
        } else {
            "$componentPath.${componentIssue.path.trimStart('.')}"
        }
        return issue(severity, source, itemId, path, component, owner, componentIssue.message)
    }

    private fun issue(
        source: String,
        itemId: String,
        path: String,
        component: String?,
        owner: String?,
        message: String
    ): ReloadIssue = issue(
        ReloadIssueSeverity.ERROR,
        source,
        itemId,
        path,
        component,
        owner,
        message
    )

    private fun issue(
        severity: ReloadIssueSeverity,
        source: String,
        itemId: String,
        path: String,
        component: String?,
        owner: String?,
        message: String
    ): ReloadIssue = ReloadIssue(
        severity = severity,
        source = source,
        message = message,
        itemId = itemId,
        path = path,
        component = component,
        owner = owner
    )
}
