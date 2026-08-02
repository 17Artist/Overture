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

package priv.seventeen.artist.overture

import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.config.BlinkConfig
import priv.seventeen.artist.blink.config.BlinkSection
import priv.seventeen.artist.blink.config.Comment
import priv.seventeen.artist.blink.config.ConfigKey

/**
 * Overture 主配置
 */
class OvertureConfig : BlinkConfig(bukkitPlugin, "config") {

    var update: UpdateSection = UpdateSection()
    var durability: DurabilitySection = DurabilitySection()
    var rarity: RaritySection = RaritySection()

    @ConfigKey("drop-label")
    var dropLabel: DropLabelSection = DropLabelSection()

    class UpdateSection : BlinkSection() {
        @Comment("玩家加入时检查背包物品更新")
        @ConfigKey("check-on-join")
        var checkOnJoin: Boolean = true

        @Comment("拾取物品时检查更新")
        @ConfigKey("check-on-pickup")
        var checkOnPickup: Boolean = true

        @Comment("切换手持物品时检查更新")
        @ConfigKey("check-on-switch")
        var checkOnSwitch: Boolean = true

        @Comment("定时检查周期 (tick, 20 tick = 1 秒)")
        @ConfigKey("async-tick-period")
        var asyncTickPeriod: Long = 100L
    }

    class DurabilitySection : BlinkSection() {
        @Comment("耐久条显示格式, 可用变量: %symbol% %current% %max% %percent%")
        var display: String = "&8[ &f%symbol% &8]"

        @ConfigKey("display-symbol")
        var displaySymbol: DurabilitySymbolSection = DurabilitySymbolSection()

        @Comment("耐久条最大格数")
        var scale: Int = 20
    }

    class DurabilitySymbolSection : BlinkSection() {
        var full: String = "◆"
        var empty: String = "◇"
    }

    class RaritySection : BlinkSection() {
        @Comment("是否启用品质发光效果")
        var enabled: Boolean = true
    }


    class DropLabelSection : BlinkSection() {
        @Comment("是否启用掉落物名称标签")
        var enabled: Boolean = true

        @Comment("标签位置同步周期 (tick, 20 tick = 1 秒)，值越小越同步越及时，值越大性能越好")
        @ConfigKey("update-interval")
        var updateInterval: Long = 2L

        @Comment("物品未指定 drop-label 时使用的默认样式 ID，对应 drop-labels.yml")
        @ConfigKey("default-style")
        var defaultStyle: String = "default"
    }

    companion object {
        lateinit var instance: OvertureConfig
            private set

        fun load() {
            instance = OvertureConfig()
            instance.load()
        }

        fun reload() {
            instance.reload()
        }
    }
}
