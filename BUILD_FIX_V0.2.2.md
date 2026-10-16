# Pulse v0.2.2 — fix missing Gradle plugin version

## Error
`Plugin [id: 'com.android.application'] was not found` / `plugin dependency must include a version number for this source`.

This failure is at the root `build.gradle.kts` file, before app compilation. It is not caused by the Android SDK license output.

## Fastest repair (one file)

1. In your GitHub repository open `build.gradle.kts` at the **repository root** (not `app/build.gradle.kts`).
2. Edit it to exactly:

```kotlin
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
```

3. Commit to `main`. Check that GitHub really displays the versions in the root file.
4. Start the **new** Build Pulse APK Actions run on `main` and look for the new commit SHA.
5. If another step fails, copy the first specific `What went wrong` and the error lines above it.

Only the root file is required for this exact logged issue. **Do not** edit `app/build.gradle.kts` to change plugin versions: this module inherits them from the root file.

## Alternative full replacement

This ZIP contains the full v0.2.1 source with the explicit plugin version declaration restored and an added build configuration check in the GitHub workflow. Copy it into the repository (preserving `.git` when using a Git clone), commit all changes, and run a new build.

## Verification status

The package structure and root script text were checked locally. No Android SDK is installed in the authoring environment, so the completed APK has **not** been compiled or tested here; GitHub Actions is the compilation test.
