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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapperFunctionTest {
    @Test
    fun `color mapper handles equal range without NaN`() {
        val color = requireNotNull(MapperFunction.get("color"))
        assertEquals("§c10", color(listOf(10, 10, 10)))
    }

    @Test
    fun `bar mapper clamps hostile scale`() {
        val bar = requireNotNull(MapperFunction.get("bar"))
        assertEquals(100, bar(listOf(1, 1, 10_000)).count { it == '◆' })
    }

    @Test
    fun `unknown bare name is not treated as extension key`() {
        assertNull(MapperFunction.get("third_party_mapper"))
    }
}
