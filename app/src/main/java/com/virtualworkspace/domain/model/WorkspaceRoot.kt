package com.virtualworkspace.domain.model

import java.time.Instant

data class WorkspaceRoot(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
