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
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.overture.core.item.ItemKey
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 唯一物品 Meta
 * 生成时写入 UUID、玩家名、时间戳
 *
 * 配置格式:
 * ```yaml
 * unique: true
 * ```
 */
@MetaKey("unique")
class MetaUnique(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "unique"

    val enabled: Boolean = value == true || value?.toString() == "true"

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        if (!enabled) return
        // 仅在首次生成时写入（unique 节点不存在时）
        if (compound.containsKey(ItemKey.UNIQUE)) return

        val uniqueTag = ItemTag()
        uniqueTag.putString(ItemKey.UNIQUE_UUID, UUID.randomUUID().toString())
        uniqueTag.putString(ItemKey.UNIQUE_PLAYER, player?.name ?: "Unknown")
        uniqueTag.putLong(ItemKey.UNIQUE_DATE, System.currentTimeMillis())
        uniqueTag.putString(ItemKey.UNIQUE_DATE_FORMATTED, DATE_FORMAT.format(LocalDateTime.now()))
        compound.putCompound(ItemKey.UNIQUE, uniqueTag)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        compound.remove(ItemKey.UNIQUE)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
