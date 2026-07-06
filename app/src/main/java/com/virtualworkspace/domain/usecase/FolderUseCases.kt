package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Navegação: filhos diretos de uma pasta virtual (lazy loading, RF01). */
class NavigateWorkspaceUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    operator fun invoke(workspaceId: Long, parentId: Long?): Flow<List<NodeWithStorage>> =
        workspaceRepository.observeChildren(workspaceId, parentId)
}

/** Criação de pasta virtual — instantâneo, apenas metadados (RF02). */
class CreateFolderUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    suspend operator fun invoke(workspaceId: Long, parentId: Long?, name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Nome vazio"))
        if (trimmed.contains('/')) return Result.failure(IllegalArgumentException("Nome não pode conter '/'"))
        if (workspaceRepository.childExists(workspaceId, parentId, trimmed)) {
            return Result.failure(IllegalArgumentException("Já existe um item com esse nome nesta pasta"))
        }
        val parentPath = parentId?.let { workspaceRepository.getNode(it)?.virtualPath } ?: ""
        val id = workspaceRepository.createNode(
            workspaceId = workspaceId,
            parentId = parentId,
            name = trimmed,
            type = NodeType.FOLDER,
            virtualPath = "$parentPath/$trimmed"
        )
        return Result.success(id)
    }
}

/** Renomear pasta ou referência — instantâneo (RF02). */
class RenameNodeUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    suspend operator fun invoke(nodeId: Long, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Nome vazio"))
        if (trimmed.contains('/')) return Result.failure(IllegalArgumentException("Nome não pode conter '/'"))
        val node = workspaceRepository.getNode(nodeId)
            ?: return Result.failure(IllegalArgumentException("Nó não encontrado"))
        if (trimmed == node.name) return Result.success(Unit)
        if (workspaceRepository.childExists(node.workspaceId, node.parentId, trimmed)) {
            return Result.failure(IllegalArgumentException("Já existe um item com esse nome nesta pasta"))
        }
        workspaceRepository.renameNode(nodeId, trimmed)
        return Result.success(Unit)
    }
}

/** Mover nó/subárvore — instantâneo, batch update de virtual_path (RF02, RNF01). */
class MoveNodeUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    suspend operator fun invoke(nodeId: Long, newParentId: Long?): Result<Unit> {
        val node = workspaceRepository.getNode(nodeId)
            ?: return Result.failure(IllegalArgumentException("Nó não encontrado"))
        if (newParentId == node.parentId) return Result.success(Unit)
        if (workspaceRepository.childExists(node.workspaceId, newParentId, node.name)) {
            return Result.failure(IllegalArgumentException("Já existe um item com esse nome no destino"))
        }
        return runCatching { workspaceRepository.moveNode(nodeId, newParentId) }
    }
}

/** Fixar/desafixar pasta na raiz (RF10). */
class TogglePinUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    suspend operator fun invoke(nodeId: Long, pinned: Boolean) =
        workspaceRepository.setPinned(nodeId, pinned)
}
