package com.pulse.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

object AiClient {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    /** Turns are intentionally only held by the overlay process; not persisted. */
    fun ask(context: Context, turns: List<Pair<String,String>>, callback: (Result<String>) -> Unit) {
        val key = KeyVault.read(context)
        if (key.isNullOrEmpty()) { callback(Result.failure(IllegalStateException("Set your OpenAI API key under System first."))); return }
        val model = PulseSettings.openAiModel
        io.execute {
            val result = runCatching {
                val arr = JSONArray()
                turns.takeLast(10).forEach { (role, content) ->
                    arr.put(JSONObject().put("role", if (role == "assistant") "assistant" else "user").put("content", content))
                }
                val json = JSONObject().put("model",model)
                    .put("instructions","You are Pulse, a concise helpful assistant in a small Android overlay. Do not claim to know live race data unless supplied.")
                    .put("input",arr).put("max_output_tokens",500)
                val response = HttpUtil.postJson("https://api.openai.com/v1/responses",json,key)
                val output = response.optJSONArray("output") ?: JSONArray()
                val lines = mutableListOf<String>()
                for (i in 0 until output.length()) {
                    val parts = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                    for (j in 0 until parts.length()) {
                        val part = parts.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") lines.add(part.optString("text"))
                    }
                }
                lines.joinToString("\n").ifEmpty { throw IllegalStateException("No text was returned by this model.") }
            }
            main.post { callback(result) }
        }
    }
}
