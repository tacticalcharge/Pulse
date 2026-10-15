# Build status — GitHub-ready source (October 15, 2026)

The source from the initial Android Studio prototype was repackaged with a cloud build workflow.
**It has not been compiled or successfully built as an APK in this environment.**

The included GitHub Actions workflow installs on GitHub-hosted Ubuntu:
- Java 17
- Android SDK platform 37 and Build Tools 36.0.0
- Gradle 9.4.1 (compatible minimum for Android Gradle Plugin 9.2)

Then it attempts to build `:app:assembleDebug` and publishes `app-debug.apk` if successful.
This bypasses the original project's missing Gradle wrapper JAR.

No APK is bundled. Consult `GITHUB_SETUP.md` for first build and installation instructions.
The initial Kotlin source may have unresolved build or device-specific issues.
Real-time F1 access and Samsung background/overlay behavior also remain unverified.
