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

package priv.seventeen.artist.overture.core.meta

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.core.meta.impl.MetaAttribute
import priv.seventeen.artist.overture.core.meta.impl.MetaColor
import priv.seventeen.artist.overture.core.meta.impl.MetaCustomModelData
import priv.seventeen.artist.overture.core.meta.impl.MetaDurability
import priv.seventeen.artist.overture.core.meta.impl.MetaEnchantment
import priv.seventeen.artist.overture.core.meta.impl.MetaItemFlag
import priv.seventeen.artist.overture.core.meta.impl.MetaNative
import priv.seventeen.artist.overture.core.meta.impl.MetaPotion
import priv.seventeen.artist.overture.core.meta.impl.MetaRarity
import priv.seventeen.artist.overture.core.meta.impl.MetaShiny
import priv.seventeen.artist.overture.core.meta.impl.MetaSkull
import priv.seventeen.artist.overture.core.meta.impl.MetaUnbreakable
import priv.seventeen.artist.overture.core.meta.impl.MetaUnique
import priv.seventeen.artist.overture.core.registry.OwnedRegistry

/**
 * 内置及第三方 Meta 工厂注册表。
 */
object MetaRegistry {
    private val builtIns = mutableMapOf<String, MetaFactory>()
    private val registry = OwnedRegistry<MetaFactory>("meta-factory")

    init {
        registerBuiltIn("attribute") { section, _, locked -> MetaAttribute(section, locked) }
        registerBuiltIn("enchantment") { section, _, locked -> MetaEnchantment(section, locked) }
        registerBuiltIn("unique") { _, value, locked -> MetaUnique(value, locked) }
        registerBuiltIn("durability") { section, _, locked -> MetaDurability(section, locked) }
        registerBuiltIn("unbreakable") { _, value, locked -> MetaUnbreakable(value, locked) }
        registerBuiltIn("custom_model_data") { _, value, locked -> MetaCustomModelData(value, locked) }
        registerBuiltIn("item_flag") { _, value, locked -> MetaItemFlag(value, locked) }
        registerBuiltIn("color") { _, value, locked -> MetaColor(value, locked) }
        registerBuiltIn("skull") { _, value, locked -> MetaSkull(value, locked) }
        registerBuiltIn("potion") { _, value, locked -> MetaPotion(value, locked) }
        registerBuiltIn("native") { section, _, locked -> MetaNative(section, locked) }
        registerBuiltIn("shiny") { _, value, locked -> MetaShiny(value, locked) }
        registerBuiltIn("rarity") { _, value, locked -> MetaRarity(value, locked) }
    }

    fun register(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        factory: MetaFactory
    ): RegistrationHandle = registry.register(owner, key, priority, factory)

    fun create(key: String, section: ConfigurationSection?, value: Any?, locked: Boolean): Meta? {
        val normalizedKey = key.lowercase()
        val factory = builtIns[normalizedKey]
            ?: normalizedKey.takeIf { ':' in it }
                ?.let(NamespacedKey::fromString)
                ?.let { registry.active(it)?.value }
            ?: return null
        return try {
            factory.create(section, value, locked)
        } catch (error: Throwable) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.meta-create-failed",
                    "meta" to normalizedKey,
                    "error" to (error.message ?: error.javaClass.simpleName)
                )
            )
            null
        }
    }

    fun getRegisteredKeys(): Set<String> = buildSet {
        addAll(builtIns.keys)
        registry.activeEntries().mapTo(this) { it.key.toString() }
    }

    fun registrations(): List<String> =
        builtIns.keys.sorted().map { "$it owner=Overture built-in" } +
            registry.infos().map {
                "${it.key} owner=${it.ownerName} priority=${it.priority} active=${it.active}"
            }

    private fun registerBuiltIn(key: String, factory: MetaFactory) {
        builtIns[key] = factory
    }

    fun interface MetaFactory {
        fun create(section: ConfigurationSection?, value: Any?, locked: Boolean): Meta
    }
}
