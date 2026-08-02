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

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.api.ItemProvider
import priv.seventeen.artist.overture.api.reload.ReloadIssue
import priv.seventeen.artist.overture.api.reload.ReloadIssueSeverity
import priv.seventeen.artist.overture.core.group.ItemGroup
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.model.ItemModel
import priv.seventeen.artist.overture.util.ColorUtil
import java.io.File

/**
 * 配置加载器
 * 递归扫描 items/ 和 displays/ 目录
 */
object LoaderManager {

    private val groups = mutableMapOf<String, ItemGroup>()
    private val loadIssues = mutableListOf<ReloadIssue>()

    /**
     * 加载物品目录
     */
    fun loadItems(directory: File): Map<String, OvertureItem> {
        val result = mutableMapOf<String, OvertureItem>()
        if (!directory.exists()) {
            directory.mkdirs()
            return result
        }
        loadItemsRecursive(directory, directory, null, result)
        return result
    }

    /**
     * 加载展示方案目录
     */
    fun loadDisplays(directory: File) {
        if (!directory.exists()) {
            directory.mkdirs()
            return
        }
        loadDisplaysRecursive(directory)
    }

    /**
     * 获取所有分组
     */
    fun getGroups(): Map<String, ItemGroup> = groups.toMap()

    /**
     * 获取分组
     */
    fun getGroup(path: String): ItemGroup? = groups[path]

    /**
     * 获取根级别分组
     */
    fun getRootGroups(): List<ItemGroup> {
        return groups.values.filter { it.parent == null }.sortedBy { it.priority }
    }

    /**
     * 重载
     */
    fun reload() {
        groups.clear()
        loadIssues.clear()
    }

    internal fun captureGroups(): Map<String, ItemGroup> = groups.toMap()

    internal fun restoreGroups(snapshot: Map<String, ItemGroup>) {
        groups.clear()
        groups.putAll(snapshot)
    }

    internal fun validationIssues(): List<ReloadIssue> = loadIssues.toList()

    // ==================== 内部实现 ====================

    private fun loadItemsRecursive(
        rootDirectory: File,
        directory: File,
        parentGroup: ItemGroup?,
        result: MutableMap<String, OvertureItem>
    ) {
        val files = directory.listFiles() ?: return

        // 读取 __group__ 配置
        val groupConfig = files.find { it.name == "__group__" || it.name == "__group__.yml" }
        val groupYaml = groupConfig?.let { YamlConfiguration.loadConfiguration(it) }
        val groupPriority = groupYaml?.getInt("priority", 0) ?: 0
        val groupIconName = groupYaml?.getString("icon", "CHEST") ?: "CHEST"
        val groupIcon = Material.getMaterial(groupIconName.uppercase()) ?: Material.CHEST
        val groupDisplayName = groupYaml?.getString("name")?.let { ColorUtil.colored(it) }
        val groupLore = groupYaml?.getStringList("lore")?.map { ColorUtil.colored(it) } ?: emptyList()

        // 创建当前目录的分组（如果不是根目录）
        val currentGroup = if (parentGroup != null || directory.name != "items") {
            ItemGroup(
                name = directory.name,
                parent = parentGroup,
                level = (parentGroup?.level ?: -1) + 1,
                priority = groupPriority,
                icon = groupIcon,
                displayName = groupDisplayName,
                description = groupLore
            ).also {
                parentGroup?.children?.add(it)
                groups[it.path] = it
            }
        } else null

        // 处理文件
        for (file in files.sortedBy { it.name }) {
            if (file.name.startsWith("__")) continue

            if (file.isDirectory) {
                loadItemsRecursive(rootDirectory, file, currentGroup, result)
            } else if (file.extension == "yml" || file.extension == "yaml") {
                loadItemFile(rootDirectory, file, currentGroup, result)
            }
        }
    }

