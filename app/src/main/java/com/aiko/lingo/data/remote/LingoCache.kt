package com.aiko.lingo.data.remote

import android.os.SystemClock

/**
 * Process-lifetime cache so revisiting a screen shows last-known content
 * instantly (no full spinner) while a background refresh updates it.
 * ViewModels emit cached values first, then fetch network.
 */
object LingoCache {
    private data class Entry(val value: Any, val at: Long)
    private val map = mutableMapOf<String, Entry>()

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String, maxAgeMs: Long): T? {
        val e = map[key] ?: return null
        if (SystemClock.elapsedRealtime() - e.at > maxAgeMs) {
            map.remove(key)
            return null
        }
        return try { e.value as T } catch (c: ClassCastException) { null }
    }

    @Synchronized
    fun <T : Any> put(key: String, value: T) {
        map[key] = Entry(value, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun invalidate(vararg keys: String) {
        keys.forEach { map.remove(it) }
    }

    @Synchronized
    fun invalidatePrefix(prefix: String) {
        map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
    }
}
