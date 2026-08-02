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

package priv.seventeen.artist.overture.core.component

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.overture.api.data.ItemDataLimits
import priv.seventeen.artist.overture.api.data.ItemDataNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ItemDataCodecTest {
    @Test
    fun `yaml mapping remains structured and round trips through ItemTag`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString(
            """
            component:
              physical_damage:
                operation: add
                value: 36
              critical_chance:
                operation: add
                value: 8%
              locked_slots:
                - index: 2
                  unlock_at_enhancement: 5
            """.trimIndent()
        )
        val source = ItemDataCodec.fromConfiguration(
            requireNotNull(yaml.getConfigurationSection("component")),
            "items.blade.components.symphony:attributes"
        )

        val physical = assertIs<ItemDataNode.Compound>(source["physical_damage"])
        assertEquals("add", assertIs<ItemDataNode.Text>(physical["operation"]).value)
        assertEquals(36L, assertIs<ItemDataNode.Integer>(physical["value"]).value)
        assertEquals("8%", assertIs<ItemDataNode.Text>(
            assertIs<ItemDataNode.Compound>(source["critical_chance"])["value"]
        ).value)

        val restored = ItemDataCodec.fromTag(ItemDataCodec.toTag(source))
        assertEquals(source, restored)
    }

    @Test
    fun `nbt reads count each compound once and reject oversized strings`() {
        var deep: ItemDataNode = ItemDataNode.Text("leaf")
        repeat(ItemDataLimits.MAX_DEPTH) {
            deep = ItemDataNode.Compound(mapOf("next" to deep))
        }
        val root = assertIs<ItemDataNode.Compound>(deep)
        assertEquals(root, ItemDataCodec.fromTag(ItemDataCodec.toTag(root)))

        val oversized = ItemTag().also {
            it["text"] = ItemTagData.of("x".repeat(ItemDataLimits.MAX_STRING_LENGTH + 1))
        }
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.fromTag(oversized)
        }
    }
    @Test
    fun `containers copy caller collections`() {
        val mutable = linkedMapOf<String, ItemDataNode>("value" to ItemDataNode.Integer(1))
        val compound = ItemDataNode.Compound(mutable)
        mutable["value"] = ItemDataNode.Integer(2)
        assertEquals(1L, assertIs<ItemDataNode.Integer>(compound["value"]).value)

        val list = mutableListOf<ItemDataNode>(ItemDataNode.Text("a"))
        val node = ItemDataNode.ListNode(list)
        list += ItemDataNode.Text("b")
        assertEquals(1, node.values.size)
    }

    @Test
    fun `storage rejects mixed nbt lists and excessive depth`() {
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.validateForStorage(
                ItemDataNode.ListNode(listOf(ItemDataNode.Text("a"), ItemDataNode.Integer(1))),
                "data.test"
            )
        }

        var deep: ItemDataNode = ItemDataNode.Text("leaf")
        repeat(34) { deep = ItemDataNode.Compound(mapOf("next" to deep)) }
        val error = assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.validateForStorage(deep, "data.deep")
        }
        assertTrue(error.message.orEmpty().contains("深度"))
    }

    @Test
    fun `storage rejects every published size boundary and arbitrary java values`() {
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.validateForStorage(
                ItemDataNode.Text("x".repeat(32_768)),
                "data.text"
            )
        }
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.validateForStorage(
                ItemDataNode.ListNode(List(65_537) { ItemDataNode.Bool(false) }),
                "data.list"
            )
        }
        val nodes = linkedMapOf<String, ItemDataNode>()
        repeat(16_384) { nodes["n$it"] = ItemDataNode.Integer(it.toLong()) }
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.validateForStorage(ItemDataNode.Compound(nodes), "data.nodes")
        }

        val yaml = YamlConfiguration()
        yaml.set("component.value", StringBuilder("not-a-supported-value"))
        assertFailsWith<ItemDataValidationException> {
            ItemDataCodec.fromConfiguration(
                requireNotNull(yaml.getConfigurationSection("component")),
                "items.test.components.example:value"
            )
        }
    }
    @Test
    fun `numeric narrowing is exact`() {
        assertEquals(Int.MAX_VALUE, ItemDataNode.Integer(Int.MAX_VALUE.toLong()).toIntExact())
        assertFailsWith<ArithmeticException> {
            ItemDataNode.Integer(Int.MAX_VALUE.toLong() + 1).toIntExact()
        }
        assertEquals(42L, ItemDataNode.Decimal(42.0).toLongExact())
        assertFailsWith<IllegalArgumentException> { ItemDataNode.Decimal(42.5).toLongExact() }
        assertFailsWith<IllegalArgumentException> {
            ItemDataNode.Decimal(Long.MAX_VALUE.toDouble()).toLongExact()
        }
    }
}
