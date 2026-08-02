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

import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.core.meta.MetaState

/**
 * 药水效果 Meta
 *
 * 配置格式:
 * ```yaml
 * potion:
 *   - "SPEED,100,1"
 *   - "REGENERATION,200,2"
 * ```
 */
@MetaKey("potion")
class MetaPotion(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "potion"

    val effects: List<PotionEffect> = parseEffects()
    private var cleanupEffects: List<PotionEffectType> = emptyList()

    @Suppress("DEPRECATION")
    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        MetaState.putStrings(compound, key, effects.map { it.type.name })
    }

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        cleanupEffects = MetaState.getStrings(compound, key).mapNotNull { resolvePotionType(it) }
    }

    override fun buildMeta(itemMeta: ItemMeta) {
        if (itemMeta !is PotionMeta) return
        for (effect in effects) {
            itemMeta.addCustomEffect(effect, true)
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        if (itemMeta !is PotionMeta) return
        for (type in cleanupEffects) {
            itemMeta.removeCustomEffect(type)
        }
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        cleanupEffects = MetaState.getStrings(compound, key).mapNotNull { resolvePotionType(it) }
        MetaState.remove(compound, key)
    }

    private fun parseEffects(): List<PotionEffect> {
        val list = when (value) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> listOf(value)
            else -> return emptyList()
        }
        return list.mapNotNull { str ->
            val parts = str.split(",").map { it.trim() }
            if (parts.size < 2) return@mapNotNull null
            val type = resolvePotionType(parts[0]) ?: return@mapNotNull null
            val duration = parts[1].toIntOrNull() ?: return@mapNotNull null
            val amplifier = parts.getOrNull(2)?.toIntOrNull() ?: 0
            PotionEffect(type, duration, amplifier)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePotionType(name: String): PotionEffectType? {
        // getByName 在所有版本都可用
        return PotionEffectType.getByName(name.uppercase())
    }
}
