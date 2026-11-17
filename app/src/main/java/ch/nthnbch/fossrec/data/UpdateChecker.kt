package ch.nthnbch.fossrec.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the latest published release. Only used when the user turned update checks on;
 * it sends nothing but a plain GET request and never downloads or installs anything.
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/nthnbch/FossRec/releases/latest"

    data class Release(val version: String, val pageUrl: String)

    /** The newest published release, or null when it cannot be read. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                Release(json.getString("tag_name").removePrefix("v"), json.getString("html_url"))
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
