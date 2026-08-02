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

import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.manager.ItemManager

/**
 * 统一解析物品自身与事件模型中的动作。
 */
object ActionResolver {
    data class ResolvedAction(
        val action: ItemAction,
        val origin: String
    )

    fun resolve(item: OvertureItem, trigger: TriggerKey): ResolvedAction? {
        item.actions[trigger]?.let {
            return ResolvedAction(it, "item:${item.id}")
        }

        // 后声明的模型覆盖先声明的模型。
        for (modelId in item.modelIds.asReversed()) {
            val model = ItemManager.getModel(modelId) ?: continue
            model.actions[trigger]?.let {
                return ResolvedAction(it, "model:$modelId")
            }
        }
        return null
    }
}
