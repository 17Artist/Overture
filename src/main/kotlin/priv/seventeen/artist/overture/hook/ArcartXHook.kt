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

package priv.seventeen.artist.overture.hook

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager

/**
 * ArcartX 可选依赖钩子
 *
 * 插件启动时检测一次 ArcartX 是否存在，后续所有模块通过 enabled 判断。
 * 所有对 ArcartX 类的直接引用必须放在 enabled 判断之后的方法体内，
 * 不能出现在顶层 import 或类初始化中，否则无 ArcartX 时类加载即崩溃。
 */
object ArcartXHook {

    /** ArcartX 是否已加载，lazy 确保只检测一次 */
    val enabled: Boolean by lazy {
        Bukkit.getPluginManager().isPluginEnabled("ArcartX").also { found ->
            if (found) BlinkLog.info(LanguageManager.text("console.arcartx-found"))
        }
    }
}
