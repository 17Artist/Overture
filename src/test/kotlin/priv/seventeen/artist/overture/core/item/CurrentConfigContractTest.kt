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

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import priv.seventeen.artist.overture.core.manager.TextDisplayStyle
import priv.seventeen.artist.overture.core.meta.MetaRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentConfigContractTest {
    @Test
    fun `icon lock is accepted on key only`() {
        val current = item(
            """
            current:
              icon!!: DIAMOND_SWORD
            """
        )
        assertEquals(Material.DIAMOND_SWORD, current.material)
        assertTrue(current.iconLocked)

        val obsolete = item(
            """
            obsolete:
              icon: DIAMOND_SWORD!!
            """
        )
        assertEquals(Material.STONE, obsolete.material)
        assertFalse(obsolete.iconLocked)
    }

    @Test
    fun `obsolete background enabled does not select custom mode`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString(
            """
            style:
              background:
                enabled: true
                argb: "255,1,2,3"
            """.trimIndent()
        )
        val style = TextDisplayStyle.fromSection(yaml.getConfigurationSection("style"))
        assertEquals("TRANSPARENT", style.backgroundMode)
    }

    @Test
    fun `unnamespaced custom meta key is not resolved`() {
        assertNull(MetaRegistry.create("third_party_meta", null, null, false))
    }

    @Test
    fun `default armor root locks load all data and meta entries`() {
        val yaml = YamlConfiguration()
        yaml.load(File("src/main/resources/items/example_items.yml"))
        val armor = OvertureItem(
            "example_armor",
            requireNotNull(yaml.getConfigurationSection("example_armor"))
        )

        assertEquals(Material.LEATHER_CHESTPLATE, armor.material)
        assertEquals(12, armor.dataResult?.tag?.getInt("level"))
        assertEquals(8, armor.dataResult?.tag?.getInt("armor"))
        assertTrue(armor.dataResult?.tag?.getBoolean("bound") == true)
        assertEquals(setOf("level", "armor", "bound"), armor.lockedData.keys)
        assertTrue(armor.metaList.size >= 8)
        assertTrue(armor.metaList.all { it.locked })
        assertTrue(armor.metaList.any { it.key == "attribute" })
        assertTrue(armor.metaList.any { it.key == "shiny" })
        assertTrue(armor.metaList.any { it.key == "native" })
    }
    private fun item(source: String): OvertureItem {
        val yaml = YamlConfiguration()
        yaml.loadFromString(source.trimIndent())
        val id = yaml.getKeys(false).single()
        return OvertureItem(id, requireNotNull(yaml.getConfigurationSection(id)))
    }
}
