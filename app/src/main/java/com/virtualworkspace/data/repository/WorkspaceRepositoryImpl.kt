package com.virtualworkspace.data.repository

import androidx.room.withTransaction
import com.virtualworkspace.data.db.VirtualNodeEntity
import com.virtualworkspace.data.db.WorkspaceDatabase
import com.virtualworkspace.data.db.WorkspaceRootEntity
import com.virtualworkspace.data.db.toDomain
import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.model.WorkspaceRoot
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    private val db: WorkspaceDatabase,
    private val clock: Clock,
    private val physicalStorage: PhysicalStorageGateway
) : WorkspaceRepository {

    private val workspaceDao get() = db.workspaceRootDao()
    private val nodeDao get() = db.virtualNodeDao()

    private fun now() = clock.millis()

    override fun observeWorkspaces(): Flow<List<WorkspaceRoot>> =
        workspaceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getWorkspace(id: Long): WorkspaceRoot? =
        workspaceDao.getById(id)?.toDomain()

    override suspend fun createWorkspace(name: String, description: String?): Long {
        require(name.isNotBlank()) { "Nome do workspace vazio" }
        return db.withTransaction {
            val cleanName = name.trim()
            require(workspaceDao.countByName(cleanName) == 0) { "Já existe um workspace com esse nome" }
            val t = now()
            workspaceDao.insert(
                WorkspaceRootEntity(name = cleanName, description = description, createdAt = t, updatedAt = t)
            )
        }
    }

    override suspend fun renameWorkspace(id: Long, name: String, description: String?) {
        val existing = workspaceDao.getById(id) ?: return
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Nome do workspace vazio" }
        if (!cleanName.equals(existing.name, ignoreCase = true)) {
            require(workspaceDao.countByName(cleanName) == 0) { "Já existe um workspace com esse nome" }
        }
        workspaceDao.update(existing.copy(name = cleanName, description = description, updatedAt = now()))
    }

    override suspend fun deleteWorkspace(id: Long) {
        workspaceDao.delete(id)
        cleanOrphanStorage()
    }

    override suspend fun getNode(id: Long): VirtualNode? = nodeDao.getById(id)?.toDomain()

    override fun observeChildren(workspaceId: Long, parentId: Long?): Flow<List<NodeWithStorage>> =
        nodeDao.observeChildren(workspaceId, parentId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getChildren(workspaceId: Long, parentId: Long?): List<VirtualNode> =
        nodeDao.getChildren(workspaceId, parentId).map { it.toDomain() }

    override suspend fun childExists(workspaceId: Long, parentId: Long?, name: String): Boolean =
        nodeDao.countByName(workspaceId, parentId, name) > 0

    override suspend fun createNode(
        workspaceId: Long,
        parentId: Long?,
        name: String,
        type: NodeType,
        virtualPath: String,
        storageObjectId: Long?,
        smartFolderRule: String?
    ): Long {
        return db.withTransaction {
            require(name.isNotBlank() && '/' !in name) { "Nome inválido" }
            val parent = parentId?.let { nodeDao.getById(it) }
            require(parentId == null || (parent != null && parent.workspaceId == workspaceId &&
                parent.type != NodeType.FILE_REFERENCE.name && !parent.isDeleted)) { "Pasta de destino inválida" }
            require(nodeDao.countByName(workspaceId, parentId, name) == 0) { "Já existe um item com esse nome" }
            val t = now()
            nodeDao.insert(
                VirtualNodeEntity(
                    workspaceId = workspaceId,
                    parentId = parentId,
                    name = name,
                    type = type.name,
                    virtualPath = virtualPath,
                    storageObjectId = storageObjectId,
                    smartFolderRule = smartFolderRule,
                    createdAt = t,
                    updatedAt = t
                )
            )
        }
    }

    override suspend fun renameNode(id: Long, newName: String) {
        db.withTransaction {
            val node = nodeDao.getById(id) ?: return@withTransaction
            val t = now()
            val oldPath = node.virtualPath
            val parentPath = oldPath.substringBeforeLast('/', "")
            val newPath = "$parentPath/$newName"
            nodeDao.update(node.copy(name = newName, virtualPath = newPath, updatedAt = t))
            // Rebase dos descendentes (o update acima já tratou do próprio nó)
            nodeDao.rebasePaths(node.workspaceId, oldPath, newPath, t)
        }
    }

    override suspend fun moveNode(nodeId: Long, newParentId: Long?) {
        db.withTransaction {
            val node = nodeDao.getById(nodeId) ?: return@withTransaction
            val newParentPath = if (newParentId == null) {
                ""
            } else {
                val parent = nodeDao.getById(newParentId)
                    ?: throw IllegalArgumentException("Pasta de destino não existe")
                require(parent.workspaceId == node.workspaceId) { "Mover entre workspaces não suportado" }
                require(parent.type != NodeType.FILE_REFERENCE.name && !parent.isDeleted) { "Pasta de destino inválida" }
                require(!parent.virtualPath.startsWith(node.virtualPath + "/") && parent.id != node.id) {
                    "Não é possível mover uma pasta para dentro de si própria"
                }
                parent.virtualPath
            }
            val t = now()
            val oldPath = node.virtualPath
            val newPath = "$newParentPath/${node.name}"
            nodeDao.reparent(nodeId, newParentId, t)
            nodeDao.rebasePaths(node.workspaceId, oldPath, newPath, t)
        }
    }

    override suspend fun deleteNodePermanently(nodeId: Long) {
        // ForeignKey CASCADE em parent_id remove os descendentes
        nodeDao.delete(nodeId)
    }

    override suspend fun markSubtreeDeleted(nodeId: Long, deleted: Boolean): List<VirtualNode> {
        return db.withTransaction {
            val node = nodeDao.getById(nodeId) ?: return@withTransaction emptyList()
            val subtree = nodeDao.getSubtreeByPath(node.workspaceId, node.virtualPath)
            nodeDao.setDeletedBySubtree(node.workspaceId, node.virtualPath, deleted, now())
            subtree.filter { it.type == NodeType.FILE_REFERENCE.name }.map { it.toDomain() }
        }
    }

    override suspend fun getSubtree(nodeId: Long): List<VirtualNode> {
        val node = nodeDao.getById(nodeId) ?: return emptyList()
        return nodeDao.getSubtreeByPath(node.workspaceId, node.virtualPath).map { it.toDomain() }
    }

    override suspend fun getFileReferencesInSubtree(nodeId: Long): List<VirtualNode> =
        getSubtree(nodeId).filter { it.type == NodeType.FILE_REFERENCE && !it.isDeleted }

    override suspend fun search(workspaceId: Long?, query: String): List<NodeWithStorage> =
        nodeDao.search(workspaceId, query).map { it.toDomain() }

    override suspend fun getNodesReferencing(storageObjectId: Long): List<VirtualNode> =
        nodeDao.getByStorageObject(storageObjectId).map { it.toDomain() }

    override fun observePinned(workspaceId: Long): Flow<List<VirtualNode>> =
        nodeDao.observePinned(workspaceId).map { list -> list.map { it.toDomain() } }

    override suspend fun setPinned(nodeId: Long, pinned: Boolean) =
        nodeDao.setPinned(nodeId, pinned, now())

    override suspend fun reassignStorageObjects(assignments: Map<Long, Long>) {
        db.withTransaction {
            val t = now()
            assignments.forEach { (nodeId, storageId) ->
                require(nodeDao.getById(nodeId) != null) { "Referência já não existe" }
                require(db.storageObjectDao().getById(storageId) != null) { "StorageObject não existe" }
                nodeDao.updateStorageObject(nodeId, storageId, t)
            }
        }
    }

    override suspend fun cleanOrphanStorage() {
        db.storageObjectDao().listOrphans().forEach { storage ->
            if (storage.backendType == StorageBackend.APP_PRIVATE.name) {
                val deleted = runCatching {
                    physicalStorage.deletePhysical(storage.uri, StorageBackend.APP_PRIVATE)
                }.getOrDefault(false)
                if (!deleted) return@forEach
            }
            db.storageObjectDao().delete(storage.id)
        }
    }
}