    private fun loadItemFile(
        rootDirectory: File,
        file: File,
        group: ItemGroup?,
        result: MutableMap<String, OvertureItem>
    ) {
        val source = "items/" + file.relativeTo(rootDirectory).invariantSeparatorsPath
        loadIssues += detectDuplicateComponentKeys(file, source)
        try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            for (key in yaml.getKeys(false)) {
                if (key.startsWith("__")) continue

                // 事件模型（$ 后缀）
                if (key.endsWith("$")) {
                    val modelId = key.removeSuffix("$")
                    val section = yaml.getConfigurationSection(key) ?: continue
                    val model = ItemModel(modelId, section)
                    model.actions
                    ItemManager.registerModel(model)
                    continue
                }

                val section = yaml.getConfigurationSection(key) ?: continue
                val item = OvertureItem(key, section, source)
                // 在加载阶段完成 Aria 编译，确保配置错误立即可见，而不是首次触发时才暴露。
                item.actions
                item.group = group
                result[key] = item
            }
        } catch (e: Exception) {
            val message = "加载物品文件失败 ${file.path}: ${e.message}"
            loadIssues += ReloadIssue(ReloadIssueSeverity.ERROR, source, message)
            BlinkLog.error(message)
        }
    }

    private fun detectDuplicateComponentKeys(file: File, source: String): List<ReloadIssue> {
        val result = mutableListOf<ReloadIssue>()
        val mapping = Regex("""^(\s*)(.+?):(?=\s|$)(.*)$""")
        var itemId: String? = null
        var componentsIndent: Int? = null
        var componentIndent: Int? = null
        val seen = linkedSetOf<String>()

        file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trimEnd()
            if (line.isBlank()) return@forEachIndexed
            val match = mapping.find(line) ?: return@forEachIndexed
            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim().let {
                when {
                    it.length >= 2 && it.first() == '"' && it.last() == '"' -> it.substring(1, it.length - 1)
                    it.length >= 2 && it.first() == '\'' && it.last() == '\'' -> it.substring(1, it.length - 1)
                    else -> it
                }
            }

            if (indent == 0) {
                itemId = key
                componentsIndent = null
                componentIndent = null
                seen.clear()
                return@forEachIndexed
            }
            val currentItem = itemId ?: return@forEachIndexed
            val rootIndent = componentsIndent
            if (rootIndent == null) {
                if (key == "components") {
                    componentsIndent = indent
                    componentIndent = null
                    seen.clear()
                }
                return@forEachIndexed
            }
            if (indent <= rootIndent) {
                componentsIndent = null
                componentIndent = null
                seen.clear()
                return@forEachIndexed
            }
            if (componentIndent == null) componentIndent = indent
            if (indent != componentIndent) return@forEachIndexed

            if (!seen.add(key)) {
                result += ReloadIssue(
                    severity = ReloadIssueSeverity.ERROR,
                    source = source,
                    message = "组件键 $key 重复（第 ${index + 1} 行）",
                    itemId = currentItem,
                    path = "items.$currentItem.components.$key",
                    component = key
                )
            }
        }
        return result
    }

    private fun loadDisplaysRecursive(directory: File) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                loadDisplaysRecursive(file)
            } else if (file.extension == "yml" || file.extension == "yaml") {
                loadDisplayFile(file)
            }
        }
    }

    private fun loadDisplayFile(file: File) {
        try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            for (key in yaml.getKeys(false)) {
                val section = yaml.getConfigurationSection(key) ?: continue
                DisplayManager.loadFromSection(key, section)
            }
        } catch (e: Exception) {
            val message = "加载展示文件失败 ${file.path}: ${e.message}"
            loadIssues += ReloadIssue(ReloadIssueSeverity.ERROR, file.path, message)
            BlinkLog.error(message)
        }
    }
}

/**
 * YAML 文件物品提供者（默认实现）
 */
class YamlItemProvider(private val dataFolder: File) : ItemProvider {
    override val id: String = "yaml"
    override val priority: Int = 0

    override fun load(): Map<String, OvertureItem> {
        val itemsDir = File(dataFolder, "items")
        return LoaderManager.loadItems(itemsDir)
    }

    override fun reload() {
        LoaderManager.reload()
        val displaysDir = File(dataFolder, "displays")
        DisplayManager.reload()
        LoaderManager.loadDisplays(displaysDir)
    }
}
