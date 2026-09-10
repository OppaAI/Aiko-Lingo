package com.aiko.lingo.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-enqueue the hourly study check after a reboot (WorkManager clears on boot). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            StudyTracker.init(context)
            StudyReminderWorker.schedule(context)
        }
    }
}
