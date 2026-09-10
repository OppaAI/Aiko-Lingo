package com.aiko.lingo.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Shared lenient JSON for cache payloads (unknown keys ignored). */
@PublishedApi
internal val OfflineJson = Json { ignoreUnknownKeys = true }

/**
 * Disk-backed twin of [com.aiko.lingo.data.remote.LingoCache].
 *
 * Synchronous-looking API that never blocks the caller: reads hit an
 * in-memory copy warmed at init (Room read on a background thread), writes
 * go straight to Room off the main thread.
 */
object OfflineCache {

    const val CURRENT_VOCAB = "current_vocab"
    const val CURRENT_GRAMMAR = "current_grammar"
    const val STATS = "stats"
    const val WORD = "word"

    @PublishedApi
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    @PublishedApi
    internal var dao: PayloadDao? = null
    @PublishedApi
    internal val memory = mutableMapOf<String, String>()

    fun init(context: Context) {
        if (dao != null) return
        synchronized(this) {
            if (dao != null) return
            val payloadDao = PayloadDao.get(context)
            dao = payloadDao
            // Warm the snapshot so get() is instant.
            scope.launch {
                for (key in listOf(CURRENT_VOCAB, CURRENT_GRAMMAR, STATS, WORD)) {
                    try {
                        payloadDao.get(key)?.let { memory[key] = it.json }
                    } catch (e: Exception) {
                        Log.w("Lingo", "Offline cache warm failed for $key", e)
                    }
                }
            }
        }
    }

    inline fun <reified T> get(key: String): T? {
        val json = memory[key] ?: return null
        return try {
            OfflineJson.decodeFromString<T>(json)
        } catch (e: Exception) {
            Log.w("Lingo", "Offline cache decode failed for $key", e)
            null
        }
    }

    inline fun <reified T> put(key: String, value: T) {
        val json = try {
            OfflineJson.encodeToString(value)
        } catch (e: Exception) {
            Log.w("Lingo", "Offline cache encode failed for $key", e)
            return
        }
        memory[key] = json
        val payloadDao = dao ?: return
        scope.launch {
            try {
                payloadDao.put(CachedPayload(key, json, System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.w("Lingo", "Offline cache write failed for $key", e)
            }
        }
    }

    fun invalidate(vararg keys: String) {
        keys.forEach { memory.remove(it) }
        val payloadDao = dao ?: return
        scope.launch {
            keys.forEach {
                try {
                    payloadDao.clear(it)
                } catch (e: Exception) {
                    Log.w("Lingo", "Offline cache clear failed for $it", e)
                }
            }
        }
    }
}
