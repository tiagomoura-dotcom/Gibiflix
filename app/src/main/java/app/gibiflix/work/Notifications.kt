package app.gibiflix.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification
import app.gibiflix.R
import app.gibiflix.ui.MainActivity

object Notifications {
    const val CHANNEL_WORK = "work"
    const val CHANNEL_SHARE = "share"
    const val CHANNEL_REMINDER = "reminder"

    const val ID_IMPORT = 1001
    const val ID_PREPARE = 1002
    const val ID_SHARE = 1003
    const val ID_DOWNLOAD = 1004
    const val ID_IMPORT_RESULT = 1005
    const val ID_BACKUP_REMINDER = 1006

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, context.getString(R.string.channel_work), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARE, context.getString(R.string.channel_share), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, channel: String, title: String, text: String, progress: Int, max: Int): Notification =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_gibiflix)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .setContentIntent(openAppIntent(context))
            .build()

    fun simple(context: Context, channel: String, title: String, text: String): Notification =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_gibiflix)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

    fun notify(context: Context, id: Int, n: Notification) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
    }
}
