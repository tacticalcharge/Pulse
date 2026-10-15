# Pulse v0.2 build status

- Source code updated from the v0.1 Kotlin-fix project and packaged for GitHub Actions.
- Kotlin setter collisions (`setAutoF1`, `setAccent`, `setFocus`) are absent from the updated source.
- Source delimiter balance and file layout were checked programmatically.
- **Not compiled against Android SDK or tested on a Z Flip5 in this environment.** A full Android build is needed to confirm correctness.
- `.github/workflows/build-apk.yml` installs Android API 36 and Gradle 9.4.1 on GitHub's hosted runner.
- Camera alignment, touches around the actual cutout, outside-touch delivery, screen rotation, battery behavior, and Android 15/One UI overlay limits need testing on the real device.
- OpenF1 historical trace and optional weather/pit endpoints require a live network test; location and event availability vary by driver and session.
- OpenF1 paid live data is not authenticated in this personal-source build. Schedule detection isn't guaranteed real-time telemetry.
- Debug APK signatures may change across cloud runs; read GITHUB_SETUP.md before updating an installed app.
