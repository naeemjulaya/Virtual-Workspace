package com.virtualworkspace.domain.model

import java.time.Instant

data class PermissionGrant(
    val id: Long,
    val uri: String,
    val flags: Int,
    val grantedAt: Instant,
    val lastValidatedAt: Instant,
    val status: PermissionStatus
)
