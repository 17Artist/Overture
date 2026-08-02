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

package priv.seventeen.artist.overture.listener

import org.bukkit.event.EventPriority
import org.bukkit.entity.Item
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.world.ChunkLoadEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.overture.OvertureConfig
import priv.seventeen.artist.overture.core.item.ItemStream
import priv.seventeen.artist.overture.core.manager.DropLabelManager
import priv.seventeen.artist.overture.core.manager.RarityGlowManager

/**
 * 掉落物效果监听器。
 *
 * 统一处理掉落物发光和名称标签，事件驱动，无全局扫描。
 */
object ItemGlowListener {

    @AutoListener(priority = EventPriority.MONITOR)
    fun onSpawn(event: ItemSpawnEvent) {
        if (event.isCancelled) return
        val entity = event.entity
        val stream = ItemStream(entity.itemStack)
        if (!stream.isOverture) return

        if (OvertureConfig.instance.rarity.enabled) {
            RarityGlowManager.applyGlow(entity)
        }
        DropLabelManager.spawnLabel(entity)
    }

    @AutoListener(priority = EventPriority.MONITOR)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.isCancelled) return
        RarityGlowManager.removeGlow(event.item)
        DropLabelManager.removeLabel(event.item)
    }

    @AutoListener(priority = EventPriority.MONITOR)
    fun onDespawn(event: ItemDespawnEvent) {
        if (event.isCancelled) return
        RarityGlowManager.removeGlow(event.entity)
        DropLabelManager.removeLabel(event.entity)
    }

    @AutoListener(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities.filterIsInstance<Item>().forEach { item ->
            val stream = ItemStream(item.itemStack)
            if (!stream.isOverture) return@forEach

            if (OvertureConfig.instance.rarity.enabled) {
                RarityGlowManager.applyGlow(item)
            }
            DropLabelManager.spawnLabel(item)
        }
    }
}
