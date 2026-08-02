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

package priv.seventeen.artist.overture.core.model

import org.bukkit.configuration.ConfigurationSection
import priv.seventeen.artist.overture.core.action.ItemAction
import priv.seventeen.artist.overture.core.action.TriggerRegistry
import priv.seventeen.artist.overture.api.action.TriggerKey
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager

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
    val actions: Map<TriggerKey, ItemAction> by lazy { loadActions() }


    private fun loadActions(): Map<TriggerKey, ItemAction> {
        val result = mutableMapOf<TriggerKey, ItemAction>()
        val eventSection = config.getConfigurationSection("event") ?: return result

        for (key in eventSection.getKeys(false)) {
            if (key == "from" || key == "data") continue
            val cleanKey = key.removeSuffix("!!")
            val cancelEvent = key.endsWith("!!")
            val trigger = TriggerRegistry.resolve(cleanKey)
            if (trigger == null) {
                BlinkLog.warn(
                    LanguageManager.text(
                        "console.model-unknown-event",
                        "model" to id,
                        "key" to cleanKey
                    )
                )
                continue
            }
            val script = eventSection.getString(key) ?: continue
            val routineName = trigger.toString().replace(':', '.')
            result[trigger] = ItemAction("$id.model.$routineName", trigger, script, cancelEvent).compile()
        }
        return result
    }


    override fun toString(): String = "ItemModel(id=$id)"
}
