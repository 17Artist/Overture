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

package priv.seventeen.artist.overture.core.meta

import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.asteroid.item.ItemTagList
import priv.seventeen.artist.overture.core.item.ItemKey

/**
 * 存放 Meta 更新/移除时所需的最小清理信息。
 *
 * 状态位于 overture.meta-state，不依赖旧 YAML 仍然存在。
 */
internal object MetaState {

    fun putStrings(compound: ItemTag, key: String, values: Collection<String>) {
        val state = compound.getCompound(ItemKey.META_STATE)
        val list = ItemTagList()
        values.forEach { list.add(ItemTagData.of(it)) }
        state.putList(key, list)
        compound.putCompound(ItemKey.META_STATE, state)
    }

    fun getStrings(compound: ItemTag, key: String): List<String> {
        if (!compound.containsKey(ItemKey.META_STATE)) return emptyList()
        val state = compound.getCompound(ItemKey.META_STATE)
        if (!state.containsKey(key)) return emptyList()
        val list = state.getTagList(key)
        return (0 until list.size).map { list[it].asString() }
    }

    fun remove(compound: ItemTag, key: String) {
        if (!compound.containsKey(ItemKey.META_STATE)) return
        val state = compound.getCompound(ItemKey.META_STATE)
        state.remove(key)
        if (state.isEmpty()) {
            compound.remove(ItemKey.META_STATE)
        } else {
            compound.putCompound(ItemKey.META_STATE, state)
        }
    }
}
