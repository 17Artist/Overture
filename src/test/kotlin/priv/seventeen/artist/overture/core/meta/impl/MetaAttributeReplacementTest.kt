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

package priv.seventeen.artist.overture.core.meta.impl

import com.google.common.collect.ArrayListMultimap
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.ItemMeta
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaAttributeReplacementTest {
    @Test
    fun `owned modifier is removed without touching foreign modifier`() {
        val owned = AttributeModifier(
            UUID.randomUUID(),
            "overture:generic_attack_damage_hand_0",
            7.0,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        )
        val foreign = AttributeModifier(
            UUID.randomUUID(),
            "foreign:damage",
            2.0,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
        )
        val modifiers = ArrayListMultimap.create<Attribute, AttributeModifier>()
        modifiers.put(Attribute.GENERIC_ATTACK_DAMAGE, owned)
        modifiers.put(Attribute.GENERIC_ATTACK_DAMAGE, foreign)
        val meta = itemMeta(modifiers)

        val removed = MetaAttribute(null).removeOwnedModifier(meta, owned.name)

        assertEquals(1, removed)
        assertEquals(listOf(foreign), modifiers.get(Attribute.GENERIC_ATTACK_DAMAGE).toList())
    }

    private fun itemMeta(
        modifiers: ArrayListMultimap<Attribute, AttributeModifier>
    ): ItemMeta = Proxy.newProxyInstance(
        ItemMeta::class.java.classLoader,
        arrayOf(ItemMeta::class.java)
    ) { proxy, method, args ->
        when {
            method.name == "getAttributeModifiers" && method.parameterCount == 0 -> modifiers
            method.name == "removeAttributeModifier" && method.parameterCount == 2 ->
                modifiers.remove(args!![0] as Attribute, args[1] as AttributeModifier)
            method.name == "toString" -> "TestItemMeta"
            method.name == "hashCode" -> System.identityHashCode(proxy)
            method.name == "equals" -> proxy === args?.get(0)
            else -> throw UnsupportedOperationException(method.toString())
        }
    } as ItemMeta
}