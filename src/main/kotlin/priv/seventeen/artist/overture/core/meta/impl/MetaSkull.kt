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

import org.bukkit.Bukkit
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey

/**
 * 头颅皮肤 Meta
 *
 * 配置格式:
 * ```yaml
 * skull: "player_name"
 * # 或
 * skull: "http://textures.minecraft.net/texture/..."
 * # 或
 * skull: "eyJ..."
 * ```
 */
@MetaKey("skull")
class MetaSkull(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "skull"

    val skullValue: String? = value?.toString()

    @Suppress("DEPRECATION")
    override fun buildMeta(itemMeta: ItemMeta) {
        if (itemMeta !is SkullMeta) return
        val skull = skullValue ?: return

        if (skull.startsWith("http") || skull.startsWith("eyJ")) {
            // URL / Base64 纹理 — 通过 Asteroid 跨版本设置
            AsteroidAPI.getSkullNMS().setTexture(itemMeta, skull)
        } else {
            // 玩家名
            itemMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skull))
        }
    }

    @Suppress("DEPRECATION")
    override fun dropMeta(itemMeta: ItemMeta) {
        if (itemMeta is SkullMeta) {
            itemMeta.owningPlayer = null
        }
    }
}
