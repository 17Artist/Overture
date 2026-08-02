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
 * 多行模板解析器
 * 处理 <var> 单值替换和 <var...> 列表展开
 */
class StructureList(templates: List<String>) {

    private val parsedLines: List<List<VariableReader.Part>> = templates.map { reader.parse(it) }

    /** 模板中使用的列表展开变量。 */
    val listVariableNames: Set<String> = parsedLines
        .flatten()
        .filterIsInstance<VariableReader.Part.ListVariable>()
        .mapTo(linkedSetOf()) { it.name }

    /**
     * 构建最终描述列表
     * @param vars 变量映射 Map<变量名, 值列表>（内部会消费列表展开变量）
     */
    fun build(vars: MutableMap<String, MutableList<String>>): List<String> {
        val result = mutableListOf<String>()
        // 使用可变队列处理模板行
        val queue = ArrayDeque(parsedLines)

        while (queue.isNotEmpty()) {
            val line = queue.first()
            var skip = false   // true = 当前行还有展开变量未消费完，不移除
            var pass = false   // true = 展开变量为空，跳过整行

            val built = StringBuilder()

            for (part in line) {
                when (part) {
                    is VariableReader.Part.Text -> {
                        built.append(part.content)
                    }
                    is VariableReader.Part.Variable -> {
                        // 普通变量: 取第一个值
                        val values = vars[part.name]
                        built.append(values?.firstOrNull() ?: "")
                    }
                    is VariableReader.Part.ListVariable -> {
                        // 展开变量
                        val values = vars[part.name]
                        if (values.isNullOrEmpty()) {
                            // 空列表 → 跳过整行
                            pass = true
                        } else {
                            // 消费第一个值
                            built.append(values.first())
                            if (values.size > 1) {
                                // 还有剩余，不移除当前模板行
                                skip = true
                                // 移除已消费的值
                                values.removeFirst()
                            } else {
                                // 最后一个值，清空列表
                                values.clear()
                            }
                        }
                    }
                }
                if (pass) break
            }

            if (!skip) {
                queue.removeFirst()
            }
            if (!pass) {
                result.add(built.toString())
            }
        }

        return result
    }

    companion object {
        private val reader = VariableReader("<", ">")
    }
}
