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

package priv.seventeen.artist.overture.api

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalConsumerContractTest {
    private val root: Path = Path.of("").toAbsolutePath().normalize()
    private val fixturePaths = listOf(
        root.resolve("qa/runtime/plugin/src/OvertureQAPlugin.java"),
        root.resolve("qa/component/src/SymphonyComponentQAPlugin.java")
    )
    private val fixture: String by lazy {
        fixturePaths.joinToString(separator = "\n") { Files.readString(it) }
    }

    @Test
    fun `every public api entry is consumed by the external java fixtures`() {
        assumePrivateFixturesAvailable()
        val methods = setOf(
            "getItemIds", "generateItem", "getItemName", "getItemLore", "getTemplateItem",
            "isOvertureItem", "getOvertureId", "serialize",
            "deserialize", "registerProvider", "registerRenderEntry", "registerTrigger",
            "registerBehavior", "registerMapperFunction", "registerMeta", "dispatchExternalTrigger",
            "reloadWithReport", "registerItemComponent", "readItemData", "mutateItem", "rebuildItem"
        )

        methods.forEach { method ->
            assertContains(fixture, "OvertureAPI.$method(", message = "external fixture does not consume: $method")
        }
    }

    @Test
    fun `external fixtures cover current events and aria calls without legacy namespaces`() {
        assumePrivateFixturesAvailable()
        val events = setOf(
            "ItemBuildEvent.Pre", "ItemBuildEvent.Post",
            "ItemReleaseEvent.Release", "ItemReleaseEvent.SelectDisplay",
            "ItemReleaseEvent.Display", "ItemReleaseEvent.Final",
            "ItemGiveEvent", "ItemUpdateEvent", "PluginReloadEvent"
        )
        events.forEach { event ->
            assertContains(fixture, event, message = "external fixture does not listen to: $event")
        }
        assertContains(fixture, "val.item.durability()")
        assertFalse(fixture.contains("hasStaticNamespace(\"overture\") &&"))

        val oldCall = Regex("""\b(?:overture_item|overture_cooldown|overture_potion)\.|\boverture\.(?:sendMessage|playSound|runCommand|broadcast|cancel)\s*\(|\bval\.(?:event|vars)\b""")
        val roots = listOf(
            root.resolve("README.md"),
            root.resolve("qa/runtime"),
            root.resolve("qa/component"),
            root.resolve("src/main/resources")
        )
        val stale = roots.flatMap { source ->
            if (Files.isRegularFile(source)) listOf(source) else Files.walk(source).use { paths ->
                paths.filter { path ->
                    Files.isRegularFile(path) && path.extension in setOf("md", "yml", "yaml", "js", "java")
                }.toList()
            }
        }.flatMap { path ->
            Files.readAllLines(path).mapIndexedNotNull { index, line ->
                if (oldCall.containsMatchIn(line)) "${root.relativize(path)}:${index + 1}:$line" else null
            }
        }
        assertTrue(stale.isEmpty(), "legacy Aria examples remain:\n${stale.joinToString("\n")}")
    }

    @Test
    fun `repository keeps the permanent wiki documentation boundary`() {
        assertFalse(Files.exists(root.resolve("docs")), "legacy docs directory must stay removed")
        assertFalse(
            Files.exists(root.resolve(".github/workflows/deploy-docs.yml")),
            "GitHub Pages workflow must stay removed"
        )
        assertContains(read("README.md"), "https://wiki.arcartx.com/docs/overture/1_start")
    }

    private fun read(relative: String): String = Files.readString(root.resolve(relative))

    private fun assumePrivateFixturesAvailable() {
        val missing = fixturePaths.filterNot { Files.isRegularFile(it) }
        assumeTrue(
            missing.isEmpty(),
            "private QA fixtures are not part of the Git checkout: ${missing.joinToString()}"
        )
    }
}
