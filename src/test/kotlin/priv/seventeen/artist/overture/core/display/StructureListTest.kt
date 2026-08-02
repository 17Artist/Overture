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

package priv.seventeen.artist.overture.core.display

import kotlin.test.Test
import kotlin.test.assertEquals

class StructureListTest {
    @Test
    fun `expands namespaced custom entry across lore lines`() {
        val structure = StructureList(
            listOf(
                "header",
                "<example:item_desc...>",
                "footer"
            )
        )
        val variables = mutableMapOf(
            "example:item_desc" to mutableListOf("line-1", "line-2")
        )

        assertEquals(
            listOf("header", "line-1", "line-2", "footer"),
            structure.build(variables)
        )
        assertEquals(setOf("example:item_desc"), structure.listVariableNames)
    }

    @Test
    fun `empty list entry removes its template line`() {
        val structure = StructureList(listOf("before", "<example:empty...>", "after"))
        val variables = mutableMapOf("example:empty" to mutableListOf<String>())
        assertEquals(listOf("before", "after"), structure.build(variables))
    }
}
