package com.pulse.app

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.view.animation.PathInterpolator
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
                if (expanded) toggleExpanded()
                return false
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private val handler=Handler(Looper.getMainLooper())
    private val floatIn=PathInterpolator(0.16f,0.85f,0.22f,1f)
    private lateinit var windows: WindowManager
    private var root: PillRoot?=null
    private var header: FrameLayout?=null
    private var headerTitle: TextView?=null
    private var preview: TextView?=null
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
        gravity=Gravity.CENTER
        background=ripple(0xff252733.toInt(),14)
        minHeight=dp(42)
        setPadding(dp(13),dp(9),dp(13),dp(9));setOnClickListener {action()}
    }
    private fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x39b6adff),bg(color,radius),null
    )
    /** Native large touch area with title/subtitle, without a grid of squared-off buttons. */
    private fun actionTile(symbol: String, title: String, detail: String, action: () -> Unit): View {
        val item=row().apply {
            background=ripple(0xff1b1e28.toInt(),17)
            minimumHeight=dp(62)
            setPadding(dp(13),dp(9),dp(13),dp(9))
            isClickable=true
            isFocusable=true
            contentDescription="$title. $detail"
            setOnClickListener {action()}
        }
        val glyph=label(symbol,19f,PulseSettings.accent).apply {
            gravity=Gravity.CENTER; background=bg(0xff292638.toInt(),13)
        }
        item.addView(glyph,LinearLayout.LayoutParams(dp(42),dp(42)))
        val texts=col().apply {setPadding(dp(12),0,0,0)}
        texts.addView(label(title,14f,Color.WHITE).apply { typeface=Typeface.DEFAULT_BOLD })
        texts.addView(label(detail,11f,0xff9b9fab.toInt()).apply {maxLines=2})
        item.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
        item.addView(label("›",23f,0xff777c8e.toInt()))
        return item
    }
    private fun entrance(view: View) {
        if(!PulseSettings.animationsOn) return
        view.alpha=0f;view.translationY=dp(14).toFloat();view.scaleX=0.975f;view.scaleY=0.975f
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(330).setInterpolator(floatIn).start()
    }
    private fun toggleExpanded() {
        if(expanded && PulseSettings.animationsOn && panel!=null) {
            val old=panel!!
            old.animate().alpha(0f).translationY(dp(-8).toFloat()).scaleX(0.98f).scaleY(0.98f)
                .setDuration(145).setInterpolator(floatIn)
                .withEndAction {expanded=false;refreshUi()}.start()
        } else {expanded=!expanded;refreshUi()}
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
    // The 68dp tap area is centered on the camera; all visible text begins BELOW it.
    private fun cameraY(): Int = ((cameraRect()?.centerY() ?: dp(23))-dp(34)+dp(PulseSettings.cameraYOffset))
        .coerceIn(-dp(12),resources.displayMetrics.heightPixels-dp(80))
    private fun positionWindow() {
        if (dragging) return
        params?.let { p ->
            val x=if(PulseSettings.cameraAnchor) 0 else dp(PulseSettings.posX)
            val y=if(PulseSettings.cameraAnchor) cameraY() else dp(PulseSettings.posY)
            if (p.x != x || p.y != y) {
                p.x=x;p.y=y
                root?.let { runCatching { windows.updateViewLayout(it,p) } }
            }
        }
    }
    private fun buildPill() {
        if (root!=null) return
        val r=PillRoot(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        root=r
        header=FrameLayout(this).apply { isClickable=true }
        r.addView(header,LinearLayout.LayoutParams(dp(68),dp(68)))
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
        var doubleTap=false
        val detector=GestureDetector(this,object: GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                doubleTap=true
                if(PulseSettings.aiOn) { expanded=false;refreshUi();showChat() }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                // Vertical drags work without a long press; long press enables full X/Y motion.
                dragging=true
            }
        })
        touch.setOnTouchListener { _,e ->
            if(e.actionMasked==MotionEvent.ACTION_DOWN) {
                downX=e.rawX;downY=e.rawY;startX=params?.x?:0;startY=params?.y?:0
                dragging=false;swiped=false;doubleTap=false
            }
            detector.onTouchEvent(e)
            when(e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx=e.rawX-downX;val dy=e.rawY-downY
                    if(!dragging && !swiped && abs(dy)>dp(12) && abs(dy)>abs(dx)*1.15) dragging=true
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
                            p.y=(startY+dy.toInt()).coerceIn(-dp(20),resources.displayMetrics.heightPixels-dp(90))
                            root?.let {runCatching {windows.updateViewLayout(it,p)}}
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx=abs(e.rawX-downX);val dy=abs(e.rawY-downY)
                    if(dragging) params?.let { PulseSettings.setPosition(
                        (it.x/resources.displayMetrics.density).toInt(),(it.y/resources.displayMetrics.density).toInt()) }
                    else if(!swiped && !doubleTap && dx<dp(14) && dy<dp(14)) {
                        // Act on touch release, rather than waiting for single-tap confirmation.
                        // This removes the perceptible ~300ms double-tap delay.
                        toggleExpanded()
                    }
                    if(dragging) handler.postDelayed({dragging=false},140)
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
        if (!anyActivity()) add("Home")
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
        f1EventLine?.text=F1Store.currentEvent().take(86)
        f1WeatherLine?.text=F1Store.currentWeather()
        f1Track?.setTrace(F1Store.track.toList(),F1Store.lapProgress())
    }
    fun refreshUi() {
        val r=root ?: return
        if(!PulseSettings.overlayOn || dragging) return
        val showing=anyActivity()
        val active=showing || expanded
        val target=focus()
        val shape="$active-$expanded-$target-${PulseSettings.accent}-${PulseSettings.cameraDotSize}-${PulseSettings.pillWidth}"
        val render="$shape|${PulseSettings.selectedDriver}|${PulseSettings.animationsOn}|$mediaTitle|$transientNotice|${F1Store.status}|${F1Store.trackStatus}|${F1Store.track.size}|${F1Store.events.size}|${F1Store.playing}"
        if (render==lastRender) {refreshF1Values();positionWindow();return}
        lastRender=render
        if(shape!=lastShape && PulseSettings.animationsOn)
            TransitionManager.beginDelayedTransition(r,AutoTransition().apply{duration=280;interpolator=floatIn})
        lastShape=shape
        val h=header ?: return
        h.removeAllViews()
        h.background=null
        h.elevation=0f
        h.layoutParams=LinearLayout.LayoutParams(dp(68),dp(68))
        h.contentDescription=if(expanded) "Close Pulse" else "Open Pulse. Double tap for AI."
        // Only the true camera-sized dot is visible in this 68x68dp hit target.
        // Camera cutout is not overlaid by text, badges or controls.
        val dot=View(this).apply {background=bg(Color.BLACK,50);isClickable=false}
        h.addView(dot,FrameLayout.LayoutParams(cameraSize(),cameraSize(),Gravity.CENTER))
        r.removeViews(1,(r.childCount-1).coerceAtLeast(0))
        preview=null;panel=null;f1Line=null;f1EventLine=null;f1WeatherLine=null;f1Track=null

        // A separate capsule BELOW the camera, so no letters can be hidden by it.
        if(showing && !expanded) {
            val subtitle=when {
                transientNotice!=null -> transientNotice!!.take(35)
                target=="F1" -> if(F1Store.mode=="LIVE") "●  F1  ·  Live session" else "◉  F1  ·  Replay"
                target=="Media" -> "♫  ${mediaTitle.take(30)}"
                else -> "Pulse"
            }
            val v=label(subtitle,12f,Color.WHITE).apply {
                gravity=Gravity.CENTER
                typeface=Typeface.DEFAULT_BOLD
                maxLines=1
                ellipsize=android.text.TextUtils.TruncateAt.END
                background=ripple(0xff0c0e14.toInt(),22)
                setPadding(dp(18),dp(10),dp(18),dp(10))
                minimumHeight=dp(38)
                contentDescription="$subtitle. Tap to expand Pulse."
                setOnClickListener {toggleExpanded()}
            }
            preview=v
            val width=dp(PulseSettings.pillWidth).coerceAtMost(resources.displayMetrics.widthPixels-dp(28))
            r.addView(v,LinearLayout.LayoutParams(width,dp(42)).apply {topMargin=dp(2)})
            entrance(v)
        }
        if(expanded) {
            val p=col().apply {
                background=bg(0xff101218.toInt(),26,0xff292d39.toInt())
                setPadding(dp(17),dp(17),dp(17),dp(17))
                elevation=dp(12).toFloat()
                clipToPadding=false
            }
            val eyebrow=label("P U L S E    /    ${target.uppercase()}",10f,PulseSettings.accent).apply {
                letterSpacing=0.1f;typeface=Typeface.DEFAULT_BOLD
            }
            p.addView(eyebrow)
            p.addView(spacer(4))
            val top=row()
            top.addView(label(when(target) {
                "F1" -> "Race center"
                "Media" -> "Now playing"
                "AI" -> "Pulse intelligence"
                else -> "Your island"
            },21f,Color.WHITE).apply {typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,dp(44),1f))
            val dismiss=label("×",22f,0xffc7c8d3.toInt()).apply {
                background=ripple(0xff22252f.toInt(),16)
                gravity=Gravity.CENTER
                contentDescription="Close expanded Pulse"
                setOnClickListener {toggleExpanded()}
            }
            top.addView(dismiss,LinearLayout.LayoutParams(dp(44),dp(44)))
            p.addView(top)
            p.addView(spacer(12))
            when(target) {
                "F1" -> {
                    p.addView(label(F1Store.selectedSession?.label ?: "No session selected",12f,0xffacaebb.toInt()))
                    p.addView(spacer(12))
                    f1Line=label("Driver #${PulseSettings.selectedDriver}   ·   ${F1Store.currentPosition()}   ·   Lap ${F1Store.currentLap()}",14f).apply {typeface=Typeface.DEFAULT_BOLD}
                    p.addView(f1Line)
                    p.addView(spacer(8))
                    if(F1Store.track.isNotEmpty()) {
                        f1Track=TrackMapView(this).apply {setTrace(F1Store.track.toList(),F1Store.lapProgress())}
                        p.addView(f1Track,LinearLayout.LayoutParams(-1,dp(145)))
                        p.addView(label("Approximate track progress",10f,0xff9093a3.toInt()))
                    } else p.addView(label(F1Store.trackStatus.take(115),11f,0xffaeb0bd.toInt()))
                    p.addView(spacer(13))
                    f1EventLine=label("${F1Store.currentEvent().take(86)}",12f,0xffd1d2dc.toInt())
                    p.addView(f1EventLine)
                    f1WeatherLine=label(F1Store.currentWeather(),11f,0xff8f95a6.toInt())
                    p.addView(f1WeatherLine)
                    p.addView(spacer(13))
                    val controls=row()
                    controls.addView(button("−15s") {F1Store.seekSeconds(-15)},LinearLayout.LayoutParams(0,dp(44),1f))
                    controls.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
                    controls.addView(button(if(F1Store.playing) "Pause" else "Play") {F1Store.togglePlay()},LinearLayout.LayoutParams(0,dp(44),1f))
                    controls.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
                    controls.addView(button("+15s") {F1Store.seekSeconds(15)},LinearLayout.LayoutParams(0,dp(44),1f))
                    p.addView(controls)
                    p.addView(spacer(10))
                    p.addView(actionTile("■", "Stop replay", "Clear the F1 timeline",{
                        F1Store.stopPlayback();expanded=false;refreshUi()
                    }))
                }
                "Media" -> {
                    p.addView(label(mediaTitle,15f,Color.WHITE).apply {typeface=Typeface.DEFAULT_BOLD})
                    p.addView(spacer(15))
                    val controls=row()
                    controls.addView(button("◀◀") {mediaController?.transportControls?.skipToPrevious()},LinearLayout.LayoutParams(0,dp(46),1f))
                    controls.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
                    controls.addView(button("▶ / ❚❚") {
                        val state=mediaController?.playbackState?.state
                        if(state==PlaybackState.STATE_PLAYING) mediaController?.transportControls?.pause()
                        else mediaController?.transportControls?.play()
                    },LinearLayout.LayoutParams(0,dp(46),2f))
                    controls.addView(Space(this),LinearLayout.LayoutParams(dp(8),1))
                    controls.addView(button("▶▶") {mediaController?.transportControls?.skipToNext()},LinearLayout.LayoutParams(0,dp(46),1f))
                    p.addView(controls)
                }
                "AI" -> p.addView(actionTile("✦", "Ask Pulse", "Open your floating conversation",{
                    expanded=false;refreshUi();showChat()
                }))
                "Notifications" -> p.addView(label(transientNotice ?: "No recent notifications",14f))
                else -> {
                    p.addView(label("No active media or F1 replay.",13f,0xffa9adbb.toInt()))
                    p.addView(spacer(12))
                }
            }
            p.addView(spacer(14))
            p.addView(View(this).apply {setBackgroundColor(0xff2a2d36.toInt())},LinearLayout.LayoutParams(-1,dp(1)))
            p.addView(spacer(12))
            if(PulseSettings.aiOn && target!="AI") {
                p.addView(actionTile("✦", "Ask Pulse", "Quick AI conversation",{
                    expanded=false;refreshUi();showChat()
                }))
                p.addView(spacer(8))
            }
            p.addView(actionTile("⚙", "Control Center", "Appearance, sessions and settings",{
                expanded=false;refreshUi()
                startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }))
            panel=p
            val width=(resources.displayMetrics.widthPixels-dp(24)).coerceAtMost(dp(350))
            // 68dp camera hit area ends BEFORE this panel begins.
            r.addView(p,LinearLayout.LayoutParams(width,-2).apply {topMargin=dp(8)})
            entrance(p)
        }
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
