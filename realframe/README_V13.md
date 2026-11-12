# RealFrame AI Studio v1.3 — hybrid creative workspace

An Android beta with a redesigned dark interface and **two distinct modes**.

## ONLINE: AI image + AI video (working provider demos)
- From the redesigned dashboard, tap **Online → AI Image Studio / AI Video Studio**.
- The embedded Hugging Face Spaces run third-party models on hosted servers.
- No Nightmare installation, no manual API URL or token needed for public demos.
- **Not unlimited**: model quotas, space uptime, content policies and logins remain with hosting providers.
- Uploads and prompts leave the phone; avoid private or non-consensual material.
- Use the in-app prompt clipboard and download button or **Open browser** when the embedded WebView cannot handle a format.

## OFFLINE: local photo tools + video editing (working)
- **Offline Photo Lab**: pick an existing image, live-preview brightness/contrast/saturation adjustments, export JPG to `Pictures/RealFrameStudio` without network.
- **Still Image → MP4**: turn an existing photo into a silent 2–14-second still-video locally using Media3 Transformer. Exports to Movies/RealFrameStudio and adds it to your clips. No AI-generated motion.\n- **Clip Timeline**: import compatible MP4s, reorder them and concatenate using Android MediaExtractor/MediaMuxer to `Movies/RealFrameStudio`.
- **Characters**: store descriptive profiles and reference URIs; **Storyboard**: create projects; **Jobs**: queue management.
- Local photo adjustment and video joins are **not generative AI**.
- Native text-to-image and text/image-to-video **do not yet run offline in RealFrame**. The app does not bundle Snapdragon/QNN inference binaries, model files, or a native diffusion renderer.
- Existing optional advanced connector screen remains available (legacy app). It requires a separately configured engine for native generation.

## UX upgrade
A full-screen custom native dark dashboard, clear Online and Offline navigation, cards for AI image/video and device-local work, access to Vault/Editor, and a matched offline photo lab. Advanced studio's navigation and action buttons were updated for consistency.

## Compatibility
- minSdk 29, compileSdk / targetSdk 35, Java 17 / Gradle 8.9.
- Intended testing on the user's Snapdragon 8 Gen 3 / 12 GB device, with real device performance unverified.
- Android system image picker and MediaStore outputs used; no storage permission required for app-created media.
- v1.3 uses a separate package `studio.realframe.v13` because cloud CI's ephemeral debug signing key may differ from the earlier beta.

## Open-source offline AI roadmap (not claimed as implemented)
- Investigate licensed embedding of `xororz/local-dream` (SD1.5/SDXL Android and Snapdragon NPU). Repo says SDXL NPU for Snapdragon 8 Gen 3+ but requires large downloaded model and QNN runtime; license and native build restrictions must be honored.
- Keep CPU/GPU fallback where possible and make model downloads, device capability checks and cancelable rendering explicit.
- For offline diffusion video investigate quantized model path with genuine device-level benchmarks. No credible guarantee on throughput with 12GB shared RAM.
- Keep image/video clip batching separate from true simultaneous model execution; avoid GPU contention.
- Use hosted generation for large video workflows when the user's free quota allows it; export/stitch on device.

## Build / test
Branch: `realframe-studio-v13` in repository `ai-movie-studio`. GitHub Actions: `.github/workflows/realframe-v13-build.yml`.
Build `gradle assembleDebug` under `realframe/android`.
Automatic tests check source wiring and existing clip/mocking tools; CI verifies signed APK.
Real Samsung smoke tests must validate the WebView, Android photo picker, JPG export and MP4 concat.
