package com.pulse.app

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.abs

/** A foreground, user-enabled overlay. No accessibility service or hidden screen capture is used. */
class PulseOverlayService : Service() {
    companion object {
        @Volatile var instance: PulseOverlayService? = null
            private set
        fun start(context: Context) {
            val i = Intent(context, PulseOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
        fun stop(context: Context) { context.stopService(Intent(context, PulseOverlayService::class.java)) }
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var root: LinearLayout? = null
    private var header: LinearLayout? = null
    private var heading: TextView? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var chatWindow: LinearLayout? = null
    private var chatParams: WindowManager.LayoutParams? = null
    private val chatTurns = mutableListOf<Pair<String, String>>()
    private var chatHistory: TextView? = null
    private var mediaController: MediaController? = null
    private var mediaTitle = "No active media"
    private var transientNotice: String? = null
    private var cycle = 0
    private val tick = object : Runnable {
        override fun run() {
            cycle++
            F1Store.tick()
            if (PulseSettings.f1On && (cycle == 1 || cycle % 30 == 0)) F1Store.checkAutomaticLive()
            if (cycle == 1 || cycle % 5 == 0) checkMedia()
            refreshUi()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        PulseSettings.init(this)
        instance = this
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        val channel = NotificationChannel("pulse_running", "Pulse running", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val intent = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this,"pulse_running")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Pulse is running")
            .setContentText("Tap to open Pulse settings").setContentIntent(intent)
            .setOngoing(true).build()
        startForeground(100,notification)
        if (Settings.canDrawOverlays(this) && PulseSettings.overlayOn) buildPill()
        handler.post(tick)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PulseSettings.overlayOn || !Settings.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        if (root == null) buildPill()
        refreshUi()
        return START_STICKY
    }
    private fun dp(value: Int): Int = (value*resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 13f, color: Int = Color.WHITE): TextView = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun action(label: String, onClick: () -> Unit): TextView = text(label,12f).apply {
        gravity = Gravity.CENTER; setPadding(dp(10),dp(9),dp(10),dp(9))
        background = background(0xff363240.toInt(),12)
        setOnClickListener { onClick() }
    }
    private fun row(gap: Int = 5) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(gap),dp(gap),dp(gap),dp(gap))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun buildPill() {
        if (root != null) return
        root = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        header = row(0).apply {
            background = background(0xff18171f.toInt(),32)
            setPadding(dp(17),dp(10),dp(17),dp(10))
            elevation = dp(7).toFloat()
        }
        heading = text("PULSE",13f).apply { typeface = Typeface.DEFAULT_BOLD }
        header!!.addView(heading)
        root!!.addView(header!!, LinearLayout.LayoutParams(dp(PulseSettings.pillWidth), dp(43)))
        initGestures()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = PulseSettings.posX; y = dp(PulseSettings.posY) }
        pillParams = params
        try { windows.addView(root,params) } catch (_: Exception) { root = null }
        refreshUi()
    }
    private fun initGestures() {
        var downX = 0f; var downY = 0f
        var originalX = 0; var originalY = 0
        var dragging = false
        var swiped = false
        val detector = GestureDetector(this,object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!swiped && !dragging) { expanded = !expanded; refreshUi() }; return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (PulseSettings.aiOn) showChat()
                return true
            }
            override fun onLongPress(e: MotionEvent) { dragging = true }
        })
        header?.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    originalX = pillParams?.x ?: 0; originalY = pillParams?.y ?: 0
                    dragging = false; swiped = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        pillParams?.let {
                            it.x = originalX + (ev.rawX-downX).toInt()
                            it.y = (originalY + (ev.rawY-downY).toInt()).coerceAtLeast(0)
                            root?.let { view -> runCatching { windows.updateViewLayout(view,it) } }
                        }
                    } else if (abs(ev.rawX - downX) > dp(65) && abs(ev.rawX-downX) > abs(ev.rawY-downY)*1.3) {
                        if (!swiped) { swiped = true; switchFocus(if (ev.rawX < downX) 1 else -1) }
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) {
                    pillParams?.let { PulseSettings.setPosition(it.x,(it.y/resources.displayMetrics.density).toInt()) }
                    handler.postDelayed({ dragging = false },350)
                }
            }
            true
        }
    }
    private fun enabledFocuses(): List<String> = buildList {
        if (PulseSettings.f1On) add("F1")
        if (PulseSettings.mediaOn) add("Media")
        if (PulseSettings.notificationsOn) add("Notifications")
        if (PulseSettings.aiOn) add("AI")
    }.ifEmpty { listOf("Pulse") }
    private fun switchFocus(dir: Int) {
        val list = enabledFocuses()
        val i = list.indexOf(PulseSettings.focus).takeIf { it >= 0 } ?: 0
        PulseSettings.setFocus(list[(i+dir+list.size)%list.size]); refreshUi()
    }
    fun briefNotification(title: String) {
        if (!PulseSettings.notificationsOn) return
        transientNotice = title
        refreshUi()
        handler.postDelayed({ if (transientNotice == title) { transientNotice = null; refreshUi() } },4500)
    }
    private fun checkMedia() {
        if (!PulseSettings.mediaOn) { mediaController = null; return }
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(this,PulseNotificationListener::class.java))
            mediaController = sessions.firstOrNull { it.playbackState != null }
            val metadata = mediaController?.metadata
            mediaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "No active media"
        } catch (_: SecurityException) {
            mediaController = null; mediaTitle = "Enable notification access for media"
        } catch (_: Exception) { mediaTitle = "Media unavailable" }
    }
    fun refreshUi() {
        val r = root ?: return
        if (!PulseSettings.overlayOn) return
        val currentFocus = enabledFocuses().let { if (PulseSettings.focus in it) PulseSettings.focus else it.first() }
        val latest = F1Store.lastLiveRecord
        val fresh = latest != null && java.time.Duration.between(latest,java.time.Instant.now()).seconds in 0..90
        heading?.apply {
            text = when {
                transientNotice != null -> "●  ${transientNotice}"
                currentFocus == "F1" -> "◉  F1 ${if (F1Store.mode == "LIVE" && fresh) "LIVE" else F1Store.mode}"
                currentFocus == "Media" -> "♫  ${mediaTitle.take(24)}"
                currentFocus == "Notifications" -> "▣  Notifications"
                currentFocus == "AI" -> "✦  Pulse AI"
                else -> "PULSE"
            }
            setTextColor(PulseSettings.accent)
            maxLines = 1
        }
        val width = dp(PulseSettings.pillWidth)
        header?.layoutParams = LinearLayout.LayoutParams(width,dp(43))
        while (r.childCount > 1) r.removeViewAt(1)
        if (expanded) {
            val panel = column().apply {
                background = background(0xff22212c.toInt(),18)
                setPadding(dp(10),dp(10),dp(10),dp(12))
            }
            panel.addView(text("${currentFocus.uppercase()}  •  swipe pill to change focus",11f,0xffaaa5b8.toInt()))
            when (currentFocus) {
                "F1" -> {
                    panel.addView(text(F1Store.selectedSession?.label ?: "No F1 session selected",12f))
                    panel.addView(text("Driver #${PulseSettings.selectedDriver}  ${F1Store.currentPosition()}  Lap ${F1Store.currentLap()}",14f))
                    panel.addView(text(F1Store.status.take(110),11f,0xffa9a7b8.toInt()))
                    val controls = row()
                    controls.addView(action("−15s") { F1Store.seekSeconds(-15) })
                    controls.addView(action(if (F1Store.playing) "Pause" else "Play") { F1Store.togglePlay() })
                    controls.addView(action("+15s") { F1Store.seekSeconds(15) })
                    controls.addView(action("Latest") { F1Store.goLatest() })
                    panel.addView(controls)
                }
                "Media" -> {
                    panel.addView(text(mediaTitle,13f))
                    val controls = row()
                    controls.addView(action("◀◀") { mediaController?.transportControls?.skipToPrevious() })
                    controls.addView(action("Play") { mediaController?.transportControls?.play() })
                    controls.addView(action("Pause") { mediaController?.transportControls?.pause() })
                    controls.addView(action("▶▶") { mediaController?.transportControls?.skipToNext() })
                    panel.addView(controls)
                }
                "AI" -> panel.addView(action("Open floating chat") { showChat() })
                "Notifications" -> panel.addView(text("Recent notifications appear temporarily on the pill.",12f))
                else -> panel.addView(text("Enable a feature in Control Center.",12f))
            }
            val bottom = row()
            bottom.addView(action("AI") { if (PulseSettings.aiOn) showChat() })
            bottom.addView(action("Settings") {
                startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            bottom.addView(action("Close") { expanded = false; refreshUi() })
            panel.addView(bottom)
            r.addView(panel,LinearLayout.LayoutParams(dp(332),WindowManager.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        pillParams?.let { params -> runCatching { windows.updateViewLayout(r,params) } }
    }
    private fun showChat() {
        if (chatWindow != null) return
        val container = column().apply {
            background = background(0xff201e29.toInt(),18)
            setPadding(dp(13),dp(12),dp(13),dp(12))
            elevation = dp(10).toFloat()
        }
        val bar = row()
        bar.addView(text("✦ Pulse AI",16f,PulseSettings.accent).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0,dp(34),1f))
        bar.addView(action("−") { hideChat() })
        bar.addView(action("×") { hideChat(clear = true) })
        container.addView(bar)
        val scroll = ScrollView(this)
        val previous = chatTurns.joinToString("\n") { (role, content) ->
            "${if (role == "user") "You" else "Pulse"}: $content"
        }
        chatHistory = text("Ask Pulse anything. API usage may cost money.\n$previous",13f).apply {
            setTextColor(Color.WHITE); gravity = Gravity.TOP
        }
        scroll.addView(chatHistory)
        container.addView(scroll,LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,dp(190)))
        val entry = row()
        val input = EditText(this).apply {
            hint = "Message…"; setHintTextColor(0xffa7a1b8.toInt()); setTextColor(Color.WHITE)
            textSize = 13f; setSingleLine(false); maxLines = 3; background = background(0xff34313e.toInt(),12)
            setPadding(dp(10),dp(7),dp(10),dp(7))
        }
        entry.addView(input,LinearLayout.LayoutParams(0,WindowManager.LayoutParams.WRAP_CONTENT,1f))
        val send = action("Send") {
            val question = input.text.toString().trim()
            if (question.isNotBlank()) {
                input.setText("")
                chatTurns.add("user" to question)
                chatHistory?.append("\nYou: $question\nPulse: …")
                val request = chatTurns.toList()
                AiClient.ask(this,request) { result ->
                    result.onSuccess { chatTurns.add("assistant" to it) }
                    val response = result.getOrElse { "Error: ${it.message}" }
                    chatHistory?.append("\n$response\n")
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
        entry.addView(send)
        container.addView(entry)
        val params = WindowManager.LayoutParams(dp(325),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(130)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        chatParams = params; chatWindow = container
        try { windows.addView(container,params) } catch (_: Exception) { chatWindow = null }
    }
    private fun hideChat(clear: Boolean = false) {
        chatWindow?.let { runCatching { windows.removeView(it) } }; chatWindow = null; chatHistory = null
        if (clear) chatTurns.clear()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick); hideChat()
        root?.let { runCatching { windows.removeView(it) } }; root = null
        instance = null
        super.onDestroy()
    }
}
