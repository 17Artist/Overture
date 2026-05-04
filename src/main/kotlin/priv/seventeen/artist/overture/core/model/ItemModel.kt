package priv.seventeen.artist.overture.core.model

import org.bukkit.configuration.ConfigurationSection
import priv.seventeen.artist.overture.core.action.ActionTrigger
import priv.seventeen.artist.overture.core.action.ItemAction

/**
 * 事件模型
 * 通过 $ 后缀定义，可被多个物品引用复用事件脚本
 */
class ItemModel(
    /** 模型 ID（去掉 $ 后缀） */
    val id: String,
    /** 原始配置 */
    val config: ConfigurationSection
) {

    /** 模型中定义的动作 */
    val actions: Map<ActionTrigger, ItemAction> by lazy { loadActions() }

    /** 事件变量 */
    val eventVars: Map<String, Any> by lazy { loadEventVars() }

    private fun loadActions(): Map<ActionTrigger, ItemAction> {
        val result = mutableMapOf<ActionTrigger, ItemAction>()
        val eventSection = config.getConfigurationSection("event") ?: return result

        for (key in eventSection.getKeys(false)) {
            if (key == "from" || key == "data") continue
            val cleanKey = key.removeSuffix("!!")
            val cancelEvent = key.endsWith("!!")
            val trigger = ActionTrigger.fromKey(cleanKey) ?: continue
            val script = eventSection.getString(key) ?: continue
            result[trigger] = ItemAction(trigger, script, cancelEvent).compile()
        }
        return result
    }

    private fun loadEventVars(): Map<String, Any> {
        val section = config.getConfigurationSection("event.data") ?: return emptyMap()
        val result = mutableMapOf<String, Any>()
        section.getKeys(true).forEach { key ->
            val value = section.get(key)
            if (value != null && value !is ConfigurationSection) {
                result[key] = value
            }
        }
        return result
    }

    override fun toString(): String = "ItemModel(id=$id)"
}
