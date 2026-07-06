package com.virtualworkspace.domain.repository

import com.virtualworkspace.domain.model.OperationJournalEntry
import com.virtualworkspace.domain.model.OperationStatus
import com.virtualworkspace.domain.model.OperationType
import kotlinx.coroutines.flow.Flow

interface OperationJournal {
    suspend fun begin(
        type: OperationType,
        totalItems: Int,
        sourceNodeId: Long? = null,
        targetNodeId: Long? = null
    ): Long

    suspend fun get(id: Long): OperationJournalEntry?
    fun observe(id: Long): Flow<OperationJournalEntry?>
    fun observeActive(): Flow<List<OperationJournalEntry>>

    suspend fun markRunning(id: Long)
    suspend fun incrementProgress(id: Long, compensationData: String?)
    suspend fun resetProgress(id: Long)
    suspend fun complete(id: Long)
    suspend fun fail(id: Long, error: String)
    suspend fun markCompensated(id: Long)

    /** Operações interrompidas (RUNNING/PENDING) que podem ser retomadas ou compensadas. */
    suspend fun findInterrupted(): List<OperationJournalEntry>
    suspend fun updateStatus(id: Long, status: OperationStatus)
    suspend fun pruneOlderThanDays(days: Int)
}
