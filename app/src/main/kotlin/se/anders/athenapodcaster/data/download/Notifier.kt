package se.anders.athenapodcaster.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import se.anders.athenapodcaster.R

/** The app's few notifications: a download in progress, and one when a private feed stops accepting us (FR-036). */
class Notifier(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_DOWNLOADS, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Shown only while a download runs. Never contains a URL, only the episode title. */
    fun downloadInProgress(title: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notification_downloading))
            .setContentText(title)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** One notification per feed that starts refusing us, so a revoked or expired token does not go unnoticed. */
    fun feedAccessRefused(feedId: Long, feedTitle: String) {
        manager.notify(
            ALERT_BASE + feedId.toInt(),
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.notification_access_title))
                .setContentText(context.getString(R.string.notification_access_text, feedTitle))
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_ALERTS = "alerts"
        const val DOWNLOAD_NOTIFICATION_ID = 2001
        private const val ALERT_BASE = 3000
    }
}
