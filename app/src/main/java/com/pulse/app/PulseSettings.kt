package com.pulse.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-local observable settings; persisted immediately in SharedPreferences. */
object PulseSettings {
    private lateinit var prefs: SharedPreferences
    var overlayOn by mutableStateOf(false); private set
    var f1On by mutableStateOf(true); private set
    var autoF1 by mutableStateOf(true); private set
    var mediaOn by mutableStateOf(true); private set
    var notificationsOn by mutableStateOf(false); private set
    var aiOn by mutableStateOf(true); private set
    var pillWidth by mutableStateOf(150); private set
    var accent by mutableStateOf(0xffac91ff.toInt()); private set
    var focus by mutableStateOf("F1"); private set
    var selectedDriver by mutableStateOf(1); private set
    var openAiModel by mutableStateOf("gpt-4.1-mini"); private set
    var posX by mutableStateOf(0); private set
    var posY by mutableStateOf(75); private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("pulse", Context.MODE_PRIVATE)
        overlayOn = prefs.getBoolean("overlayOn", false)
        f1On = prefs.getBoolean("f1On", true)
        autoF1 = prefs.getBoolean("autoF1", true)
        mediaOn = prefs.getBoolean("mediaOn", true)
        notificationsOn = prefs.getBoolean("notificationsOn", false)
        aiOn = prefs.getBoolean("aiOn", true)
        pillWidth = prefs.getInt("pillWidth", 150)
        accent = prefs.getInt("accent", 0xffac91ff.toInt())
        focus = prefs.getString("focus", "F1") ?: "F1"
        selectedDriver = prefs.getInt("selectedDriver", 1)
        openAiModel = prefs.getString("model", "gpt-4.1-mini") ?: "gpt-4.1-mini"
        posX = prefs.getInt("posX", 0)
        posY = prefs.getInt("posY", 75)
    }
    private fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun save(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    private fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun setOverlay(v: Boolean) { overlayOn = v; save("overlayOn",v) }
    fun setF1(v: Boolean) { f1On = v; save("f1On",v); if (!v && focus == "F1") updateFocus("Media") }
    fun updateAutoF1(v: Boolean) { autoF1 = v; save("autoF1",v) }
    fun setMedia(v: Boolean) { mediaOn = v; save("mediaOn",v) }
    fun setNotifications(v: Boolean) { notificationsOn = v; save("notificationsOn",v) }
    fun setAi(v: Boolean) { aiOn = v; save("aiOn",v) }
    fun setWidth(v: Int) { pillWidth = v; save("pillWidth",v) }
    fun updateAccent(v: Int) { accent = v; save("accent",v) }
    fun updateFocus(v: String) { focus = v; save("focus",v) }
    fun setDriver(v: Int) { selectedDriver = v; save("selectedDriver",v) }
    fun setModel(v: String) { openAiModel = v; save("model",v) }
    fun setPosition(x: Int, y: Int) { posX = x; posY = y; save("posX",x); save("posY",y) }
}
