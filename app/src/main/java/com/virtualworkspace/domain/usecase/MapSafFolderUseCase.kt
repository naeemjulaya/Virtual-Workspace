package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.repository.PermissionManager
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.WorkspaceRepository
import javax.inject.Inject

/**
 * Mapeia uma pasta física via SAF (RF09): regista a permissão persistente,
 * cria uma pasta virtual com o nome da pasta física e uma referência para
 * cada ficheiro encontrado (não recursivo no MVP).
 *
 * Pré-condição: a UI já chamou takePersistableUriPermission no URI.
 */
class MapSafFolderUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val storageRepository: StorageRepository,
    private val permissionManager: PermissionManager,
    private val physicalStorage: PhysicalStorageGateway
) {
    suspend operator fun invoke(
        workspaceId: Long,
        parentId: Long?,
        treeUri: String,
        grantFlags: Int,
        folderDisplayName: String
    ): Result<Long> {
        var createdFolderId: Long? = null
        return runCatching {
            permissionManager.recordGrant(treeUri, grantFlags)

        val baseFolderName = folderDisplayName.trim().ifBlank { "Pasta mapeada" }
        var folderName = baseFolderName
        var counter = 1
        while (workspaceRepository.childExists(workspaceId, parentId, folderName)) {
            folderName = "$baseFolderName ($counter)"
            counter++
        }

        val parentPath = parentId?.let { workspaceRepository.getNode(it)?.virtualPath } ?: ""
        val folderPath = "$parentPath/$folderName"
        val folderId = workspaceRepository.createNode(
            workspaceId = workspaceId,
            parentId = parentId,
            name = folderName,
            type = NodeType.FOLDER,
            virtualPath = folderPath
        )
        createdFolderId = folderId

        for (file in physicalStorage.listSafTreeChildren(treeUri)) {
            val storageObject = storageRepository.getOrCreate(
                uri = file.uri,
                backendType = StorageBackend.SAF_TREE,
                mimeType = file.mimeType,
                size = file.size,
                lastModifiedEpochMs = file.lastModifiedEpochMs
            )
            workspaceRepository.createNode(
                workspaceId = workspaceId,
                parentId = folderId,
                name = file.displayName,
                type = NodeType.FILE_REFERENCE,
                virtualPath = "$folderPath/${file.displayName}",
                storageObjectId = storageObject.id
            )
        }
        folderId
        }.onFailure {
            createdFolderId?.let { folderId ->
                runCatching { workspaceRepository.deleteNodePermanently(folderId) }
                runCatching { workspaceRepository.cleanOrphanStorage() }
            }
        }
    }
}
