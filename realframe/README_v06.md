# RealFrame AI Studio v0.6 — Android source and test kit

**This project is not yet an installable APK.** It is a native Android source prototype/controller, requiring a separately installed compatible image/video model engine (for local use) or a Gradio Space API (for optional free quota-limited cloud use). No models or external free GPUs are included. A physical Android compile and device test have **not** been performed in this environment.

## What changed since v0.5

1. **Queue safety:** cancel one waiting/error/blocked job, or all queued jobs at once, without deleting completed clips. Running requests are not interrupted. A status recheck reduces cancellation/start races.
2. **Better verification:** added `tools/preflight.py` to fail fast when critical project files/config are missing. GitHub Actions now checks APK package signature and badging after compilation.
3. **API contract test bench:** `tools/mock_nightmare.py` provides synthetic `/info`, `/models`, `/flows`, `/run`, and `/results` routes for offline connector testing, including *fake* PNG/MP4 media and bearer-token checks. This **does not generate AI content**. It only verifies integration plumbing.
4. **25 automated tests pass** on Python/FFmpeg + mocked HTTP. The Android SDK is not present here, so this is *not* proof of Android APK compilation.
5. **More accurate device expectations:** Snapdragon 8 Gen 3 video support depends on exact Nightmare flow/model; upstream lists several experimental video paths. Never assume all models work from RAM alone.

## Current architecture and controls

- Native Java Android UI (Generate, Character Vault, Storyboard, Jobs, Clips, Settings)
- Character appearance, wardrobe and optional image reference storage; no guaranteed identity lock
- Single image/video jobs, up to 250 jobs submitted per batch; serial local rendering
- Storyboard and chain-planned projects of up to 1,800 clips, plus MP4 muxer for matching formats
- Nightmare local API adapter; independent upstream app and model downloads required
- User-specified Hugging Face Gradio Space connector; free quotas and cloud policies apply
- Local job and clip persistence; pause-after-current, retry and cancel waiting jobs
- Previous clip end-frame references supported *when the chosen flow honors images*

## Run tests on PC or Python-enabled phone

```bash
python3 -m unittest discover -s tests -v
python3 tools/preflight.py
```

FFmpeg is required for the MP4 tests. Test the synthetic API separately:

```bash
python3 tools/mock_nightmare.py --port 8821
# other terminal:
curl -H 'Authorization: Bearer local-test-token' http://127.0.0.1:8821/models
```

The mock API generates a 1×1 PNG and a black 1-second MP4. **It is not an AI model.**

## Build Android APK

See [BUILD_APK.md](BUILD_APK.md) for Android Studio and GitHub Actions instructions. The source root for Gradle is **`android/`**, not the top of the ZIP. Debug APK build requires Android SDK 35, Gradle 8.9, Android Gradle Plugin 8.6.1 and JDK 17.

**Known limits:** No on-device APK test, no independent high-end image/video model weights, no unlimited free cloud GPU, no true simultaneous heavy video rendering, no guaranteed character consistency, and no universal video format transcoding in-app. Do not misinterpret the 1,800-clip planner as a 1,800-clip free cloud quota.
