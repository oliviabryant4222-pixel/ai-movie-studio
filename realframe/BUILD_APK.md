# RealFrame Studio v1.1: GitHub Actions Debug APK build

GitHub repo workflow entrypoint is `.github/workflows/android-debug.yml`, project root `android/`.

1. Upload/commit this project's extracted contents to an appropriately named GitHub branch, including `android/`, `tests/`, `tools/`, and `.github/workflows/android-debug.yml`.
2. Trigger a build from the Actions tab (workflow_dispatch, select the branch), or configure the workflow push trigger to your branch.
3. Confirm all checks pass: Python tests, Java MediaSignature test, Android Gradle compilation, `apksigner verify`, and `aapt dump badging`.
4. From successful workflow run → Artifacts → `RealFrameStudio-v1_1-debug-apk` → extract `app-debug.apk`.
5. Install on Samsung. If Android refuses with "App not installed" / conflicting signature, previous v1.0 debug APK was signed with a different key; **do not uninstall old app if you need its private saved data**. See README update-signing warning.

CLI equivalent when SDK 35/JDK 17/Gradle 8.9 installed:
```
python3 -m unittest discover -s tests -v
javac -d /tmp/realframe-test android/app/src/main/java/studio/realframe/MediaSignature.java tests/TestMediaSignature.java
java -cp /tmp/realframe-test TestMediaSignature
python3 tools/preflight.py
cd android && gradle --no-daemon assembleDebug
```

This repository does not include third-party native AI inference binaries. An APK build alone is not evidence real model inference is functional. Validate via Connect AI screen with a compatible installed Nightmare image checkpoint and saved video flow.
