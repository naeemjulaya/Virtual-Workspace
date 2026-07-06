package com.virtualworkspace.data.repository

import com.virtualworkspace.data.db.StorageObjectDao
import com.virtualworkspace.data.db.StorageObjectEntity
import com.virtualworkspace.data.db.toDomain
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.StorageObject
import com.virtualworkspace.domain.repository.StorageRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val dao: StorageObjectDao,
    private val clock: Clock
) : StorageRepository {

    private fun now() = clock.millis()

    override suspend fun getById(id: Long): StorageObject? = dao.getById(id)?.toDomain()

    override suspend fun getByUri(uri: String): StorageObject? = dao.getByUri(uri)?.toDomain()

    override suspend fun getOrCreate(
        uri: String,
        backendType: StorageBackend,
        mimeType: String,
        size: Long,
        lastModifiedEpochMs: Long
    ): StorageObject {
        dao.getByUri(uri)?.let { return it.toDomain() }
        val t = now()
        val id = dao.getOrInsert(
            StorageObjectEntity(
                backendType = backendType.name,
                uri = uri,
                mimeType = mimeType,
                size = size,
                lastModified = lastModifiedEpochMs,
                checksum = null,
                checksumComputedAt = null,
                permissionStatus = PermissionStatus.VALID.name,
                lastValidatedAt = t,
                createdAt = t
            )
        )
        return requireNotNull(dao.getById(id)).toDomain()
    }

    override suspend fun updateUri(id: Long, newUri: String) = dao.updateUri(id, newUri)

    override suspend fun updateUriAndBackend(id: Long, newUri: String, backend: StorageBackend) =
        dao.updateUriAndBackend(id, newUri, backend.name)

    override suspend fun updatePermissionStatus(id: Long, status: PermissionStatus) =
        dao.updatePermissionStatus(id, status.name, now())

    override suspend fun updateChecksum(id: Long, checksum: String) =
        dao.updateChecksum(id, checksum, now())

    override suspend fun updateMetadata(id: Long, size: Long, lastModifiedEpochMs: Long) =
        dao.updateMetadata(id, size, lastModifiedEpochMs, now())

    override suspend fun touchValidated(id: Long) = dao.touchValidated(id, now())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun listByBackend(backendType: StorageBackend): List<StorageObject> =
        dao.listByBackend(backendType.name).map { it.toDomain() }

    override suspend fun listByStatus(status: PermissionStatus): List<StorageObject> =
        dao.listByStatus(status.name).map { it.toDomain() }
}
