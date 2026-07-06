package com.virtualworkspace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceRootDao {
    @Query("SELECT * FROM workspace_roots ORDER BY name")
    fun observeAll(): Flow<List<WorkspaceRootEntity>>

    @Query("SELECT * FROM workspace_roots ORDER BY name")
    suspend fun listAll(): List<WorkspaceRootEntity>

    @Query("SELECT * FROM workspace_roots WHERE id = :id")
    suspend fun getById(id: Long): WorkspaceRootEntity?

    @Query("SELECT COUNT(*) FROM workspace_roots")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM workspace_roots WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Insert
    suspend fun insert(entity: WorkspaceRootEntity): Long

    @Update
    suspend fun update(entity: WorkspaceRootEntity)

    @Query("DELETE FROM workspace_roots WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VirtualNodeDao {
    @Query("SELECT * FROM virtual_nodes WHERE id = :id")
    suspend fun getById(id: Long): VirtualNodeEntity?

    @Transaction
    @Query(
        """SELECT * FROM virtual_nodes
           WHERE workspace_id = :workspaceId
             AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
             AND is_deleted = 0
           ORDER BY type = 'FILE_REFERENCE', name COLLATE NOCASE"""
    )
    fun observeChildren(workspaceId: Long, parentId: Long?): Flow<List<NodeWithStorageRow>>

    @Transaction
    @Query(
        """SELECT * FROM virtual_nodes
           WHERE workspace_id = :workspaceId
             AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
             AND is_deleted = 0
           ORDER BY type = 'FILE_REFERENCE', name COLLATE NOCASE"""
    )
    suspend fun getChildrenWithStorage(workspaceId: Long, parentId: Long?): List<NodeWithStorageRow>

    @Query(
        """SELECT * FROM virtual_nodes
           WHERE workspace_id = :workspaceId
             AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
             AND is_deleted = 0
           ORDER BY name COLLATE NOCASE"""
    )
    suspend fun getChildren(workspaceId: Long, parentId: Long?): List<VirtualNodeEntity>

    @Query(
        """SELECT COUNT(*) FROM virtual_nodes
           WHERE workspace_id = :workspaceId
             AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
             AND name = :name COLLATE NOCASE AND is_deleted = 0"""
    )
    suspend fun countByName(workspaceId: Long, parentId: Long?, name: String): Int

    @Insert
    suspend fun insert(entity: VirtualNodeEntity): Long

    @Update
    suspend fun update(entity: VirtualNodeEntity)

    @Query("DELETE FROM virtual_nodes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """SELECT * FROM virtual_nodes
           WHERE workspace_id = :workspaceId AND (virtual_path = :path OR virtual_path LIKE :path || '/%')"""
    )
    suspend fun getSubtreeByPath(workspaceId: Long, path: String): List<VirtualNodeEntity>

    /** Batch update do materialized path de toda a subárvore (estratégia da secção 5 da arquitetura). */
    @Query(
        """UPDATE virtual_nodes
           SET virtual_path = :newPrefix || SUBSTR(virtual_path, LENGTH(:oldPrefix) + 1),
               updated_at = :now
           WHERE workspace_id = :workspaceId
             AND (virtual_path = :oldPrefix OR virtual_path LIKE :oldPrefix || '/%')"""
    )
    suspend fun rebasePaths(workspaceId: Long, oldPrefix: String, newPrefix: String, now: Long)

    @Query("UPDATE virtual_nodes SET parent_id = :newParentId, updated_at = :now WHERE id = :nodeId")
    suspend fun reparent(nodeId: Long, newParentId: Long?, now: Long)

    @Query(
        """UPDATE virtual_nodes SET is_deleted = :deleted, updated_at = :now
           WHERE workspace_id = :workspaceId AND (virtual_path = :path OR virtual_path LIKE :path || '/%')"""
    )
    suspend fun setDeletedBySubtree(workspaceId: Long, path: String, deleted: Boolean, now: Long)

    @Transaction
    @Query(
        """SELECT * FROM virtual_nodes
           WHERE is_deleted = 0 AND name LIKE '%' || :query || '%'
             AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
           ORDER BY name COLLATE NOCASE LIMIT 200"""
    )
    suspend fun search(workspaceId: Long?, query: String): List<NodeWithStorageRow>

    @Query("SELECT * FROM virtual_nodes WHERE storage_object_id = :storageObjectId")
    suspend fun getByStorageObject(storageObjectId: Long): List<VirtualNodeEntity>

    @Query(
        """SELECT * FROM virtual_nodes
           WHERE workspace_id = :workspaceId AND is_pinned = 1 AND is_deleted = 0
           ORDER BY name COLLATE NOCASE"""
    )
    fun observePinned(workspaceId: Long): Flow<List<VirtualNodeEntity>>

    @Query("UPDATE virtual_nodes SET is_pinned = :pinned, updated_at = :now WHERE id = :nodeId")
    suspend fun setPinned(nodeId: Long, pinned: Boolean, now: Long)

    @Query("UPDATE virtual_nodes SET storage_object_id = :storageObjectId, updated_at = :now WHERE id = :nodeId")
    suspend fun updateStorageObject(nodeId: Long, storageObjectId: Long, now: Long)

    @Transaction
    @Query(
        """SELECT * FROM virtual_nodes WHERE is_deleted = 1
             AND id IN (SELECT original_virtual_node_id FROM trash_items)
           ORDER BY updated_at DESC"""
    )
    fun observeTrashed(): Flow<List<NodeWithStorageRow>>
}

@Dao
interface StorageObjectDao {
    @Query("SELECT * FROM storage_objects WHERE id = :id")
    suspend fun getById(id: Long): StorageObjectEntity?

    @Query("SELECT * FROM storage_objects WHERE uri = :uri")
    suspend fun getByUri(uri: String): StorageObjectEntity?

    @Insert
    suspend fun insert(entity: StorageObjectEntity): Long

    @Transaction
    suspend fun getOrInsert(entity: StorageObjectEntity): Long =
        getByUri(entity.uri)?.id ?: insert(entity)

    @Query("UPDATE storage_objects SET uri = :newUri WHERE id = :id")
    suspend fun updateUri(id: Long, newUri: String)

    @Query("UPDATE storage_objects SET uri = :newUri, backend_type = :backend WHERE id = :id")
    suspend fun updateUriAndBackend(id: Long, newUri: String, backend: String)

    @Query("UPDATE storage_objects SET permission_status = :status, last_validated_at = :now WHERE id = :id")
    suspend fun updatePermissionStatus(id: Long, status: String, now: Long)

    @Query("UPDATE storage_objects SET checksum = :checksum, checksum_computed_at = :now WHERE id = :id")
    suspend fun updateChecksum(id: Long, checksum: String, now: Long)

    @Query("UPDATE storage_objects SET size = :size, last_modified = :lastModified, checksum = NULL, checksum_computed_at = NULL, permission_status = 'VALID', last_validated_at = :now WHERE id = :id")
    suspend fun updateMetadata(id: Long, size: Long, lastModified: Long, now: Long)

    @Query("UPDATE storage_objects SET last_validated_at = :now WHERE id = :id")
    suspend fun touchValidated(id: Long, now: Long)

    @Query("DELETE FROM storage_objects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM storage_objects WHERE backend_type = :backend")
    suspend fun listByBackend(backend: String): List<StorageObjectEntity>

    @Query("SELECT * FROM storage_objects WHERE permission_status = :status")
    suspend fun listByStatus(status: String): List<StorageObjectEntity>

    @Query("SELECT * FROM storage_objects WHERE id NOT IN (SELECT DISTINCT storage_object_id FROM virtual_nodes WHERE storage_object_id IS NOT NULL)")
    suspend fun listOrphans(): List<StorageObjectEntity>
}

@Dao
interface OperationJournalDao {
    @Insert
    suspend fun insert(entity: OperationJournalEntity): Long

    @Query("SELECT * FROM operation_journal WHERE id = :id")
    suspend fun getById(id: Long): OperationJournalEntity?

    @Query("SELECT * FROM operation_journal WHERE id = :id")
    fun observeById(id: Long): Flow<OperationJournalEntity?>

    @Query("SELECT * FROM operation_journal WHERE status IN ('PENDING','RUNNING') ORDER BY started_at DESC")
    fun observeActive(): Flow<List<OperationJournalEntity>>

    @Query("SELECT * FROM operation_journal WHERE status IN ('PENDING','RUNNING')")
    suspend fun findInterrupted(): List<OperationJournalEntity>

    @Query("UPDATE operation_journal SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        """UPDATE operation_journal
           SET completed_items = completed_items + 1,
               compensation_data = COALESCE(:compensationData, compensation_data)
           WHERE id = :id"""
    )
    suspend fun incrementProgress(id: Long, compensationData: String?)

    @Query("UPDATE operation_journal SET completed_items = 0, compensation_data = NULL, status = 'PENDING', completed_at = NULL, error_message = NULL WHERE id = :id")
    suspend fun resetProgress(id: Long)

    @Query("UPDATE operation_journal SET status = :status, completed_at = :now, error_message = :error WHERE id = :id")
    suspend fun finish(id: Long, status: String, now: Long, error: String?)

    @Query("DELETE FROM operation_journal WHERE status IN ('COMPLETED','COMPENSATED') AND started_at < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}

@Dao
interface PermissionGrantDao {
    @Query("SELECT * FROM permission_grants ORDER BY granted_at DESC")
    fun observeAll(): Flow<List<PermissionGrantEntity>>

    @Query("SELECT * FROM permission_grants WHERE uri = :uri")
    suspend fun getByUri(uri: String): PermissionGrantEntity?

    @Query("SELECT * FROM permission_grants WHERE status = 'VALID'")
    suspend fun listValid(): List<PermissionGrantEntity>

    @Insert
    suspend fun insert(entity: PermissionGrantEntity): Long

    @Update
    suspend fun update(entity: PermissionGrantEntity)

    @Query("DELETE FROM permission_grants WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}

@Dao
interface TrashItemDao {
    @Insert
    suspend fun insert(entity: TrashItemEntity): Long

    @Query("SELECT * FROM trash_items WHERE original_virtual_node_id = :nodeId")
    suspend fun getByNodeId(nodeId: Long): TrashItemEntity?

    @Query("SELECT * FROM trash_items WHERE expires_at < :now")
    suspend fun listExpired(now: Long): List<TrashItemEntity>

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash_items WHERE original_virtual_node_id = :nodeId")
    suspend fun deleteByNodeId(nodeId: Long)
}
