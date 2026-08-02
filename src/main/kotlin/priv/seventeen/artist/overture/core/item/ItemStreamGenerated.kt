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

import org.bukkit.inventory.ItemStack

/**
 * 首次生成的物品流
 * 携带名称和描述变量映射，用于 Display 构建
 */
class ItemStreamGenerated(
    sourceItem: ItemStack,
    /** 名称变量 Map<变量名, 值> */
    val nameVars: MutableMap<String, String>,
    /** 描述变量 Map<变量名, 值列表> */
    val loreVars: MutableMap<String, MutableList<String>>,
    /** 是否由已有物品更新而来 */
    val updating: Boolean = false
) : ItemStream(sourceItem) {

    /** ItemBuildEvent.Pre 是否取消了本次构建。 */
    var buildCancelled: Boolean = false
        internal set

    /** 本次构建中各第三方渲染条目的耗时（纳秒）。 */
    val renderTimings: MutableMap<String, Long> = linkedMapOf()

    /**
     * 添加名称变量
     */
    fun addName(key: String, value: String) {
        nameVars[key] = value
    }

    /**
     * 添加描述变量（单值）
     */
    fun addLore(key: String, value: String) {
        loreVars.getOrPut(key) { mutableListOf() }.also {
            it.clear()
            it.add(value)
        }
    }

    /**
     * 添加描述变量（多值）
     */
    fun addLore(key: String, values: List<String>) {
        loreVars[key] = values.toMutableList()
    }

    /**
     * 同时添加到名称和描述变量
     */
    fun addVariable(key: String, value: String) {
        addName(key, value)
        addLore(key, value)
    }

    /**
     * 同时添加到名称和描述变量（多值）
     */
    fun addVariable(key: String, values: List<String>) {
        addName(key, values.firstOrNull() ?: "")
        addLore(key, values)
    }
}
