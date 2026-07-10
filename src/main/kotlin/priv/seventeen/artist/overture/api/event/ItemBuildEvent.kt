package priv.seventeen.artist.overture.api.event

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.core.item.ItemStreamGenerated

/**
 * 物品构建事件
 * 动态 async 标记：允许在异步线程生成物品（如后台序列化入库场景），
 * 监听器如需操作世界状态请先检查 isAsynchronous
 */
object ItemBuildEvent {

    /**
     * 物品构建前事件（可取消，可修改变量）
     */
    class Pre(
        val player: Player?,
        val itemId: String,
        val stream: ItemStreamGenerated
    ) : Event(!Bukkit.isPrimaryThread()), Cancellable {

        private var cancelled = false

        override fun isCancelled(): Boolean = cancelled
        override fun setCancelled(cancel: Boolean) { cancelled = cancel }
        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }

    /**
     * 物品构建后事件（不可取消）
     */
    class Post(
        val player: Player?,
        val itemId: String,
        val stream: ItemStreamGenerated
    ) : Event(!Bukkit.isPrimaryThread()) {

        override fun getHandlers(): HandlerList = handlerList

        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
    }
}
