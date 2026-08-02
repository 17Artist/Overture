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
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.overture.OvertureConfig
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.util.ColorUtil
import java.io.File
import java.util.UUID

/**
 * 掉落物名称标签管理器。
 *
 * 配置结构：
 * - config.yml/drop-label：全局开关、同步周期、默认样式
 * - drop-labels.yml：样式库
 * - 物品配置 drop-label：指定样式或关闭标签
 *
 * 性能设计：
 * - 全局最多一个同步任务
 * - 有标签时启动，无标签时停止
 * - 每次批量同步全部标签位置
 */
object DropLabelManager {

    val useTextDisplay: Boolean by lazy {
        try {
            Class.forName("org.bukkit.entity.TextDisplay")
            val version = Bukkit.getBukkitVersion()
            val minor = version.substringAfter("1.").substringBefore(".").toIntOrNull() ?: 0
            val patch = version.substringAfter("1.$minor.").substringBefore("-").toIntOrNull() ?: 0
            minor > 19 || (minor == 19 && patch >= 4)
        } catch (_: Throwable) {
            false
        }
    }

    private val labels = mutableMapOf<UUID, LabelEntry>()
    private val styles = mutableMapOf<String, DropLabelStyle>()
    private var followTask: BukkitTask? = null

    fun init() {
        BlinkLog.info(
            LanguageManager.text(
                "console.drop-label-mode",
                "mode" to if (useTextDisplay) "TextDisplay" else "ArmorStand"
            )
        )
    }

    fun load(styleFile: File) {
        if (!styleFile.exists()) {
            writeDefaults(styleFile)
        }

        styles.clear()
        val yaml = YamlConfiguration.loadConfiguration(styleFile)
        for (styleId in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(styleId) ?: continue
            styles[styleId] = DropLabelStyle.fromSection(styleId, section)
        }

        if (styles.isEmpty()) {
            styles["default"] = DropLabelStyle()
            BlinkLog.warn(LanguageManager.text("console.drop-label-empty"))
        }
        BlinkLog.info(
            LanguageManager.text(
                "console.drop-label-loaded",
                "count" to styles.size,
                "styles" to styles.keys.joinToString()
            )
        )
        refreshExistingLabels()
    }

    fun spawnLabel(item: Item) {
        val global = OvertureConfig.instance.dropLabel
        if (!global.enabled) return

        val stream = ItemStream(item.itemStack)
        if (!stream.isOverture) return

        val itemDef = ItemManager.getItem(stream.overtureId ?: return) ?: return
        val style = resolveStyle(itemDef) ?: return

        removeLabel(item)

        val text = buildText(item.itemStack, style)
        val label = if (useTextDisplay) {
            spawnTextDisplay(item, text, style) ?: spawnArmorStand(item, text, style)
        } else {
            spawnArmorStand(item, text, style)
        } ?: return

        labels[item.uniqueId] = LabelEntry(item.uniqueId, label.uniqueId, style.height)
        ensureFollowTask()
    }

    fun removeLabel(item: Item) {
        val entry = labels.remove(item.uniqueId) ?: return
        Bukkit.getEntity(entry.labelUuid)?.remove()
        stopFollowTaskIfIdle()
    }

    fun cleanup() {
        followTask?.cancel()
        followTask = null
        labels.values.forEach { entry -> Bukkit.getEntity(entry.labelUuid)?.remove() }
        labels.clear()
    }


    private fun refreshExistingLabels() {
        val droppedItems = Bukkit.getWorlds()
            .flatMap { world -> world.entities.filterIsInstance<Item>() }
            .filter { item -> ItemStream(item.itemStack).isOverture }
        cleanup()
        if (!OvertureConfig.instance.dropLabel.enabled) return
        droppedItems.forEach(::spawnLabel)
    }
    private fun resolveStyle(itemDef: OvertureItem): DropLabelStyle? {
        val raw = itemDef.config.get("drop-label")
        val styleId = when (raw) {
            null -> OvertureConfig.instance.dropLabel.defaultStyle
            is Boolean -> if (raw) OvertureConfig.instance.dropLabel.defaultStyle else return null
            is String -> {
                if (raw.equals("false", ignoreCase = true) || raw.equals("none", ignoreCase = true)) return null
                raw
            }
            is ConfigurationSection -> {
                if (!raw.getBoolean("enabled", true)) return null
                raw.getString("style", OvertureConfig.instance.dropLabel.defaultStyle)!!
            }
            else -> OvertureConfig.instance.dropLabel.defaultStyle
        }
        return styles[styleId] ?: styles[OvertureConfig.instance.dropLabel.defaultStyle] ?: styles.values.firstOrNull()
    }

