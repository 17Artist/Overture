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
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArcartXIndependenceContractTest {

    @Test
    fun `overture has no production dependency on arcartx`() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        assertTrue(build.contains("softDepend.set(emptyList())"))
        assertFalse(build.contains("priv.seventeen.artist.arcartx"))

        val sourceRoot = Path.of("src/main/kotlin")
        val source = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }

        assertFalse(source.contains("priv.seventeen.artist.arcartx"))
        assertFalse(source.contains("ArcartXHook"))
        assertFalse(source.contains("MetaDrop"))
    }

    @Test
    fun `arcartx tags are not overture built in meta`() {
        val registry = Files.readString(
            Path.of(
                "src/main/kotlin/priv/seventeen/artist/overture/core/meta/MetaRegistry.kt"
            )
        )

        assertFalse(registry.contains("registerBuiltIn(\"drop\")"))
        assertFalse(registry.contains("MetaDrop"))
    }
}
