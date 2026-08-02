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

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.event.PluginReloadEvent
import priv.seventeen.artist.overture.api.reload.ReloadReport
import priv.seventeen.artist.overture.core.meta.Meta
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OvertureApiContractTest {
    @Test
    fun `public api facade exposes static methods only`() {
        val publicMethods = OvertureAPI::class.java.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && !it.isSynthetic
        }
        assertTrue(publicMethods.isNotEmpty())
        publicMethods.forEach { method ->
            assertTrue(Modifier.isStatic(method.modifiers), method.toString())
        }
    }

    @Test
    fun `removed legacy api methods stay absent`() {
        val methods = OvertureAPI::class.java.declaredMethods
        assertFalse(methods.any { it.name == "reload" })
        assertFalse(methods.any { it.name == "unregisterProvider" })
        assertFalse(methods.any {
            it.name == "registerProvider" &&
                it.parameterTypes.contentEquals(arrayOf(ItemProvider::class.java))
        })

        val providerRegistrations = methods.filter {
            it.name == "registerProvider" && !it.isSynthetic
        }
        assertTrue(providerRegistrations.isNotEmpty())
        providerRegistrations.forEach {
            assertEquals(Plugin::class.java, it.parameterTypes[0])
            assertEquals(NamespacedKey::class.java, it.parameterTypes[1])
        }
    }

    @Test
    fun `extension contexts expose stable api types without compatibility constructors`() {
        val names = setOf("registerItemComponent", "readItemData", "mutateItem", "rebuildItem")
        val methods = OvertureAPI::class.java.declaredMethods.filter {
            it.name in names && Modifier.isPublic(it.modifiers) && !it.isSynthetic
        }
        assertEquals(names, methods.mapTo(linkedSetOf()) { it.name })
        methods.forEach { method ->
            (method.parameterTypes.asList() + method.returnType).forEach { type ->
                val name = type.canonicalName ?: return@forEach
                if (name.startsWith("priv.seventeen.artist.overture.")) {
                    assertTrue(name.startsWith("priv.seventeen.artist.overture.api."), method.toString())
                }
                assertFalse(name.startsWith("priv.seventeen.artist.asteroid."), method.toString())
                assertFalse(name.startsWith("org.bukkit.configuration."), method.toString())
            }
        }

        val renderContext = Class.forName("priv.seventeen.artist.overture.api.render.RenderEntryContext")
        assertEquals(
            "priv.seventeen.artist.overture.api.data.ItemDataView",
            renderContext.getMethod("getData").returnType.canonicalName
        )
        assertTrue(renderContext.declaredConstructors.any { it.parameterCount == 4 })
        val behaviorContext = Class.forName("priv.seventeen.artist.overture.api.behavior.ItemBehaviorContext")
        listOf(renderContext, behaviorContext).forEach { type ->
            type.declaredMethods.forEach { method ->
                (method.parameterTypes.asList() + method.returnType).forEach { exposed ->
                    val name = exposed.canonicalName ?: return@forEach
                    assertFalse(name.startsWith("priv.seventeen.artist.overture.core."), method.toString())
                    assertFalse(name.startsWith("priv.seventeen.artist.asteroid."), method.toString())
                }
            }
    }
    }
    @Test
    fun `reload event and meta drop expose current signatures only`() {
        val constructors = PluginReloadEvent::class.java.declaredConstructors
        assertEquals(1, constructors.size)
        assertTrue(
            constructors.single().parameterTypes.contentEquals(
                arrayOf(ReloadReport::class.java)
            )
        )

        val drops = Meta::class.java.declaredMethods.filter { it.name == "drop" }
        assertEquals(1, drops.size)
        assertEquals(3, drops.single().parameterCount)
    }
}
