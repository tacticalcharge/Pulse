package com.pulse.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Ink=Color(0xff08090d)
private val Panel=Color(0xff15171d)
private val Raised=Color(0xff1c1f28)
private val Outline=Color(0xff2d303d)
private val Muted=Color(0xff9ea4b4)

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private val notificationRequest=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PulseSettings.init(this)
        overlayGranted=Settings.canDrawOverlays(this)
        setContent {
            val accent=Color(PulseSettings.accent)
            MaterialTheme(colorScheme=darkColorScheme(
                primary=accent,onPrimary=Ink,secondary=accent,
                background=Ink,onBackground=Color.White,surface=Panel,onSurface=Color.White,
                surfaceVariant=Raised,outline=Outline)) {
                PulseApp(
                    overlayGranted=overlayGranted,
                    requestOverlay={startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))},
                    openNotificationAccess={startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},
                    enableOverlay={ enabled ->
                        if(enabled && !Settings.canDrawOverlays(this)) {
                            Toast.makeText(this,"Grant overlay permission first",Toast.LENGTH_LONG).show()
                        } else {
                            PulseSettings.setOverlay(enabled)
                            if(enabled) {
                                if(Build.VERSION.SDK_INT>=33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                PulseOverlayService.start(this)
                            } else PulseOverlayService.stop(this)
                        }
                    },
                    update={PulseOverlayService.instance?.refreshUi()}
                )
            }
        }
    }
    override fun onResume() {super.onResume();overlayGranted=Settings.canDrawOverlays(this)}
}

private enum class Tab(val title: String,val symbol: String) {
    HOME("Home","⌂"),F1("Racing","◉"),FEATURES("Features","▦"),LOOK("Design","◈"),SYSTEM("System","⚙")
}
@Composable
private fun PulseApp(overlayGranted: Boolean,requestOverlay:()->Unit,
                     openNotificationAccess:()->Unit,enableOverlay:(Boolean)->Unit,update:()->Unit) {
    var tab by remember {mutableStateOf(Tab.HOME)}
    LaunchedEffect(tab) {if(tab==Tab.F1 && F1Store.sessions.isEmpty()) F1Store.listYear(2026)}
    Scaffold(containerColor=Ink,
        bottomBar={
            NavigationBar(containerColor=Color(0xff101218),tonalElevation=0.dp) {
                Tab.entries.forEach { option ->
                    NavigationBarItem(selected=tab==option,onClick={tab=option},
                        icon={Text(option.symbol,fontSize=19.sp)},label={Text(option.title,fontSize=10.sp)},
                        alwaysShowLabel=true,
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=MaterialTheme.colorScheme.primary,
                            selectedTextColor=Color.White,
                            unselectedIconColor=Muted,unselectedTextColor=Muted,
                            indicatorColor=Raised))
                }
            }
        },
        topBar={
            Column(Modifier.fillMaxWidth().background(Ink).padding(start=20.dp,end=20.dp,top=23.dp,bottom=12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(13.dp)).background(Color(PulseSettings.accent)),contentAlignment=Alignment.Center) {
                        Text("P",fontWeight=FontWeight.Black,fontSize=19.sp,color=Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PULSE",fontWeight=FontWeight.Black,fontSize=21.sp,letterSpacing=3.sp)
                        Text("Galaxy Z Flip5 · Personal edition",fontSize=11.sp,color=Muted)
                    }
                    StatusPill(if(PulseSettings.overlayOn) "RUNNING" else "OFF",PulseSettings.overlayOn)
                }
                Spacer(Modifier.height(17.dp))
                Text(tab.title,fontSize=27.sp,fontWeight=FontWeight.Bold)
                Text(when(tab) {
                    Tab.HOME->"A quieter, more useful camera island."
                    Tab.F1->"Live sessions, circuit views and race replays."
                    Tab.FEATURES->"Only show what matters to you."
                    Tab.LOOK->"Fine-tune your floating island."
                    Tab.SYSTEM->"Permissions, connectivity and API settings."
                },fontSize=12.sp,color=Muted)
            }
        }
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),
            verticalArrangement=Arrangement.spacedBy(13.dp)) {
            when(tab) {
                Tab.HOME -> HomeTab(overlayGranted,requestOverlay,enableOverlay,update,{tab=Tab.F1},{tab=Tab.LOOK})
                Tab.F1 -> RacingTab(update)
                Tab.FEATURES -> FeaturesTab(openNotificationAccess,update)
                Tab.LOOK -> DesignTab(update)
                Tab.SYSTEM -> SystemTab(overlayGranted,requestOverlay,openNotificationAccess)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable
private fun StatusPill(text: String,on: Boolean) {
    Surface(color=if(on) Color(0xff1a322d) else Raised,shape=RoundedCornerShape(40.dp)) {
        Text(text,Modifier.padding(horizontal=11.dp,vertical=7.dp),fontSize=10.sp,fontWeight=FontWeight.Bold,
            color=if(on) Color(0xff7ed5b8) else Muted)
    }
}
@Composable
private fun Group(title: String,subtitle: String?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(21.dp),color=Panel,border=BorderStroke(1.dp,Outline),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            if(subtitle!=null) Text(subtitle,fontSize=12.sp,color=Muted,lineHeight=17.sp)
            content()
        }
    }
}
@Composable
private fun Note(value:String) { Text(value,fontSize=12.sp,color=Muted,lineHeight=18.sp) }
@Composable
private fun OptionToggle(name:String,description:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(name,fontSize=14.sp,fontWeight=FontWeight.Medium)
            Text(description,fontSize=11.sp,color=Muted,lineHeight=15.sp)
        }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable
