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

package priv.seventeen.artist.overture.core.action

import org.bukkit.entity.Player
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.overture.core.item.ItemStream
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AriaRegistrationShapeTest {
    @Test
    fun `all aria handlers are public static object functions with exact targets`() {
        val classes = mapOf(
            OvertureItemAriaFunctions::class.java to Pair(
                ItemStream::class.java,
                setOf(
                    "damage", "repair", "durability", "maxDurability", "consume", "data",
                    "removeData", "update", "id", "amount", "uses", "use"
                )
            ),
            OverturePlayerAriaFunctions::class.java to Pair(
                Player::class.java,
                setOf("checkItemCooldown", "setItemCooldown", "itemCooldownRemaining")
            )
        )

        classes.forEach { (type, expected) ->
            val (expectedTarget, expectedHandlers) = expected
            val handlers = type.declaredMethods.filter {
                it.isAnnotationPresent(AriaInvokeHandler::class.java)
            }
            assertTrue(handlers.isNotEmpty(), "${type.name} has no handlers")
            assertEquals(
                expectedHandlers,
                handlers.mapTo(linkedSetOf()) {
                    it.getAnnotation(AriaInvokeHandler::class.java).value
                },
                type.name
            )
            handlers.forEach { method ->
                assertTrue(Modifier.isPublic(method.modifiers), method.toString())
                assertTrue(Modifier.isStatic(method.modifiers), method.toString())
                assertEquals(
                    expectedTarget,
                    method.getAnnotation(AriaInvokeHandler::class.java).target.java,
                    method.toString()
                )
            }
        }
    }
}
