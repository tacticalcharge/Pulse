# Fix for GitHub Actions Android SDK installation failure

Your previous GitHub log ended with:

`Warning: Failed to find package 'platforms;android-37'`

Two changes fix that *specific* installer error:

1. In `.github/workflows/build-apk.yml`, use `packages: 'platform-tools platforms;android-36 build-tools;36.0.0'`.
2. In `app/build.gradle.kts`, set `compileSdk = 36`.

Do **both** changes: the requested SDK and the compile SDK must agree.

## Uploading the correction without Android Studio

- In your **private GitHub repository**, open `.github/workflows/build-apk.yml`, press the pencil icon, make the change above, and commit to `main`.
- Then open `app/build.gradle.kts`, change `compileSdk`, and commit to `main`.
- Open **Actions** -> **Build Pulse APK** -> **Run workflow**. The automatic push build may already have started; you can use that run instead.
- On success, download the `Pulse-debug-apk` artifact, unzip it, and install the APK on your Z Flip5.

Alternatively, replace the old project contents with the files in `Pulse-GitHub-Fixed.zip` (including the hidden `.github` directory).

**Note:** This addresses the first error encountered, before Gradle compilation. Other build errors may show up afterward. Share the last 40-80 lines of the failed `Build installable debug APK` step if that happens.
