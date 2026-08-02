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

import org.bukkit.entity.Player
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemKey
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey

/**
 * 品质 Meta
 * 将品质标识写入物品 NBT，由 RarityGlowManager 负责掉落物发光效果。
 *
 * 配置格式:
 * ```yaml
 * meta:
 *   rarity: LEGENDARY
 *   # 或锁定（更新时不丢失）
 *   rarity!!: LEGENDARY
 * ```
 *
 * 品质名称对应 rarity.yml 中的 key，大小写不敏感。
 */
@MetaKey("rarity")
class MetaRarity(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "rarity"

    /** 品质标识，统一转大写与 rarity.yml 的 key 保持一致 */
    val tier: String = value?.toString()?.uppercase()?.trim() ?: ""

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        if (tier.isBlank()) return
        val dataTag = compound.getCompound(ItemKey.DATA)
        dataTag.putString("rarity", tier)
        compound.putCompound(ItemKey.DATA, dataTag)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        val dataTag = compound.getCompound(ItemKey.DATA)
        dataTag.remove("rarity")
        compound.putCompound(ItemKey.DATA, dataTag)
    }
}
