# Pulse v0.3 — Galaxy Z Flip5 camera-island prototype

Native Kotlin/Compose Android app, compiled with GitHub Actions. **This is source code, not an already-built APK.** GitHub's `Build Pulse APK` workflow produces an installable debug APK after a successful run.

**New in v0.3:** [Camera interaction, animation and OpenF1 rate limiting](V0.3_CHANGES.md).

## What's new

- **Camera-sized idle:** when there is no actual active F1 data playback, usable media session, or temporary notification, the pill becomes a small black camera-centered target. Tap it for an expanded menu.
- **Camera-aware placement:** use Android display-cutout geometry where exposed, otherwise a tunable top-center default. Drag vertically immediately; long-press to drag in any direction. Design > Camera alignment has a Y-offset slider, manual-position controls, and a reset button.
- **Expanded menu dismissal:** tapping outside an expanded pill requests it to collapse using Android's `FLAG_WATCH_OUTSIDE_TOUCH` (subject to Samsung/Android overlay behavior).
- **Dark UI refresh:** OLED-oriented blacks and graphite surfaces, violet/mint/ice/rose accents, rounded surfaces and short resize transitions.
- **Control Center:** a five-tab bottom navigation interface (Home, Racing, Features, Design, System).
- **F1 race browser:** year filter, circuit/country search, session type filters, grouped circuit cards, replay sessions.
- **F1 replay:** pause/resume, rewind/fast-forward 15 seconds, skip events, latest data, active driver, reconstructed position order, timestamped race-control and pit messages, weather when available.
- **F1 map:** an approximate path traced from **one real lap** of the selected driver's OpenF1 location coordinates, not an official circuit graphic. The progress dot is **estimated**, not accurate live GPS. Map availability varies by session and driver.
- **Stop replay:** a stop button in Racing and the expanded F1 pill returns the app to inactive, and the overlay to its camera-only state if nothing else is active. Turning off F1 also stops it.
- Existing media controls, notification indicators, floating OpenAI chat and customizable features remain.

## Important hardware and provider limitations

**Camera:** Android's `TYPE_APPLICATION_OVERLAY` may be prevented from drawing or receiving touches over the **status bar and physical front-camera cutout** on One UI. The app attempts camera alignment, with a manual offset and position reset. The exact appearance and tap behavior must be verified on the real Galaxy Z Flip5. It cannot guarantee pixels are drawn over the hole punch or that an untouched black dot is 100% invisible.

**F1:** OpenF1 offers public historical data (2023+); real-time data requires separate paid access. Session detection checks scheduling, and the pill only shows live data when recent timestamped records have arrived. Map coordinates and location samples are approximate, and the single-lap trace does not constitute an official live all-car map.

**AI:** An API key is saved encrypted in Android Keystore-backed storage on your **phone**, never in the GitHub project. OpenAI API access can cost money. Do not publish or share the key; the app-side-key design is for your own personal test build.

**Cover screen / virtual pet:** intentionally not included.

## Build and install

Read [`GITHUB_SETUP.md`](GITHUB_SETUP.md). GitHub installs the Android SDK on its hosted runner, **not on your Windows PC**.

Since this version adds new files and replaces major code, replace **all project source files**, not just one `.kt` file. Confirm `.github/workflows/build-apk.yml` remains present. GitHub has not yet built and tested this version in this environment; treat this as an unverified development build until its APK workflow succeeds on your repository.

## Source layout

- `app/src/main/java/com/pulse/app/MainActivity.kt` — Compose Control Center and F1 replay browser
- `app/src/main/java/com/pulse/app/PulseOverlayService.kt` — camera-aligned overlay, gestures, expanded panel, chat overlay
- `app/src/main/java/com/pulse/app/TrackMapView.kt` — Canvas-drawn approximate circuit trace
- `app/src/main/java/com/pulse/app/F1Store.kt` — OpenF1 HTTP and timeline playback
- `app/src/main/java/com/pulse/app/PulseSettings.kt` — persisted customization preferences
- `.github/workflows/build-apk.yml` — APK build, GitHub-hosted SDK and Gradle
