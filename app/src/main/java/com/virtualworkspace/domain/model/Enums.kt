package com.virtualworkspace.domain.model

enum class NodeType {
    FOLDER,
    FILE_REFERENCE,
    SMART_FOLDER
}

enum class StorageBackend {
    APP_PRIVATE,
    SAF_TREE,
    MEDIASTORE
}

enum class PermissionStatus {
    VALID,
    EXPIRED,
    REVOKED,
    BROKEN
}

enum class OperationType {
    CONSOLIDATE,
    EXPORT,
    DELETE_PERMANENT,
    MOVE_SUBTREE,
    IMPORT
}

enum class OperationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATED
}
