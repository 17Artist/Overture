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

package priv.seventeen.artist.overture.api.reload

/**
 * 资源来源。
 */
data class ResourceOrigin(
    val providerKey: String,
    val providerId: String,
    val owner: String,
    val priority: Int
)

enum class ReloadIssueSeverity {
    WARNING,
    ERROR
}

data class ReloadIssue(
    val severity: ReloadIssueSeverity,
    val source: String,
    val message: String,
    val itemId: String? = null,
    val path: String? = null,
    val component: String? = null,
    val owner: String? = null
)

data class ReloadConflict(
    val resourceType: String,
    val resourceId: String,
    val previous: ResourceOrigin,
    val candidate: ResourceOrigin,
    val winner: ResourceOrigin?,
    val policy: String
)

data class ReloadReport(
    val success: Boolean,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val itemCount: Int,
    val modelCount: Int,
    val issues: List<ReloadIssue>,
    val conflicts: List<ReloadConflict>,
    val rolledBack: Boolean
) {
    val errorCount: Int get() = issues.count { it.severity == ReloadIssueSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ReloadIssueSeverity.WARNING }
}
