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
    val start: Instant, val end: Instant, val circuit: String = "", val meetingKey: Int = 0
) {
    val label: String get() = "$year · $country · $name"
}
data class TrackPoint(val x: Float, val y: Float)
data class F1Weather(val at: Instant, val air: Double, val track: Double, val raining: Boolean)

data class F1Event(
    val at: Instant, val driver: Int? = null, val position: Int? = null,
    val lap: Int? = null, val message: String? = null
)

object F1Store {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val sessions = mutableStateListOf<F1Session>()
    val events = mutableStateListOf<F1Event>()
    /** One sampled lap: a real coordinate trace, not a licensed circuit graphic. */
    val track = mutableStateListOf<TrackPoint>()
    val weather = mutableStateListOf<F1Weather>()
    var trackStatus by mutableStateOf("Choose a replay to load its track trace."); private set
    private var requestGeneration = 0
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
    private var lastLiveFetchAttempt = Instant.EPOCH
    private var lastMapAttempt = Instant.EPOCH
    private var fetchingLive = false

    private fun timestamp(raw: String): Instant = OffsetDateTime.parse(raw).toInstant()
    private fun sessionFrom(json: JSONObject): F1Session? = runCatching {
        F1Session(json.getInt("session_key"),json.optInt("year"),
            json.optString("country_name", "Grand Prix"),json.optString("session_name","Session"),
            timestamp(json.getString("date_start")), timestamp(json.getString("date_end")),
            json.optString("circuit_short_name", ""),json.optInt("meeting_key"))
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
        // Optional pit stop events help make the replay timeline richer.
        runCatching {
            val pits=HttpUtil.getArray("https://api.openf1.org/v1/pit?session_key=$sessionKey$encoded")
            for(i in 0 until pits.length()) {
                val obj=pits.optJSONObject(i) ?: continue
                runCatching {
                    val driver=obj.optInt("driver_number",0)
                    val lap=obj.optInt("lap_number",0)
                    output.add(F1Event(timestamp(obj.getString("date")),driver=driver.takeIf{it>0},
                        lap=lap.takeIf{it>0},message="Pit lane · car #$driver · lap $lap"))
                }
            }
        }
        return output.sortedBy { it.at }
    }
    /** Download at most one real lap worth of (x,y) location points to outline the circuit.
     * OpenF1 positions are approximate. The moving dot is estimated from lap progress,
     * not an exact live GPS point. Never present the dot as precise live telemetry.
     */
    private fun fetchTrackTrace(sessionKey: Int, driver: Int): List<TrackPoint> {
        val laps = HttpUtil.getArray("https://api.openf1.org/v1/laps?session_key=$sessionKey&driver_number=$driver")
        var chosenStart: Instant? = null
        var duration = 100.0
        for (i in 0 until laps.length()) {
            val lap = laps.optJSONObject(i) ?: continue
            val secs = lap.optDouble("lap_duration",0.0)
            val date = lap.optString("date_start")
            if (secs in 45.0..180.0 && date.isNotBlank() && date != "null") {
                val parsed = runCatching { timestamp(date) }.getOrNull()
                if (parsed != null) { chosenStart=parsed; duration=secs; break }
            }
        }
        val start = chosenStart ?: return emptyList()
        val end = start.plusMillis((duration*1000).toLong())
        val raw = HttpUtil.getArray("https://api.openf1.org/v1/location?session_key=$sessionKey&driver_number=$driver&date>=${start}&date<=${end}")
        val data = mutableListOf<TrackPoint>()
        for (i in 0 until raw.length()) {
            val obj = raw.optJSONObject(i) ?: continue
            if (obj.has("x") && obj.has("y")) data.add(TrackPoint(obj.optDouble("x").toFloat(),obj.optDouble("y").toFloat()))
        }
        if (data.size < 15) return emptyList()
        return data.filterIndexed { index, _ -> index % 2 == 0 }.take(500)
    }

