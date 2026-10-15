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
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.*
import android.widget.*
import kotlin.math.abs

/** Camera-aligned, user-enabled main-display overlay. A display-cutout position is best-effort:
 * Android / One UI can restrict drawing above the status bar. The manual offset is intentional.
 */
class PulseOverlayService : Service() {
    companion object {
        @Volatile var instance: PulseOverlayService? = null
            private set
        fun start(context: Context) {
            val intent=Intent(context,PulseOverlayService::class.java)
            if (Build.VERSION.SDK_INT>=26) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context,PulseOverlayService::class.java)) }
    }
    private inner class PillRoot(context: Context) : LinearLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked==MotionEvent.ACTION_OUTSIDE) {
                if (expanded) { expanded=false; refreshUi() }
                return false
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var root: PillRoot?=null
    private var header: FrameLayout?=null
    private var headerTitle: TextView?=null
    private var panel: LinearLayout?=null
    private var f1Line: TextView?=null
    private var f1EventLine: TextView?=null
    private var f1WeatherLine: TextView?=null
    private var f1Track: TrackMapView?=null
    private var params: WindowManager.LayoutParams?=null
    private var expanded=false
    private var dragging=false
    private var lastShape=""
    private var lastRender=""
    private var chatWindow: LinearLayout?=null
    private val chatTurns=mutableListOf<Pair<String,String>>()
    private var chatHistory: TextView?=null
    private var mediaController: MediaController?=null
    private var mediaTitle="No active media"
    private var mediaActive=false
    private var transientNotice: String?=null
    private var cycle=0
    private val tick=object: Runnable {
        override fun run() {
            cycle++
            F1Store.tick()
            if(cycle%5==0) F1Store.refreshLiveIfNeeded()
            if (PulseSettings.f1On && (cycle==1 || cycle%30==0)) F1Store.checkAutomaticLive()
            if (cycle==1 || cycle%5==0) checkMedia()
            refreshUi()
            handler.postDelayed(this,1000)
        }
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        PulseSettings.init(this)
        instance=this
        windows=getSystemService(WINDOW_SERVICE) as WindowManager
        val channel=NotificationChannel("pulse_running","Pulse running",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(this,"pulse_running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pulse is running")
            .setContentText("Tap for Pulse settings")
            .setContentIntent(intent).setOngoing(true).build()
        startForeground(100,notification)
        if (Settings.canDrawOverlays(this) && PulseSettings.overlayOn) buildPill()
        handler.post(tick)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if (!PulseSettings.overlayOn || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (root==null) buildPill()
        refreshUi()
        return START_STICKY
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int, outline: Int?=null): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
        if (outline!=null) setStroke(dp(1),outline)
    }
    private fun label(text: String,size: Float=13f,color: Int=Color.WHITE): TextView = TextView(this).apply {
        this.text=text; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL
    }
    private fun button(title: String, action: ()->Unit): TextView = label(title,12f).apply {
        gravity=Gravity.CENTER; background=bg(0xff292b36.toInt(),12,0xff343644.toInt())
        setPadding(dp(10),dp(10),dp(10),dp(10));setOnClickListener {action()}
    }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun col(): LinearLayout = LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun spacer(height: Int)=Space(this).apply { minimumHeight=dp(height) }
    private fun cameraSize(): Int {
        val override=PulseSettings.cameraDotSize
        if(override>0) return dp(override)
        val rect=cameraRect()
        return if (rect==null) dp(19) else rect.width().coerceIn(dp(8),dp(36))
    }
    private fun cameraRect(): android.graphics.Rect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val bounds=windows.currentWindowMetrics.bounds
        return windows.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < dp(100) }
            ?.minByOrNull { abs(it.centerX()-bounds.centerX()) }
    }
    /** Offset is relative to the full screen for overlay windows using layout-in-screen. */
    private fun cameraY(): Int = ((cameraRect()?.centerY() ?: dp(23))-dp(22)+dp(PulseSettings.cameraYOffset))
        .coerceIn(-dp(12),resources.displayMetrics.heightPixels-dp(80))
    private fun positionWindow() {
        if (dragging) return
        params?.let { p ->
            p.x=if(PulseSettings.cameraAnchor) 0 else dp(PulseSettings.posX)
            p.y=if(PulseSettings.cameraAnchor) cameraY() else dp(PulseSettings.posY)
            root?.let { runCatching { windows.updateViewLayout(it,p) } }
        }
    }
    private fun buildPill() {
        if (root!=null) return
        val r=PillRoot(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        root=r
        header=FrameLayout(this).apply { isClickable=true }
        r.addView(header,LinearLayout.LayoutParams(dp(44),dp(44)))
        attachGestures(header!!)
        val lp=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            x=if(PulseSettings.cameraAnchor) 0 else dp(PulseSettings.posX)
            y=if(PulseSettings.cameraAnchor) cameraY() else dp(PulseSettings.posY)
        }
        params=lp
        try { windows.addView(r,lp) } catch (_:Exception) {root=null}
        refreshUi()
    }
    private fun attachGestures(touch: View) {
        var downX=0f;var downY=0f;var startX=0;var startY=0
        var swiped=false
        var longDragging=false
        val detector=GestureDetector(this,object: GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if(!dragging && !swiped) {expanded=!expanded; refreshUi()}
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {if(PulseSettings.aiOn) showChat();return true}
            override fun onLongPress(e: MotionEvent) { longDragging=true;dragging=true }
        })
        touch.setOnTouchListener { _,e ->
            detector.onTouchEvent(e)
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX=e.rawX;downY=e.rawY;startX=params?.x?:0;startY=params?.y?:0
                    dragging=false;longDragging=false;swiped=false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx=e.rawX-downX;val dy=e.rawY-downY
                    if(!dragging && !swiped && abs(dy)>dp(9) && abs(dy)>abs(dx)*1.15) dragging=true
                    if(!dragging && !swiped && abs(dx)>dp(42) && abs(dx)>abs(dy)*1.15) {
                        swiped=true;switchFocus(if(dx<0) 1 else -1)
                    }
                    if(dragging) {
                        val p=params
                        if(p!=null) {
                            val screenW=resources.displayMetrics.widthPixels
                            val contentW=if(expanded) dp(350) else dp(PulseSettings.pillWidth)
                            val maxX=((screenW-contentW)/2).coerceAtLeast(0)
                            p.x=(startX+dx.toInt()).coerceIn(-maxX,maxX)
                            p.y=(startY+dy.toInt()).coerceIn(-dp(14),resources.displayMetrics.heightPixels-dp(80))
                            root?.let {runCatching {windows.updateViewLayout(it,p)}}
                        }
                    }
                }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                    if(dragging) params?.let { PulseSettings.setPosition(
                        (it.x/resources.displayMetrics.density).toInt(),(it.y/resources.displayMetrics.density).toInt()) }
                    if(longDragging) { /* A long press can be followed by drag in both axes. */ }
                    if(dragging) handler.postDelayed({dragging=false},220)
                }
            }
            true
        }
    }
    private fun f1Active(): Boolean {
        if(!PulseSettings.f1On || F1Store.selectedSession==null) return false
        return when(F1Store.mode) {
            "REPLAY" -> F1Store.events.isNotEmpty()
            "LIVE" -> F1Store.lastLiveRecord?.let {java.time.Duration.between(it,java.time.Instant.now()).seconds in 0..90} == true
            else -> false
        }
    }
    private fun anyActivity(): Boolean = f1Active() || (PulseSettings.mediaOn && mediaActive) || transientNotice!=null
    private fun availableFocuses(): List<String> = buildList {
        if (f1Active()) add("F1")
        if (PulseSettings.mediaOn && mediaActive) add("Media")
        if (PulseSettings.notificationsOn && transientNotice!=null) add("Notifications")
        if (PulseSettings.aiOn) add("AI")
        add("Settings")
    }
    private fun focus(): String {
        val options=availableFocuses()
        return if(PulseSettings.focus in options) PulseSettings.focus else options.first()
    }
    private fun switchFocus(dir: Int) {
        val list=availableFocuses();val i=list.indexOf(focus()).coerceAtLeast(0)
        PulseSettings.updateFocus(list[(i+dir+list.size)%list.size]);refreshUi()
    }
    fun briefNotification(title: String) {
        if(!PulseSettings.notificationsOn) return
        transientNotice=title
        refreshUi()
        handler.postDelayed({if(transientNotice==title){transientNotice=null;refreshUi()}},4000)
    }
    private fun checkMedia() {
        if(!PulseSettings.mediaOn) {mediaController=null;mediaActive=false;return}
        try {
            val manager=getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions=manager.getActiveSessions(ComponentName(this,PulseNotificationListener::class.java))
            mediaController=sessions.firstOrNull { it.playbackState!=null }
            val state=mediaController?.playbackState?.state
            mediaActive=mediaController!=null && state!=null && state!=PlaybackState.STATE_NONE && state!=PlaybackState.STATE_STOPPED
            mediaTitle=mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Media player"
        } catch (_:SecurityException) {mediaActive=false;mediaTitle="Media permission needed"}
        catch (_:Exception) {mediaActive=false;mediaTitle="Media unavailable"}
    }
    private fun refreshF1Values() {
        if(!expanded || focus()!="F1") return
        f1Line?.text="Driver #${PulseSettings.selectedDriver}  ·  ${F1Store.currentPosition()}  ·  Lap ${F1Store.currentLap()}"
        f1EventLine?.text="Race control: ${F1Store.currentEvent().take(84)}"
        f1WeatherLine?.text=F1Store.currentWeather()
        f1Track?.setTrace(F1Store.track.toList(),F1Store.lapProgress())
    }
    fun refreshUi() {
        val r=root ?:return
        if(!PulseSettings.overlayOn || dragging) return
        val active=anyActivity() || expanded
        val focus=focus()
        val shape="$active-$expanded-$focus-${PulseSettings.pillWidth}-${PulseSettings.accent}-${PulseSettings.cameraDotSize}"
        val render="$shape|${PulseSettings.selectedDriver}|${PulseSettings.animationsOn}|$mediaTitle|$transientNotice|${F1Store.status}|${F1Store.trackStatus}|${F1Store.track.size}|${F1Store.events.size}|${F1Store.playing}"
        if (render==lastRender) {refreshF1Values();positionWindow();return}
        lastRender=render
        if(shape!=lastShape && PulseSettings.animationsOn) TransitionManager.beginDelayedTransition(r,AutoTransition().apply{duration=190})
        lastShape=shape
        val h=header ?:return
        h.removeAllViews()
        if(!active) {
            h.background=null
            val diameter=cameraSize()
            val dot=View(this).apply {background=bg(Color.BLACK,50)}
            h.addView(dot,FrameLayout.LayoutParams(diameter,diameter,Gravity.CENTER))
            h.layoutParams=LinearLayout.LayoutParams(dp(44),dp(44))
        } else {
            h.background=bg(0xff090a0e.toInt(),50,0xff343644.toInt())
            h.elevation=dp(8).toFloat()
            h.layoutParams=LinearLayout.LayoutParams(dp(PulseSettings.pillWidth),dp(44))
            val content=row().apply {
                setPadding(dp(10),0,dp(12),0)
                gravity=Gravity.CENTER_VERTICAL
            }
            val lead=label(when {transientNotice!=null -> "●";focus=="F1" -> "◉";focus=="Media" -> "♫";focus=="AI" -> "✦";else -> "•"},13f,PulseSettings.accent)
            content.addView(lead,LinearLayout.LayoutParams(dp(16),-1))
            // Nothing is drawn across the central selfie-camera hole.
            content.addView(Space(this),LinearLayout.LayoutParams((cameraSize()+dp(9)).coerceAtLeast(dp(26)),-1))
            val title=label(when {
                transientNotice!=null -> transientNotice!!.take(26)
                focus=="F1" -> if(F1Store.mode=="LIVE") "F1 LIVE" else "F1 REPLAY"
                focus=="Media" -> mediaTitle.take(20)
                focus=="AI" -> "Pulse AI"
                else -> "Pulse"
            },12f,Color.WHITE).apply{typeface=Typeface.DEFAULT_BOLD;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
            headerTitle=title
            content.addView(title,LinearLayout.LayoutParams(0,-1,1f))
            h.addView(content,FrameLayout.LayoutParams(-1,-1))
        }
        r.removeViews(1,(r.childCount-1).coerceAtLeast(0))
        f1Line=null; f1EventLine=null; f1WeatherLine=null; f1Track=null
        if(expanded) {
            val p=col().apply {
                background=bg(0xff12141b.toInt(),22,0xff303441.toInt())
                setPadding(dp(15),dp(13),dp(15),dp(15))
                elevation=dp(10).toFloat()
            }
            val top=row()
            top.addView(label(focus.uppercase(),14f,PulseSettings.accent).apply{typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,dp(32),1f))
            top.addView(button("×") {expanded=false;refreshUi()})
            p.addView(top)
            p.addView(spacer(8))
            when(focus) {
                "F1" -> {
                    p.addView(label(F1Store.selectedSession?.label ?: "No session",13f))
                    p.addView(spacer(8))
                    f1Line=label("Driver #${PulseSettings.selectedDriver}  ·  ${F1Store.currentPosition()}  ·  Lap ${F1Store.currentLap()}",13f)
                    p.addView(f1Line)
                    p.addView(spacer(8))
                    if(F1Store.track.isNotEmpty()) {
                        f1Track=TrackMapView(this).apply {setTrace(F1Store.track.toList(),F1Store.lapProgress())}
                        p.addView(f1Track,LinearLayout.LayoutParams(-1,dp(150)))
                        p.addView(label("Approximate lap progress · not precise GPS",10f,0xffa7a7b3.toInt()))
                    } else p.addView(label(F1Store.trackStatus.take(110),11f,0xffb3afc2.toInt()))
                    p.addView(spacer(8))
                    f1EventLine=label("Race control: ${F1Store.currentEvent().take(84)}",11f,0xffa6a8b5.toInt())
                    p.addView(f1EventLine)
                    f1WeatherLine=label(F1Store.currentWeather(),11f,0xffa6a8b5.toInt())
                    p.addView(f1WeatherLine)
                    p.addView(spacer(10))
                    val controls=row()
                    controls.addView(button("−15s") {F1Store.seekSeconds(-15)})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button(if(F1Store.playing) "Pause" else "Play") {F1Store.togglePlay()})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button("+15s") {F1Store.seekSeconds(15)})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button("Latest") {F1Store.goLatest()})
                    p.addView(controls)
                    p.addView(spacer(9))
                    p.addView(button("Stop F1 replay / timeline") {F1Store.stopPlayback(); expanded=false;refreshUi()})
                }
                "Media" -> {
                    p.addView(label(mediaTitle,15f));p.addView(spacer(10))
                    val controls=row()
                    controls.addView(button("◀◀") {mediaController?.transportControls?.skipToPrevious()})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button("Play") {mediaController?.transportControls?.play()})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button("Pause") {mediaController?.transportControls?.pause()})
                    controls.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    controls.addView(button("▶▶") {mediaController?.transportControls?.skipToNext()})
                    p.addView(controls)
                }
                "AI" -> p.addView(button("Open floating AI chat") {showChat();expanded=false;refreshUi()})
                "Notifications" -> p.addView(label(transientNotice ?: "No new notifications",13f))
                else -> p.addView(label("No active sessions. Configure Pulse or choose a replay.",12f))
            }
            p.addView(spacer(12))
            val footer=row()
            footer.addView(button("AI") {if(PulseSettings.aiOn){showChat();expanded=false;refreshUi()}})
            footer.addView(Space(this),LinearLayout.LayoutParams(0,dp(1),1f))
            footer.addView(button("Control Center") {
                expanded=false;refreshUi()
                startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            p.addView(footer)
            panel=p
            val screenW=resources.displayMetrics.widthPixels
            val width=(screenW-dp(20)).coerceAtMost(dp(350))
            r.addView(p,LinearLayout.LayoutParams(width,-2).apply {topMargin=dp(8)})
        } else panel=null
        positionWindow()
    }
    private fun showChat() {
        if(chatWindow!=null) return
        val body=col().apply {background=bg(0xff14161e.toInt(),22,0xff353344.toInt());setPadding(dp(14),dp(14),dp(14),dp(14));elevation=dp(10).toFloat()}
        val bar=row()
        bar.addView(label("✦  Pulse AI",16f,PulseSettings.accent).apply{typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,dp(40),1f))
        bar.addView(button("×") {closeChat()})
        body.addView(bar)
        val scroll=ScrollView(this)
        chatHistory=label("Ask Pulse anything. OpenAI API usage may cost money.\n"+chatTurns.joinToString("\n"){"${if(it.first=="user") "You" else "Pulse"}: ${it.second}"},13f).apply{gravity=Gravity.TOP}
        scroll.addView(chatHistory)
        body.addView(scroll,LinearLayout.LayoutParams(-1,dp(190)))
        body.addView(spacer(10))
        val entry=row()
        val input=EditText(this).apply {
            hint="Ask something…";setHintTextColor(0xff9a9cab.toInt());setTextColor(Color.WHITE)
            textSize=13f;setSingleLine(false);maxLines=3
            background=bg(0xff252834.toInt(),12);setPadding(dp(10),dp(8),dp(10),dp(8))
        }
        entry.addView(input,LinearLayout.LayoutParams(0,-2,1f))
        entry.addView(button("Send") {
            val question=input.text.toString().trim()
            if(question.isNotBlank()) {
                input.setText("");chatTurns.add("user" to question)
                chatHistory?.append("\nYou: $question\nPulse: …")
                AiClient.ask(this,chatTurns.toList()) {result ->
                    val answer=result.getOrElse {"Error: ${it.message}"}
                    result.onSuccess {chatTurns.add("assistant" to it)}
                    chatHistory?.append("\n$answer\n")
                    scroll.post {scroll.fullScroll(View.FOCUS_DOWN)}
                }
            }
        })
        body.addView(entry)
        val lp=WindowManager.LayoutParams(dp(335),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y=dp(95)
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        chatWindow=body
        try {windows.addView(body,lp)} catch (_:Exception) {chatWindow=null}
    }
    private fun closeChat() {
        chatWindow?.let {runCatching {windows.removeView(it)}};chatWindow=null;chatHistory=null
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick);closeChat()
        root?.let {runCatching {windows.removeView(it)}};root=null
        instance=null
        super.onDestroy()
    }
}
