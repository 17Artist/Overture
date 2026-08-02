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

package priv.seventeen.artist.overture.listener

import priv.seventeen.artist.overture.core.item.ItemSignal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayRefreshPolicyTest {
    @Test
    fun `ordinary interaction rebuild overwrites unlocked display`() {
        assertTrue(ItemBuilder.shouldOverwriteDisplay(emptySet(), locked = false))
        assertTrue(ItemBuilder.shouldOverwriteDisplay(setOf(ItemSignal.ITEM_CHANGED), locked = false))
    }

    @Test
    fun `version update preserves only unlocked display`() {
        val update = setOf(ItemSignal.UPDATE_CHECKED)
        assertFalse(ItemBuilder.shouldOverwriteDisplay(update, locked = false))
        assertTrue(ItemBuilder.shouldOverwriteDisplay(update, locked = true))
    }
}