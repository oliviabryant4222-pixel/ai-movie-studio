# RealFrame AI Studio v1.1 — real-model connection beta

## Important status
This is the **source-code update** to v1.0, not a compiled APK. v1.0's synthetic image/video tests verify installation, gallery and clip playback; they do **not** verify that a real image/video model runs. Version 1.1 adds a guided **Connect AI** screen and the documented Nightmare Mobile Automatic1111-compatible image API for a real local text-to-image request, plus a way to select a saved video flow. Both require installing and configuring Nightmare Mobile separately. No NPU model weights or native backend are bundled; no free cloud GPU is guaranteed. Same-character face consistency is reference-assisted, not locked or guaranteed.

## New in v1.1
- **Connect AI tab:** enter Nightmare API URL & token, check installed image models, queue one **REAL image**, discover saved flows and queue one **REAL video** with optional image reference. `a1111` automatically uses `/sdapi/v1/txt2img` with HTTP Basic auth. `/run` uses Bearer auth. Those are different credentials for the same Nightmare token.
- **Easy real image batch:** in Generate, choose `image`, `Local`, flow `a1111`; prompts and batch seeds now call the documented Automatic1111 API. Advanced `/run` image flows still work if you select their flow ID.
- **Media format verification:** byte signature check of PNG/JPEG/WebP/MP4 before writing provider results to Gallery. A provider's HTML error page cannot become a corrupt .mp4.
- **Safer backups:** Settings exports/imports project **metadata JSON** via Android document picker. It excludes generated media bytes, original reference pictures and API tokens. Import replaces data, pauses waiting jobs; externally linked `content://` URIs may be invalid after reinstall.
- **Java and mock API tests:** 28 Python unit tests, 7 pure Java media signature checks on the development machine. A GitHub Actions workflow includes Gradle Android compile/signature verification.

## Get your first REAL AI image on the Samsung
1. Install official **Nightmare Mobile** from `https://github.com/AbrahamPaulJ/nightmare-mobile/releases`, run it, download and select a compatible **image model**. On Snapdragon 8 Gen 3, check Nightmare's Device compatibility panel.
2. Inside Nightmare, **Settings → Advanced → API → Listen on the local network**. Copy its URL and token. Avoid publishing the API address/token.
3. Launch RealFrame v1.1 → **Connect AI** → enter URL/token → **Save local connection**.
4. Tap **1 · Check installed image models**. Successful result means you connected, **not** that image inference passed yet.
5. Tap **2 · Generate REAL local image** and inspect Jobs → Open result. It can take time and fail depending on model/chip/memory. Unlike offline TEST, this sends an actual rendering request to Nightmare.
6. For video, make sure a video flow produces an MP4 in Nightmare first. In RealFrame tap **3 · Discover local video flows**, select an actual video flow, optionally attach an input image, then queue video. 8 Gen 3 support is model-specific and some routes are experimental.

## Bulk and long videos
- Generate tab supports batches up to 250 jobs submitted at once; local inference is **sequential** (not multi-GPU parallelism).
- Storyboard tab schedules short clips; previous clip's last frame is an optional reference; identity drift is possible.
- Clips tab stitches MP4s with **matching formats**. Mismatched MP4s need normalization via `tools/stitch.py` using FFmpeg on a computer/Termux.
- Real remote rendering: configure Gradio Space endpoints in Settings following each Space's **Use via API** schema. Provider limitations and quotas apply. There is no bundled cloud account.

## Build
Project is in `android/` (Android Gradle Plugin 8.6.1, compile SDK 35, JDK 17). GitHub Actions workflow: `.github/workflows/android-debug.yml`. It runs Python + Java unit tests and `gradle assembleDebug`, then verifies debug APK signature. See `BUILD_APK.md`.

**Developer validation:** 28 Python tests passed, 7 Java media-header tests passed. Android compilation and Snapdragon device testing have **NOT** happened in this development environment. A source ZIP is not an APK.

## Important update-install warning
GitHub Actions' generated **debug signing key** can change between runs. If it does, Android won't accept a new debug APK as an upgrade to v1.0 with the same package name (`studio.realframe`). Before uninstalling v1.0, consider that its private jobs and characters will be deleted and v1.0 has no backup button. v1.1 adds metadata backups for future updates, but they cannot back up a previous version that lacks the function. To preserve existing installation data across further updates, keep the **same signing keystore** securely in CI; don't distribute a release app with a committed debug keystore.

## Legal and privacy
Local rendering doesn't automatically remove model licenses or legal constraints. Make fictional adult characters and respect consent. Local rendering stays on your device; selecting Cloud uploads prompts/references to an external provider. Never post your Nightmare/HF API tokens publicly.
