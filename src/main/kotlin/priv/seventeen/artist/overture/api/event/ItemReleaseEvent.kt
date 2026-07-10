package priv.seventeen.artist.overture.api.event

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.overture.core.item.ItemStream

/**
 * 物品释放事件
 * 动态 async 标记：允许在异步线程生成物品（如后台序列化入库场景），
 * 监听器如需操作世界状态请先检查 isAsynchronous
 */
object ItemReleaseEvent {

    /**
     * 物品释放为 ItemStack 时触发
     */
    class Release(
        val player: Player?,
        val stream: ItemStream,
        val itemMeta: ItemMeta
    ) : Event(!Bukkit.isPrimaryThread()) {

        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }

    /**
     * 展示方案选择事件
     */
    class SelectDisplay(
        val player: Player?,
        val stream: ItemStream,
        var displayId: String?
    ) : Event(!Bukkit.isPrimaryThread()) {

        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }

    /**
     * 展示变量生成事件（data-mapper 注入）
     */
    class Display(
        val player: Player?,
        val stream: ItemStream,
        val nameVars: MutableMap<String, String>,
        val loreVars: MutableMap<String, MutableList<String>>
    ) : Event(!Bukkit.isPrimaryThread()) {

        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }

    /**
     * 最终修改事件
     */
    class Final(
        val player: Player?,
        val stream: ItemStream,
        var itemStack: ItemStack
    ) : Event(!Bukkit.isPrimaryThread()) {

        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }
}
