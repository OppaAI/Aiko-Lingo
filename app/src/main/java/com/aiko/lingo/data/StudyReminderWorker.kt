package com.aiko.lingo.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aiko.lingo.MainActivity
import com.aiko.lingo.R
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK = "study_reminder_hourly"
private const val CHANNEL_ID = "study_reminders"

/**
 * Hourly study nudge. Fires at the top of the hour while the user has not
 * studied today ([StudyTracker.hasStudiedToday]); silent otherwise. Tapping
 * the notification opens the app, and any study action that day stops
 * further nudges until after midnight.
 *
 * WorkManager is best-effort on timing (Doze/vendor schedulers may shift a
 * tick), so "top of the hour" means "roughly hourly", not exact alarms.
 */
class StudyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        StudyTracker.init(applicationContext)
        if (StudyTracker.hasStudiedToday()) {
            return Result.success()
        }
        showReminder()
        return Result.success()
    }

    private fun showReminder() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Study reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Hourly nudge to study Japanese" }
        )

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val note = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Time to study Japanese 🌸")
            .setContentText("Aiko is waiting — a quick review keeps your streak alive!")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(1, note)
    }

    companion object {
        /** Enqueue (or keep) the hourly check, first tick at the next :00. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val delayMin = Duration.between(now, nextHour).toMinutes().coerceAtLeast(1)
            val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
