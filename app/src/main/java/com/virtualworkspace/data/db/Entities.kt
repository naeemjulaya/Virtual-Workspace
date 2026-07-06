package com.virtualworkspace.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workspace_roots")
data class WorkspaceRootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "virtual_nodes",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VirtualNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StorageObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["storage_object_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("workspace_id"),
        Index("parent_id"),
        Index("virtual_path"),
        Index("storage_object_id"),
        Index("is_deleted")
    ]
)
data class VirtualNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workspace_id") val workspaceId: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    val name: String,
    val type: String,
    @ColumnInfo(name = "virtual_path") val virtualPath: String,
    @ColumnInfo(name = "storage_object_id") val storageObjectId: Long?,
    @ColumnInfo(name = "smart_folder_rule") val smartFolderRule: String?,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "storage_objects",
    indices = [
        Index("uri", unique = true),
        Index("backend_type"),
        Index("permission_status")
    ]
)
data class StorageObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "backend_type") val backendType: String,
    val uri: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val size: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    val checksum: String?,
    @ColumnInfo(name = "checksum_computed_at") val checksumComputedAt: Long?,
    @ColumnInfo(name = "permission_status") val permissionStatus: String,
    @ColumnInfo(name = "last_validated_at") val lastValidatedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "operation_journal",
    indices = [Index("status"), Index("started_at")]
)
data class OperationJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "operation_type") val operationType: String,
    val status: String,
    @ColumnInfo(name = "total_items") val totalItems: Int,
    @ColumnInfo(name = "completed_items") val completedItems: Int,
    @ColumnInfo(name = "source_node_id") val sourceNodeId: Long?,
    @ColumnInfo(name = "target_node_id") val targetNodeId: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "compensation_data") val compensationData: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?
)

@Entity(
    tableName = "permission_grants",
    indices = [Index("uri", unique = true), Index("status")]
)
data class PermissionGrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val flags: Int,
    @ColumnInfo(name = "granted_at") val grantedAt: Long,
    @ColumnInfo(name = "last_validated_at") val lastValidatedAt: Long,
    val status: String
)

@Entity(
    tableName = "trash_items",
    foreignKeys = [
        ForeignKey(
            entity = VirtualNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["original_virtual_node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expires_at"), Index("original_virtual_node_id", unique = true)]
)
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "storage_object_id") val storageObjectId: Long?,
    @ColumnInfo(name = "original_virtual_node_id") val originalVirtualNodeId: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long
)

/** Nó + StorageObject numa só query (evita N+1). */
data class NodeWithStorageRow(
    @Embedded val node: VirtualNodeEntity,
    @Relation(parentColumn = "storage_object_id", entityColumn = "id")
    val storage: StorageObjectEntity?
)
