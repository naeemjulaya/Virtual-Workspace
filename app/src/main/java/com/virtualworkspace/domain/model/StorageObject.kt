package com.virtualworkspace.domain.model

import java.time.Instant

data class StorageObject(
    val id: Long,
    val backendType: StorageBackend,
    val uri: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Instant,
    /** SHA-256, calculado lazy apenas quando necessário. */
    val checksum: String?,
    val checksumComputedAt: Instant?,
    val permissionStatus: PermissionStatus,
    val lastValidatedAt: Instant,
    val createdAt: Instant
)
