package priv.seventeen.artist.overture.core.action

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog

/**
 * 物品动作定义
 * 封装一个触发器对应的 Aria 脚本
 */
class ItemAction(
    val trigger: ActionTrigger,
    val script: String,
    val cancelEvent: Boolean = false
) {
    /** 预编译的 Aria 脚本（不绑定 Context，每次执行传入新 Context） */
    var compiled: AriaCompiledRoutine? = null
        private set

    /**
     * 编译脚本（延迟到 Aria 引擎初始化之后调用）
     */
    fun compile(): ItemAction {
        compiled = try {
            Aria.compile("${trigger.key}_action", script)
        } catch (e: Exception) {
            BlinkLog.error("Aria 脚本编译失败 [${trigger.key}]: ${e.message}")
            null
        }
        return this
    }
}
