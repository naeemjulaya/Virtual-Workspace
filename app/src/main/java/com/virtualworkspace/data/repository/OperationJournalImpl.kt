package com.virtualworkspace.data.repository

import com.virtualworkspace.data.db.OperationJournalDao
import com.virtualworkspace.data.db.OperationJournalEntity
import com.virtualworkspace.data.db.toDomain
import com.virtualworkspace.domain.model.OperationJournalEntry
import com.virtualworkspace.domain.model.OperationStatus
import com.virtualworkspace.domain.model.OperationType
import com.virtualworkspace.domain.repository.OperationJournal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationJournalImpl @Inject constructor(
    private val dao: OperationJournalDao,
    private val clock: Clock
) : OperationJournal {

    override suspend fun begin(
        type: OperationType,
        totalItems: Int,
        sourceNodeId: Long?,
        targetNodeId: Long?
    ): Long = dao.insert(
        OperationJournalEntity(
            operationType = type.name,
            status = OperationStatus.PENDING.name,
            totalItems = totalItems,
            completedItems = 0,
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            startedAt = clock.millis(),
            completedAt = null,
            compensationData = null,
            errorMessage = null
        )
    )

    override suspend fun get(id: Long): OperationJournalEntry? = dao.getById(id)?.toDomain()

    override fun observe(id: Long): Flow<OperationJournalEntry?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeActive(): Flow<List<OperationJournalEntry>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun markRunning(id: Long) =
        dao.updateStatus(id, OperationStatus.RUNNING.name)

    override suspend fun incrementProgress(id: Long, compensationData: String?) =
        dao.incrementProgress(id, compensationData)

    override suspend fun resetProgress(id: Long) = dao.resetProgress(id)

    override suspend fun complete(id: Long) =
        dao.finish(id, OperationStatus.COMPLETED.name, clock.millis(), null)

    override suspend fun fail(id: Long, error: String) =
        dao.finish(id, OperationStatus.FAILED.name, clock.millis(), error)

    override suspend fun markCompensated(id: Long) =
        dao.finish(id, OperationStatus.COMPENSATED.name, clock.millis(), null)

    override suspend fun findInterrupted(): List<OperationJournalEntry> =
        dao.findInterrupted().map { it.toDomain() }

    override suspend fun updateStatus(id: Long, status: OperationStatus) =
        dao.updateStatus(id, status.name)

    override suspend fun pruneOlderThanDays(days: Int) =
        dao.pruneOlderThan(clock.millis() - TimeUnit.DAYS.toMillis(days.toLong()))
}
