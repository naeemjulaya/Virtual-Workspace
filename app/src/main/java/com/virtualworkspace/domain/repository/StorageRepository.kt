package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.StorageObject

interface StorageRepository {
    suspend fun getById(id: Long): StorageObject?
    suspend fun getByUri(uri: String): StorageObject?

    /** Cria (ou devolve existente) um StorageObject para o URI dado. */
    suspend fun getOrCreate(
        uri: String,
        backendType: StorageBackend,
        mimeType: String,
        size: Long,
        lastModifiedEpochMs: Long
    ): StorageObject

    suspend fun updateUri(id: Long, newUri: String)
    suspend fun updateUriAndBackend(id: Long, newUri: String, backend: StorageBackend)
    suspend fun updatePermissionStatus(id: Long, status: PermissionStatus)
    suspend fun updateChecksum(id: Long, checksum: String)
    suspend fun updateMetadata(id: Long, size: Long, lastModifiedEpochMs: Long)
    suspend fun touchValidated(id: Long)
    suspend fun delete(id: Long)
    suspend fun listByBackend(backendType: StorageBackend): List<StorageObject>
    suspend fun listByStatus(status: PermissionStatus): List<StorageObject>
}
