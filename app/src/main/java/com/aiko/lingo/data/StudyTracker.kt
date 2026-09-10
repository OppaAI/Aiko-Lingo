package com.aiko.lingo.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Tracks whether the user has done any real studying today.
 *
 * The hourly reminder worker ([StudyReminderWorker]) stays quiet once this
 * reports true. The check is date-based, so a new day automatically
 * re-arms reminders after 0:00 with no reset logic needed.
 */
object StudyTracker {

    private const val PREFS = "aiko_study"
    private const val KEY_LAST_STUDY_DAY = "last_study_day"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
            }
        }
    }

    /** Call after a genuine study action (review, practice, test, chat turn…). */
    fun markStudied() {
        prefs?.edit()?.putString(KEY_LAST_STUDY_DAY, LocalDate.now().toString())?.apply()
    }

    fun hasStudiedToday(): Boolean {
        val prefs = prefs ?: return false
        return prefs.getString(KEY_LAST_STUDY_DAY, null) == LocalDate.now().toString()
    }
}
