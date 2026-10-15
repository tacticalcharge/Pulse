package com.pulse.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object HttpUtil {
    fun getArray(url: String, bearer: String? = null): JSONArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 16000
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}: ${c.errorStream?.bufferedReader()?.readText()?.take(160) ?: "request failed"}")
            return JSONArray(c.inputStream.bufferedReader().use { it.readText() })
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
