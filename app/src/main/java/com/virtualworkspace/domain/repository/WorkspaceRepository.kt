package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.model.WorkspaceRoot
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {

    // --- Workspaces ---
    fun observeWorkspaces(): Flow<List<WorkspaceRoot>>
    suspend fun getWorkspace(id: Long): WorkspaceRoot?
    suspend fun createWorkspace(name: String, description: String?): Long
    suspend fun renameWorkspace(id: Long, name: String, description: String?)
    suspend fun deleteWorkspace(id: Long)

    // --- Nós ---
    suspend fun getNode(id: Long): VirtualNode?
    fun observeChildren(workspaceId: Long, parentId: Long?): Flow<List<NodeWithStorage>>
    suspend fun getChildren(workspaceId: Long, parentId: Long?): List<VirtualNode>
    suspend fun childExists(workspaceId: Long, parentId: Long?, name: String): Boolean
    suspend fun createNode(
        workspaceId: Long,
        parentId: Long?,
        name: String,
        type: NodeType,
        virtualPath: String,
        storageObjectId: Long? = null,
        smartFolderRule: String? = null
    ): Long

    suspend fun renameNode(id: Long, newName: String)

    /**
     * Move o nó (e toda a subárvore, via batch update do virtual_path)
     * numa única transação Room. Instantâneo — apenas metadados.
     */
    suspend fun moveNode(nodeId: Long, newParentId: Long?)

    /** Remove definitivamente o nó (e descendentes) da base de dados. */
    suspend fun deleteNodePermanently(nodeId: Long)

    /** Soft-delete de um nó e todos os descendentes. Devolve os FILE_REFERENCE afetados. */
    suspend fun markSubtreeDeleted(nodeId: Long, deleted: Boolean): List<VirtualNode>

    suspend fun getSubtree(nodeId: Long): List<VirtualNode>
    suspend fun getFileReferencesInSubtree(nodeId: Long): List<VirtualNode>

    suspend fun search(workspaceId: Long?, query: String): List<NodeWithStorage>

    /** Nós que referenciam um StorageObject (o mesmo ficheiro pode aparecer em várias pastas). */
    suspend fun getNodesReferencing(storageObjectId: Long): List<VirtualNode>

    /** Pastas fixadas na raiz (favoritos, RF10). */
    fun observePinned(workspaceId: Long): Flow<List<VirtualNode>>
    suspend fun setPinned(nodeId: Long, pinned: Boolean)

    /** Reatribui referências a novos objetos de armazenamento numa única transação. */
    suspend fun reassignStorageObjects(assignments: Map<Long, Long>)
    suspend fun cleanOrphanStorage()
}
