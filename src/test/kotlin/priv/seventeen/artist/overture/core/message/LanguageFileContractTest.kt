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
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageFileContractTest {
    private val root: Path = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `every referenced language key exists`() {
        val language = YamlConfiguration.loadConfiguration(
            root.resolve("src/main/resources/language.yml").toFile()
        )
        val keyPattern = Regex(
            """(?:LanguageManager\.(?:text|raw)|message|word)\(\s*"([^"]+)""""
        )
        val keys = Files.walk(root.resolve("src/main/kotlin")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .toList()
                .flatMap { path ->
                    keyPattern.findAll(Files.readString(path))
                        .map { match -> match.groupValues[1] }
                        .toList()
                }
                .toSet()
        }

        assertTrue(keys.isNotEmpty())
        keys.forEach { key ->
            assertTrue(language.contains(key), "language.yml is missing key: $key")
        }
    }

    @Test
    fun `player and console output do not bypass the language file`() {
        val sources = Files.walk(root.resolve("src/main/kotlin")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .map(Files::readString)
                .toList()
        }
        val allSources = sources.joinToString("\n")
        assertFalse(
            Regex("""BlinkLog\.(?:info|warn|error|success)\(\s*"""").containsMatchIn(allSources),
            "direct BlinkLog string remains"
        )
        assertFalse(
            Regex("""(?:sendMessage|ctx\.reply)\(\s*ColorUtil\.colored\(\s*"""")
                .containsMatchIn(allSources),
            "direct player message remains"
        )

        val interactionSources = listOf(
            root.resolve("src/main/kotlin/priv/seventeen/artist/overture/command/OvertureCommand.kt"),
            root.resolve("src/main/kotlin/priv/seventeen/artist/overture/feature/ItemMenu.kt")
        ).joinToString("\n") { Files.readString(it) }
        assertFalse(
            Regex(""""[^"\r\n]*[\u4e00-\u9fff][^"\r\n]*"""").containsMatchIn(interactionSources),
            "visible Chinese text remains hardcoded in command or menu code"
        )
    }

    @Test
    fun `custom messages override defaults and invalid reload keeps the last valid file`() {
        val directory = createTempDirectory("overture-language-")
        try {
            val file = directory.resolve("language.yml")
            assertTrue(LanguageManager.load(file.toFile(), ::openResource).success)
            assertContains(
                LanguageManager.raw("command.item-not-found", "item" to "sample"),
                "sample"
            )

            file.writeText(
                """
                prefix: "&8[测试] "
                command:
                  item-not-found: "%prefix%&cMissing %item%"
                """.trimIndent()
            )
            assertTrue(LanguageManager.load(file.toFile(), ::openResource).success)
            assertContains(
                LanguageManager.raw("command.item-not-found", "item" to "sample"),
                "[测试]"
            )
            assertContains(
                LanguageManager.raw("command.item-not-found", "item" to "sample"),
                "Missing sample"
            )
            assertTrue(LanguageManager.raw("menu.close").isNotBlank())

            file.writeText("broken: [")
            assertFalse(LanguageManager.load(file.toFile(), ::openResource).success)
            assertContains(
                LanguageManager.raw("command.item-not-found", "item" to "sample"),
                "Missing sample"
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun openResource(path: String): InputStream? =
        javaClass.classLoader.getResourceAsStream(path)
}
