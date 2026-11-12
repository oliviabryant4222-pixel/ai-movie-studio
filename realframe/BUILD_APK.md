# Build RealFrame Android APK

This is a **debug/testing build**, not a Play Store release. The project root is `android/`.

## GitHub Actions
This repo contains a workflow at `.github/workflows/android-debug.yml`. Upload the extracted project contents **at the repository root**, not only a ZIP. Open Actions → Build Android debug APK → Run workflow. The workflow runs 25 Python tests, preflight, downloads SDK, builds, signs and uploads `app-debug.apk` as an artifact.

If source lives under `realframe/` in a GitHub branch, a branch workflow under `.github/workflows/realframe-v1-build.yml` is also provided and builds from that folder; it uploads `RealFrameStudio-v1-debug-apk`.

## PC build
Install Android SDK 35, Build Tools 35.0.0, JDK 17, and Gradle 8.9, then:

```
cd realframe_studio_v1/android
gradle --no-daemon assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Samsung installation
Download artifact ZIP, extract `app-debug.apk`, use Files → APK → Install. Android may ask to allow installs from your browser/file manager. After installing: first test Home → Offline TEST image / video, then configure an inference backend under Settings for real AI.

If the workflow fails, open Actions job logs and fix the reported compilation or SDK issue. Do not claim a working APK without a successful build.
