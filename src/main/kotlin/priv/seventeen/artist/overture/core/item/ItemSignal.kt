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

/**
 * 物品信号枚举
 * 用于在物品构建/更新流程中传递状态信息
 */
enum class ItemSignal {

    /**
     * 只读模板构建：保留 Meta/Display/Event 构建链，但禁止执行物品动作与 Behavior 副作用。
     */
    TEMPLATE,

    /**
     * 物品经过了更新检查
     * 影响 Meta build 逻辑：仅 locked Meta 在此信号下重新 build
     */
    UPDATE_CHECKED,

    /**
     * 耐久值发生变化
     * 脚本执行完毕后如果存在此信号则触发 rebuild
     */
    DURABILITY_CHANGED,

    /**
     * 物品数量或自定义数据发生变化，需要把 ItemStream 写回实际槽位/实体。
     */
    ITEM_CHANGED,

    /**
     * 物品已损坏
     * rebuildToItemStack 中检测到此信号会跳过 rebuild
     */
    DURABILITY_DESTROYED
}
