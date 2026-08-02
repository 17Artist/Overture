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

package priv.seventeen.artist.overture.core.mapper

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.aria.value.StringValue
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.util.Translator

/**
 * 数据映射器
 * 将物品 NBT data 映射为展示变量
 */
object DataMapper {

    private val VARIABLE_PATTERN = Regex("\\{([^}]+)}")
    private val FUNCTION_PATTERN = Regex("^([A-Za-z0-9_.:-]+)\\((.*)\\)$")

    /**
     * 执行数据映射
     * @param mapper data-mapper 配置 Map<目标变量名, 映射表达式>
     * @param stream 物品流
     * @return 映射结果 Map<变量名, 值>
     */
    fun map(mapper: Map<String, String>, stream: ItemStream): Map<String, String> {
        if (mapper.isEmpty()) return emptyMap()

        // 展平 NBT 数据
        val flatData = Translator.flatten(stream.overtureData)
        val result = mutableMapOf<String, String>()

        for ((key, expression) in mapper) {
            result[key] = evaluate(expression, flatData)
        }

        return result
    }

    /**
     * 求值映射表达式
     */
    private fun evaluate(expression: String, data: Map<String, Any>): String {
        // 尝试简单变量引用 "{key}"
        if (expression.startsWith("{") && expression.endsWith("}") && expression.count { it == '{' } == 1) {
            val key = expression.removeSurrounding("{", "}")
            return data[key]?.toString() ?: ""
        }

        // 尝试内置函数调用
        val funcMatch = FUNCTION_PATTERN.matchEntire(expression)
        if (funcMatch != null) {
            val funcName = funcMatch.groupValues[1]
            val argsStr = funcMatch.groupValues[2]
            val args = parseArgs(argsStr, data)
            val func = MapperFunction.get(funcName)
            if (func != null) {
                return func(args)
            }
        }

        // 替换变量后尝试作为 Aria 表达式
        val textResolved = VARIABLE_PATTERN.replace(expression) { match ->
            val key = match.groupValues[1]
            data[key]?.toString() ?: "0"
        }
        val ariaResolved = VARIABLE_PATTERN.replace(expression) { match ->
            ariaVariableName(match.groupValues[1])
        }

        // 没有表达式运算特征时按普通文本处理。
        if (!EXPRESSION_MARKER.containsMatchIn(ariaResolved)) {
            return textResolved
        }

        // 作为 Aria 表达式执行
        return try {
            val ctx = Aria.createContext()
            // Aria 1.1.19 的 GlobalStorage 跨 Context 共享；数据必须绑定到 local val。
            data.forEach { (k, v) ->
                val varKey = VariableKey.of(ariaVariableName(k))
                when (v) {
                    is Number -> ctx.forceSetLocalValue(varKey, NumberValue(v.toDouble()))
                    else -> ctx.forceSetLocalValue(varKey, StringValue(v.toString()))
                }
            }
            Aria.eval("return $ariaResolved", ctx).stringValue()
        } catch (_: Exception) {
            textResolved
        }
    }

    private fun ariaVariableName(key: String): String {
        val normalized = key.replace(Regex("[^A-Za-z0-9_]"), "_")
        return "__overture_${normalized}_${key.hashCode().toUInt().toString(16)}"
    }

    /**
     * 解析函数参数
     */
    private fun parseArgs(argsStr: String, data: Map<String, Any>): List<Any> {
        if (argsStr.isBlank()) return emptyList()
        return argsStr.split(",").map { arg ->
            val trimmed = arg.trim()
            // 字符串字面量
            if ((trimmed.startsWith("'") && trimmed.endsWith("'")) ||
                (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                return@map trimmed.removeSurrounding("'").removeSurrounding("\"")
            }
            // 变量引用
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val key = trimmed.removeSurrounding("{", "}")
                return@map data[key] ?: 0
            }
            // 数字
            trimmed.toDoubleOrNull() ?: trimmed
        }
    }

    private val EXPRESSION_MARKER = Regex("[-+*/<>=!&|?:()]|\\b(if|else|true|false)\\b")
}
