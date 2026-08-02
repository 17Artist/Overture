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

package priv.seventeen.artist.overture.util

import java.security.MessageDigest

/**
 * SHA-1 版本签名工具
 */
object HashUtil {

    private val digest = ThreadLocal.withInitial {
        MessageDigest.getInstance("SHA-1")
    }

    /**
     * 计算字符串的 SHA-1 哈希
     */
    fun sha1(input: String): String {
        val md = digest.get()
        md.reset()
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算多个字符串拼接后的 SHA-1
     */
    fun sha1(vararg inputs: String): String {
        return sha1(inputs.joinToString("|"))
    }
}
