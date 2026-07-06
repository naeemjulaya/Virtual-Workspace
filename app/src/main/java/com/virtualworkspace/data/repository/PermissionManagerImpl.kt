package com.virtualworkspace.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.virtualworkspace.data.db.PermissionGrantDao
import com.virtualworkspace.data.db.PermissionGrantEntity
import com.virtualworkspace.data.db.StorageObjectDao
import com.virtualworkspace.data.db.toDomain
import com.virtualworkspace.domain.model.PermissionGrant
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.repository.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionGrantDao: PermissionGrantDao,
    private val storageObjectDao: StorageObjectDao,
    private val clock: Clock
) : PermissionManager {

    override fun observeGrants(): Flow<List<PermissionGrant>> =
        permissionGrantDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun recordGrant(uri: String, flags: Int) {
        val t = clock.millis()
        val existing = permissionGrantDao.getByUri(uri)
        if (existing != null) {
            permissionGrantDao.update(
                existing.copy(flags = flags, lastValidatedAt = t, status = PermissionStatus.VALID.name)
            )
        } else {
            permissionGrantDao.insert(
                PermissionGrantEntity(
                    uri = uri,
                    flags = flags,
                    grantedAt = t,
                    lastValidatedAt = t,
                    status = PermissionStatus.VALID.name
                )
            )
        }
    }

    override suspend fun revokeGrant(uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        permissionGrantDao.deleteByUri(uri)
    }

    override suspend fun isUriAccessible(uri: String): Boolean = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val persisted = context.contentResolver.persistedUriPermissions
            .any { it.uri == parsed && it.isReadPermission }
        if (!persisted) return@withContext false
        runCatching {
            DocumentFile.fromTreeUri(context, parsed)?.exists() == true
        }.getOrDefault(false)
    }

    override suspend fun validateAll(): List<PermissionGrant> = withContext(Dispatchers.IO) {
        val t = clock.millis()
        val expired = mutableListOf<PermissionGrant>()
        for (grant in permissionGrantDao.listValid()) {
            if (isUriAccessible(grant.uri)) {
                permissionGrantDao.update(grant.copy(lastValidatedAt = t))
            } else {
                val updated = grant.copy(status = PermissionStatus.EXPIRED.name, lastValidatedAt = t)
                permissionGrantDao.update(updated)
                expired += updated.toDomain()
            }
        }
        if (expired.isNotEmpty()) {
            storageObjectDao.listByBackend(com.virtualworkspace.domain.model.StorageBackend.SAF_TREE.name)
                .forEach { storage ->
                    val accessible = runCatching {
                        context.contentResolver.openFileDescriptor(Uri.parse(storage.uri), "r")?.use { true } ?: false
                    }.getOrDefault(false)
                    storageObjectDao.updatePermissionStatus(
                        storage.id,
                        if (accessible) PermissionStatus.VALID.name else PermissionStatus.BROKEN.name,
                        t
                    )
                }
        }
        expired
    }
}
