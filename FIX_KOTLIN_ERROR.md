# Fix for `Platform declaration clash` in `PulseSettings.kt`

The GitHub build reached `:app:compileDebugKotlin` and failed because Kotlin
properties (`autoF1`, `accent`, `focus`) automatically generate JVM setters
whose names conflict with manually defined member functions.

## Fastest way to fix in an existing GitHub repository

Replace these **four** files with the versions in this ZIP, preserving paths:

1. `app/src/main/java/com/pulse/app/PulseSettings.kt`
2. `app/src/main/java/com/pulse/app/MainActivity.kt`
3. `app/src/main/java/com/pulse/app/F1Store.kt`
4. `app/src/main/java/com/pulse/app/PulseOverlayService.kt`

The changes rename methods and their references:

- `setAutoF1(...)` → `updateAutoF1(...)`
- `setAccent(...)` → `updateAccent(...)`
- `setFocus(...)` → `updateFocus(...)`

You can instead replace all repository files with the contents of this ZIP,
including the hidden `.github` folder. Do not upload the outer folder itself.

Commit to `main`, open **Actions → Build Pulse APK**, and review the build result.
If it succeeds, download the `Pulse-debug-apk` artifact and extract `app-debug.apk`.
If it fails, copy the first `e:` Kotlin error lines or the first `What went wrong` block.

**Build status:** These exact Kotlin naming conflicts were fixed by source inspection.
A successful Android build has not been independently verified.
