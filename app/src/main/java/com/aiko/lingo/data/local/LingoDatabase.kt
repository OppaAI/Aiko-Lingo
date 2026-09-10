package com.aiko.lingo.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline cache so the study screens open with zero network.
 *
 * Payloads are stored as JSON blobs keyed by purpose:
 * - "current_vocab"   → CurrentLessonResponse (lesson + progress)
 * - "current_grammar" → CurrentLessonResponse
 * - "stats"           → StatsResponse
 * - "word"            → WordOfDayResponse
 *
 * ViewModels emit the cached payload first, then refresh from the network
 * and overwrite it — the same stale-while-revalidate pattern LingoCache
 * uses in memory, but surviving restarts and dead zones. Review/Practice
 * sessions still need the server (live SM-2 grading), so they are not
 * cached: the lesson viewer, dashboard, and widget work offline.
 *
 * Plain SQLiteOpenHelper (no annotation processing): one tiny table,
 * zero toolchain risk.
 */
data class CachedPayload(
    val key: String,
    val json: String,
    val updatedAt: Long
)

private class PayloadDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "lingo_offline.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS payloads (" +
                "pkey TEXT PRIMARY KEY, json TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS payloads")
        onCreate(db)
    }
}

class PayloadDao private constructor(private val helper: PayloadDbHelper) {

    suspend fun get(key: String): CachedPayload? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "payloads", arrayOf("pkey", "json", "updatedAt"),
            "pkey = ?", arrayOf(key), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return@withContext null
            CachedPayload(c.getString(0), c.getString(1), c.getLong(2))
        }
    }

    suspend fun put(payload: CachedPayload): Unit = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("pkey", payload.key)
            put("json", payload.json)
            put("updatedAt", payload.updatedAt)
        }
        helper.writableDatabase.insertWithOnConflict(
            "payloads", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun clear(key: String): Unit = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("payloads", "pkey = ?", arrayOf(key))
    }

    companion object {
        @Volatile
        private var dao: PayloadDao? = null

        fun get(context: Context): PayloadDao {
            return dao ?: synchronized(this) {
                dao ?: PayloadDao(PayloadDbHelper(context)).also { dao = it }
            }
        }
    }
}
