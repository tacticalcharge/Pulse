# Pulse v0.2.1 — GitHub build fix

The previous GitHub run failed in two tasks: `:app:checkDebugAarMetadata` and `:app:compileDebugKotlin`.

## Changes

- `app/build.gradle.kts`: Compose BOM 2026.09.00 → 2025.12.00, compatible with compiling against Android API 36. Version updated to 0.2.1.
- `app/src/main/java/com/pulse/app/MainActivity.kt`: all invalid `DarkButton` trailing-lambda calls now use `onClick = { ... }`. Fixed play callback to call `F1Store.togglePlay()` and repaired the Apply driver button.
- `app/src/main/java/com/pulse/app/TrackMapView.kt`: added the missing `py` argument to both `canvas.drawCircle()` calls.

## Upload to GitHub

Option A — full replacement (recommended if unsure your repository is in sync): clone your existing private repository with GitHub Desktop; copy **all contents** of this folder into your repository, preserving `.git`; commit, then push.

Option B — replace just these three files in your repository, retaining their folder structure:

1. `app/build.gradle.kts`
2. `app/src/main/java/com/pulse/app/MainActivity.kt`
3. `app/src/main/java/com/pulse/app/TrackMapView.kt`

Next: open **Actions → Build Pulse APK** and check the latest run for the commit that includes these changes. If successful, download the `Pulse-debug-apk` artifact under **Artifacts**. Extract `app-debug.apk` from its ZIP.

If the build still fails, share the first compiler errors from the **new** run, not the previous one.

The source has not been compiled locally or tested on a device.
