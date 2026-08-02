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

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.component.ComponentIssue
import priv.seventeen.artist.overture.api.component.ItemComponentCodec
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.registry.RegistrationConflictException
import priv.seventeen.artist.overture.api.reload.ReloadIssueSeverity
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemComponentRegistryTest {
    @Test
    fun `owned priority registry selects active codec and closes idempotently`() {
        val lowOwner = plugin("Symphony")
        val highOwner = plugin("Symphony")
        val key = NamespacedKey(lowOwner, "attributes")
        val low = ItemComponentRegistry.register(lowOwner, key, 0, successCodec("low"))
        val high = ItemComponentRegistry.register(highOwner, key, 10, successCodec("high"))
        try {
            val compilation = compile("""
                item:
                  components:
                    symphony:attributes:
                      value: 1
            """)
            val definition = compilation.namespaces.getValue("symphony").definitions.getValue("attributes")
            assertEquals("high", (definition["winner"] as ItemDataNode.Text).value)
            assertTrue(high.closeOnce())
            assertFalse(high.closeOnce())
        } finally {
            high.unregister()
            low.unregister()
        }
    }

    @Test
    fun `foreign namespace equal priority and namespace schema mismatch are rejected`() {
        val owner = plugin("Symphony")
        assertFailsWith<IllegalArgumentException> {
            ItemComponentRegistry.register(
                owner,
                requireNotNull(NamespacedKey.fromString("foreign:attributes")),
                0,
                successCodec("foreign")
            )
        }

        val firstOwner = plugin("Symphony")
        val secondOwner = plugin("Symphony")
        val attributes = NamespacedKey(firstOwner, "attributes")
        val first = ItemComponentRegistry.register(firstOwner, attributes, 0, successCodec("one"))
        try {
            assertFailsWith<RegistrationConflictException> {
                ItemComponentRegistry.register(secondOwner, attributes, 0, successCodec("two"))
            }
            assertFailsWith<IllegalArgumentException> {
                ItemComponentRegistry.register(
                    secondOwner,
                    NamespacedKey(secondOwner, "sockets"),
                    0,
                    successCodec("schema-two", schema = 2)
                )
            }
        } finally {
            first.unregister()
        }
    }

    @Test
    fun `priority fallback schema mismatch is rejected by candidate validation`() {
        val lowOwner = plugin("Symphony")
        val highOwner = plugin("Symphony")
        val socketsOwner = plugin("Symphony")
        val attributes = NamespacedKey(lowOwner, "attributes")
        val low = ItemComponentRegistry.register(lowOwner, attributes, 0, successCodec("low", schema = 2))
        val high = ItemComponentRegistry.register(highOwner, attributes, 10, successCodec("high", schema = 1))
        val sockets = ItemComponentRegistry.register(
            socketsOwner,
            NamespacedKey(socketsOwner, "sockets"),
            0,
            successCodec("sockets", schema = 1)
        )
        try {
            assertTrue(ItemComponentRegistry.validationIssues().isEmpty())
            assertTrue(high.unregister())
            val issue = ItemComponentRegistry.validationIssues().single()
            assertEquals(ReloadIssueSeverity.ERROR, issue.severity)
            assertEquals("component-registry", issue.source)
            assertTrue(issue.message.contains("schemaVersion"))
        } finally {
            sockets.unregister()
            high.unregister()
            low.unregister()
        }
    }
    @Test
    fun `codec warnings are aggregated without rejecting the compiled definition`() {
        val owner = plugin("Symphony")
        val key = NamespacedKey(owner, "warning")
        val handle = ItemComponentRegistry.register(
            owner,
            key,
            0,
            object : ItemComponentCodec {
                override val schemaVersion: Int = 1
                override fun decode(context: priv.seventeen.artist.overture.api.component.ComponentDecodeContext) =
                    ComponentDecodeResult.Success(
                        context.source,
                        listOf(ComponentIssue("value", "normalized warning"))
                    )
            }
        )
        try {
            val compilation = compile("""
                item:
                  components:
                    symphony:warning:
                      value: 1
            """)
            assertTrue(compilation.namespaces.getValue("symphony").definitions.containsKey("warning"))
            assertEquals(1, compilation.issues.size)
            assertEquals(ReloadIssueSeverity.WARNING, compilation.issues.single().severity)
            assertTrue(compilation.issues.single().path?.endsWith("symphony:warning.value") == true)
        } finally {
            handle.unregister()
        }
    }
    @Test
    fun `compile aggregates failures throws unregistered and non mapping errors`() {
        val owner = plugin("Symphony")
        val failure = ItemComponentRegistry.register(
            owner,
            NamespacedKey(owner, "failure"),
            0,
            object : ItemComponentCodec {
                override val schemaVersion: Int = 1
                override fun decode(context: priv.seventeen.artist.overture.api.component.ComponentDecodeContext) =
                    ComponentDecodeResult.Failure(
                        listOf(
                            ComponentIssue("first.value", "first invalid"),
                            ComponentIssue("second.value", "second invalid")
                        )
                    )
            }
        )
        val throwing = ItemComponentRegistry.register(
            owner,
            NamespacedKey(owner, "throwing"),
            0,
            object : ItemComponentCodec {
                override val schemaVersion: Int = 1
                override fun decode(context: priv.seventeen.artist.overture.api.component.ComponentDecodeContext): ComponentDecodeResult {
                    error("intentional codec failure")
                }
            }
        )
        try {
            val compilation = compile("""
                item:
                  components:
                    symphony:failure:
                      first: 1
                    symphony:throwing:
                      value: 1
                    symphony:missing:
                      value: 1
                    invalid: scalar
            """)
            val errors = compilation.issues.filter { it.severity == ReloadIssueSeverity.ERROR }
            assertEquals(5, errors.size)
            assertTrue(errors.any { it.path?.endsWith("first.value") == true })
            assertTrue(errors.any { it.path?.endsWith("second.value") == true })
            assertTrue(errors.any { it.component == "symphony:throwing" && it.owner == "Symphony" })
            assertTrue(errors.any { it.component == "symphony:missing" && it.message.contains("未注册") })
            assertTrue(errors.any { it.component == "invalid" && it.message.contains("namespace:key") })
            assertTrue(errors.all { it.itemId == "item" && it.source == "items/test.yml" })
        } finally {
            throwing.unregister()
            failure.unregister()
        }
    }

    private fun compile(source: String): ComponentCompilation {
        val yaml = YamlConfiguration()
        yaml.loadFromString(source.trimIndent())
        return ItemComponentRegistry.compile(
            "item",
            "items/test.yml",
            requireNotNull(yaml.getConfigurationSection("item"))
        )
    }

    private fun successCodec(winner: String, schema: Int = 1) = object : ItemComponentCodec {
        override val schemaVersion: Int = schema
        override fun decode(context: priv.seventeen.artist.overture.api.component.ComponentDecodeContext) =
            ComponentDecodeResult.Success(
                ItemDataNode.Compound(context.source.values + ("winner" to ItemDataNode.Text(winner)))
            )
    }

    private fun priv.seventeen.artist.overture.api.registry.RegistrationHandle.closeOnce(): Boolean = unregister()

    private fun plugin(name: String): Plugin = Proxy.newProxyInstance(
        Plugin::class.java.classLoader,
        arrayOf(Plugin::class.java)
    ) { _, method, _ ->
        when {
            method.name == "getName" -> name
            method.name == "isEnabled" -> true
            method.returnType == Boolean::class.javaPrimitiveType -> false
            method.returnType == Int::class.javaPrimitiveType -> 0
            method.returnType == Long::class.javaPrimitiveType -> 0L
            method.returnType == Double::class.javaPrimitiveType -> 0.0
            method.returnType == Float::class.javaPrimitiveType -> 0f
            else -> null
        }
    } as Plugin
}
