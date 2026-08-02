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

package priv.seventeen.artist.overture.core.resource

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 把 JAR 内的默认配置与示例写入数据目录。
 */
internal object DefaultResourceInstaller {
    internal val resourcePaths = listOf(
        "language.yml",
        "items/example_items.yml",
        "items/weapons/__group__.yml",
        "items/weapons/combat_examples.yml",
        "items/utility/__group__.yml",
        "items/utility/utility_examples.yml",
        "displays/example_display.yml",
        "displays/example_advanced.yml"
    )

    data class InstallResult(
        val copied: List<String>,
        val existing: List<String>
    )

    fun install(
        dataFolder: File,
        openResource: (String) -> InputStream?
    ): InstallResult {
        val root = dataFolder.toPath().toAbsolutePath().normalize()
        Files.createDirectories(root)

        val copied = mutableListOf<String>()
        val existing = mutableListOf<String>()
        for (resourcePath in resourcePaths) {
            val target = root.resolve(resourcePath.replace('/', File.separatorChar)).normalize()
            check(target.startsWith(root)) { "默认资源路径越界: $resourcePath" }

            if (Files.exists(target)) {
                check(Files.isRegularFile(target)) { "默认资源目标不是文件: $target" }
                existing += resourcePath
                continue
            }

            val parent = requireNotNull(target.parent)
            Files.createDirectories(parent)
            val input = openResource(resourcePath)
                ?: error("JAR 内缺少默认资源: $resourcePath")
            installOne(input, target)
            copied += resourcePath
        }
        return InstallResult(copied, existing)
    }

    private fun installOne(input: InputStream, target: Path) {
        val temporary = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
        try {
            input.use {
                Files.copy(it, temporary, StandardCopyOption.REPLACE_EXISTING)
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
