package com.pulse.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.ArrayDeque
import kotlin.math.max

/**
 * OpenF1 requests share ONE limiter, including replay, track, weather, and live polling.
 * 800 ms between starts and 25 requests per rolling minute sit below the public tier.
 * Persisted historical responses let replay switches avoid re-downloading the same data.
 * 429s trigger a global cooldown (Retry-After when available) rather than rapid retries.
 */
object HttpUtil {
    class RateLimitedException(val retrySeconds: Long) : IOException("OpenF1 rate limit. Try again in about $retrySeconds seconds.")
    private val lock = Object()
    private val starts = ArrayDeque<Long>()
    private var nextAllowedMs = 0L
    private var serverCooldownUntilMs = 0L
    @Volatile private var cacheDir: File? = null

    fun init(context: Context) {
        cacheDir = File(context.applicationContext.cacheDir, "pulse_openf1").apply { mkdirs() }
    }
    private fun ttlMs(url: String): Long {
        if (!url.startsWith("https://api.openf1.org/v1/")) return 0L
        // Time-filtered data is used by live polling, so do not cache it.
        if (url.contains("date>=")) {
            // The one-lap circuit request has a closed historical time window.
            // Live polling only sends a lower bound and must always bypass cache.
            return if (url.contains("date<=")) 24L * 60 * 60 * 1000 else 0L
        }
        if (url.contains("/sessions?year=")) return 6L * 60 * 60 * 1000
        return 24L * 60 * 60 * 1000
    }
    private fun cacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
    private fun cached(url: String, ttl: Long): JSONArray? {
        if (ttl <= 0) return null
        val f = cacheFile(url) ?: return null
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > ttl) return null
        return runCatching { JSONArray(f.readText(Charsets.UTF_8)) }.getOrNull()
    }
    private fun save(url: String, ttl: Long, json: String) {
        if (ttl <= 0 || json.length > 9_000_000) return
        val f = cacheFile(url) ?: return
        runCatching {
            f.writeText(json, Charsets.UTF_8)
            // At most 50 MB of cache, prune oldest first. Cache isn't critical to playback.
            val files = f.parentFile?.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            var size = files.sumOf { it.length() }
            for (item in files) {
                if (size <= 50_000_000) break
                size -= item.length(); item.delete()
            }
        }
    }
    private fun reserveOpenF1Slot() {
        synchronized(lock) {
            while (true) {
                val now = System.currentTimeMillis()
                while (starts.isNotEmpty() && now - starts.first > 60_000L) starts.removeFirst()
                if (serverCooldownUntilMs > now) {
                    throw RateLimitedException(((serverCooldownUntilMs - now) + 999L) / 1000L)
                }
                val perMinute = if (starts.size >= 25) starts.peekFirst() + 60_100L else now
                val available = max(perMinute, nextAllowedMs)
                val delay = available - now
                if (delay <= 0) {
                    starts.addLast(now)
                    nextAllowedMs = now + 800L
                    return
                }
                // Called only from the F1 worker thread; never block the UI thread.
                lock.wait(delay.coerceAtMost(60_000L))
            }
        }
    }
    private fun retryAfterMs(header: String?): Long {
        val seconds = header?.trim()?.toLongOrNull()
        if (seconds != null) return seconds.coerceIn(5,300) * 1000L
        val date = runCatching { java.time.ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        return (date?.minus(System.currentTimeMillis()) ?: 60_000L).coerceIn(5_000L,300_000L)
    }
    fun getArray(url: String, bearer: String? = null): JSONArray {
        val isOpenF1 = url.startsWith("https://api.openf1.org/v1/")
        val ttl = if (bearer.isNullOrBlank()) ttlMs(url) else 0L
        cached(url, ttl)?.let { return it }
        if (isOpenF1) reserveOpenF1Slot()
        // Another queued request might have cached the same URL while we waited.
        cached(url,ttl)?.let { return it }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            val code = c.responseCode
            if (code == 429 && isOpenF1) {
                val delay = retryAfterMs(c.getHeaderField("Retry-After"))
                synchronized(lock) { serverCooldownUntilMs = max(serverCooldownUntilMs, System.currentTimeMillis() + delay) }
                throw RateLimitedException((delay + 999L) / 1000L)
            }
            if (code !in 200..299) {
                val description = c.errorStream?.bufferedReader()?.use { it.readText().take(160) } ?: "request failed"
                throw IOException("OpenF1 HTTP $code: $description")
            }
            val response = c.inputStream.bufferedReader().use { it.readText() }
            val result = JSONArray(response)
            save(url, ttl, response)
            return result
        } finally { c.disconnect() }
    }
    fun postJson(url: String, json: JSONObject, bearer: String): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 45000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (c.responseCode !in 200..299) {
                val err = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IOException("OpenAI HTTP ${c.responseCode}: ${err ?: body.take(200)}")
            }
            return JSONObject(body)
        } finally { c.disconnect() }
    }
}
