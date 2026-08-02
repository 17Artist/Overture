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

package priv.seventeen.artist.overture.core.mapper

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.core.registry.OwnedRegistry

fun interface MapperHandler {
    fun map(args: List<Any>): String
}

/**
 * 内置及第三方数据映射函数注册表。
 */
object MapperFunction {
    private val builtIns = mutableMapOf<String, MapperHandler>()
    private val registry = OwnedRegistry<MapperHandler>("mapper-function")

    init {
        registerBuiltIn("bar") { args ->
            val current = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val max = (args.getOrNull(1) as? Number)?.toInt() ?: 1
            val scale = (args.getOrNull(2) as? Number)?.toInt() ?: 20
            buildBar(current, max, scale)
        }
        registerBuiltIn("repeat") { args ->
            val str = args.getOrNull(0)?.toString() ?: ""
            val n = (args.getOrNull(1) as? Number)?.toInt() ?: 0
            str.repeat(n.coerceIn(0, 100))
        }
        registerBuiltIn("format") { args ->
            val pattern = args.getOrNull(0)?.toString() ?: "%s"
            try {
                String.format(pattern, *args.drop(1).toTypedArray())
            } catch (_: Exception) {
                pattern
            }
        }
        registerBuiltIn("color") { args ->
            val value = (args.getOrNull(0) as? Number)?.toDouble() ?: 0.0
            val min = (args.getOrNull(1) as? Number)?.toDouble() ?: 0.0
            val max = (args.getOrNull(2) as? Number)?.toDouble() ?: 100.0
            val percent = if (max == min) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
            val color = when {
                percent <= 0.25 -> "§c"
                percent <= 0.5 -> "§e"
                percent <= 0.75 -> "§a"
                else -> "§2"
            }
            "$color${value.toInt()}"
        }
        registerBuiltIn("percent") { args ->
            val current = (args.getOrNull(0) as? Number)?.toDouble() ?: 0.0
            val max = (args.getOrNull(1) as? Number)?.toDouble() ?: 1.0
            val percent = if (max > 0) current / max * 100 else 0.0
            "%.1f%%".format(percent)
        }
        registerBuiltIn("roman") { args ->
            toRoman((args.getOrNull(0) as? Number)?.toInt() ?: 0)
        }
        registerBuiltIn("fixed") { args ->
            val value = (args.getOrNull(0) as? Number)?.toDouble() ?: 0.0
            val decimals = (args.getOrNull(1) as? Number)?.toInt()?.coerceIn(0, 10) ?: 1
            "%.${decimals}f".format(value)
        }
        registerBuiltIn("condition") { args ->
            val condition = when (val value = args.getOrNull(0)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.isNotEmpty() && value != "false" && value != "0"
                else -> false
            }
            if (condition) args.getOrNull(1)?.toString().orEmpty()
            else args.getOrNull(2)?.toString().orEmpty()
        }
    }

    fun register(
        owner: Plugin,
        key: NamespacedKey,
        priority: Int = 0,
        handler: MapperHandler
    ): RegistrationHandle = registry.register(owner, key, priority, handler)

    fun get(name: String): ((List<Any>) -> String)? {
        builtIns[name]?.let { return it::map }
        if (':' !in name) return null
        val key = NamespacedKey.fromString(name.lowercase()) ?: return null
        return registry.active(key)?.value?.let { it::map }
    }

    fun registrations(): List<String> =
        builtIns.keys.sorted().map { "$it owner=Overture built-in" } +
            registry.infos().map {
                "${it.key} owner=${it.ownerName} priority=${it.priority} active=${it.active}"
            }

    private fun registerBuiltIn(name: String, handler: MapperHandler) {
        builtIns[name] = handler
    }

    private fun buildBar(current: Int, max: Int, scale: Int): String {
        val safeScale = scale.coerceIn(0, 100)
        val filled = if (max > 0) {
            (current.toDouble() / max * safeScale).toInt().coerceIn(0, safeScale)
        } else {
            0
        }
        return buildString {
            for (index in 1..safeScale) append(if (index <= filled) "§f◆" else "§7◇")
        }
    }

    private fun toRoman(num: Int): String {
        if (num <= 0 || num > 3999) return num.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        return buildString {
            var remaining = num
            for (index in values.indices) {
                while (remaining >= values[index]) {
                    append(symbols[index])
                    remaining -= values[index]
                }
            }
        }
    }
}
