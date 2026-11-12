# RealFrame AI Studio v2.1 — local reference AI, automatic story movies, online + hybrid

**Status: Android beta, not 100% feature-complete or device-verified.** This version adds genuine on-device SD image-to-image conditioning and two useful offline MP4 creation workflows. Existing Online and Local-While-Online modes are retained. We do not label photo animation as AI motion diffusion.

## Features in one APK

**LOCAL image AI**
- Native arm64 stable-diffusion.cpp (MIT) runs text-to-image on the phone's CPU.
- **One-Tap Local AI Setup** downloads an SD1.5 Q4 GGUF model (~1.75GB) automatically inside RealFrame *once*, over HTTPS, following explicit model license consent; no Nightmare, API key, manual model files or second application. Verifies published SHA-256.
- 1–4 image batch with on-phone Gallery exports, pause/cancel support. Actual SD checkpoint loading and performance remain **unverified on the user's Samsung**.

**NEW: Reference Image Studio**
- Gallery picker, local image crop/downsampling, Character Vault appearance preset, edit strength (0.25/0.40/0.55/0.75), negative prompt, seed, 1–4 batch results.
- C++ JNI populates stable-diffusion.cpp `sd_img_gen_params_t.init_image` with the actual photo RGB pixels and chooses `params.strength`, rather than pretending a text description is reference conditioning.
- Every rendered photo saves to Gallery without network upload. Photo input also stays local.
- **Limit:** img2img preservation is not an identity lock. There is no trained IP-Adapter, LoRA, FaceID, or face embedding pipeline in this beta.

**NEW: Automatic Local AI Story → MP4**
- Prompt + character preset, 2/4/6/8 distinct AI keyframe scenes, optional continuation strength, stable seed, 1/2/3 seconds per scene.
- A single SD model session generates actual image keyframes. Scene N+1 is conditioned on scene N's previous RGB result using `init_image`, preserving visual continuity more effectively than prompts alone (not guaranteed).
- Android H.264 MediaCodec encoder animates those still keyframes with camera pan/zoom/fades into a real **Gallery MP4**. All steps run on phone, even if Internet stays ON.
- **Limit:** This is an AI KEYFRAME MOVIE with conventional 2D cinematic motion, NOT a photorealistic moving-person diffusion video. Each generated AI keyframe may take substantial time on phone CPU. H.264 output still needs a Samsung device smoke test.

**NEW: Photo Motion Movie Composer**
- Choose 2–16 photos using Android's gallery/document picker.
- Subtle pan/zoom, crossfade between scenes, 2–5 seconds per photo; H.264 MP4 locally exported to `Movies/RealFrameStudio`. No AI model needed for this editor.
- Useful for downloaded AI images or photos. It is editing/animation, not generative video.

**LOCAL text-to-video (experimental)**
- Existing v1.8 Wan T2V `generate_video` bridge remains with Android H.264 encoding and 3 component model import.
- Requires large separate Wan model/T5/VAE weights and unverified phone hardware capacity. Cannot guarantee that native Wan loads, finishes or fits 12 GB RAM.

**ONLINE hosted AI**
- Embedded HTTPS third-party image/video demos (provider quotas, availability, login, content rules). Prompts/uploads leave phone only after entering online workflow. No unlimited GPU claim.
- Your local phone AI web server and authenticated browser portal work independently of hosted demos.

**HYBRID**
- One Create page selects Online, Local or Hybrid. Prefers local image when model is ready and RAM seems sufficient; prompts before sending data to hosted providers.
- Local while online means **phone uses its CPU even when mobile data / Wi-Fi is ON**, and optionally shares an authenticated HTTP server over trusted private Wi-Fi. It does NOT expose the phone to the public Internet; use VPN/HTTPS reverse proxy if you need remote access.
- No automatic cloud fallback without user consent. Heavy concurrent local GPU workers are not safe on 12 GB shared RAM; sequential batching is intentional.

**Also retained:** Character Vault and profiles, storyboard planner, local photo lab, still→MP4, video clip joiner, queue UI, offline exports.

## Test v2.1 on Samsung
1. Install APK (separate beta application package from v2.0 due to debug signing, so imported model data from v2.0 does not carry over automatically).
2. **Home → GET LOCAL AI READY** → accept license to let the app fetch ~1.75GB model automatically. No separate model-file download needed. Start with local text-to-image 256×256 / 4 steps / batch 1. If this fails, collect the exact on-screen error; native library compilation != device inference success.
3. **Home / Offline → Reference Character AI**. Choose a reference photo; select edit strength 0.25 and batch 1; test.
4. **Home / Offline → Automatic Local AI Story → MP4**. Start with 2 scenes × 1 second and 4 steps. Confirm Gallery movie if both inference and encoder succeed.
5. **Local Cinematic Movie Composer** can be tested independently of AI models with 2 ordinary Gallery pictures. Check `Movies/RealFrameStudio`.
6. **Online** and **Hybrid** tabs should still expose the original hosted demo and private on-phone web server.

## Engineering and licensing
- Branch `realframe-studio-v21`; CI workflow `.github/workflows/realframe-v21-build.yml`.
- Package `studio.realframe.v21`, version `2.1.0-reference-movie-beta`.
- Upstream C++ source pinned to `leejet/stable-diffusion.cpp@b9d7176bf88fe465576e0aafbe47ba253ffcfc2c` (MIT).
- One-tap SD1.5 quantized model: `gpustack/stable-diffusion-v1-5-GGUF` Q4_0, CreativeML Open RAIL-M, expected SHA-256 `c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b`. Model weights are NOT bundled; first-run downloads from Hugging Face and license restrictions apply.
- Android SDK 35, NDK 27.2.12479018, CMake3.22.1, arm64 only.
- Automated tests verify JNI wiring, Android compile, media/code paths and signature, **not** photorealistic quality, NPU acceleration, native Wan video success or actual Samsung temperatures.
- Intended for fictional adults. Don't use real individuals' photos without permission and don't upload private references to third parties without consent.

## What blocks a claim of “100% complete”
- No confirmed successful local diffusion inference benchmark with the user's installed model on the Samsung device.
- No proven Snapdragon QNN/NPU acceleration, no true high-fidelity locally generated motion video or secure public internet cloud service included.
- No guaranteed face identity/LoRA consistency; improved img2img reference continuity is useful but limited.
- No unrestricted free cloud GPU, and no safe simultaneous large-model processing guarantee on 12GB hardware.

These limitations are technical, not items solved by more buttons. Keep earlier working v2.0 installed until this build has been tested.
