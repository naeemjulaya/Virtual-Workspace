package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.StorageObject
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.repository.StorageRepository
import com.virtualworkspace.domain.repository.WorkspaceRepository
import javax.inject.Inject

/** Pesquisa por nome, com localização física real nos resultados (RF11). */
class SearchFilesUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    suspend operator fun invoke(workspaceId: Long?, query: String): List<NodeWithStorage> {
        if (query.isBlank()) return emptyList()
        return workspaceRepository.search(workspaceId, query.trim())
    }
}

/** Propriedades detalhadas de um nó (RF14). */
class GetNodePropertiesUseCase @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val storageRepository: StorageRepository
) {
    data class NodeProperties(
        val node: VirtualNode,
        val storageObject: StorageObject?,
        /** Caminhos virtuais de todos os nós que referenciam o mesmo ficheiro. */
        val referencedIn: List<String>
    )

    suspend operator fun invoke(nodeId: Long): NodeProperties? {
        val node = workspaceRepository.getNode(nodeId) ?: return null
        val storageObject = node.storageObjectId?.let { storageRepository.getById(it) }
        val referencedIn = node.storageObjectId
            ?.let { workspaceRepository.getNodesReferencing(it) }
            ?.map { it.virtualPath }
            ?: emptyList()
        return NodeProperties(node, storageObject, referencedIn)
    }
}
