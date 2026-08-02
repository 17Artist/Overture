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

package priv.seventeen.artist.overture.core.component

import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagType
import priv.seventeen.artist.overture.core.item.ItemKey
import priv.seventeen.artist.overture.core.item.ItemStream

/** 将候选 definition 覆盖到物品，同时完整保留 namespace.instance。 */
internal object ComponentStateMerger {
    private const val SCHEMA = "schema"
    private const val DEFINITION = "definition"

    fun apply(stream: ItemStream, compilation: ComponentCompilation, updating: Boolean) {
        val root = stream.getOrCreateRoot()
        val data = if (root[ItemKey.DATA]?.type == ItemTagType.COMPOUND) {
            root.getCompound(ItemKey.DATA)
        } else {
            ItemTag()
        }

        val targets = compilation.namespaces.keys.toMutableSet()
        if (updating) {
            val activeNamespaces = ItemComponentRegistry.activeNamespaces()
            data.forEach { (namespace, value) ->
                if (namespace !in activeNamespaces || value.type != ItemTagType.COMPOUND) return@forEach
                val namespaceTag = value.asCompound()
                if (namespaceTag.containsKey(SCHEMA) || namespaceTag.containsKey(DEFINITION)) {
                    targets += namespace
                }
            }
        }

        for (namespace in targets) {
            val existing = data[namespace]
            val namespaceTag = if (existing?.type == ItemTagType.COMPOUND) {
                existing.asCompound()
            } else {
                ItemTag()
            }
            val compiled = compilation.namespaces[namespace]
            if (compiled == null) {
                namespaceTag.remove(DEFINITION)
            } else {
                namespaceTag.putInt(SCHEMA, compiled.schemaVersion)
                val definitions = ItemTag()
                compiled.definitions.forEach { (key, value) ->
                    definitions.putCompound(key, ItemDataCodec.toTag(value))
                }
                namespaceTag.putCompound(DEFINITION, definitions)
            }
            data.putCompound(namespace, namespaceTag)
        }

        root.putCompound(ItemKey.DATA, data)
        stream.sourceTag.putCompound(ItemKey.ROOT, root)
    }
}