    private fun fetchWeather(sessionKey: Int): List<F1Weather> {
        val raw=HttpUtil.getArray("https://api.openf1.org/v1/weather?session_key=$sessionKey")
        val output=mutableListOf<F1Weather>()
        for(i in 0 until raw.length()) {
            val obj=raw.optJSONObject(i) ?: continue
            runCatching {output.add(F1Weather(timestamp(obj.getString("date")),
                obj.optDouble("air_temperature",0.0),obj.optDouble("track_temperature",0.0),
                obj.optInt("rainfall",0)>0))}
        }
        return output.sortedBy {it.at}
    }
    fun currentWeather(): String {
        val event=weather.lastOrNull {it.at.toEpochMilli() <= cursorMs} ?: return "Weather unavailable"
        return "Air %.1f°C · Track %.1f°C · %s".format(event.air,event.track,if(event.raining) "Rain" else "Dry")
    }

    fun stopPlayback() {
        requestGeneration++
        mode="IDLE"; playing=false; loading=false; selectedSession=null; events.clear(); track.clear(); weather.clear()
        cursorMs=0; trackStatus="Choose a replay to load its track trace."
        status="F1 playback stopped. Pulse will return to the camera when idle."
        if (PulseSettings.focus == "F1") PulseSettings.updateFocus("Media")
        PulseOverlayService.instance?.refreshUi()
    }
    fun positionTable(): List<Pair<Int,Int>> {
        if (events.isEmpty()) return emptyList()
        val at = cursorMs
        val map=mutableMapOf<Int,Int>()
        events.forEach { e -> if (e.at.toEpochMilli() <= at && e.driver != null && e.position != null) map[e.driver] = e.position }
        return map.entries.sortedBy { it.value }.map { it.value to it.key }
    }
    /** Approximate fraction of selected driver's current lap, from lap-start records. */
    fun lapProgress(): Float {
        if (events.isEmpty()) return 0f
        val laps = events.filter { it.driver==PulseSettings.selectedDriver && it.lap!=null && it.message==null }
        val previous = laps.lastOrNull { it.at.toEpochMilli()<=cursorMs } ?: return 0f
        val next = laps.firstOrNull { it.at.isAfter(previous.at) } ?: return 0f
        val duration = next.at.toEpochMilli()-previous.at.toEpochMilli()
        return ((cursorMs-previous.at.toEpochMilli()).toFloat()/duration.coerceAtLeast(1L)).coerceIn(0f,1f)
    }