private fun DarkButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    OutlinedButton(onClick=onClick,modifier=modifier,border=BorderStroke(1.dp,Outline)) {Text(text)}
}
@Composable
private fun HomeTab(overlayGranted:Boolean,requestOverlay:()->Unit,enableOverlay:(Boolean)->Unit,
                    update:()->Unit,openF1:()->Unit,openDesign:()->Unit) {
    Group("Your island","Centered on the Z Flip5's selfie camera. When idle, the overlay shrinks to a tiny black touch target.") {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xff06070b)),contentAlignment=Alignment.TopCenter) {
            Box(Modifier.padding(top=14.dp).size(24.dp).background(Color.Black,RoundedCornerShape(100)),contentAlignment=Alignment.Center) {}
            Text("CAMERA-ALIGNED IDLE PREVIEW",Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp),fontSize=10.sp,color=Muted)
        }
        OptionToggle("Floating pill","Enable the system overlay",PulseSettings.overlayOn,enableOverlay)
        if(!overlayGranted) Button(onClick=requestOverlay,modifier=Modifier.fillMaxWidth()) {Text("Allow display over other apps")}
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            DarkButton("Customize position",openDesign,Modifier.weight(1f))
            DarkButton("Open F1",openF1,Modifier.weight(1f))
        }
    }
    Group("Quick controls") {
        OptionToggle("Formula 1","Automatically focus live sessions",PulseSettings.f1On,{PulseSettings.setF1(it);update()})
        OptionToggle("Media","Show active music / video sessions",PulseSettings.mediaOn,{PulseSettings.setMedia(it);update()})
        OptionToggle("AI chat","Double-tap to open OpenAI chat",PulseSettings.aiOn,{PulseSettings.setAi(it);update()})
    }
    Group("Current activity") {
        val active=F1Store.mode!="IDLE" && PulseSettings.f1On
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(active) "F1 ${F1Store.mode}" else "Quiet mode",fontWeight=FontWeight.SemiBold)
                Note(if(active) F1Store.selectedSession?.label ?: "Session loading" else "Nothing to display? The camera remains unobtrusive.")
            }
            if(active) DarkButton("Stop", onClick={ F1Store.stopPlayback();update() })
        }
    }
}

