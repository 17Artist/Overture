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

package priv.seventeen.artist.overture.core.message

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.overture.util.ColorUtil
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


internal object LanguageManager {
    private data class State(
        val messages: YamlConfiguration,
        val defaults: YamlConfiguration
    )

    data class LoadResult(
        val success: Boolean,
        val error: String? = null
    )

    @Volatile
    private var state: State? = null

    fun load(file: File, openResource: (String) -> InputStream?): LoadResult {
        val defaults = openResource("language.yml")?.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use(YamlConfiguration::loadConfiguration)
        } ?: throw IllegalStateException("JAR 内缺少 language.yml")

        if (!file.isFile) {
            state = State(defaults, defaults)
            return LoadResult(success = true)
        }

        val custom = YamlConfiguration()
        return try {
            FileInputStream(file).use { input -> InputStreamReader(input, StandardCharsets.UTF_8).use(custom::load) }
            state = State(custom, defaults)
            LoadResult(success = true)
        } catch (error: Exception) {
            if (state == null) {
                state = State(defaults, defaults)
            }
            LoadResult(success = false, error = error.message ?: error.javaClass.simpleName)
        }
    }

    fun text(path: String, vararg placeholders: Pair<String, Any?>): String =
        ColorUtil.colored(raw(path, *placeholders))

    fun raw(path: String, vararg placeholders: Pair<String, Any?>): String {
        val snapshot = state
        var value = snapshot?.messages?.getString(path)
            ?: snapshot?.defaults?.getString(path)
            ?: path

        val prefix = snapshot?.messages?.getString("prefix")
            ?: snapshot?.defaults?.getString("prefix")
            ?: ""
        value = value.replace("%prefix%", prefix)
        placeholders.forEach { (key, replacement) ->
            value = value.replace("%$key%", replacement?.toString().orEmpty())
        }
        return value
    }
}
