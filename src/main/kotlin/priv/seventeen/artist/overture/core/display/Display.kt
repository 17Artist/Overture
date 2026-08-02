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

package priv.seventeen.artist.overture.core.display

import org.bukkit.configuration.ConfigurationSection
import priv.seventeen.artist.overture.util.ColorUtil

/**
 * 展示方案
 * 定义物品名称和描述的模板结构
 */
class Display(
    /** 展示方案 ID */
    val id: String,
    /** 原始配置 */
    val config: ConfigurationSection
) {

    /** 名称模板 */
    val structureName: StructureSingle = StructureSingle(config.getString("name")?.let { ColorUtil.colored(it) })

    /** 描述模板 */
    val structureLore: StructureList = StructureList(
        config.getStringList("lore").map { ColorUtil.colored(it) }
    )

    /**
     * 构建展示产物
     * @param nameVars 名称变量
     * @param loreVars 描述变量
     */
    fun build(nameVars: Map<String, String>, loreVars: Map<String, List<String>>): DisplayProduct {
        val name = structureName.build(nameVars)
        // 创建可变副本用于展开消费
        val mutableLoreVars = loreVars.mapValues { it.value.toMutableList() }.toMutableMap()
        val lore = structureLore.build(mutableLoreVars)
        return DisplayProduct(name, lore)
    }

    override fun toString(): String = "Display(id=$id)"
}
