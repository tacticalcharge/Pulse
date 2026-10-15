# Pulse for Galaxy Z Flip5 — Windows setup guide

**Build:** 0.1.0 source prototype · **Target device:** Samsung Galaxy Z Flip5 (main screen) · **Date:** 15 October 2026

This is an Android Studio *source project*, not a ready-to-install APK. It was authored in this environment but **was not compiled or tested on a physical Android device** because the Android SDK, Gradle distribution, and connected phone are unavailable here. Android Studio may flag issues needing small adjustments after the first sync. Follow this guide in order.

## What this build actually includes

- **Control Center:** Home, F1, Features, Appearance and System settings pages. Preferences persist locally.
- **Floating pill:** Android system overlay using a user-started foreground service. Tap to expand; double-tap to open Pulse AI; swipe horizontally to focus another enabled module; long-press and drag to reposition.
- **F1:** OpenF1 year/session browser (2023 onward), historical position/race-control/lap-event playback, play/pause, 15-second skips, seek bar, event stepping and jump to newest buffered record. Automatic *session-window* detection while the overlay is running. A genuine live badge requires recently received data.
- **Media:** Basic transport controls using Android media sessions *when notification-listener access is granted* and the media app supports them.
- **Notifications:** Brief notification-title indicator; no message-body storage.
- **Floating Pulse AI:** Text chat using the OpenAI Responses API, with model selection and an optional locally entered API key (encrypted at rest with Android Keystore).

**Not included yet:** an installable precompiled APK; automatic video-player sync with Viaplay; F1 broadcaster video; guaranteed paid live OpenF1 access; cover screen support; virtual pet; voice input; a secure hosted AI proxy; persistent chat history; sophisticated media controls; verified reliability after reboot. Details below.

## 1 — Install Android Studio

