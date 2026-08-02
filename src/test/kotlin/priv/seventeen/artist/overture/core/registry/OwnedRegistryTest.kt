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

package priv.seventeen.artist.overture.core.registry

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.registry.RegistrationConflictException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedRegistryTest {
    @Test
    fun `owner cleanup invalidates registration handle`() {
        val owner = plugin("Example")
        val registry = OwnedRegistry<String>("test")
        val key = NamespacedKey(owner, "entry")
        val handle = registry.register(owner, key, 0, "value")

        assertTrue(handle.isRegistered)
        assertEquals("value", registry.active(key)?.value)
        assertEquals(1, registry.unregisterOwner(owner))
        assertFalse(handle.isRegistered)
        assertFalse(handle.unregister())
    }

    @Test
    fun `equal priority collision is rejected deterministically`() {
        val first = plugin("Shared")
        val second = plugin("Shared")
        val registry = OwnedRegistry<String>("test")
        val key = NamespacedKey(first, "entry")
        registry.register(first, key, 10, "first")

        assertFailsWith<RegistrationConflictException> {
            registry.register(second, key, 10, "second")
        }
    }

    @Test
    fun `higher priority wins and unregister falls back`() {
        val first = plugin("Shared")
        val second = plugin("Shared")
        val registry = OwnedRegistry<String>("test")
        val key = NamespacedKey(first, "entry")
        val low = registry.register(first, key, 10, "low")
        val high = registry.register(second, key, 20, "high")

        assertEquals("high", registry.active(key)?.value)
        assertTrue(high.unregister())
        assertEquals("low", registry.active(key)?.value)
        assertTrue(low.isRegistered)
    }

    @Test
    fun `foreign namespace is rejected`() {
        val owner = plugin("Example")
        val registry = OwnedRegistry<String>("test")
        val foreign = requireNotNull(NamespacedKey.fromString("foreign:entry"))

        assertFailsWith<IllegalArgumentException> {
            registry.register(owner, foreign, 0, "value")
        }
    }

    private fun plugin(name: String): Plugin {
        return Proxy.newProxyInstance(
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
}
