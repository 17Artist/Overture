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

package priv.seventeen.artist.overture.core.resource

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.overture.core.action.AriaRegistry
import java.io.InputStream
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultResourceInstallerTest {
    @Test
    fun `installs every missing resource and preserves existing user files`() {
        val root = createTempDirectory("overture-defaults-")
        try {
            val preserved = root.resolve("items/example_items.yml")
            preserved.parent.createDirectories()
            preserved.writeText("# user-owned")

            val first = DefaultResourceInstaller.install(root.toFile(), ::openResource)
            assertEquals(listOf("items/example_items.yml"), first.existing)
            assertEquals(
                DefaultResourceInstaller.resourcePaths.size - 1,
                first.copied.size
            )
            assertEquals("# user-owned", Files.readString(preserved))
            DefaultResourceInstaller.resourcePaths.forEach { path ->
                assertTrue(Files.isRegularFile(root.resolve(path)))
            }

            val second = DefaultResourceInstaller.install(root.toFile(), ::openResource)
            assertTrue(second.copied.isEmpty())
            assertEquals(DefaultResourceInstaller.resourcePaths, second.existing)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `packaged examples cover the default catalog and compile all aria expressions`() {
        AriaRegistry.register()
        val itemIds = mutableSetOf<String>()
        val modelIds = mutableSetOf<String>()
        val displayIds = mutableSetOf<String>()
        var routineIndex = 0

        DefaultResourceInstaller.resourcePaths.forEach { path ->
            val yaml = YamlConfiguration()
            openResource(path).reader(Charsets.UTF_8).use { reader ->
                yaml.load(reader)
            }
            when {
                path.startsWith("items/") && !path.endsWith("__group__.yml") -> {
                    yaml.getKeys(false).forEach { key ->
                        val section = assertNotNull(yaml.getConfigurationSection(key))
                        if (key.endsWith("$")) {
                            modelIds += key.removeSuffix("$")
                        } else {
                            itemIds += key
                            if (key == "example_greatsword") {
                                assertEquals(20, section.getInt("data.durability_current"))
                            }
                            if (key == "example_potion") {
                                assertEquals("potion_display", section.getString("display"))
                                assertEquals(0, section.getInt("data.consumed"))
                                assertTrue(section.getString("event.on_consume").orEmpty().contains("+ 1"))
                            }
                        }
                        section.getConfigurationSection("event")?.let { events ->
                            events.getKeys(false)
                                .filterNot { it == "from" || it == "data" }
                                .forEach { trigger ->
                                    val script = assertNotNull(events.getString(trigger))
                                    Aria.compile("defaults.event.${routineIndex++}", script)
                                }
                        }
                    }
                }
                path.startsWith("displays/") -> {
                    yaml.getKeys(false).forEach { key ->
                        displayIds += key
                        yaml.getMapList("$key.conditions").forEach { condition ->
                            val expression = assertNotNull(condition["condition"]?.toString())
                            Aria.compile("defaults.condition.${routineIndex++}", "return $expression")
                        }
                    }
                }
            }
        }

        assertEquals(
            setOf(
                "example_sword",
                "example_potion",
                "example_armor",
                "example_greatsword",
                "example_bow",
                "example_scroll",
                "example_compass"
            ),
            itemIds
        )
        assertEquals(setOf("example_weapon_events"), modelIds)
        assertEquals(10, displayIds.size)
        assertTrue("potion_display" in displayIds)
        assertTrue(routineIndex >= 10)
    }

    private fun openResource(path: String): InputStream =
        assertNotNull(javaClass.classLoader.getResourceAsStream(path), "missing resource: $path")
}
