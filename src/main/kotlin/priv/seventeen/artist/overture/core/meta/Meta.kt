package priv.seventeen.artist.overture.core.meta

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.overture.core.item.ItemSignal

/**
 * Meta 抽象基类
 * 定义物品元数据的生命周期方法
 */
abstract class Meta {

    /** Meta 键名 */
    abstract val key: String

    /** 是否锁定（!! 后缀）— 更新时是否强制重新应用 */
    open var locked: Boolean = false

    /** 构建优先级（值越小越先执行） */
    open val priority: Int = 0

    /**
     * NBT 构建阶段
     * 将 Meta 数据写入 ItemTag compound
     *
     * @param player 当前玩家（可能为 null）
     * @param compound overture 根 compound
     * @param sourceTag 物品完整 NBT 根节点（用于 native 等需要写入根 NBT 的 Meta）
     * @param signals 当前信号集合
     */
    open fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {}

    /**
     * ItemMeta 构建阶段
     * 将 Meta 数据写入 Bukkit ItemMeta
     */
    open fun buildMeta(itemMeta: ItemMeta) {}

    /**
     * 释放阶段
     * 在 ItemReleaseEvent 中操作 ItemStack
     */
    open fun buildRelease(itemStack: ItemStack, itemMeta: ItemMeta) {}

    /**
     * 清理阶段 — NBT
     * 当 Meta 从物品定义中移除时调用
     */
    open fun drop(player: Player?, compound: ItemTag) {}

    /**
     * 清理阶段 — NBT（带根节点）
     * 需要操作根 NBT（非 overture 子节点）的 Meta 重写此方法
     *
     * @param sourceTag 物品完整 NBT 根节点
     */
    open fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        drop(player, compound)
    }

    /**
     * 清理阶段 — ItemMeta
     */
    open fun dropMeta(itemMeta: ItemMeta) {}
}
