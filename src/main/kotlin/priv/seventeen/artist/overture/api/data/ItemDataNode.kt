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

package priv.seventeen.artist.overture.api.data

import java.util.Collections
import java.util.LinkedHashMap
import java.math.BigDecimal

/**
 * 组件数据树。
 *
 * 所有容器都会在构造时复制输入，调用方无法通过原始集合修改已提交的数据。
 */
sealed interface ItemDataNode {
    class Compound(values: Map<String, ItemDataNode>) : ItemDataNode {
        val values: Map<String, ItemDataNode> = Collections.unmodifiableMap(LinkedHashMap(values))

        operator fun get(key: String): ItemDataNode? = values[key]

        override fun equals(other: Any?): Boolean = other is Compound && values == other.values
        override fun hashCode(): Int = values.hashCode()
        override fun toString(): String = values.toString()
    }

    class ListNode(values: List<ItemDataNode>) : ItemDataNode {
        val values: List<ItemDataNode> = Collections.unmodifiableList(ArrayList(values))

        operator fun get(index: Int): ItemDataNode = values[index]

        override fun equals(other: Any?): Boolean = other is ListNode && values == other.values
        override fun hashCode(): Int = values.hashCode()
        override fun toString(): String = values.toString()
    }

    data class Text(val value: String) : ItemDataNode

    data class Integer(val value: Long) : ItemDataNode {
        fun toIntExact(): Int = Math.toIntExact(value)
    }

    data class Decimal(val value: Double) : ItemDataNode {
        init {
            require(value.isFinite()) { "组件小数必须是有限值" }
        }

        fun toLongExact(): Long = try {
            BigDecimal.valueOf(value).longValueExact()
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("组件小数 $value 不能无损转换为 Long", error)
        }
    }

    data class Bool(val value: Boolean) : ItemDataNode
}

/** 与 Overture schema-v2 序列化边界一致的组件数据限制。 */
object ItemDataLimits {
    const val MAX_DEPTH: Int = 32
    const val MAX_NODES: Int = 16_384
    const val MAX_STRING_LENGTH: Int = 32_767
    const val MAX_LIST_LENGTH: Int = 65_536
}
