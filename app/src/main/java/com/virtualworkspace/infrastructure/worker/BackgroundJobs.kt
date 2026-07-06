package com.virtualworkspace.infrastructure.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Agenda os jobs periódicos com restrições de bateria (RNF08). */
@Singleton
class BackgroundJobs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodicJobs() {
        // Lazy: só obtém o WorkManager depois de a Application estar totalmente inicializada
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PermissionValidationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PermissionValidationWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            TrashCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
        )
    }
}