    private fun buildText(itemStack: ItemStack, style: DropLabelStyle): String {
        val meta = itemStack.itemMeta
        val name = if (meta != null && meta.hasDisplayName()) {
            meta.displayName
        } else {
            itemStack.type.name.lowercase().replace('_', ' ')
                .split(" ").joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
        }
        if (itemStack.amount <= 1) return ColorUtil.colored(style.format.replace("%name%", name).replace("%amount%", ""))

        val amount = style.amountFormat.replace("%amount%", itemStack.amount.toString())
        return ColorUtil.colored(
            style.format
                .replace("%name%", name)
                .replace("%amount%", amount)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun spawnTextDisplay(item: Item, text: String, style: DropLabelStyle): Entity? {
        return try {
            val location = item.location.clone().add(0.0, style.height, 0.0)
            val textDisplayClass = Class.forName("org.bukkit.entity.TextDisplay") as Class<out Entity>
            val display = item.world.spawn(location, textDisplayClass)

            display.javaClass.getMethod("setText", String::class.java).invoke(display, text)
            applyTextDisplayStyle(display, style)

            display.isPersistent = false
            display.setGravity(false)
            display
        } catch (e: Throwable) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.drop-label-text-display-failed",
                    "error" to "${e.javaClass.simpleName}: ${e.message}"
                )
            )
            null
        }
    }

    private fun applyTextDisplayStyle(display: Entity, style: DropLabelStyle) {
        val cfg = style.textDisplay

        runCatching {
            val billboardClass = Class.forName("org.bukkit.entity.Display\$Billboard")
            val value = billboardClass.getMethod("valueOf", String::class.java).invoke(null, cfg.billboard.uppercase())
            display.javaClass.getMethod("setBillboard", billboardClass).invoke(display, value)
        }.onFailure {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.drop-label-billboard-invalid",
                    "value" to cfg.billboard
                )
            )
        }

        runCatching {
            display.javaClass.getMethod("setShadowed", Boolean::class.java).invoke(display, cfg.shadow)
        }

        when (cfg.backgroundMode.uppercase()) {
            "VANILLA" -> {
                // 保留 Minecraft 默认 TextDisplay 背景，不主动调用 setBackgroundColor
            }
            "TRANSPARENT" -> applyTextDisplayBackground(display, "0,0,0,0")
            "CUSTOM" -> applyTextDisplayBackground(display, cfg.backgroundArgb)
            else -> {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.drop-label-background-mode-invalid",
                        "value" to cfg.backgroundMode
                    )
                )
            }
        }
    }

    private fun applyTextDisplayBackground(display: Entity, argb: String) {
        runCatching {
            val (a, r, g, b) = parseArgb(argb)
            val colorClass = Class.forName("org.bukkit.Color")
            val color = colorClass
                .getMethod("fromARGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                .invoke(null, a, r, g, b)
            display.javaClass.getMethod("setBackgroundColor", colorClass).invoke(display, color)
        }.onFailure {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.drop-label-background-argb-invalid",
                    "value" to argb
                )
            )
        }
    }

    private fun parseArgb(value: String): List<Int> {
        val parts = value.split(',').map { it.trim().toInt().coerceIn(0, 255) }
        if (parts.size != 4) error("ARGB must contain 4 numbers")
        return parts
    }

    private fun spawnArmorStand(item: Item, text: String, style: DropLabelStyle): Entity? {
        return try {
            val cfg = style.armorStand
            val location = item.location.clone().add(0.0, style.height, 0.0)
            val stand = item.world.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand
            stand.customName = text
            stand.isCustomNameVisible = true
            stand.setGravity(false)
            stand.setArms(false)
            stand.setBasePlate(false)
            stand.isSmall = cfg.small
            stand.isVisible = false
            stand.isInvulnerable = true
            stand.isPersistent = false
            runCatching { stand.setMarker(cfg.marker) }
            stand
        } catch (e: Throwable) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.drop-label-armor-stand-failed",
                    "error" to "${e.javaClass.simpleName}: ${e.message}"
                )
            )
            null
        }
    }

    private fun ensureFollowTask() {
        if (followTask != null) return
        val period = OvertureConfig.instance.dropLabel.updateInterval.coerceAtLeast(1L)
        followTask = Bukkit.getScheduler().runTaskTimer(bukkitPlugin, Runnable { tickLabels() }, 1L, period)
    }

    private fun stopFollowTaskIfIdle() {
        if (labels.isNotEmpty()) return
        followTask?.cancel()
        followTask = null
    }

    private fun tickLabels() {
        if (labels.isEmpty()) {
            stopFollowTaskIfIdle()
            return
        }

        val iterator = labels.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val item = Bukkit.getEntity(entry.itemUuid) as? Item
            val label = Bukkit.getEntity(entry.labelUuid)
            if (item == null || label == null || !item.isValid || !label.isValid) {
                label?.remove()
                iterator.remove()
                continue
            }
            label.teleport(item.location.clone().add(0.0, entry.height, 0.0))
        }
        stopFollowTaskIfIdle()
    }

    private fun writeDefaults(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            default:
              height: 0.8
              format: "%name%%amount%"
              amount-format: " &fx%amount%"
              text-display:
                billboard: CENTER
                shadow: true
                background:
                  mode: TRANSPARENT
                  argb: "0,0,0,0"
              armor-stand:
                small: true
                marker: true
            
            legendary:
              height: 1.0
              format: "&6✦ %name%%amount%"
              amount-format: " &7x%amount%"
              text-display:
                billboard: CENTER
                shadow: true
                background:
                  mode: TRANSPARENT
                  argb: "0,0,0,0"
              armor-stand:
                small: true
                marker: true
            """.trimIndent()
        )
        BlinkLog.info(LanguageManager.text("console.drop-label-default-created"))
    }

    private data class LabelEntry(val itemUuid: UUID, val labelUuid: UUID, val height: Double)
}

data class DropLabelStyle(
    val id: String = "default",
    val height: Double = 0.8,
    val format: String = "%name%%amount%",
    val amountFormat: String = " &fx%amount%",
    val textDisplay: TextDisplayStyle = TextDisplayStyle(),
    val armorStand: ArmorStandStyle = ArmorStandStyle()
) {
    companion object {
        fun fromSection(id: String, section: ConfigurationSection): DropLabelStyle {
            return DropLabelStyle(
                id = id,
                height = section.getDouble("height", 0.8),
                format = section.getString("format", "%name%%amount%")!!,
                amountFormat = section.getString("amount-format", " &fx%amount%")!!,
                textDisplay = TextDisplayStyle.fromSection(section.getConfigurationSection("text-display")),
                armorStand = ArmorStandStyle.fromSection(section.getConfigurationSection("armor-stand"))
            )
        }
    }
}

data class TextDisplayStyle(
    val billboard: String = "CENTER",
    val shadow: Boolean = true,
    val backgroundMode: String = "TRANSPARENT",
    val backgroundArgb: String = "0,0,0,0"
) {
    companion object {
        fun fromSection(section: ConfigurationSection?): TextDisplayStyle {
            val backgroundSection = section?.getConfigurationSection("background")
            val mode = backgroundSection?.getString("mode", "TRANSPARENT") ?: "TRANSPARENT"
            return TextDisplayStyle(
                billboard = section?.getString("billboard", "CENTER") ?: "CENTER",
                shadow = section?.getBoolean("shadow", true) ?: true,
                backgroundMode = mode,
                backgroundArgb = section?.getString("background.argb", "0,0,0,0") ?: "0,0,0,0"
            )
        }
    }
}

data class ArmorStandStyle(
    val small: Boolean = true,
    val marker: Boolean = true
) {
    companion object {
        fun fromSection(section: ConfigurationSection?): ArmorStandStyle {
            return ArmorStandStyle(
                small = section?.getBoolean("small", true) ?: true,
                marker = section?.getBoolean("marker", true) ?: true
            )
        }
    }
}
