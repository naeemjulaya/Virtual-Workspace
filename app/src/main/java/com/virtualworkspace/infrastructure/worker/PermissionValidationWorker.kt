package com.virtualworkspace.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.virtualworkspace.domain.repository.PermissionManager
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.infrastructure.notification.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Validação diária de permissões SAF (Fluxo 9, RF13). */
@HiltWorker
class PermissionValidationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val permissionManager: PermissionManager,
    private val storageRepository: StorageRepository,
    private val physicalStorage: PhysicalStorageGateway,
    private val notifier: AppNotifier
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "permission_validation"
    }

    override suspend fun doWork(): Result {
        val expired = permissionManager.validateAll()
        storageRepository.listByBackend(StorageBackend.SAF_TREE).forEach { storage ->
            val metadata = physicalStorage.queryMetadata(storage.uri, storage.backendType)
            if (metadata == null) {
                storageRepository.updatePermissionStatus(storage.id, PermissionStatus.BROKEN)
            } else if (metadata.size != storage.size ||
                metadata.lastModifiedEpochMs != storage.lastModified.toEpochMilli()) {
                storageRepository.updateMetadata(storage.id, metadata.size, metadata.lastModifiedEpochMs)
            } else {
                storageRepository.updatePermissionStatus(storage.id, PermissionStatus.VALID)
            }
        }
        if (expired.isNotEmpty()) {
            notifier.notifyAlert(
                AppNotifier.NOTIFICATION_PERMISSION_EXPIRED,
                "Permissões expiradas",
                "${expired.size} pasta(s) mapeada(s) já não estão acessíveis. Renove em Definições."
            )
        }
        return Result.success()
    }
}
