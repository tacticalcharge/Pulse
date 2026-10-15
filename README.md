# Pulse for Android — GitHub cloud-build edition

Personal Android source prototype for the Samsung Galaxy Z Flip5 (main screen only).
No Android Studio or local Android SDK installation is required for the **GitHub Actions** build route.

**Start here: [GITHUB_SETUP.md](GITHUB_SETUP.md)**

The workflow in `.github/workflows/build-apk.yml` installs Android SDK platform 37, SDK Build Tools 36.0.0,
Java 17 and Gradle 9.4.1 **on GitHub's runner**, then attempts to build `app-debug.apk`.
The workflow does **not** use the incomplete wrapper from the original source archive.

## What the source aims to implement
- Android overlay pill, long-press-to-move, tap to expand, double-tap to open floating AI and horizontal swipes.
- Control Center toggles for F1, notifications, media, AI and appearance.
- OpenF1 historical session browsing and timestamp-based data playback with play/pause and seek.
- Schedule-based live-session focus, with data freshness and availability warnings.
- Media controls and notification indicators (Android access required).
- Floating text chat using the OpenAI API; configure API key on the phone only.

## Limitations
**This is an uncompiled source prototype, not a tested APK.** GitHub may report source/dependency errors during the first build.
Do not assume the build will succeed or the overlay will work on your phone until tested.
Real-time OpenF1 timing may require paid API access. Video sync only moves Pulse's data timeline, not Viaplay.
Cover-screen support and the virtual pet are not in scope.

**Keep the repository private. Do not upload an OpenAI API key, credentials, or personal chats.**
