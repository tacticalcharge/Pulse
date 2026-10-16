## v0.3 source status

The new overlay and OpenF1 rate-limiter code has been statically inspected but **not compiled/tested on an Android SDK**. GitHub Actions must confirm it builds. Android API 36 and the previous Gradle/Kotlin fixes are kept.

# Pulse v0.2.1 build status

GitHub Actions is configured to compile against Android API 36 using Compose BOM 2025.12.00 (Compose core 1.10), avoiding the Compose 1.12.1 compileSdk 37 incompatibility seen in the previous log.

Fixed known Kotlin compilation errors in `MainActivity.kt`: missing `onClick` parameters in `DarkButton` calls, incorrectly referenced toggle callback, and malformed `Apply driver` trailing-lambda call.

Fixed two `Canvas.drawCircle()` calls in `TrackMapView.kt` to supply both X and Y coordinates.

**Important: This code has been statically inspected, not compiled with the Android SDK or tested on a phone in this environment.** The next GitHub Actions build is the authoritative test.

If another failure occurs, copy the lines starting with `e:` or the first `What went wrong` block from that **new build run**.


## v0.2.2 repair
The uploaded error was `com.android.application` without a resolvable plugin version. Root `build.gradle.kts` has an explicit `9.2.1` version, and the workflow prints/checks this declaration. GitHub Actions build still needed.
