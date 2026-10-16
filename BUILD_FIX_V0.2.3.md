# Pulse Kotlin Settings Setter Fix

The latest GitHub Actions log (build result `__run-1792099002262.json`) now fails solely in `PulseSettings.kt` due to JVM signature clashes. Kotlin generates JVM setters for `cameraAnchor`, `cameraYOffset`, and `cameraDotSize`, which collide with manually declared `setCameraAnchor`, `setCameraYOffset`, and `setCameraDotSize` methods.

## Replace exactly these TWO repository files

- `app/src/main/java/com/pulse/app/PulseSettings.kt`
- `app/src/main/java/com/pulse/app/MainActivity.kt`

This patch renames all three methods to `updateCameraAnchor`, `updateCameraYOffset`, and `updateCameraDotSize`, including their usages in MainActivity. No changes to either Gradle file are required.

Upload/replace these files at their existing paths in GitHub, commit them to `main`, and inspect the *new* Actions run. Do not re-run a previous failed run, which uses the old commit.

Source checked for any remaining references to the conflicting names. APK compilation or on-device testing has not been performed locally.
