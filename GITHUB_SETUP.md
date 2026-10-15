# Pulse — Build and install with GitHub (Windows + Galaxy Z Flip5)

You do **not** need to download Android Studio or the Android SDK onto your PC.
GitHub Actions will install Java, Gradle and the Android SDK on a temporary GitHub computer to compile the project.
You need a GitHub account, a browser, an internet connection, your phone and the provided source ZIP.

## Part A — Create the private repository

1. Go to https://github.com and sign in (or create an account).
2. Open https://github.com/new to create a repository.
3. Repository name: `Pulse`. Choose **Private**.
4. Do **not** check **Add a README**, `.gitignore`, or license; this ZIP already contains its own.
5. Click **Create repository**.
6. On your Windows PC, unzip `Pulse-GitHub.zip`.
7. Open the extracted `Pulse-GitHub` folder. You should see `app`, `gradle`, `.github`, `README.md`, `settings.gradle.kts` and other project files.
8. In the empty repository page, click **uploading an existing file** (or **Add file > Upload files**).
9. Drag **the CONTENTS of the `Pulse-GitHub` folder**, including the `.github` folder, onto GitHub's upload area. Do not upload the ZIP file itself and do not upload a single nested `Pulse-GitHub` directory.
10. Scroll to the bottom and click **Commit changes**. Choose the `main` branch.

**Check your folder layout on GitHub.** At the repository root you need both `app/build.gradle.kts` and `.github/workflows/build-apk.yml`.
If you see `Pulse-GitHub/app/build.gradle.kts`, everything is nested one folder too deep and the workflow may not work.

If the browser refuses a folder, try Chrome/Edge with drag-and-drop. Alternatively upload the files in batches, preserving folder paths. The `.github` folder starts with a dot; make sure it is included.

## Part B — Run the cloud build

1. On GitHub open your new **Pulse** repository.
2. Select the **Actions** tab.
3. If GitHub asks to enable Actions for this repository, enable them.
4. Select **Build Pulse APK** in the workflows list.
5. You can use the run triggered automatically by your first upload, or click **Run workflow**, choose `main`, then **Run workflow**.
6. Open the newest workflow run, then open the **Android debug APK** job to see the steps.
7. If everything passes (green check), return to that run's summary and find **Artifacts**.
8. Click **Pulse-debug-apk** to download an artifact ZIP. Extract it to get `app-debug.apk`.

If the build is red, you **do not have a compiled app yet**. Open the failed job step, copy the first meaningful error and share it for troubleshooting. The original Pulse source has not been fully compiled or tested, so this first build may need fixes.

### What if no Actions build appears?

- Check that `.github/workflows/build-apk.yml` exists **exactly** at the root under `.github/workflows/`.
- Check that the repository's default branch is `main`.
- Reopen **Actions**; on private accounts or some policies, the Actions feature may need to be enabled.
- If the build says `Gradle` cannot resolve a dependency, share the actual error; don't randomly change version numbers.

## Part C — Install the debug APK on the Galaxy Z Flip5

1. Transfer `app-debug.apk` to the Z Flip5 (download it directly on the phone, use USB file transfer, or another trusted method).
2. On the phone, open **My Files** or your file manager and tap `app-debug.apk`.
3. Android may prompt you to allow app installation from that source. Review the warning carefully; if you trust this app you built yourself, enable that permission for **that specific file manager** in Settings, then retry. You can disable it afterward.
4. Tap **Install**, then **Open**.
5. In the Pulse app, grant **Display over other apps** permission when prompted, return to Pulse and toggle **Run Pulse pill**.
6. Optional: grant **Notification access** for notification previews/media controls if you want those features. Review the access implications before allowing it.
7. Test single-tap to expand, double-tap for AI, swipe sideways for focus, and long-press to reposition.

**You do not need USB debugging for this cloud-build/install method.** Android Studio and a PC SDK are also unnecessary.

## Part D — F1 and AI configuration

### F1 replay

1. Open Pulse > **F1**.
2. Enable F1 and select a supported year in the historical browser.
3. Pick a session and load its timestamped events.
4. Use **Play/Pause**, **−15 seconds**, **+15 seconds**, event skipping and **Latest**.
5. Pulse controls its own data timeline only; it does not control Viaplay video playback.

Public historical OpenF1 API access and coverage vary. Automatic live-session focus is **schedule-based**; live data may not arrive without a separate subscription, so the app must not be relied on for real-time positions until verified.

### OpenAI floating chat

1. Obtain an OpenAI API key through your own authorized OpenAI API account and review the API's separate billing.
2. In the **installed Pulse app** open **System** and configure the key there, not in source code or GitHub.
3. Enable AI, then double-tap the pill to try the floating chat.
4. Do **not** put the API key in a GitHub file, Actions workflow or commit. Personal source projects are not immune to credential leaks.

The prototype currently stores the key encrypted at rest on the phone. For stronger credential isolation, a private backend is the recommended future improvement.

## Part E — Making updates later

1. Edit project files locally using any text editor (VS Code is optional; Android Studio is not required to edit).
2. Upload changed files via **Add file > Upload files**, or use Git on Windows.
3. Each new commit to `main` starts a new build automatically. You can also choose **Run workflow** manually.
4. Download the new APK artifact, transfer it to the phone and install it as an update.
5. This debug APK is signed for testing. Because GitHub-hosted runners can create different debug signing keys, **later builds may not install directly over earlier builds**. You may have to uninstall the previous debug build (which can erase local app settings) unless a persistent signing key is configured. For convenient updates later, we should set up a consistent private signing key using GitHub repository secrets; never publish that key.

## Security, costs and limits

- Keep the GitHub repository **private** and never commit secrets.
- GitHub Actions is subject to account plan, usage and billing limits. Check current GitHub pricing and Actions allowances before repeated builds.
- The OpenAI API has separate usage billing; monitor spending in your OpenAI account.
- OpenF1 live timing requires appropriate API access and provider permission.
- Overlays and background services can behave differently on Samsung devices; device testing is needed.
- Debug APKs are for personal testing, not a production release.

## Troubleshooting checklist

**Build fails:** open Actions > failed run > failed step, and copy the actual error plus file and line number.

**No artifact:** artifacts are uploaded only if the Android build succeeds.

**APK won't install:** ensure you're installing the actual extracted `.apk`, not the artifact `.zip`. If an existing Pulse build is installed, a debug-signing-key mismatch is possible; consider backing up settings before reinstalling.

**App opens but no pill:** check overlay permission and the Pulse master switch; Samsung battery limits may affect background behavior.

**AI fails:** check internet connection, API key, account permissions, model name and billing. Never paste your secret API key into a chat or issue.

**F1 lacks live positions:** scheduled-session detection does not give free real-time timing; check the provider's access rules and data freshness.

## For troubleshooting or future development

Workflow: `.github/workflows/build-apk.yml`  
Android configuration: `app/build.gradle.kts`  
Source: `app/src/main/java/com/pulse/app/`  
Build output on GitHub: `app/build/outputs/apk/debug/app-debug.apk`
