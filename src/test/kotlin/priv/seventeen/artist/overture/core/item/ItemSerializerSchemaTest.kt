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

package priv.seventeen.artist.overture.core.item

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.bukkit.Material
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ItemSerializerSchemaTest {
    @Test
    fun `accepts current schema vanilla item`() {
        val item = ItemSerializer.deserializeStrict(
            """{"schema":2,"kind":"minecraft","id":"minecraft:stone","amount":2,"future_field":"ignored"}"""
        )
        assertEquals(Material.STONE, item.type)
        assertEquals(2, item.amount)
    }

    @Test
    fun `rejects missing schema`() {
        assertFailsWith<ItemSerializationException> {
            ItemSerializer.deserializeStrict(
                """{"kind":"minecraft","id":"minecraft:stone","amount":1}"""
            )
        }
    }

    @Test
    fun `rejects non-current schema`() {
        assertFailsWith<ItemSerializationException> {
            ItemSerializer.deserializeStrict(
                """{"schema":1,"kind":"minecraft","id":"minecraft:stone","amount":1}"""
            )
        }
    }

    @Test
    fun `requires explicit kind and amount`() {
        assertFailsWith<ItemSerializationException> {
            ItemSerializer.deserializeStrict(
                """{"schema":2,"id":"minecraft:stone","amount":1}"""
            )
        }
        assertFailsWith<ItemSerializationException> {
            ItemSerializer.deserializeStrict(
                """{"schema":2,"kind":"minecraft","id":"minecraft:stone"}"""
            )
        }
    }

    @Test
    fun `safe entry rejects obsolete schemas`() {
        assertNull(
            ItemSerializer.deserialize(
                """{"id":"minecraft:stone","amount":1}"""
            )
        )
        assertNull(
            ItemSerializer.deserialize(
                """{"schema":1,"kind":"minecraft","id":"minecraft:stone","amount":1}"""
            )
        )
    }

    @Test
    fun `tag decoder rejects obsolete t v shape`() {
        val error = invokeDecodeData("""{"t":3,"v":1}""")
        assertIs<ItemSerializationException>(error.cause)
    }

    @Test
    fun `tag decoder rejects unknown type`() {
        val error = invokeDecodeData("""{"type":"FUTURE","value":1}""")
        assertIs<ItemSerializationException>(error.cause)
    }

    @Test
    fun `rejects excessive nesting`() {
        val nested = buildString {
            repeat(ItemSerializer.MAX_DEPTH + 2) { append("""{"x":""") }
            append("1")
            repeat(ItemSerializer.MAX_DEPTH + 2) { append("}") }
        }
        assertFailsWith<ItemSerializationException> {
            ItemSerializer.deserializeStrict(nested)
        }
    }

    private fun invokeDecodeData(json: String): InvocationTargetException {
        val method = ItemSerializer::class.java.getDeclaredMethod(
            "decodeData",
            JsonElement::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return assertFailsWith {
            method.invoke(ItemSerializer, JsonParser.parseString(json), 0)
        }
    }
}
