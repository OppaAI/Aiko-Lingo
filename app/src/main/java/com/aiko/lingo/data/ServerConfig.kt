package com.aiko.lingo.data

import com.aiko.lingo.BuildConfig
import java.net.URL

/**
 * Which Aiko-chan server the app talks to.
 *
 * Single source of truth: AIKO_PUBLIC_BASE_URL in Aiko-chan
 * (~/.aiko/.env.age, or config yaml), synced at BUILD time into
 * local.properties (aikoServerUrl=...) → BuildConfig.AIKO_SERVER_URL.
 * There is intentionally no in-app editor and no per-install override:
 * rebuild the APK to repoint the app.
 */
object ServerConfig {

    val DEFAULT_URL: String = BuildConfig.AIKO_SERVER_URL

    /** Baked-in URL, normalized, always ending in "/". */
    fun get(): String = normalize(DEFAULT_URL)

    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return DEFAULT_URL
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://$s"
        }
        return s.removeSuffix("/") + "/"
    }

    /** Short host label for the menu row, e.g. "aiko.ide-chroma.ts.net". */
    fun displayHost(url: String): String {
        return try {
            URL(url).host.ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }
}
