package com.virtualworkspace.domain.usecase

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.model.WorkspaceRoot
import com.virtualworkspace.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FolderUseCasesTest {
    private val node = VirtualNode(
        id = 1, workspaceId = 7, parentId = null, name = "Documentos",
        type = NodeType.FOLDER, virtualPath = "/Documentos", storageObjectId = null,
        smartFolderRule = null, isDeleted = false, isPinned = false,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    @Test fun `renomear para o mesmo nome e uma operacao valida`() = runBlocking {
        val result = RenameNodeUseCase(FakeRepository(node))(node.id, node.name)
        assertTrue(result.isSuccess)
    }

    @Test fun `nome vazio de pasta e rejeitado`() = runBlocking {
        val result = CreateFolderUseCase(FakeRepository(node))(7, null, "   ")
        assertTrue(result.isFailure)
    }

    @Test fun `mover para o pai atual e idempotente`() = runBlocking {
        val result = MoveNodeUseCase(FakeRepository(node))(node.id, node.parentId)
        assertTrue(result.isSuccess)
    }
}

private class FakeRepository(private val existing: VirtualNode) : WorkspaceRepository {
    override fun observeWorkspaces(): Flow<List<WorkspaceRoot>> = flowOf(emptyList())
    override suspend fun getWorkspace(id: Long): WorkspaceRoot? = null
    override suspend fun createWorkspace(name: String, description: String?) = 1L
    override suspend fun renameWorkspace(id: Long, name: String, description: String?) = Unit
    override suspend fun deleteWorkspace(id: Long) = Unit
    override suspend fun getNode(id: Long) = existing.takeIf { it.id == id }
    override fun observeChildren(workspaceId: Long, parentId: Long?): Flow<List<NodeWithStorage>> = flowOf(emptyList())
    override suspend fun getChildren(workspaceId: Long, parentId: Long?) = emptyList<VirtualNode>()
    override suspend fun childExists(workspaceId: Long, parentId: Long?, name: String) = name == existing.name
    override suspend fun createNode(workspaceId: Long, parentId: Long?, name: String, type: NodeType, virtualPath: String, storageObjectId: Long?, smartFolderRule: String?) = 2L
    override suspend fun renameNode(id: Long, newName: String) = Unit
    override suspend fun moveNode(nodeId: Long, newParentId: Long?) = Unit
    override suspend fun deleteNodePermanently(nodeId: Long) = Unit
    override suspend fun markSubtreeDeleted(nodeId: Long, deleted: Boolean) = emptyList<VirtualNode>()
    override suspend fun getSubtree(nodeId: Long) = emptyList<VirtualNode>()
    override suspend fun getFileReferencesInSubtree(nodeId: Long) = emptyList<VirtualNode>()
    override suspend fun search(workspaceId: Long?, query: String) = emptyList<NodeWithStorage>()
    override suspend fun getNodesReferencing(storageObjectId: Long) = emptyList<VirtualNode>()
    override fun observePinned(workspaceId: Long): Flow<List<VirtualNode>> = flowOf(emptyList())
    override suspend fun setPinned(nodeId: Long, pinned: Boolean) = Unit
    override suspend fun reassignStorageObjects(assignments: Map<Long, Long>) = Unit
    override suspend fun cleanOrphanStorage() = Unit
}
