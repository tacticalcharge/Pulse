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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PulseSettings.init(this)
        overlayGranted = Settings.canDrawOverlays(this)
        setContent {
            val violet = Color(PulseSettings.accent)
            MaterialTheme(colorScheme = darkColorScheme(primary=violet,secondary=violet,
                background=Color(0xff101116), surface=Color(0xff1c1d25))) {
                PulseApp(
                    overlayGranted = overlayGranted,
                    requestOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    },
                    openNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    enableOverlay = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(this)) {
                            Toast.makeText(this,"Grant overlay permission first",Toast.LENGTH_LONG).show()
                        } else {
                            PulseSettings.setOverlay(enabled)
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                PulseOverlayService.start(this)
                            } else PulseOverlayService.stop(this)
                        }
                    },
                    update = { PulseOverlayService.instance?.refreshUi() }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
    }
}

@Composable
private fun PulseApp(
    overlayGranted: Boolean, requestOverlay: () -> Unit, openNotificationAccess: () -> Unit,
    enableOverlay: (Boolean) -> Unit, update: () -> Unit
) {
    var page by remember { mutableStateOf("Home") }
    val pages = listOf("Home","F1","Features","Appearance","System")
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
        Column(Modifier.fillMaxWidth().padding(top=22.dp,start=20.dp,end=20.dp,bottom=10.dp)) {
            Text("PULSE",fontWeight=FontWeight.Black,fontSize=30.sp,color=MaterialTheme.colorScheme.primary)
            Text("Galaxy Z Flip5 · Personal build",color=Color.LightGray,fontSize=12.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                pages.forEach { name ->
                    FilterChip(selected=page==name,onClick={ page=name },label={Text(name)})
                }
            }
        }
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(17.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(page) {
                "Home" -> {
                    Section("Floating pill") {
                        Text("A small overlay above other apps. Tap to expand, double-tap for AI, swipe sideways to change focus, long-press to drag.")
                        Toggle("Run Pulse pill",PulseSettings.overlayOn, enableOverlay)
                        if (!overlayGranted) {
                            Text("Overlay permission has not been granted yet.",color=Color(0xffffbe92))
                            Button(onClick=requestOverlay) { Text("Grant display-over-other-apps permission") }
                        }
                        Text(if (overlayGranted) "Overlay permission: granted" else "Overlay permission: not granted",fontSize=12.sp)
                    }
                    Section("Quick switches") {
                        Toggle("F1",PulseSettings.f1On,{ PulseSettings.setF1(it);update() })
                        Toggle("Automatic F1 focus on session start",PulseSettings.autoF1,{ PulseSettings.updateAutoF1(it);update() })
                        Toggle("Media",PulseSettings.mediaOn,{ PulseSettings.setMedia(it);update() })
                        Toggle("Notifications",PulseSettings.notificationsOn,{ PulseSettings.setNotifications(it);update() })
                        Toggle("OpenAI chat",PulseSettings.aiOn,{ PulseSettings.setAi(it);update() })
                    }
                    Section("Start here") {
                        Text("1. Grant the overlay permission.\n2. Start the pill.\n3. Open F1 and load a historical session.\n4. Add an OpenAI API key under System for floating chat.")
                        Text("OpenF1 historical data is public. Live timing access is separate and may require payment.",fontSize=12.sp,color=Color.LightGray)
                    }
                }
                "F1" -> {
                    Section("F1 data and replay") {
                        Toggle("Enable F1",PulseSettings.f1On,{PulseSettings.setF1(it);update()})
                        Toggle("Auto-focus F1 at a scheduled session start",PulseSettings.autoF1,{PulseSettings.updateAutoF1(it);update()})
                        Text("Session detection is schedule-based. A confirmed live feed requires recent API data; unavailable data is labelled.",fontSize=12.sp,color=Color.LightGray)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(2026,2025,2024,2023).forEach { year ->
                                AssistChip(onClick={F1Store.listYear(year)},label={Text(year.toString())})
                            }
                        }
                        Text(F1Store.status,fontSize=12.sp,color=Color.LightGray)
                    }
                    if (F1Store.sessions.isNotEmpty()) Section("Choose a session · ${F1Store.selectedYear}") {
                        Text("Tap a session to load timestamped positions and race control. This may download many records.",fontSize=12.sp,color=Color.LightGray)
                        LazyColumn(modifier=Modifier.fillMaxWidth().height(235.dp)) {
                            items(F1Store.sessions,key={it.key}) { s ->
                                TextButton(onClick={F1Store.loadReplay(s)},modifier=Modifier.fillMaxWidth()) {
                                    Text(s.label,modifier=Modifier.fillMaxWidth())
                                }
                                HorizontalDivider(color=Color(0xff333542))
                            }
                        }
                    }
                    Section("Playback · ${F1Store.mode}") {
                        Text(F1Store.selectedSession?.label ?: "No session selected",fontWeight=FontWeight.Bold)
                        Text("Selected driver: #${PulseSettings.selectedDriver}, position ${F1Store.currentPosition()}, lap ${F1Store.currentLap()}")
                        Text("Race control: ${F1Store.currentEvent()}",fontSize=12.sp,color=Color.LightGray)
                        if (F1Store.events.isNotEmpty()) {
                            Slider(value=F1Store.fraction(),onValueChange=F1Store::seekFraction)
                            val shown = java.time.Instant.ofEpochMilli(F1Store.cursorMs)
                            Text(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss")
                                .withZone(ZoneId.systemDefault()).format(shown),fontSize=12.sp)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SmallButton("◀ Event") { F1Store.jumpEvent(false) }
                            SmallButton("−15s") { F1Store.seekSeconds(-15) }
                            SmallButton(if (F1Store.playing) "Pause" else "Play") { F1Store.togglePlay() }
                            SmallButton("+15s") { F1Store.seekSeconds(15) }
                            SmallButton("Event ▶") { F1Store.jumpEvent(true) }
                            SmallButton("Latest") { F1Store.goLatest() }
                        }
                        if (F1Store.liveSession != null) OutlinedButton(onClick=F1Store::watchLiveNow) {
                            Text("Follow scheduled live session")
                        }
                        OutlinedTextField(value=PulseSettings.selectedDriver.toString(),
                            onValueChange={ txt -> txt.toIntOrNull()?.takeIf { it in 1..99 }?.let(PulseSettings::setDriver) },
                            label={Text("Driver car number (1–99)")},singleLine=true,
                            keyboardOptions=KeyboardOptions.Default,modifier=Modifier.fillMaxWidth())
                        Text("Sync with Viaplay manually using pause, ±15 seconds and the seek bar. These controls do not control Viaplay itself.",fontSize=12.sp,color=Color.LightGray)
                    }
                }
                "Features" -> Section("Modules") {
                    Toggle("OpenAI floating chat",PulseSettings.aiOn,{PulseSettings.setAi(it);update()})
                    Toggle("Media sessions",PulseSettings.mediaOn,{PulseSettings.setMedia(it);update()})
                    Toggle("Notification title indicator",PulseSettings.notificationsOn,{PulseSettings.setNotifications(it);update()})
                    Toggle("F1 sessions",PulseSettings.f1On,{PulseSettings.setF1(it);update()})
                    Text("Swipe the pill sideways to switch active modules. Media controls require Android notification access.",fontSize=12.sp,color=Color.LightGray)
                    Button(onClick=openNotificationAccess){Text("Open notification access settings")}
                }
                "Appearance" -> {
                    Section("Pill width") {
                        Text("${PulseSettings.pillWidth} dp")
                        Slider(value=PulseSettings.pillWidth.toFloat(),onValueChange={PulseSettings.setWidth(it.toInt());update()},valueRange=120f..250f)
                    }
                    Section("Accent color") {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("Violet" to 0xffac91ff.toInt(),"Cyan" to 0xff63dce6.toInt(),
                                "Pink" to 0xffff91be.toInt(),"Green" to 0xff8be6a5.toInt()).forEach { (name,col) ->
                                AssistChip(onClick={PulseSettings.updateAccent(col);update()},label={Text(name)})
                            }
                        }
                        Text("Long-press and drag the pill to change its position.",fontSize=12.sp,color=Color.LightGray)
                    }
                }
                "System" -> {
                    Section("Permissions") {
                        Text(if (overlayGranted) "Overlay access granted" else "Overlay access needed")
                        Button(onClick=requestOverlay){Text("Overlay permission settings")}
                        Button(onClick=openNotificationAccess){Text("Notification access settings")}
                    }
                    ApiSettings()
                    Section("Notes") {
                        Text("Pulse is a personal prototype. AI calls incur separate OpenAI API usage costs. Live OpenF1 data requires provider access. No pet or cover-screen support is included.",fontSize=12.sp)
                        Text("Never share your API key or publish screenshots that show it.",fontSize=12.sp,color=Color.LightGray)
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape=RoundedCornerShape(18.dp),color=Color(0xff20212a),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Text(title,fontSize=19.sp,fontWeight=FontWeight.Bold)
            content()
        }
    }
}
@Composable
private fun Toggle(label: String,checked: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,modifier=Modifier.weight(1f),fontSize=14.sp)
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick=onClick,contentPadding=PaddingValues(horizontal=12.dp,vertical=2.dp)) {Text(label,fontSize=12.sp)}
}
@Composable
private fun ApiSettings() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var newKey by remember { mutableStateOf("") }
    var isSaved by remember { mutableStateOf(KeyVault.exists(ctx)) }
    var message by remember { mutableStateOf("") }
    Section("OpenAI API · personal key") {
        Text(if (isSaved) "Key saved on device (encrypted with Android Keystore)." else "No key saved. Chat will not work until configured.",fontSize=13.sp)
        OutlinedTextField(value=newKey,onValueChange={newKey=it},singleLine=true,
            label={Text("Paste API key on your phone")},visualTransformation=PasswordVisualTransformation(),
            modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if (newKey.isBlank()) message="Enter a key first."
                else runCatching { KeyVault.save(ctx,newKey.trim());newKey="";isSaved=true;message="Saved on this device." }
                    .onFailure { message="Could not save: ${it.message}" }
            }){Text("Save key")}
            OutlinedButton(onClick={KeyVault.clear(ctx);isSaved=false;message="Key cleared."}){Text("Clear key")}
        }
        Text(message,fontSize=12.sp)
        OutlinedTextField(value=PulseSettings.openAiModel,onValueChange=PulseSettings::setModel,
            label={Text("OpenAI model ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("For a personal test build only: an app-side key is still exposed to compromise on a rooted/debugged device. Never put a key into source code. A private backend is required before sharing an app.",fontSize=12.sp,color=Color.LightGray)
    }
}
