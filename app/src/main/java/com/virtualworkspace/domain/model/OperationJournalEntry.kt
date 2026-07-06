package com.virtualworkspace.domain.model

import java.time.Instant

/**
 * Entrada do journal de operações físicas. Garante rastreabilidade, retomada e
 * compensação — não atomicidade física total (o filesystem não participa em
 * transações Room).
 */
data class OperationJournalEntry(
    val id: Long,
    val operationType: OperationType,
    val status: OperationStatus,
    val totalItems: Int,
    val completedItems: Int,
    val sourceNodeId: Long?,
    val targetNodeId: Long?,
    val startedAt: Instant,
    val completedAt: Instant?,
    /** JSON: lista de URIs já copiados, para rollback/compensação. */
    val compensationData: String?,
    val errorMessage: String?
)
