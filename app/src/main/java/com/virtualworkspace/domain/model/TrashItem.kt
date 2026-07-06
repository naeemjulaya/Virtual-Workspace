package com.virtualworkspace.domain.model

import java.time.Instant

data class TrashItem(
    val id: Long,
    val storageObjectId: Long?,
    val originalVirtualNodeId: Long,
    val deletedAt: Instant,
    /** deletedAt + período de retenção (30 dias por defeito). */
    val expiresAt: Instant
)
