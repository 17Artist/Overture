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

import priv.seventeen.artist.overture.util.VariableReader

/**
 * 单行模板解析器
 * 处理 <var> 变量替换
 */
class StructureSingle(template: String?) {

    private val parts: List<VariableReader.Part>? = template?.let { reader.parse(it) }

    /** 模板中使用的单值变量；列表展开语法不适用于物品名称。 */
    val variableNames: Set<String> = parts
        .orEmpty()
        .filterIsInstance<VariableReader.Part.Variable>()
        .mapTo(linkedSetOf()) { it.name }

    /**
     * 构建最终字符串
     * @param vars 变量映射 Map<变量名, 值>
     */
    fun build(vars: Map<String, String>): String? {
        val parts = this.parts ?: return null
        return parts.joinToString("") { part ->
            when (part) {
                is VariableReader.Part.Text -> part.content
                is VariableReader.Part.Variable -> vars[part.name] ?: ""
                is VariableReader.Part.ListVariable -> vars[part.name] ?: ""
            }
        }
    }

    companion object {
        private val reader = VariableReader("<", ">")
    }
}
