package com.pulse.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.OffsetDateTime
import java.time.Instant
import java.util.concurrent.Executors

/** OpenF1 HTTP client plus a small timestamp-based playback engine. */
data class F1Session(
    val key: Int, val year: Int, val country: String, val name: String,
    val start: Instant, val end: Instant
) {
    val label: String get() = "$year · $country · $name"
}
data class F1Event(
    val at: Instant, val driver: Int? = null, val position: Int? = null,
    val lap: Int? = null, val message: String? = null
)

object F1Store {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val sessions = mutableStateListOf<F1Session>()
    val events = mutableStateListOf<F1Event>()
    var selectedSession by mutableStateOf<F1Session?>(null); private set
    var status by mutableStateOf("Choose a season to load sessions."); private set
    var mode by mutableStateOf("IDLE"); private set
    var playing by mutableStateOf(false); private set
    var cursorMs by mutableStateOf(0L); private set
    var selectedYear by mutableStateOf(2025); private set
    var liveSession by mutableStateOf<F1Session?>(null); private set
    var lastLiveRecord by mutableStateOf<Instant?>(null); private set
    private var lastAutoFocused: Int? = null
    private var loading = false
    private var lastLivePoll = Instant.EPOCH
    private var lastLiveEventAt = Instant.EPOCH

