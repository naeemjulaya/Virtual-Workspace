package com.virtualworkspace.data.db

import com.virtualworkspace.domain.model.NodeType
import com.virtualworkspace.domain.model.NodeWithStorage
import com.virtualworkspace.domain.model.OperationJournalEntry
import com.virtualworkspace.domain.model.OperationStatus
import com.virtualworkspace.domain.model.OperationType
import com.virtualworkspace.domain.model.PermissionGrant
import com.virtualworkspace.domain.model.PermissionStatus
import com.virtualworkspace.domain.model.StorageBackend
import com.virtualworkspace.domain.model.StorageObject
import com.virtualworkspace.domain.model.TrashItem
import com.virtualworkspace.domain.model.VirtualNode
import com.virtualworkspace.domain.model.WorkspaceRoot
import java.time.Instant

fun WorkspaceRootEntity.toDomain() = WorkspaceRoot(
    id = id,
    name = name,
    description = description,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun VirtualNodeEntity.toDomain() = VirtualNode(
    id = id,
    workspaceId = workspaceId,
    parentId = parentId,
    name = name,
    type = NodeType.valueOf(type),
    virtualPath = virtualPath,
    storageObjectId = storageObjectId,
    smartFolderRule = smartFolderRule,
    isDeleted = isDeleted,
    isPinned = isPinned,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun StorageObjectEntity.toDomain() = StorageObject(
    id = id,
    backendType = StorageBackend.valueOf(backendType),
    uri = uri,
    mimeType = mimeType,
    size = size,
    lastModified = Instant.ofEpochMilli(lastModified),
    checksum = checksum,
    checksumComputedAt = checksumComputedAt?.let(Instant::ofEpochMilli),
    permissionStatus = PermissionStatus.valueOf(permissionStatus),
    lastValidatedAt = Instant.ofEpochMilli(lastValidatedAt),
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun OperationJournalEntity.toDomain() = OperationJournalEntry(
    id = id,
    operationType = OperationType.valueOf(operationType),
    status = OperationStatus.valueOf(status),
    totalItems = totalItems,
    completedItems = completedItems,
    sourceNodeId = sourceNodeId,
    targetNodeId = targetNodeId,
    startedAt = Instant.ofEpochMilli(startedAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    compensationData = compensationData,
    errorMessage = errorMessage
)

fun PermissionGrantEntity.toDomain() = PermissionGrant(
    id = id,
    uri = uri,
    flags = flags,
    grantedAt = Instant.ofEpochMilli(grantedAt),
    lastValidatedAt = Instant.ofEpochMilli(lastValidatedAt),
    status = PermissionStatus.valueOf(status)
)

fun TrashItemEntity.toDomain() = TrashItem(
    id = id,
    storageObjectId = storageObjectId,
    originalVirtualNodeId = originalVirtualNodeId,
    deletedAt = Instant.ofEpochMilli(deletedAt),
    expiresAt = Instant.ofEpochMilli(expiresAt)
)

fun NodeWithStorageRow.toDomain() = NodeWithStorage(
    node = node.toDomain(),
    storageObject = storage?.toDomain()
)
