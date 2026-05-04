package priv.seventeen.artist.overture.core.action

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.context.Context
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.aria.interop.JavaObjectMirror
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.MapValue
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.aria.value.ObjectValue
import priv.seventeen.artist.aria.value.StringValue
import priv.seventeen.artist.aria.value.BooleanValue
import priv.seventeen.artist.aria.value.NoneValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.item.ItemStream

/**
 * 动作执行器
 * 负责创建 Aria 上下文并执行物品脚本
 */
object ActionExecutor {

    /**
     * 执行物品动作
     *
     * @param action 物品动作
     * @param player 当前玩家
     * @param stream 物品流
     * @param event 原始 Bukkit 事件
     * @param eventVars 事件变量
     * @return 是否成功执行
     */
    fun execute(
        action: ItemAction,
        player: Player,
        stream: ItemStream,
        event: Event? = null,
        eventVars: Map<String, Any> = emptyMap()
    ): Boolean {
//        BlinkLog.detail("[ActionExecutor] trigger=${action.trigger.key}, compiled=${action.compiled != null}, cancelEvent=${action.cancelEvent}")
        val compiled = action.compiled ?: run {
//            BlinkLog.warn("[ActionExecutor] compiled 为 null, 脚本: ${action.script.take(50)}...")
            return false
        }

        // 自动取消事件
        if (action.cancelEvent && event is Cancellable) {
            event.isCancelled = true
        }

        return try {
            val ctx = createContext(player, stream, event, eventVars)
            compiled.execute(ctx)
            true
        } catch (e: Exception) {
            BlinkLog.warn("执行物品脚本失败 [${action.trigger.key}]: ${e.message}")
            false
        }
    }

    /**
     * 执行 Aria 表达式并返回布尔值
     */
    fun evaluateCondition(
        expression: String,
        player: Player?,
        stream: ItemStream
    ): Boolean {
        return try {
            val ctx = createContext(player, stream, null, emptyMap())
            val result = Aria.eval("return $expression", ctx)
            result?.booleanValue() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 创建 Aria 执行上下文
     */
    private fun createContext(
        player: Player?,
        stream: ItemStream,
        event: Event?,
        eventVars: Map<String, Any>
    ): Context {
        val ctx = Aria.createContext()
        val storage = ctx.globalStorage

        // 注入 player
        if (player != null) {
            storage.getGlobalVariable(VariableKey.of("player"))
                .setValue(ObjectValue(JavaObjectMirror(player)))
        }

        // 注入 item stream
        storage.getGlobalVariable(VariableKey.of("item"))
            .setValue(ObjectValue(JavaObjectMirror(stream)))

        // 注入 event
        if (event != null) {
            storage.getGlobalVariable(VariableKey.of("event"))
                .setValue(ObjectValue(JavaObjectMirror(event)))
        }

        // 注入 event vars
        if (eventVars.isNotEmpty()) {
            val varsMap = MapValue()
            eventVars.forEach { (k, v) ->
                varsMap.jvmValue().put(StringValue(k), toAriaValue(v))
            }
            storage.getGlobalVariable(VariableKey.of("vars"))
                .setValue(varsMap)
        }

        return ctx
    }

    private fun toAriaValue(value: Any): IValue<*> {
        return when (value) {
            is Number -> NumberValue(value.toDouble())
            is String -> StringValue(value)
            is Boolean -> BooleanValue.of(value)
            else -> StringValue(value.toString())
        }
    }
}
