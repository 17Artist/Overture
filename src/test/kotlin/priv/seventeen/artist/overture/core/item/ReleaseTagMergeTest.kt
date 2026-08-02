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

import priv.seventeen.artist.asteroid.item.ItemTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseTagMergeTest {
    @Test
    fun `release merge preserves fresh Bukkit roots and replaces only overture state`() {
        val release = ItemTag().apply {
            putCompound("display", ItemTag().apply { putString("Name", "old") })
            putString("AttributeModifiers", "old-attributes")
            putCompound(ItemKey.ROOT, ItemTag().apply {
                putString(ItemKey.ID, "example")
                putCompound(ItemKey.DATA, ItemTag().apply { putInt("uses", 1) })
            })
        }
        val fresh = ItemTag().apply {
            putCompound("display", ItemTag().apply { putString("Name", "new") })
            putString("AttributeModifiers", "new-attributes")
            putString("native-key", "kept")
            putCompound(ItemKey.ROOT, ItemTag().apply { putString("stale", "value") })
        }

        mergeOvertureStateAfterRelease(fresh, release)

        assertEquals("new", fresh.getCompound("display").getString("Name"))
        assertEquals("new-attributes", fresh.getString("AttributeModifiers"))
        assertEquals("kept", fresh.getString("native-key"))
        assertEquals("example", fresh.getCompound(ItemKey.ROOT).getString(ItemKey.ID))
        assertEquals(1, fresh.getCompound(ItemKey.ROOT).getCompound(ItemKey.DATA).getInt("uses"))
        assertTrue("stale" !in fresh.getCompound(ItemKey.ROOT))
    }
}