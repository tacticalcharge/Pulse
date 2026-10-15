# Build status (October 15, 2026)

**Status:** Source prototype created and statically inspected, **not compiled or run**.

This environment has a Java/Kotlin compiler but lacks Android SDK/API 37, Gradle, the Android Gradle Plugin artifacts and a connected Galaxy Z Flip5. Therefore, no APK and no successful Gradle/Android build can be claimed. A Kotlin parser check found no syntax-level `expecting` errors; unresolved Android/Compose references were expected without their SDK dependencies. Android Studio build and runtime testing remain necessary.

## Included

- Android Studio Gradle project (Kotlin + Jetpack Compose)
- Control Center with feature toggles and appearance options
- Foreground overlay service (pill) with tap/double-tap/swipe/long-press handling
- OpenF1 historical session browser, timeline playback and manual seek controls
- Schedule-window live-session focus attempt, with explicit feed failure/status reporting
- Media transport integration using Android media sessions where accessible
- Notification-title indicator through Android notification listener
- Floating text chat powered by OpenAI Responses API
- Keystore-encrypted device-side API key storage for this private personal prototype
- Windows setup guide and wrapper bootstrap script

## Not complete / limitations

- Source not device-built or tested, so first-build corrections may be necessary
- OpenF1 live requests have no subscription authentication; provider may deny them
- Background session start detection works only while Pulse foreground overlay service runs and Android permits its operation
- Sync is manual and only changes the Pulse data timeline (not Viaplay's video)
- Recorded events are not persisted across process termination
- Overlay keyboard behavior and exact gestures need device tuning
- No cover screen, virtual pet, advanced alerts or native phone-call control
- OpenAI key is encrypted at rest but client-side credentials are still a risk; do not share this build with a saved key

Read `SETUP_WINDOWS.md` first.