@Composable
private fun RacingTab(update:()->Unit) {
    var search by remember {mutableStateOf("")}
    var filter by remember {mutableStateOf("Race")}
    var opened by remember {mutableStateOf<Int?>(null)}
    var driverNumber by remember {mutableStateOf(PulseSettings.selectedDriver.toString())}
    Group("Racing preferences") {
        OptionToggle("Enable F1","Master switch for all F1 content",PulseSettings.f1On,{PulseSettings.setF1(it);update()})
        OptionToggle("Automatic live focus","Show F1 at the beginning of a detected session",PulseSettings.autoF1,{PulseSettings.updateAutoF1(it);update()})
        Note("Public historical OpenF1 data is available from 2023 onward. Real-time data requires provider access; scheduled activity alone is not proof that live positions are available.")
    }
    Group("Find a track","Select a year, search by track or country, then pick the session you want to replay.") {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf(2026,2025,2024,2023).forEach { year ->
                FilterChip(selected=F1Store.selectedYear==year,onClick={F1Store.listYear(year);opened=null},label={Text("$year")})
            }
        }
        OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text("Search circuit or country")},placeholder={Text("e.g. Monza, Japan, Monaco")})
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("Race","Qualifying","All").forEach { t ->
                FilterChip(selected=filter==t,onClick={filter=t},label={Text(t)})
            }
        }
        Note(F1Store.status)
        val list=F1Store.sessions.filter { s ->
            (search.isBlank() || listOf(s.country,s.circuit,s.name).any { it.contains(search,true) }) &&
                (filter=="All" || (filter=="Race" && (s.name=="Race" || s.name=="Sprint")) ||
                    (filter=="Qualifying" && s.name.contains("Qualifying",true)))
        }
        val groups=list.groupBy { it.meetingKey.takeIf { key->key!=0 } ?: it.country.hashCode() }
            .toList().sortedByDescending {(_,sessions)->sessions.maxOfOrNull{it.start} ?: Instant.EPOCH}
        if(groups.isEmpty()) Note(if(F1Store.sessions.isEmpty()) "Load a year to see circuits." else "No tracks match those filters.")
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            groups.take(45).forEach { (key,sessions) ->
                val first=sessions.first()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Raised)) {
                    Row(Modifier.fillMaxWidth().clickable {opened=if(opened==key)null else key}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(first.circuit.ifBlank {first.country},fontWeight=FontWeight.SemiBold)
                            Text("${first.country}  ·  ${sessions.size} matching session(s)",fontSize=11.sp,color=Muted)
                        }
                        Text(if(opened==key) "⌃" else "⌄",fontSize=22.sp,color=Muted)
                    }
                    if(opened==key) {
                        sessions.sortedByDescending {it.start}.forEach { session ->
                            HorizontalDivider(color=Outline)
                            Row(Modifier.fillMaxWidth().clickable {F1Store.loadReplay(session);update()}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(session.name,Modifier.weight(1f),fontSize=13.sp)
                                Text("Load  ›",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    Group("Playback", "Replay and Viaplay sync controls work on Pulse's data timeline, not the Viaplay video player.") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(F1Store.selectedSession?.let {"${it.country} · ${it.name}"} ?: "No replay active",fontWeight=FontWeight.Medium)
                Note(F1Store.status)
            }
            StatusPill(F1Store.mode,F1Store.mode=="LIVE")
        }
        if(F1Store.track.isNotEmpty()) {
            AndroidView(factory={TrackMapView(it)},update={view->view.setTrace(F1Store.track.toList(),F1Store.lapProgress())},
                modifier=Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xff0c0e14)))
        } else {
            Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(15.dp)).background(Raised),contentAlignment=Alignment.Center) {
                Text(F1Store.trackStatus,color=Muted,fontSize=12.sp,modifier=Modifier.padding(10.dp))
            }
        }
        Note("Circuit outline uses one recorded lap of approximate OpenF1 location data. The dot estimates progress from lap timestamps, not precise GPS.")
        Text("Driver #${PulseSettings.selectedDriver}: ${F1Store.currentPosition()} · Lap ${F1Store.currentLap()}",fontWeight=FontWeight.Medium)
        Note(F1Store.currentEvent())
        Note(F1Store.currentWeather())
        if(F1Store.events.isNotEmpty()) {
            Slider(value=F1Store.fraction(),onValueChange=F1Store::seekFraction)
            Text(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(F1Store.cursorMs)),fontSize=12.sp,color=Muted)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DarkButton("◀ Event", onClick={ F1Store.jumpEvent(false) })
            DarkButton("−15s", onClick={ F1Store.seekSeconds(-15) })
            Button(onClick={F1Store.togglePlay()},enabled=F1Store.events.isNotEmpty()) {Text(if(F1Store.playing) "Pause" else "Play")}
            DarkButton("+15s", onClick={ F1Store.seekSeconds(15) })
            DarkButton("Event ▶", onClick={ F1Store.jumpEvent(true) })
            DarkButton("Latest", onClick={ F1Store.goLatest() })
        }
        if(F1Store.mode!="IDLE") {
            Button(onClick={F1Store.stopPlayback();update()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xff3d252e))) {
                Text("Stop replay / hide F1 timeline",color=Color(0xffffbec7))
            }
        }
        if(F1Store.liveSession!=null) DarkButton("Follow current session", onClick={ F1Store.watchLiveNow();update() })
        OutlinedTextField(value=driverNumber,onValueChange={driverNumber=it.filter(Char::isDigit).take(2)},singleLine=true,
            label={Text("Selected driver car number")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions.Default)
        DarkButton("Apply driver", onClick={
            driverNumber.toIntOrNull()?.takeIf {it in 1..99}?.let {PulseSettings.setDriver(it);F1Store.reloadTrackForDriver();update()}
        })
    }
    if(F1Store.events.isNotEmpty()) Group("Timing order","Positions reconstructed from available timestamped updates. May be incomplete.") {
        val table=F1Store.positionTable()
        if(table.isEmpty()) Note("No positions available yet.")
        table.take(22).forEach { (pos,driver) ->
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Text("P$pos",Modifier.width(50.dp),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                Text("Car #$driver",Modifier.weight(1f),fontSize=13.sp)
            }
        }
    }
}
@Composable
private fun FeaturesTab(openNotificationAccess:()->Unit,update:()->Unit) {
    Group("Available modules","Swiping left or right on the island changes the active focus.") {
        OptionToggle("F1 sessions","Live detection and selected replay",PulseSettings.f1On,{PulseSettings.setF1(it);update()})
        OptionToggle("Media controls","Playback controls for supported sessions",PulseSettings.mediaOn,{PulseSettings.setMedia(it);update()})
        OptionToggle("Notification indicators","Only show a short notification title",PulseSettings.notificationsOn,{PulseSettings.setNotifications(it);update()})
        OptionToggle("OpenAI chat","Open the floating chat with a double tap",PulseSettings.aiOn,{PulseSettings.setAi(it);update()})
        DarkButton("Grant notification access",openNotificationAccess,Modifier.fillMaxWidth())
    }
    Group("When nothing is active") {
        Text("Camera-only idle mode",fontWeight=FontWeight.SemiBold)
        Note("F1 IDLE text is no longer shown. If no race replay, active media or notification needs attention, the island shrinks around the selfie-camera cutout. Tap that area to open the menu.")
    }
}
@Composable
private fun DesignTab(update:()->Unit) {
    Group("Pill appearance", "OLED-black styling, restrained highlights and softer layout transitions.") {
        Text("Active width · ${PulseSettings.pillWidth} dp",fontWeight=FontWeight.Medium)
        Slider(value=PulseSettings.pillWidth.toFloat(),onValueChange={PulseSettings.setWidth(it.toInt());update()},valueRange=125f..290f)
        OptionToggle("Smooth transitions","Subtle size and panel animations",PulseSettings.animationsOn,{PulseSettings.setAnimations(it);update()})
        Text("Accent",fontWeight=FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Violet" to 0xff9c86ff.toInt(),"Ice" to 0xff68d9f0.toInt(),"Mint" to 0xff87e6c3.toInt(),"Rose" to 0xfffc92ba.toInt()).forEach { (name,color) ->
                FilterChip(selected=PulseSettings.accent==color,onClick={PulseSettings.updateAccent(color);update()},label={Text(name)})
            }
        }
    }
    Group("Camera alignment","Designed for the inner display only. Samsung may restrict overlays in the status bar, so verify this on your phone.") {
        OptionToggle("Align to camera automatically","Use Android's detected display cutout when available",PulseSettings.cameraAnchor,{PulseSettings.setCameraAnchor(it);update()})
        Text("Camera vertical adjustment · ${PulseSettings.cameraYOffset} dp",fontWeight=FontWeight.Medium)
        Slider(value=PulseSettings.cameraYOffset.toFloat(),onValueChange={PulseSettings.setCameraYOffset(it.toInt());update()},valueRange=-45f..160f)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Camera cover · ${if(PulseSettings.cameraDotSize==0) "Automatic" else "${PulseSettings.cameraDotSize} dp"}",Modifier.weight(1f),fontWeight=FontWeight.Medium)
            DarkButton("Auto", onClick={ PulseSettings.setCameraDotSize(0);update() })
        }
        Slider(value=(PulseSettings.cameraDotSize.takeIf{it>0} ?: 19).toFloat(),
            onValueChange={PulseSettings.setCameraDotSize(it.toInt());update()},valueRange=8f..42f)
        Note("Camera cover diameter is detected automatically where possible. If the black circle looks too large or small, adjust it here.")
        Note("Drag vertically directly on the pill for free positioning. Long-press, then drag to move in either direction. Dragging switches from camera alignment to manual placement.")
        Text("Manual position: X ${PulseSettings.posX} dp  ·  Y ${PulseSettings.posY} dp",fontSize=12.sp,color=Muted)
        DarkButton("Reset to selfie camera",{PulseSettings.resetCameraPosition();update()},Modifier.fillMaxWidth())
    }
    Group("Gestures") {
        Note("Tap: expand or collapse · Double-tap: AI · Swipe sideways: switch focus · Drag vertically: reposition · Tap outside expanded pill: dismiss.")
    }
}
@Composable
private fun SystemTab(overlayGranted:Boolean,requestOverlay:()->Unit,openNotificationAccess:()->Unit) {
    Group("Android permissions") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Display over apps",Modifier.weight(1f))
            StatusPill(if(overlayGranted) "ALLOWED" else "REQUIRED",overlayGranted)
        }
        DarkButton("Overlay access settings",requestOverlay,Modifier.fillMaxWidth())
        DarkButton("Notification listener settings",openNotificationAccess,Modifier.fillMaxWidth())
    }
    ApiSettings()
    Group("About Pulse") {
        Note("Personal development build · main screen only · no virtual pet. Auto live focus is schedule-based. Live OpenF1 data requires provider access; historical replays are free subject to OpenF1 terms.")
        Note("Never upload an OpenAI API key to GitHub. The personal Android key is encrypted on this device but local app storage still carries risk. API usage is billed separately.")
    }
}
@Composable
private fun ApiSettings() {
    val ctx=androidx.compose.ui.platform.LocalContext.current
    var newKey by remember {mutableStateOf("")}
    var saved by remember {mutableStateOf(KeyVault.exists(ctx))}
    var message by remember {mutableStateOf("")}
    Group("OpenAI API","This stays on your device. You can change the AI model at any time.") {
        Note(if(saved) "API key saved in encrypted local storage." else "API key not configured; AI chat won't respond until set.")
        OutlinedTextField(value=newKey,onValueChange={newKey=it},singleLine=true,
            label={Text("API key")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Button(onClick={
                if(newKey.isBlank()) message="Enter a key first."
                else runCatching {KeyVault.save(ctx,newKey.trim());newKey="";saved=true;message="Saved on this device."}
                    .onFailure {message="Save failed: ${it.message}"}
            },modifier=Modifier.weight(1f)) {Text("Save")}
            DarkButton("Clear",{KeyVault.clear(ctx);saved=false;message="Key removed."},Modifier.weight(1f))
        }
        if(message.isNotBlank()) Note(message)
        OutlinedTextField(value=PulseSettings.openAiModel,onValueChange=PulseSettings::setModel,
            label={Text("Model ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
    }
}