    fun listYear(year: Int) {
        if (selectedYear == year && sessions.isNotEmpty()) return
        selectedYear = year
        status = "Loading $year sessions… (cached on this device when available)"
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
        if (!PulseSettings.f1On) {status="Enable F1 before starting a replay.";return}
        val generation=++requestGeneration
        loading=true
        selectedSession=session; track.clear(); weather.clear(); trackStatus="Loading track outline…"
        mode="REPLAY"; playing=false; status="Loading historical positions and race control…"
        io.execute {
            val result=runCatching { fetchEvents(session.key) }
            main.post {
                if (generation != requestGeneration) return@post
                loading=false
                result.onSuccess {
                    events.clear(); events.addAll(it)
                    cursorMs=(it.firstOrNull()?.at ?: session.start).toEpochMilli()
                    playing=it.isNotEmpty()
                    status=if (it.isEmpty()) "No historical events for this session." else "Replay loaded: ${it.size} events."
                    if(it.isEmpty()) {mode="IDLE";selectedSession=null}
                    else PulseSettings.updateFocus("F1")
                    PulseOverlayService.instance?.refreshUi()
                }.onFailure {
                    status="Replay failed: ${it.message}"; playing=false;mode="IDLE";selectedSession=null
                }
            }
            val map=runCatching { fetchTrackTrace(session.key,PulseSettings.selectedDriver) }
            main.post {
                if (generation != requestGeneration) return@post
                map.onSuccess {
                    track.clear(); track.addAll(it)
                    trackStatus=if (it.isEmpty()) "No usable location trace for this driver/session." else "Approximate circuit outline from one recorded lap."
                    PulseOverlayService.instance?.refreshUi()
                }.onFailure { trackStatus="Track trace unavailable: ${it.message}" }
            }
            val sky=runCatching {fetchWeather(session.key)}
            main.post {
                if(generation != requestGeneration) return@post
                sky.onSuccess {weather.clear();weather.addAll(it)}
            }
        }
    }
    fun reloadTrackForDriver() {
        val session=selectedSession ?: return
        val generation=requestGeneration
        track.clear(); trackStatus="Loading selected driver's circuit trace…"
        io.execute {
            val response=runCatching { fetchTrackTrace(session.key,PulseSettings.selectedDriver) }
            main.post {
                if (generation != requestGeneration || selectedSession?.key!=session.key) return@post
                response.onSuccess {
                    track.clear(); track.addAll(it)
                    trackStatus=if(it.isEmpty()) "No track trace for this driver." else "Approximate circuit outline from one recorded lap."
                }.onFailure {trackStatus="Track trace unavailable: ${it.message}"}
                PulseOverlayService.instance?.refreshUi()
            }
        }
    }
    /** Called by the overlay's visible foreground service. Detection uses session schedule; feed verification is shown separately. */
    fun checkAutomaticLive() {
        if (!PulseSettings.f1On || !PulseSettings.autoF1) return
        val now = Instant.now()
        if (Duration.between(lastLivePoll,now).seconds < 240) return
        lastLivePoll = now
        io.execute {
            val result = runCatching {
                parseSessions(HttpUtil.getArray("https://api.openf1.org/v1/sessions?year=${java.time.Year.now().value}"))
                    .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            }
            main.post {
                result.onSuccess { active ->
                    liveSession = active
                    if (active == null && mode == "LIVE" && selectedSession?.end?.let {
                        Duration.between(it,Instant.now()).toMinutes()>10
                    } == true) stopPlayback()
                    if (active != null) {
                        if (lastAutoFocused != active.key) {
                            lastAutoFocused = active.key
                            selectedSession = active; mode = "LIVE"; events.clear(); track.clear(); weather.clear()
                            lastLiveEventAt=Instant.EPOCH; lastLiveRecord=null; lastLiveFetchAttempt=Instant.EPOCH
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
        selectedSession = active; mode = "LIVE"; playing = true; track.clear(); weather.clear()
        lastLiveEventAt=Instant.EPOCH;lastLiveRecord=null;lastLiveFetchAttempt=Instant.EPOCH
        events.clear(); cursorMs = Instant.now().toEpochMilli()
        status = "Following session window; checking live feed…"
        pollLive(active)
        PulseOverlayService.instance?.refreshUi()
    }
    fun refreshLiveIfNeeded() {
        val active=liveSession ?: return
        if(mode=="LIVE") pollLive(active)
    }
    private fun loadLiveMapIfNeeded(session: F1Session) {
        if(track.isNotEmpty() || Duration.between(lastMapAttempt,Instant.now()).seconds < 600) return
        lastMapAttempt=Instant.now()
        val key=session.key
        io.execute {
            val data=runCatching {fetchTrackTrace(key,PulseSettings.selectedDriver)}
            main.post {
                if(mode!="LIVE" || selectedSession?.key!=key) return@post
                data.onSuccess {
                    if(it.isNotEmpty()) {track.clear();track.addAll(it);trackStatus="Approximate circuit trace from a recorded lap."}
                }
            }
        }
    }
    private fun pollLive(session: F1Session) {
        if (!PulseSettings.f1On || mode != "LIVE" || selectedSession?.key != session.key || fetchingLive) return
        if(Duration.between(lastLiveFetchAttempt,Instant.now()).seconds < 45) return
        lastLiveFetchAttempt=Instant.now();fetchingLive=true
        val since = if (lastLiveEventAt == Instant.EPOCH) Instant.now().minusSeconds(600) else lastLiveEventAt.minusSeconds(2)
        io.execute {
            val result = runCatching { fetchEvents(session.key, since) }
            main.post {
                fetchingLive=false
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
                        loadLiveMapIfNeeded(session)
                    }
                    val fresh = lastLiveRecord?.let { Duration.between(it,Instant.now()).seconds in 0..90 } == true
                    status = if (fresh) "Live feed connected" else "Session window active; data delayed or unavailable"
                    PulseOverlayService.instance?.refreshUi()
                }.onFailure {
                    if(it.message?.contains("401")==true || it.message?.contains("403")==true) {
                        lastLiveFetchAttempt=Instant.now().plusSeconds(90)
                    }
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
    fun togglePlay() {
        if (events.isEmpty() || mode == "IDLE") return
        playing = !playing; PulseOverlayService.instance?.refreshUi()
    }
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
