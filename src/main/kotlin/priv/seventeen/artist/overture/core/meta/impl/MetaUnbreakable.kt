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

import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey

/**
 * 不可破坏 Meta
 *
 * 配置格式:
 * ```yaml
 * unbreakable: true
 * ```
 */
@MetaKey("unbreakable")
class MetaUnbreakable(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "unbreakable"

    val enabled: Boolean = value == true || value?.toString() == "true"

    override fun buildMeta(itemMeta: ItemMeta) {
        if (enabled) {
            itemMeta.isUnbreakable = true
        }
    }

    override fun dropMeta(itemMeta: ItemMeta) {
        itemMeta.isUnbreakable = false
    }
}
