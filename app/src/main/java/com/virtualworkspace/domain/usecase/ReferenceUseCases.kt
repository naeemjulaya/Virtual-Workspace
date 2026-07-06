package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.StorageObject
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.WorkspaceRepository
import javax.inject.Inject

/**
 * Adiciona uma referência a um ficheiro físico numa pasta virtual (RF03).
 * Não copia nem move o ficheiro — apenas cria metadados.
 */
class AddReferenceUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val storageRepository: StorageRepository,
    private val physicalStorage: PhysicalStorageGateway
) {
    suspend operator fun invoke(
        workspaceId: Long,
        parentId: Long?,
        uri: String,
        backend: StorageBackend
    ): Result<Long> {
        val metadata = physicalStorage.queryMetadata(uri, backend)
            ?: return Result.failure(IllegalStateException("Ficheiro inacessível: $uri"))

        val storageObject = storageRepository.getOrCreate(
            uri = uri,
            backendType = backend,
            mimeType = metadata.mimeType,
            size = metadata.size,
            lastModifiedEpochMs = metadata.lastModifiedEpochMs
        )

        var name = metadata.displayName
        var counter = 1
        while (workspaceRepository.childExists(workspaceId, parentId, name)) {
            val base = metadata.displayName.substringBeforeLast('.', metadata.displayName)
            val ext = metadata.displayName.substringAfterLast('.', "")
            name = if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext"
            counter++
        }

        val parentPath = parentId?.let { workspaceRepository.getNode(it)?.virtualPath } ?: ""
        val nodeId = workspaceRepository.createNode(
            workspaceId = workspaceId,
            parentId = parentId,
            name = name,
            type = NodeType.FILE_REFERENCE,
            virtualPath = "$parentPath/$name",
            storageObjectId = storageObject.id
        )
        return Result.success(nodeId)
    }
}

/** Remove apenas a referência; o ficheiro físico permanece intacto (RF05). */
class RemoveReferenceUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val storageRepository: StorageRepository
) {
    suspend operator fun invoke(nodeId: Long): Result<Unit> {
        val node = workspaceRepository.getNode(nodeId)
            ?: return Result.failure(IllegalArgumentException("Nó não encontrado"))
        if (node.type != NodeType.FILE_REFERENCE) {
            return Result.failure(IllegalArgumentException("Nó não é uma referência"))
        }
        workspaceRepository.deleteNodePermanently(nodeId)
        // Limpar StorageObject órfão (sem outras referências)
        node.storageObjectId?.let { soId ->
            if (workspaceRepository.getNodesReferencing(soId).isEmpty()) {
                storageRepository.delete(soId)
            }
        }
        return Result.success(Unit)
    }
}

/**
 * Valida uma referência na ordem de custo definida (RNF07 / Decisão 9):
 * uri → lastModified → size → checksum (lazy).
 */
class ResolveBrokenReferenceUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
    private val physicalStorage: PhysicalStorageGateway
) {
    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data object Broken : ValidationResult
        data class Modified(val newSize: Long, val newLastModified: Long) : ValidationResult
    }

    suspend operator fun invoke(storageObject: StorageObject): ValidationResult {
        val metadata = physicalStorage.queryMetadata(storageObject.uri, storageObject.backendType)
        if (metadata == null) {
            storageRepository.updatePermissionStatus(storageObject.id, PermissionStatus.BROKEN)
            return ValidationResult.Broken
        }
        val changed = metadata.lastModifiedEpochMs != storageObject.lastModified.toEpochMilli() ||
            metadata.size != storageObject.size
        if (changed) {
            // Só agora vale a pena o checksum (caro), se já existir um para comparar
            if (storageObject.checksum != null) {
                val fresh = physicalStorage.computeChecksum(storageObject.uri, storageObject.backendType)
                if (fresh == storageObject.checksum) {
                    storageRepository.touchValidated(storageObject.id)
                    return ValidationResult.Valid
                }
            }
            storageRepository.updateMetadata(storageObject.id, metadata.size, metadata.lastModifiedEpochMs)
            return ValidationResult.Modified(metadata.size, metadata.lastModifiedEpochMs)
        }
        storageRepository.touchValidated(storageObject.id)
        return ValidationResult.Valid
    }
}