1. On your Windows PC, open Google's official installer page: https://developer.android.com/studio
2. Download the **Windows Android Studio installer** and run it.
3. Keep the default Android Studio and Android SDK installation selections. Complete its setup wizard.
4. Open **More Actions > SDK Manager**, or inside Android Studio use **Tools > SDK Manager**.
5. Under **SDK Platforms**, install **Android API 37** (the project's `compileSdk`).
6. Under **SDK Tools**, confirm **Android SDK Platform-Tools** and **Android SDK Build-Tools 36.0.0** are installed.
7. Use Android Studio's embedded JDK (a compatible JDK 17+). You do **not** need to download a separate Java development kit for the usual Studio setup.

Google's Windows install instructions: https://developer.android.com/studio/install

## 2 — Unzip the Pulse project

1. Extract `Pulse-Android.zip` to a normal folder, for example `C:\Projects\Pulse-Android`.
2. Inside that folder you should see `settings.gradle.kts`, `build.gradle.kts`, `app`, `gradle`, `gradlew.bat`, `setup-wrapper.ps1`, and this guide.
3. Do **not** put your OpenAI API key in project files.

## 3 — Download the official Gradle Wrapper JAR

Because no Gradle distribution can be downloaded in the environment where this source archive was made, it intentionally does not contain the standard binary `gradle-wrapper.jar`. The included script downloads it from Gradle's official GitHub release:

1. In Windows Explorer, open the extracted `Pulse-Android` folder.
2. Click the Explorer address bar, type `powershell`, and press Enter.
3. Run:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-wrapper.ps1
   ```

4. It should create `gradle\wrapper\gradle-wrapper.jar` and print a success message. The script itself is readable in Notepad. It fetches only the wrapper JAR from `github.com/gradle/gradle` for tag `v9.4.1`. You can validate the JAR using Gradle's checksum guidance: https://gradle.org/release-checksums/

**If downloads are blocked:** use a trusted internet connection or generate an official Gradle Wrapper with a locally installed Gradle 9.4.1 (`gradle wrapper --gradle-version 9.4.1`) rather than downloading a wrapper from an unknown site.

## 4 — Open Pulse in Android Studio

1. Start Android Studio.
2. Click **Open** and select the `Pulse-Android` *folder*, not its ZIP and not the `app` folder alone.
3. Select **Trust Project** if prompted, once you have reviewed the local source and bootstrap script.
4. Let Gradle sync. The first sync will download **Gradle 9.4.1**, the Android Gradle plugin, Jetpack Compose and other dependencies; this requires internet access.
5. The build uses **AGP 9.2.1**, **Kotlin Compose compiler plugin 2.3.10**, and **Compose BOM 2026.09.00** with `compileSdk = 37`.
6. If you see **SDK location not found**, use **Tools > SDK Manager** to install/select the Android SDK. Android Studio should generate `local.properties` itself.
7. If sync succeeds, choose **Build > Make Project**. Resolve any build errors shown in the Build Output. This project has not been compiled here.

Optional Windows terminal check after setup:

```powershell
.\gradlew.bat :app:assembleDebug
```

When successful, the debug APK is at `app\build\outputs\apk\debug\app-debug.apk`.

## 5 — Enable developer mode on your Galaxy Z Flip5

1. Open **Settings > About phone > Software information**.
2. Tap **Build number** approximately seven times, and unlock the phone if requested.
3. Return to Settings and open **Developer options**.
4. Enable **USB debugging**.
5. Connect the Z Flip5 to your Windows PC with a USB data cable (not a charge-only cable).
6. On the phone, accept the **Allow USB debugging** prompt for your own PC.
7. In Android Studio's device selector, choose the phone. If it is missing, install the **Samsung OEM USB driver** through the official manufacturer link in Google's Android device-driver documentation: https://developer.android.com/studio/run/oem-usb

You can confirm the connection from Android Studio's Terminal:

```powershell
adb devices
```

A connected, authorized phone should appear as `device`. If not, reconnect, unlock the phone, approve the debugging prompt, and try **Tools > Troubleshoot Device Connections**.

Android's device setup documentation: https://developer.android.com/studio/run/device

## 6 — Install and open Pulse

1. In Android Studio, choose **app** from the run configuration selector.
2. Choose your Samsung Z Flip5.
3. Click the green **Run** triangle. Android Studio will build, install and launch the debug app.
4. You should see a dark-purple **Pulse Control Center** with tabs for Home, F1, Features, Appearance and System.
5. If Windows asks for permission to allow Android Studio or Gradle network access, review the prompt and allow the access needed for dependencies.

## 7 — Let Pulse draw the floating pill

1. On Pulse's **Home** tab, tap **Grant display-over-other-apps permission**.
2. Android will open the app's **Appear on top / Display over other apps** setting; enable it for Pulse.
3. Return to Pulse and switch **Run Pulse pill** ON.
4. Allow notification permission if Android asks. The foreground service uses an ongoing notification so Android can show that Pulse is running.
5. Minimize Pulse or switch to another app. The pill should remain at the top of the main screen.

Gestures: **tap** to expand, **double-tap** for AI, **swipe sideways** to cycle focus, **long-press + drag** to move it. The long-press-to-drag gesture is an initial implementation and may need tuning on the Z Flip5.

To stop the overlay, return to the Control Center and switch **Run Pulse pill** OFF. If anything seems stuck, use Android **Settings > Apps > Pulse > Force stop**.

## 8 — Enable media and notification integration

1. In Pulse **Features** or **System**, tap **Open notification access settings**.
2. Find **Pulse notification access** and explicitly allow it if you want media controls or notification indicators.
3. In Pulse, turn on **Media** and optionally **Notifications**.
4. Start media playback in an app such as Spotify. Swipe the pill until it focuses Media.
5. Tap to expand and try the transport controls.

Media controls only work if the playback app exposes a supported Android media session. Notifications display a brief **title**, not message body. Note that enabling notification listener access grants significant system access: only enable it if you are comfortable with that permission.

## 9 — Test historical F1 replay (does not require a paid live feed)

1. In Pulse, go to **F1**.
2. Press **2025**, **2024**, or **2023** to load public OpenF1 sessions.
3. Wait for the session list to load, then tap a **Race**.
4. Pulse downloads that session's timestamped position records, race-control events and available lap data. Some sessions are larger than others and may load slowly.
5. The playback section will show a seek bar, selected driver number, latest known position, and race-control message.
6. Try **Pause**, **Play**, **−15s**, **+15s**, **Event** and **Latest**.
7. To see the pill display the replay, ensure F1 is enabled and swipe the pill until F1 is focused.
8. Enter the car's **driver number** in the F1 page to view that driver's position. Lap events are initially loaded for the driver number selected when you opened the replay; reselect the session after changing driver number if needed.

Historical data may have missing events, delayed timestamps, or inconsistencies. This is a timestamped-data replay, not a reconstruction of a complete TV race broadcast.

OpenF1 documentation: https://openf1.org/docs/

## 10 — What automatic LIVE F1 does in this build

With Pulse running and **F1 enabled + Auto-focus** enabled, the service periodically checks the current year's OpenF1 sessions list. When the phone clock enters the published **session start/end window**, it selects F1 once and lets you swipe to another module. It will **not** repeatedly steal focus after you swipe away. It attempts to load fresh position and race-control records.

**Important:** OpenF1's current documentation says historical data is free, while real-time data requires a paid subscription. This starter version **does not include a subscription-authentication flow**. As a result, live requests may fail or remain unavailable. The app shows that situation instead of inventing timing values. Also, the published session window is not proof the event has actually started or is still racing; weather delays and red flags can change the schedule.

The 'LIVE' label is used only for recent received data; otherwise it shows the mode/status. A full live-session integration and auth/configuration remain subsequent work.

## 11 — Sync data to Viaplay

Pulse's F1 controls adjust **Pulse's data timeline only**. They do **not** pause or seek Viaplay's video. You manually adjust the Viaplay player and Pulse independently:

- **Pause** Pulse's data display if you pause the video.
- **−15s / +15s** or the F1 seek bar move Pulse's timeline to better match the broadcast.
- **Latest** jumps to the newest *received* F1 data point, not necessarily the actual race's present moment.
- A live data buffer only exists from data Pulse successfully fetched while the service was running. It cannot rewind information that was never retrieved.
- **Event** buttons jump among timestamped data records (not just overtakes). Precise broadcaster-delay auto-detection is not implemented.

## 12 — Connect the OpenAI API to floating chat

**The API is billed separately from ChatGPT** and may require appropriate account/billing eligibility. Review current terms and limits with the account owner before entering a key.

1. Obtain an authorized OpenAI API key through your OpenAI API account, following https://platform.openai.com/docs/overview .
2. Open Pulse **System > OpenAI API** on your Z Flip5.
3. Paste the key into the concealed **API key** box and tap **Save key**. Do not put it in Kotlin files, screenshots, or Git.
4. The default model ID is `gpt-4.1-mini`. You can change it to another Responses API-compatible model your account can access.
5. Turn **OpenAI chat** ON in Home/Features.
6. Return to the floating pill, double-tap it, type a question, and press **Send**.
7. Replies appear in the floating panel. Chats are held in process memory and are not saved to a file. Use **Clear key** in System when you want to remove it.

The key is encrypted at rest with Android Keystore, but **sending requests directly from a mobile app is not as secure as keeping keys in a private backend**. Because Pulse is personal-only, the tradeoff is documented here; a private backend is recommended before distributing the app. API usage may cost money; this prototype does not contain a spending cap. Set any available budget alerts/limits in your API account.

## 13 — Fix common problems

| Problem | What to check |
|---|---|
| `gradle-wrapper.jar` missing | Run `setup-wrapper.ps1` from the extracted Pulse folder. |
| Gradle sync cannot find SDK 37 | SDK Manager > install Android API 37 and Build Tools 36.0.0. |
| Gradle sync cannot download dependencies | Check internet and firewall; verify repositories `google()` and `mavenCentral()`. |
| Device doesn't appear | Unlock phone, enable USB debugging, approve USB prompt, try another data cable, install Samsung USB driver. |
| Pill not visible | Grant overlay permission; turn on Run Pulse pill; check foreground-running notification. |
| Pill disappears after some time | Samsung background/battery policies may stop services; try allowing Pulse unrestricted battery use for testing, and reopen the app. |
| Media is blank | Grant notification-listener access; ensure playback is active and exposed as an Android media session. |
| Replay fails with HTTP status | Check connection; retry another year/session; confirm data is available at OpenF1. |
| Live F1 data unavailable | Expected without appropriate real-time provider access; schedule detection alone doesn't grant live positions. |
| AI says no key | Save a key in System on the device. |
| OpenAI HTTP 401 | Check key validity/permissions. |
| OpenAI HTTP 429 | Check API quota and limits. |
| Chat obscures keyboard | Android overlay + keyboard layout needs tuning on the actual phone; minimize or open Pulse app as a workaround. |
| APK does not compile | Use Android Studio's exact error messages. This starter source was not compiled or phone-tested in its creation environment. |

## 14 — Safety, privacy and what to improve next

- The overlay is a **special-use foreground service**. If Play Store publishing becomes relevant, its policy and permissions would require additional review; Pulse currently targets personal use.
- Notification listener access is powerful. Pulse avoids storing notification bodies, but Android gives the service broad notification visibility once granted.
- The AI key is never included in the archive. Anyone with debugging/root/control of the device might still compromise app-side secrets. Prefer a backend for more protection.
- No automatic prompts to buy F1 or AI products; any paid service is optional and user-managed.
- F1 data is unofficial. Respect provider rate limits and licensing if the project evolves.
- No cover-screen support and no virtual pet per your updated preferences.
- Further reliability work: background restarts, notification permission flows, session-status verification, more efficient incremental streaming, robust replay state, better UI and tests.

## Project layout

```text
Pulse-Android/
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/pulse/app/
      MainActivity.kt                  # Control Center UI
      PulseSettings.kt                # Local preferences
      PulseOverlayService.kt          # Floating pill + AI window + media
      PulseNotificationListener.kt    # Notification indicator and media access
      F1Store.kt                      # OpenF1 sessions, replay, live timeline
      AiClient.kt                     # Responses API text chat
      KeyVault.kt                     # Android Keystore encrypted API key
      HttpUtil.kt                     # HTTP / JSON helpers
  gradle/wrapper/gradle-wrapper.properties
  setup-wrapper.ps1                    # First-run official wrapper download
  gradlew.bat, gradlew                 # Wrapper launch scripts
  build.gradle.kts, settings.gradle.kts, gradle.properties
  SETUP_WINDOWS.md
```

### Official reference links

- Android Studio installation: https://developer.android.com/studio/install
- Physical Android device: https://developer.android.com/studio/run/device
- OEM USB drivers: https://developer.android.com/studio/run/oem-usb
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- AGP 9.2 notes: https://developer.android.com/build/releases/agp-9-2-0-release-notes
- OpenF1 API: https://openf1.org/docs/
- OpenAI API docs: https://platform.openai.com/docs/overview
- Gradle Wrapper: https://docs.gradle.org/current/userguide/gradle_wrapper.html
