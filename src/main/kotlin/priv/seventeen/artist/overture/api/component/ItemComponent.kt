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

package priv.seventeen.artist.overture.api.component

import org.bukkit.NamespacedKey
import priv.seventeen.artist.overture.api.data.ItemDataNode

/** 将一个 namespaced 组件配置编译为稳定定义数据。 */
interface ItemComponentCodec {
    /** 同一 namespace 下所有 active codec 必须使用相同正整数版本。 */
    val schemaVersion: Int

    fun decode(context: ComponentDecodeContext): ComponentDecodeResult
}

/**
 * 组件解码上下文。[source] 是不可变快照，不暴露 Bukkit YAML 类型。
 */
data class ComponentDecodeContext(
    val componentKey: NamespacedKey,
    val itemId: String,
    val source: ItemDataNode.Compound,
    val sourcePath: String
)

/** codec 返回的可定位问题；[path] 相对 [ComponentDecodeContext.sourcePath]。 */
data class ComponentIssue(
    val path: String = "",
    val message: String
)

sealed interface ComponentDecodeResult {
    data class Success(
        val definition: ItemDataNode.Compound,
        val warnings: List<ComponentIssue> = emptyList()
    ) : ComponentDecodeResult

    data class Failure(val issues: List<ComponentIssue>) : ComponentDecodeResult
}
