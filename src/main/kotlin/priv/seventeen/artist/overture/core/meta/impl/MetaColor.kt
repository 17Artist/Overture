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

package priv.seventeen.artist.overture.core.meta.impl

import org.bukkit.Color
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey

/**
 * 皮革颜色 Meta
 *
 * 配置格式:
 * ```yaml
 * color: "255,128,0"
 * ```
 */
@MetaKey("color")
class MetaColor(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "color"

    val color: Color? = parseColor()

    override fun buildMeta(itemMeta: ItemMeta) {
        if (itemMeta is LeatherArmorMeta && color != null) {
            itemMeta.setColor(color)
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        if (itemMeta is LeatherArmorMeta) {
            itemMeta.setColor(null)
        }
    }

    private fun parseColor(): Color? {
        val str = value?.toString() ?: return null
        val parts = str.split(",").map { it.trim() }
        if (parts.size == 3) {
            val r = parts[0].toIntOrNull() ?: return null
            val g = parts[1].toIntOrNull() ?: return null
            val b = parts[2].toIntOrNull() ?: return null
            return Color.fromRGB(r, g, b)
        }
        // 尝试 hex
        if (str.startsWith("#") && str.length == 7) {
            val rgb = str.substring(1).toIntOrNull(16) ?: return null
            return Color.fromRGB(rgb)
        }
        return null
    }
}
