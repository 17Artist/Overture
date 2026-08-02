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

package priv.seventeen.artist.overture.util

/**
 * 变量解析器
 * 支持 <var> 单值变量和 <var...> 列表展开变量
 */
class VariableReader(private val start: String = "<", private val end: String = ">") {

    /**
     * 解析模板字符串为片段列表
     */
    fun parse(template: String): List<Part> {
        val parts = mutableListOf<Part>()
        var index = 0
        while (index < template.length) {
            val startIdx = template.indexOf(start, index)
            if (startIdx == -1) {
                parts.add(Part.Text(template.substring(index)))
                break
            }
            if (startIdx > index) {
                parts.add(Part.Text(template.substring(index, startIdx)))
            }
            val endIdx = template.indexOf(end, startIdx + start.length)
            if (endIdx == -1) {
                parts.add(Part.Text(template.substring(index)))
                break
            }
            val varName = template.substring(startIdx + start.length, endIdx)
            if (varName.endsWith("...")) {
                parts.add(Part.ListVariable(varName.removeSuffix("...")))
            } else {
                parts.add(Part.Variable(varName))
            }
            index = endIdx + end.length
        }
        return parts
    }

    sealed class Part {
        data class Text(val content: String) : Part()
        data class Variable(val name: String) : Part()
        data class ListVariable(val name: String) : Part()
    }
}
