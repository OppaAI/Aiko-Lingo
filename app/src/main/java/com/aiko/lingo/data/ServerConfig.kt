package com.aiko.lingo.data

import android.content.Context
import java.net.URL

/**
 * Which Aiko-chan server the app talks to.
 *
 * No address is hardcoded into network calls: MainActivity builds Retrofit
 * from [get], and the user can change it in-app (menu → Server ⚙️) without
 * reinstalling. Changing networks (home Tailnet, Tailnet IP, funnel URL)
 * is a settings edit, not a code edit.
 */
object ServerConfig {

    const val DEFAULT_URL = "https://aiko.ide-chroma.ts.net/"

    private const val PREFS = "aiko_server"
    private const val KEY_URL = "server_url"

    /** Stored URL, normalized, always ending in "/". */
    fun get(context: Context): String {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        return normalize(raw)
    }

    /** Persist [raw] after normalizing; returns the stored value. */
    fun set(context: Context, raw: String): String {
        val normalized = normalize(raw)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, normalized)
            .apply()
        return normalized
    }

    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return DEFAULT_URL
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://$s"
        }
        return s.removeSuffix("/") + "/"
    }

    /** True for parseable http(s) URLs with a host (port/path allowed). */
    fun isValid(raw: String): Boolean {
        return try {
            val url = URL(normalize(raw))
            (url.protocol == "http" || url.protocol == "https") &&
                url.host.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /** Short host label for the menu row, e.g. "aiko.ide-chroma.ts.net". */
    fun displayHost(url: String): String {
        return try {
            URL(url).host.ifBlank { url }
        } catch (e: Exception) {
            url
        }
    }
}
