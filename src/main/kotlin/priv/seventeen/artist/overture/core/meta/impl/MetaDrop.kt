package priv.seventeen.artist.overture.core.meta.impl

import org.bukkit.entity.Player
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta
import priv.seventeen.artist.overture.core.meta.MetaKey
import priv.seventeen.artist.overture.hook.ArcartXHook

/**
 * 掉落物附加模型 Meta（需要 ArcartX）
 *
 * 为物品指定在地面掉落时显示的附加 3D 模型路径，
 * 由 ArcartX 客户端渲染，服务端无 ArcartX 时此 Meta 静默无效。
 *
 * drop tag 写在物品根 NBT（非 overture 子节点），与 ArcartX 约定一致。
 *
 * 配置格式:
 * ```yaml
 * meta:
 *   drop!!: "weapons/legendary_blade"
 * ```
 */
@MetaKey("drop")
class MetaDrop(
    private val value: Any?,
    override var locked: Boolean = false
) : Meta() {

    override val key: String = "drop"

    /** 模型路径，为空则不生效 */
    val path: String = value?.toString()?.trim() ?: ""

    override fun build(player: Player?, compound: ItemTag, sourceTag: ItemTag, signals: Set<ItemSignal>) {
        if (path.isBlank()) return
        if (!ArcartXHook.enabled) return
        // drop tag 写入根 NBT，与 ArcartX /ax item setDrop 行为完全一致
        sourceTag["drop"] = ItemTagData.of(path)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        sourceTag.remove("drop")
    }
}
