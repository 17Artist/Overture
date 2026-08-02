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

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.core.meta.MetaState

/**
 * 附魔 Meta
 *
 * 配置格式:
 * ```yaml
 * enchantment:
 *   sharpness: 3
 *   unbreaking: 2
 * ```
 */
@MetaKey("enchantment")
class MetaEnchantment(
    private val section: ConfigurationSection?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "enchantment"

    private val enchantments: Map<Enchantment, Int> = parseEnchantments()
    private var cleanupEnchantments: List<Enchantment> = emptyList()

    @Suppress("DEPRECATION")
    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        MetaState.putStrings(
            compound,
            key,
            enchantments.keys.map { it.key.toString() }
        )
    }

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        cleanupEnchantments = MetaState.getStrings(compound, key).mapNotNull { resolveEnchantment(it) }
    }

    override fun buildMeta(itemMeta: ItemMeta) {
        for ((enchant, level) in enchantments) {
            itemMeta.addEnchant(enchant, level, true)
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        for (enchant in cleanupEnchantments) {
            itemMeta.removeEnchant(enchant)
        }
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        cleanupEnchantments = MetaState.getStrings(compound, key).mapNotNull { resolveEnchantment(it) }
        MetaState.remove(compound, key)
    }

    @Suppress("DEPRECATION")
    private fun parseEnchantments(): Map<Enchantment, Int> {
        val result = mutableMapOf<Enchantment, Int>()
        section ?: return result

        for (key in section.getKeys(false)) {
            val level = section.getInt(key, 1)
            val enchant = resolveEnchantment(key)
            if (enchant != null) {
                result[enchant] = level
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun resolveEnchantment(name: String): Enchantment? {
        // 优先尝试 NamespacedKey
        return try {
            val key = NamespacedKey.fromString(name.lowercase())
                ?: NamespacedKey.minecraft(name.lowercase())
            Enchantment.getByKey(key)
        } catch (_: Exception) {
            // 回退到旧 API
            try {
                Enchantment.getByName(name.uppercase())
            } catch (_: Exception) {
                null
            }
        }
    }
}
