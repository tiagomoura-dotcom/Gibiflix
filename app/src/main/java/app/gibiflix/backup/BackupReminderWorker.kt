package app.gibiflix.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.work.Notifications
import java.util.concurrent.TimeUnit

/** Lembrete semanal (opcional) para fazer o backup leve. */
class BackupReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = applicationContext.container.settings.current().user
        if (!user.backupReminder) return Result.success()
        if (System.currentTimeMillis() - user.lastBackupAt < TimeUnit.DAYS.toMillis(6)) return Result.success()
        val ctx = applicationContext
        Notifications.notify(
            ctx, Notifications.ID_BACKUP_REMINDER,
            Notifications.simple(ctx, Notifications.CHANNEL_REMINDER, ctx.getString(R.string.backup_reminder_title), ctx.getString(R.string.backup_reminder_text)),
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "backup-reminder"

        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (on) {
                wm.enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS).build(),
                )
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }
}
