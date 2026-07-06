package com.virtualworkspace.infrastructure.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.virtualworkspace.domain.repository.OperationJournal
import com.virtualworkspace.domain.usecase.CleanExpiredTrashUseCase
import com.virtualworkspace.infrastructure.notification.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Limpeza diária da lixeira expirada (Fluxo 10, RF06) e do journal antigo. */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cleanExpiredTrash: CleanExpiredTrashUseCase,
    private val journal: OperationJournal,
    private val notifier: AppNotifier
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "trash_cleanup"
    }

    override suspend fun doWork(): Result {
        val removed = cleanExpiredTrash()
        journal.pruneOlderThanDays(90)
        if (removed > 0) {
            notifier.notifyDone(
                AppNotifier.NOTIFICATION_TRASH_CLEANED,
                "Lixeira limpa",
                "$removed item(ns) expirados foram eliminados permanentemente"
            )
        }
        return Result.success()
    }
}
