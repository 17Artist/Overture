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

package priv.seventeen.artist.overture.feature

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.core.group.ItemGroup
import priv.seventeen.artist.overture.core.item.OvertureItem
import priv.seventeen.artist.overture.core.manager.ItemManager
import priv.seventeen.artist.overture.core.manager.LoaderManager
import priv.seventeen.artist.overture.core.message.LanguageManager

/**
 * 物品菜单 GUI
 */
object ItemMenu {

    private const val ROWS = 6
    private const val SIZE = ROWS * 9
    private const val CONTENT_START = 9
    private const val CONTENT_END = 45
    private const val CONTENT_SIZE = CONTENT_END - CONTENT_START  // 36
    private const val SLOT_PREV = 45
    private const val SLOT_BACK = 49
    private const val SLOT_NEXT = 53

    /**
     * 打开物品菜单
     */
    fun open(player: Player, group: ItemGroup? = null, page: Int = 0) {
        if (!player.isOp) {
            player.sendMessage(message("command.op-only"))
            return
        }
        val entries = buildEntries(group)
        val totalPages = ((entries.size - 1).coerceAtLeast(0) / CONTENT_SIZE) + 1
        val safePage = page.coerceIn(0, totalPages - 1)

        val holder = ItemMenuHolder(group, safePage)
        val title = buildTitle(group, safePage + 1, totalPages)
        val inventory = Bukkit.createInventory(holder, SIZE, title)
        holder.attach(inventory)

        // 面包屑导航
        renderBreadcrumb(inventory, group)

        // 内容区
        val start = safePage * CONTENT_SIZE
        val end = (start + CONTENT_SIZE).coerceAtMost(entries.size)
        for (i in start until end) {
            val entry = entries[i]
            inventory.setItem(CONTENT_START + (i - start), entry.icon)
            holder.entries[CONTENT_START + (i - start)] = entry
        }

        // 功能栏
        if (safePage > 0) {
            inventory.setItem(
                SLOT_PREV,
                createNav(
                    message("menu.previous"),
                    message("menu.page", "page" to safePage + 1, "pages" to totalPages),
                    Material.ARROW
                )
            )
        }
        if (end < entries.size) {
            inventory.setItem(
                SLOT_NEXT,
                createNav(
                    message("menu.next"),
                    message("menu.page", "page" to safePage + 1, "pages" to totalPages),
                    Material.ARROW
                )
            )
        }
        if (group != null) {
            val parentName = group.parent?.title ?: word("menu.root-label")
            inventory.setItem(
                SLOT_BACK,
                createNav(
                    message("menu.back"),
                    message("menu.back-lore", "group" to parentName),
                    Material.OAK_DOOR
                )
            )
        } else {
            inventory.setItem(
                SLOT_BACK,
                createNav(message("menu.close"), message("menu.close-lore"), Material.BARRIER)
            )
        }

        player.openInventory(inventory)
    }

    @AutoListener
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? ItemMenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (!player.isOp) {
            player.closeInventory()
            player.sendMessage(message("command.op-only"))
            return
        }
        val slot = event.rawSlot
        if (slot < 0 || slot >= SIZE) return

        // 功能栏
        when (slot) {
            SLOT_PREV -> {
                if (holder.page > 0) open(player, holder.group, holder.page - 1)
                return
            }
            SLOT_NEXT -> {
                open(player, holder.group, holder.page + 1)
                return
            }
            SLOT_BACK -> {
                if (holder.group != null) {
                    open(player, holder.group.parent)
                } else {
                    player.closeInventory()
                }
                return
            }
        }

        // 面包屑
        if (holder.breadcrumb.containsKey(slot)) {
            val target = holder.breadcrumb[slot]
            open(player, target)
            return
        }

