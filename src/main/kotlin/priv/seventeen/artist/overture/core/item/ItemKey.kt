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
 * NBT 键常量
 * 物品 NBT 中 overture 根节点下的键定义
 */
object ItemKey {

    /** 根节点名 */
    const val ROOT = "overture"

    /** 物品 ID */
    const val ID = "id"

    /** 版本签名 (SHA-1) */
    const val VERSION = "version"

    /** 活跃数据 (Compound) */
    const val DATA = "data"

    /** 唯一数据 (Compound) */
    const val UNIQUE = "unique"

    /** Meta 历史记录 (List<String>) */
    const val META_HISTORY = "meta-history"

    /** Meta 清理所需的内部状态 */
    const val META_STATE = "meta-state"

    // unique 子键
    const val UNIQUE_UUID = "uuid"
    const val UNIQUE_PLAYER = "player"
    const val UNIQUE_DATE = "date"
    const val UNIQUE_DATE_FORMATTED = "date-formatted"

    /** 获取完整路径 */
    fun path(key: String): String = "$ROOT.$key"
}
