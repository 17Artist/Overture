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

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.core.meta.MetaState
import priv.seventeen.artist.overture.util.Translator

/**
 * 原生 NBT 直写 Meta
 * 将配置中的数据直接写入物品 NBT
 *
 * 配置格式:
 * ```yaml
 * native:
 *   CustomTag: "hello"
 *   nested:
 *     key: 42
 * ```
 */
@MetaKey("native")
class MetaNative(
    private val section: ConfigurationSection?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "native"

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        for (nativeKey in MetaState.getStrings(compound, key)) {
            sourceTag.remove(nativeKey)
        }
    }

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        section ?: return
        val nativeTag = Translator.toItemTag(section)
        // 直接写入物品 NBT 根节点（不在 overture 节点下）
        for ((k, v) in nativeTag) {
            sourceTag.put(k, v)
        }
        MetaState.putStrings(compound, key, nativeTag.keys)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        val keys = MetaState.getStrings(compound, key)
        for (nativeKey in keys) {
            sourceTag.remove(nativeKey)
        }
        MetaState.remove(compound, key)
    }
}