        // 内容区条目
        val entry = holder.entries[slot] ?: return
        when (entry) {
            is MenuEntry.GroupEntry -> open(player, entry.group)
            is MenuEntry.ItemEntry -> {
                if (ItemManager.give(player, entry.item.id)) {
                    player.sendMessage(message("menu.obtained", "item" to entry.item.id))
                } else {
                    player.sendMessage(message("menu.obtain-failed", "item" to entry.item.id))
                }
            }
        }
    }

    private fun message(path: String, vararg placeholders: Pair<String, Any?>): String =
        LanguageManager.text(path, *placeholders)

    private fun word(path: String): String = LanguageManager.raw(path)

    // ==================== 内部实现 ====================

    private fun buildEntries(group: ItemGroup?): List<MenuEntry> {
        val result = mutableListOf<MenuEntry>()

        // 子分组
        val subGroups = if (group != null) {
            group.getSubGroups()
        } else {
            LoaderManager.getRootGroups()
        }
        for (sub in subGroups) {
            result.add(MenuEntry.GroupEntry(sub, buildGroupIcon(sub)))
        }

        // 物品
        val items = if (group != null) {
            group.getItems(ItemManager.getItems())
        } else {
            ItemManager.getItems().values.filter { it.group == null }
        }
        for (item in items.sortedBy { it.id }) {
            result.add(MenuEntry.ItemEntry(item, buildItemIcon(item)))
        }

        return result
    }

    private fun buildGroupIcon(group: ItemGroup): ItemStack {
        val icon = ItemStack(group.icon)
        val meta = icon.itemMeta ?: return icon
        meta.setDisplayName("§f§l${group.title}")
        val lore = mutableListOf<String>()
        lore.addAll(group.description)
        if (lore.isEmpty()) {
            lore.add(message("menu.browse-group"))
        }
        lore.add("")
        val itemCount = group.getItems(ItemManager.getItems()).size
        val subCount = group.getSubGroups().size
        if (subCount > 0) lore.add(message("menu.subgroup-count", "count" to subCount))
        if (itemCount > 0) lore.add(message("menu.item-count", "count" to itemCount))
        lore.add(message("menu.enter"))
        meta.lore = lore
        icon.itemMeta = meta
        return icon
    }

    private fun buildItemIcon(item: OvertureItem): ItemStack {
        val icon = try {
            item.templateItemStack()
        } catch (_: Exception) {
            null
        } ?: ItemStack(item.material)
        val meta = icon.itemMeta ?: return icon
        val lore = (meta.lore ?: mutableListOf()).toMutableList()
        if (lore.isNotEmpty()) lore.add("")
        lore.add(message("menu.obtain"))
        lore.add("§8§o${item.id}")
        meta.lore = lore
        icon.itemMeta = meta
        return icon
    }

    private fun renderBreadcrumb(inventory: Inventory, group: ItemGroup?) {
        val holder = inventory.holder as? ItemMenuHolder ?: return

        // 根目录图标（槽位 0）
        val rootIcon = ItemStack(Material.COMPASS)
        val rootMeta = rootIcon.itemMeta
        rootMeta?.setDisplayName(message("menu.root-name"))
        rootMeta?.lore = if (group == null) {
            listOf(message("menu.current"))
        } else {
            listOf(message("menu.return-root"))
        }
        rootIcon.itemMeta = rootMeta
        inventory.setItem(0, rootIcon)
        if (group != null) holder.breadcrumb[0] = null // null 表示根

        if (group == null) return

        // 分组链（最多 7 层：槽位 1-7）
        val chain = group.getBreadcrumb()
        val maxSlots = 7
        val displayChain = if (chain.size <= maxSlots) {
            chain
        } else {
            chain.takeLast(maxSlots)
        }

        for ((index, node) in displayChain.withIndex()) {
            val slot = 1 + index
            if (slot > 7) break

            val isCurrent = node == group
            val icon = ItemStack(if (isCurrent) Material.NETHER_STAR else node.icon)
            val meta = icon.itemMeta ?: continue
            meta.setDisplayName("§f${node.title}")
            val lore = mutableListOf<String>()
            if (isCurrent) {
                lore.add(message("menu.current"))
            } else {
                lore.add(message("menu.jump"))
            }
            lore.add(message("menu.path", "path" to node.path))
            meta.lore = lore
            icon.itemMeta = meta
            inventory.setItem(slot, icon)
            if (!isCurrent) {
                holder.breadcrumb[slot] = node
            }
        }

        // 分隔装饰（槽位 8）
        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val fillerMeta = filler.itemMeta
        fillerMeta?.setDisplayName(" ")
        filler.itemMeta = fillerMeta
        inventory.setItem(8, filler)
    }

    private fun buildTitle(group: ItemGroup?, page: Int, totalPages: Int): String {
        val base = if (group == null) {
            message("menu.title.root")
        } else {
            message("menu.title.group", "group" to stripColor(group.title))
        }
        return if (totalPages > 1) {
            message("menu.title.page", "title" to base, "page" to page, "pages" to totalPages)
        } else {
            base
        }
    }

    private fun stripColor(s: String): String {
        return s.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")
    }

    private fun createNav(name: String, lore: String, material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(name)
        meta.lore = listOf(lore)
        item.itemMeta = meta
        return item
    }

    // ==================== 菜单条目 ====================

    private sealed class MenuEntry {
        abstract val icon: ItemStack

        class GroupEntry(val group: ItemGroup, override val icon: ItemStack) : MenuEntry()
        class ItemEntry(val item: OvertureItem, override val icon: ItemStack) : MenuEntry()
    }

    /**
     * 菜单状态持有器
     * 通过 InventoryHolder 机制保存当前分组、页码、条目映射，避免依赖 title 字符串解析
     */
    private class ItemMenuHolder(
        val group: ItemGroup?,
        val page: Int
    ) : InventoryHolder {
        private var inv: Inventory? = null
        /** 槽位 → 菜单条目 */
        val entries: MutableMap<Int, MenuEntry> = mutableMapOf()
        /** 槽位 → 目标分组（null 表示根） */
        val breadcrumb: MutableMap<Int, ItemGroup?> = mutableMapOf()

        fun attach(inventory: Inventory) {
            this.inv = inventory
        }

        override fun getInventory(): Inventory = inv
            ?: throw IllegalStateException("ItemMenuHolder accessed before inventory attached")
    }
}
