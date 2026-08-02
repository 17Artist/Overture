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

package priv.seventeen.artist.overture.core.group

import org.bukkit.Material
import priv.seventeen.artist.overture.core.item.OvertureItem

/**
 * 物品分组
 * 支持层级结构，文件夹即分组
 */
class ItemGroup(
    /** 分组名称（文件夹名） */
    val name: String,
    /** 父分组 */
    val parent: ItemGroup? = null,
    /** 层级深度 */
    val level: Int = 0,
    /** 排序优先级（越小越靠前） */
    val priority: Int = 0,
    /** 分组图标 */
    val icon: Material = Material.CHEST,
    /** 自定义显示名（null 时使用 name） */
    val displayName: String? = null,
    /** 分组描述（GUI lore） */
    val description: List<String> = emptyList()
) {

    /** 子分组 */
    val children: MutableList<ItemGroup> = mutableListOf()

    /** 分组路径 */
    val path: String
        get() = if (parent != null) "${parent.path}/$name" else name

    /** 展示名（优先 displayName，否则 name） */
    val title: String
        get() = displayName ?: name

    /**
     * 获取该分组下的所有物品
     */
    fun getItems(allItems: Map<String, OvertureItem>): List<OvertureItem> {
        return allItems.values.filter { it.group == this }
    }

    /**
     * 获取直接子分组
     */
    fun getSubGroups(): List<ItemGroup> {
        return children.sortedBy { it.priority }
    }

    /**
     * 获取从根到当前分组的路径链
     */
    fun getBreadcrumb(): List<ItemGroup> {
        val chain = mutableListOf<ItemGroup>()
        var current: ItemGroup? = this
        while (current != null) {
            chain.add(0, current)
            current = current.parent
        }
        return chain
    }

    override fun toString(): String = "ItemGroup(path=$path)"
    override fun equals(other: Any?): Boolean = other is ItemGroup && path == other.path
    override fun hashCode(): Int = path.hashCode()

    companion object {
        /** 无分组（根级别物品） */
        val NO_GROUP = ItemGroup("", null, 0)
    }
}
