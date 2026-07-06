package com.virtualworkspace.data.repository

import androidx.room.withTransaction
import com.virtualworkspace.data.db.TrashItemEntity
import com.virtualworkspace.data.db.WorkspaceDatabase
import com.virtualworkspace.data.db.toDomain
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.TrashItem
import com.virtualworkspace.domain.repository.PhysicalStorageGateway
import com.virtualworkspace.domain.repository.TrashManager
import com.virtualworkspace.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashManagerImpl @Inject constructor(
    private val db: WorkspaceDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val physicalStorage: PhysicalStorageGateway,
    private val clock: Clock
) : TrashManager {

    companion object {
        const val RETENTION_DAYS = 30L
    }

    private val trashDao get() = db.trashItemDao()
    private val nodeDao get() = db.virtualNodeDao()
    private val storageDao get() = db.storageObjectDao()

    override fun observeTrash(): Flow<List<NodeWithStorage>> =
        nodeDao.observeTrashed().map { rows -> rows.map { it.toDomain() } }

    override suspend fun moveToTrash(nodeId: Long) {
        db.withTransaction {
            val now = clock.millis()
            val expires = now + TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            workspaceRepository.markSubtreeDeleted(nodeId, deleted = true)
            // Apenas a raiz é um item da lixeira; descendentes são restaurados em conjunto.
            if (trashDao.getByNodeId(nodeId) == null) {
                val rootNode = nodeDao.getById(nodeId)
                trashDao.insert(
                    TrashItemEntity(
                        storageObjectId = rootNode?.storageObjectId,
                        originalVirtualNodeId = nodeId,
                        deletedAt = now,
                        expiresAt = expires
                    )
                )
            }
        }
    }

    override suspend fun restore(nodeId: Long) {
        db.withTransaction {
            val subtree = workspaceRepository.getSubtree(nodeId)
            workspaceRepository.markSubtreeDeleted(nodeId, deleted = false)
            trashDao.deleteByNodeId(nodeId)
            subtree.forEach { trashDao.deleteByNodeId(it.id) }
        }
    }

    override suspend fun deletePermanently(nodeId: Long) {
        val subtree = workspaceRepository.getSubtree(nodeId)
        // Apagar ficheiros físicos APP_PRIVATE fora da transação (filesystem não é transacional)
        for (node in subtree) {
            val soId = node.storageObjectId ?: continue
            deletePhysicalIfOrphan(soId, excludingNodeIds = subtree.map { it.id }.toSet())
        }
        db.withTransaction {
            subtree.forEach { trashDao.deleteByNodeId(it.id) }
            workspaceRepository.deleteNodePermanently(nodeId)
        }
    }

    override suspend fun cleanExpired(): Int {
        val expired = trashDao.listExpired(clock.millis())
        for (item in expired) {
            // O nó pode já ter sido removido por eliminação de um antecessor
            if (nodeDao.getById(item.originalVirtualNodeId) != null) {
                deletePermanently(item.originalVirtualNodeId)
            } else {
                trashDao.delete(item.id)
            }
        }
        return expired.size
    }

    override suspend fun getTrashItemForNode(nodeId: Long): TrashItem? =
        trashDao.getByNodeId(nodeId)?.toDomain()

    /**
     * Apaga o ficheiro físico (só APP_PRIVATE) e o StorageObject se nenhum outro
     * nó fora da subárvore em eliminação o referenciar.
     */
    private suspend fun deletePhysicalIfOrphan(storageObjectId: Long, excludingNodeIds: Set<Long>) {
        val otherRefs = nodeDao.getByStorageObject(storageObjectId)
            .filterNot { it.id in excludingNodeIds }
        if (otherRefs.isNotEmpty()) return

        val storage = storageDao.getById(storageObjectId) ?: return
        if (storage.backendType == StorageBackend.APP_PRIVATE.name) {
            val deleted = physicalStorage.deletePhysical(storage.uri, StorageBackend.APP_PRIVATE)
            if (!deleted) return
        }
        storageDao.delete(storageObjectId)
    }
}