    private fun timestamp(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()
    private fun sessionFrom(json: JSONObject): F1Session? = runCatching {
        F1Session(json.getInt("session_key"),json.optInt("year"),
            json.optString("country_name", "Grand Prix"),json.optString("session_name","Session"),
            timestamp(json.getString("date_start")), timestamp(json.getString("date_end")))
    }.getOrNull()
    private fun parseSessions(raw: JSONArray): List<F1Session> {
        val list = mutableListOf<F1Session>()
        for (i in 0 until raw.length()) raw.optJSONObject(i)?.let { sessionFrom(it)?.let(list::add) }
        return list.sortedByDescending { it.start }
    }
    private fun fetchEvents(sessionKey: Int, onlySince: Instant? = null): List<F1Event> {
        val encoded = onlySince?.let { "&date>=${it}" } ?: ""
        val positions = HttpUtil.getArray("https://api.openf1.org/v1/position?session_key=$sessionKey$encoded")
        val output = mutableListOf<F1Event>()
        for (i in 0 until positions.length()) {
            val obj = positions.optJSONObject(i) ?: continue
            runCatching {
                output.add(F1Event(timestamp(obj.getString("date")),obj.getInt("driver_number"),obj.getInt("position")))
            }
        }
        // Race-control events add flags and context to the time line.
        val rc = HttpUtil.getArray("https://api.openf1.org/v1/race_control?session_key=$sessionKey$encoded")
        for (i in 0 until rc.length()) {
            val obj = rc.optJSONObject(i) ?: continue
            runCatching {
                output.add(F1Event(timestamp(obj.getString("date")),lap=obj.optInt("lap_number",0).takeIf { it > 0 },
                    message=obj.optString("message").take(200)))
            }
        }
        // Lap-start records provide timeline labels when this endpoint is available.
        runCatching {
            val laps = HttpUtil.getArray("https://api.openf1.org/v1/laps?session_key=$sessionKey&driver_number=${PulseSettings.selectedDriver}$encoded")
            for (i in 0 until laps.length()) {
                val obj = laps.optJSONObject(i) ?: continue
                val start = obj.optString("date_start")
                if (start.isNotBlank() && start != "null") runCatching {
                    output.add(F1Event(timestamp(start), driver=obj.optInt("driver_number"), lap=obj.optInt("lap_number")))
                }
            }
        }
        return output.sortedBy { it.at }
    }
    fun listYear(year: Int) {
        selectedYear = year
        status = "Loading $year sessions…"
        io.execute {
            val result = runCatching { parseSessions(HttpUtil.getArray("https://api.openf1.org/v1/sessions?year=$year")) }
            main.post {
                result.onSuccess {
                    sessions.clear(); sessions.addAll(it)
                    status = if (it.isEmpty()) "No sessions returned for $year." else "${it.size} sessions available. Choose one to replay."
                }.onFailure { status = "Could not load sessions: ${it.message}" }
            }
        }
    }
    fun loadReplay(session: F1Session) {
        if (loading) return
        loading = true
        selectedSession = session
        mode = "REPLAY"; playing = false; status = "Loading historical positions and race control…"
        io.execute {
            val result = runCatching { fetchEvents(session.key) }
            main.post {
                loading = false
                result.onSuccess {
                    events.clear(); events.addAll(it)
                    cursorMs = (it.firstOrNull()?.at ?: session.start).toEpochMilli()
                    playing = it.isNotEmpty()
                    status = if (it.isEmpty()) "No historical events returned for this session." else "Replay loaded: ${it.size} events."
                    PulseOverlayService.instance?.refreshUi()
                }.onFailure { status = "Replay failed: ${it.message}"; playing = false }
            }
        }
    }
    /** Called by the overlay's visible foreground service. Detection uses session schedule; feed verification is shown separately. */
    fun checkAutomaticLive() {
        if (!PulseSettings.f1On || !PulseSettings.autoF1) return
        val now = Instant.now()
        if (Duration.between(lastLivePoll,now).seconds < 75) return
        lastLivePoll = now
        io.execute {
            val result = runCatching {
                parseSessions(HttpUtil.getArray("https://api.openf1.org/v1/sessions?year=${java.time.Year.now().value}"))
                    .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            }
            main.post {
                result.onSuccess { active ->
                    liveSession = active
                    if (active != null) {
                        if (lastAutoFocused != active.key) {
                            lastAutoFocused = active.key
                            selectedSession = active; mode = "LIVE"; events.clear()
                            cursorMs = now.toEpochMilli(); playing = true
                            PulseSettings.updateFocus("F1")
                            PulseOverlayService.instance?.refreshUi()
                        }
                        pollLive(active)
                    }
                }.onFailure { status = "Session check failed: ${it.message}" }
            }
        }
    }
    fun watchLiveNow() {
        val active = liveSession ?: run { status = "No current session found in the schedule."; return }
        selectedSession = active; mode = "LIVE"; playing = true
        events.clear(); cursorMs = Instant.now().toEpochMilli()
        status = "Following session window; checking live feed…"
        pollLive(active)
        PulseOverlayService.instance?.refreshUi()
    }
    private fun pollLive(session: F1Session) {
        if (!PulseSettings.f1On || mode != "LIVE" || selectedSession?.key != session.key) return
        val since = if (lastLiveEventAt == Instant.EPOCH) Instant.now().minusSeconds(600) else lastLiveEventAt.minusSeconds(2)
        io.execute {
            val result = runCatching { fetchEvents(session.key, since) }
            main.post {
                result.onSuccess { incoming ->
                    val latestKnown = events.map { "${it.at}:${it.driver}:${it.position}:${it.message}" }.toHashSet()
                    val appended = incoming.filter { latestKnown.add("${it.at}:${it.driver}:${it.position}:${it.message}") }
                    if (appended.isNotEmpty()) {
                        val wasNearLive = cursorMs >= (events.lastOrNull()?.at?.toEpochMilli() ?: 0L) - 10000
                        events.addAll(appended); events.sortBy { it.at }
                        while (events.size > 6000) events.removeAt(0)
                        lastLiveEventAt = events.last().at
                        lastLiveRecord = lastLiveEventAt
                        if (playing && wasNearLive) cursorMs = lastLiveEventAt.toEpochMilli()
                    }
                    val fresh = lastLiveRecord?.let { Duration.between(it,Instant.now()).seconds in 0..90 } == true
                    status = if (fresh) "Live feed connected" else "Session window active; data delayed or unavailable"
                    PulseOverlayService.instance?.refreshUi()
                }.onFailure {
                    status = "Live data unavailable: ${it.message}. OpenF1 live access may require a subscription."
                    PulseOverlayService.instance?.refreshUi()
                }
            }
        }
    }
    fun tick() {
        if (playing && events.isNotEmpty()) {
            val last = events.last().at.toEpochMilli()
            // A live buffer cannot seek ahead of the latest received event.
            cursorMs = (cursorMs + 1000L).coerceAtMost(last)
            if (mode == "REPLAY" && cursorMs >= last) playing = false
        }
    }
    fun togglePlay() { playing = !playing; PulseOverlayService.instance?.refreshUi() }
    fun seekSeconds(seconds: Long) {
        if (events.isEmpty()) return
        cursorMs = (cursorMs + seconds * 1000L).coerceIn(events.first().at.toEpochMilli(),events.last().at.toEpochMilli())
        PulseOverlayService.instance?.refreshUi()
    }
    fun jumpEvent(next: Boolean) {
        if (events.isEmpty()) return
        val candidate = if (next) events.firstOrNull { it.at.toEpochMilli() > cursorMs + 1000L }
            else events.lastOrNull { it.at.toEpochMilli() < cursorMs - 1000L }
        candidate?.let { cursorMs = it.at.toEpochMilli() }
        PulseOverlayService.instance?.refreshUi()
    }
    fun goLatest() { if (events.isNotEmpty()) { cursorMs = events.last().at.toEpochMilli(); playing = true; PulseOverlayService.instance?.refreshUi() } }
    fun seekFraction(fraction: Float) {
        if (events.isEmpty()) return
        val start = events.first().at.toEpochMilli(); val end = events.last().at.toEpochMilli()
        cursorMs = start + ((end-start)*fraction.coerceIn(0f,1f)).toLong()
        PulseOverlayService.instance?.refreshUi()
    }
    fun fraction(): Float {
        if (events.size < 2) return 0f
        val first = events.first().at.toEpochMilli(); val last = events.last().at.toEpochMilli()
        return ((cursorMs-first).toDouble()/(last-first).coerceAtLeast(1)).toFloat().coerceIn(0f,1f)
    }
    fun currentPosition(): String {
        val e = events.lastOrNull { it.at.toEpochMilli() <= cursorMs && it.driver == PulseSettings.selectedDriver && it.position != null }
        return e?.position?.let { "P$it" } ?: "—"
    }
    fun currentEvent(): String = events.lastOrNull { it.at.toEpochMilli() <= cursorMs && !it.message.isNullOrBlank() }?.message ?: "No race-control message at this time"
    fun currentLap(): String = events.lastOrNull { it.at.toEpochMilli() <= cursorMs && it.lap != null && it.driver == PulseSettings.selectedDriver }?.lap?.toString() ?: "—"
}
