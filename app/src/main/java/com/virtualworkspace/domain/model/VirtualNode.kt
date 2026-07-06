package com.virtualworkspace.domain.model

import java.time.Instant

data class VirtualNode(
    val id: Long,
    val workspaceId: Long,
    val parentId: Long?,
    val name: String,
    val type: NodeType,
    /** Materialized path (ex: "/Trabalho/ClienteA/Contratos") para pesquisa e subárvores. */
    val virtualPath: String,
    /** Null se for pasta ou smart folder. */
    val storageObjectId: Long?,
    /** JSON com regra (apenas se SMART_FOLDER). */
    val smartFolderRule: String?,
    /** Soft-delete para lixeira. */
    val isDeleted: Boolean,
    /** Fixado na raiz para acesso rápido (RF10). */
    val isPinned: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
