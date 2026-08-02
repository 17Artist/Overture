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

package priv.seventeen.artist.overture.core.registry

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.core.message.LanguageManager
import priv.seventeen.artist.overture.api.registry.RegistrationConflictException
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 扩展注册表。
 *
 * 同一 key 可有不同优先级的候选项，最高优先级生效；相同优先级拒绝，
 * 避免结果依赖插件加载顺序。当前项注销后会自动回退到下一候选项。
 */
internal class OwnedRegistry<T>(
    private val type: String
) : OwnerCleanupRegistry {

    data class ActiveEntry<T>(
        val key: NamespacedKey,
        val owner: Plugin,
        val priority: Int,
        val value: T
    )

    data class EntryInfo(
        val key: NamespacedKey,
        val ownerName: String,
        val priority: Int,
        val active: Boolean
    )

    private data class Entry<T>(
        val token: Long,
        val key: NamespacedKey,
        val owner: Plugin,
        val priority: Int,
        val value: T
    )

    private val entries = linkedMapOf<NamespacedKey, MutableList<Entry<T>>>()
    private val tokenSequence = AtomicLong()

    init {
        ExtensionRegistryHub.attach(this)
    }

    @Synchronized
    fun register(owner: Plugin, key: NamespacedKey, priority: Int, value: T): RegistrationHandle {
        requireOwnedNamespace(owner, key)
        val candidates = entries.getOrPut(key) { mutableListOf() }
        if (candidates.any { it.owner === owner }) {
            throw RegistrationConflictException(
                "$type 注册冲突 [$key]：${owner.name} 已注册该键，请先注销旧句柄"
            )
        }
        val samePriority = candidates.firstOrNull { it.priority == priority }
        if (samePriority != null) {
            throw RegistrationConflictException(
                "$type 注册冲突 [$key]：${owner.name} 与 ${samePriority.owner.name} 的优先级均为 $priority"
            )
        }

        val previous = candidates.maxByOrNull { it.priority }
        val entry = Entry(tokenSequence.incrementAndGet(), key, owner, priority, value)
        candidates += entry
        val active = candidates.maxByOrNull { it.priority }
        if (previous != null) {
            BlinkLog.warn(
                LanguageManager.text(
                    "console.registry-conflict",
                    "type" to type,
                    "key" to key,
                    "active" to "${active?.owner?.name}@${active?.priority}",
                    "candidates" to candidates.sortedByDescending { it.priority }
                        .joinToString { "${it.owner.name}@${it.priority}" }
                )
            )
        }
        return Handle(entry)
    }

    @Synchronized
    fun active(key: NamespacedKey): ActiveEntry<T>? {
        val entry = entries[key]?.maxByOrNull { it.priority } ?: return null
        return ActiveEntry(entry.key, entry.owner, entry.priority, entry.value)
    }

    @Synchronized
    fun activeEntries(): List<ActiveEntry<T>> =
        entries.values.mapNotNull { candidates ->
            candidates.maxByOrNull { it.priority }?.let {
                ActiveEntry(it.key, it.owner, it.priority, it.value)
            }
        }

    @Synchronized
    fun infos(): List<EntryInfo> = entries.values.flatMap { candidates ->
        val activeToken = candidates.maxByOrNull { it.priority }?.token
        candidates.map {
            EntryInfo(it.key, it.owner.name, it.priority, it.token == activeToken)
        }
    }

    @Synchronized
    override fun unregisterOwner(owner: Plugin): Int {
        var removed = 0
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (_, candidates) = iterator.next()
            val before = candidates.size
            candidates.removeAll { it.owner === owner }
            removed += before - candidates.size
            if (candidates.isEmpty()) iterator.remove()
        }
        return removed
    }

    @Synchronized
    fun clear(): Int {
        val count = entries.values.sumOf { it.size }
        entries.clear()
        return count
    }

    @Synchronized
    private fun unregister(entry: Entry<T>): Boolean {
        val candidates = entries[entry.key] ?: return false
        val removed = candidates.removeIf { it.token == entry.token }
        if (candidates.isEmpty()) entries.remove(entry.key)
        return removed
    }

    private fun requireOwnedNamespace(owner: Plugin, key: NamespacedKey) {
        val expected = NamespacedKey(owner, "ownership_probe").namespace
        require(key.namespace == expected) {
            "$type 注册键 $key 不属于 ${owner.name}；应使用 NamespacedKey(plugin, key)"
        }
    }

    private inner class Handle(
        private val entry: Entry<T>
    ) : RegistrationHandle {
        private val live = AtomicBoolean(true)

        override val key: NamespacedKey get() = entry.key
        override val owner: Plugin get() = entry.owner
        override val type: String get() = this@OwnedRegistry.type
        override val isRegistered: Boolean get() = live.get() && contains(entry)

        override fun unregister(): Boolean {
            if (!live.compareAndSet(true, false)) return false
            return unregister(entry)
        }
    }

    @Synchronized
    private fun contains(entry: Entry<T>): Boolean =
        entries[entry.key]?.any { it.token == entry.token } == true
}

internal interface OwnerCleanupRegistry {
    fun unregisterOwner(owner: Plugin): Int
}
