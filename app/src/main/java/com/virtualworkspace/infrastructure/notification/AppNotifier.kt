package com.virtualworkspace.infrastructure.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.virtualworkspace.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_OPERATIONS = "operations"
        const val CHANNEL_ALERTS = "alerts"
        const val NOTIFICATION_CONSOLIDATION = 1001
        const val NOTIFICATION_PERMISSION_EXPIRED = 1002
        const val NOTIFICATION_TRASH_CLEANED = 1003
    }

    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OPERATIONS,
                context.getString(R.string.notification_channel_operations),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun canNotify(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyProgress(title: String, progress: Int, max: Int) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_CONSOLIDATION, notification)
    }

    fun foregroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_CONSOLIDATION,
            notification,
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    fun notifyDone(id: Int, title: String, text: String) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun notifyAlert(id: Int, title: String, text: String) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
