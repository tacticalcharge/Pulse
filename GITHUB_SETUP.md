# Pulse v0.2 — GitHub build and installation guide (Windows → Galaxy Z Flip5)

You **do not need Android Studio or a local Android SDK**. GitHub Actions downloads the Android SDK, Java and Gradle on a hosted runner to compile Pulse. Your phone downloads only the finished APK.

## 1. Update your existing Pulse GitHub repository

Because you previously had issues with old Kotlin files remaining on `main`, the safest workflow is to replace the **whole source tree**, including the new `TrackMapView.kt` file.

### Recommended: GitHub Desktop (GUI, no Android SDK)

1. Download and install [GitHub Desktop](https://desktop.github.com/) (much smaller than Android Studio).
2. Sign in and choose **File > Clone repository**. Choose your existing `tacticalcharge/Pulse` repository and clone it to a local folder.
3. Download and extract `Pulse-v0.2-GitHub.zip`.
4. Open the extracted project folder. Copy its **contents** into the cloned Pulse repository folder, **replacing existing files**. This means `app`, `.github`, the Gradle configuration files, and documentation. Do **not** delete the cloned folder's hidden `.git` directory.
5. GitHub Desktop should now show modified files and the added `TrackMapView.kt`.
6. Enter a commit message, for example `Pulse v0.2 camera UI and F1 improvements`, click **Commit to main**, then **Push origin**.
7. On GitHub, verify `app/src/main/java/com/pulse/app/TrackMapView.kt` exists and `PulseSettings.kt` contains `updateAutoF1` (not the old `setAutoF1` function).

### Browser-only alternative

Use **Add file > Upload files** in your GitHub repository. Drag the **contents** of the extracted project folder into the upload area, preserving directory paths and including `.github` (the hidden folder). Commit to `main`. If GitHub's browser upload does not reliably overwrite existing files, use GitHub Desktop above; partial uploads caused past builds to compile old code.

**Do not upload the ZIP file itself** to the repository. GitHub Actions expects the unzipped source tree.

## 2. Compile the APK on GitHub

1. Open [your Pulse repository's Actions page](https://github.com/tacticalcharge/Pulse/actions).
2. Open the newest **Build Pulse APK** run (or manually select the workflow and **Run workflow** on `main`).
3. Open the **Android debug APK** job.
4. Check the **Build installable debug APK** step. A green check indicates the source compiled and packaged successfully. A red X means **no finished APK was produced**.
5. If successful, return to the run summary, scroll to **Artifacts**, and download `Pulse-debug-apk`.
6. Extract the downloaded artifact ZIP. Inside is `app-debug.apk`.

If the run fails, copy the first compiler/error lines including file names and line numbers. Do not copy hundreds of lines of the Gradle stack trace. **This version has not been independently compiled or tested on a physical Z Flip5.** The build result on GitHub is the next verification step.

## 3. Install on your phone

1. Send `app-debug.apk` to the Z Flip5 or download the artifact directly on the phone.
2. Open the APK from **My Files**. Review Android's installation permission prompt, and only approve installation from the source you trust.
3. Launch Pulse, then use the Home tab to grant **Display over other apps** and turn on the pill.
4. For media and notification features, open **System / Features > Notification access** and review the requested access before granting.
5. For chat, open **System > OpenAI API**, and add your own authorized API key on the phone. Never paste it into GitHub, a build log, or a chat message.

**Debug-signing caveat:** GitHub may sign separate debug builds with different ephemeral debug keys. If the updated APK reports a signature mismatch, you may have to remove the older Pulse installation before installing this version; **uninstalling can erase local settings and the saved key**. A future improvement is to configure a persistent signing key securely in repository secrets.

## 4. Customize camera alignment

- Open **Design > Camera alignment** and keep **Align to camera automatically** on initially.
- In the idle state, the pill should look like a tiny black dot around the top selfie camera. Tap that area to expand.
- If One UI places it too high/low, use **Camera vertical adjustment**. If the black dot is too large/small, adjust **Camera cover** diameter or select **Auto**.
- **Drag vertically on the pill immediately** to move it up/down; **long-press then drag** to reposition freely in either direction. Dragging disables automatic alignment.
- Press **Reset to selfie camera** to recenter it.
- **Single tap** expands/collapses; **double tap** opens floating AI; **horizontal swipe** switches available focus; **tap outside** requests collapse.

**Android limitation:** On some Z Flip5 firmware versions, application overlays cannot occupy the actual system status-bar area or accept touches over the camera cutout. If the overlay won't sit directly on the camera, manually position it just below the status bar. No ordinary overlay API can guarantee system-level Dynamic Island behavior.

## 5. F1 replay, map and stop controls

1. Open **Racing**, enable F1, and choose a year (2023 onward).
2. Search for a country/circuit and select a filter: **Race**, **Qualifying**, or **All**.
3. Expand a circuit card and select a session to load the replay. Historical data loads from OpenF1 and requires a network connection.
4. Use the playback timeline, **Pause**, **−15s / +15s**, event skipping and **Latest** to sync Pulse's data to Viaplay's video. Pulse does not control Viaplay playback.
5. The map, when available, uses sampled location coordinates from a single recorded lap. It shows a circuit path and an **estimated progress dot**, not precise live car positions. Driver numbers without location data may show an unavailable-map message. You can change the selected driver in the Playback card.
6. Weather, reconstructed positions, pit-event and race-control information appear when OpenF1 provides them for the session.
7. Select **Stop replay / hide F1 timeline** (or use Stop in the expanded pill) to clear the session and hide F1. If no other activity is active, Pulse returns to a camera-sized idle dot. Switch **Enable F1** off to disable the feature completely.

**Live data:** OpenF1 real-time feed access requires a subscription. Pulse can detect session windows, but only shows active F1 in the pill when fresh live data is actually available. The live GPS-style map is not guaranteed in this personal prototype.

## 6. Troubleshooting

- **No APK in Artifacts:** check that the run is green. Artifacts upload only after a successful build.
- **Repeated old Kotlin errors:** check that your newest GitHub commit truly replaces old `.kt` files on `main` and `TrackMapView.kt` exists.
- **Pill below camera:** Android system-bar overlay restrictions may prevent exact cutout placement; use Design > Camera vertical adjustment or manual drag.
- **Pill doesn't expand when you tap the camera:** the status bar may intercept touches. Move the pill slightly lower and tap the visible portion.
- **Tap outside doesn't dismiss:** `ACTION_OUTSIDE` delivery depends on Android/One UI window routing; use the panel's × close button as a fallback.
- **No F1 map:** the selected driver/session may lack enough usable historical location records or the API may be unavailable. Try a completed race and another driver number.
- **No live F1:** a session schedule isn't a live-feed license. Live data access is separate.
- **AI errors:** verify network connectivity, model name, API access and billing. Never share API keys to debug an error.
