# RealFrame AI Studio v1.8 — online + local CPU image + experimental local T2V

## One Android app; three processing modes
**ONLINE:** the in-app WebStudio opens externally hosted image and video diffusion demos from Hugging Face. Some Spaces may require login or be offline. Provider GPU quotas, terms, model licenses and content policies apply. The provider receives prompts and images you upload.

**LOCAL:** RealFrame includes native arm64 `stable-diffusion.cpp` image generation (SD checkpoints) and **new experimental Wan text-to-video** implemented with the upstream C API `generate_video`. The Java encoder packs returned RGB frames into actual **H.264 MP4** via Android `MediaCodec` + `MediaMuxer`, then exports them to `Movies/RealFrameStudio`. No model weights included; the app must download/import legal compatible components separately. Device-level inference is NOT verified.

**HYBRID:** Unified Creator routes an image/video request to local when a compatible imported model and engine are detected and RAM appears adequate; otherwise it gives a **manual choice**, and prompts for explicit **cloud processing consent**. Hybrid mode does not silently upload your photos or prompts.

## New v1.8 local AI video model setup

1. Install the v1.8 APK.
2. Open **Offline → Local AI VIDEO (Wan CPU lab)** or **Unified Creator → Local → Video**.
3. Obtain three properly licensed *compatible* model components. See the upstream guide: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/wan.md
   - **Wan2.1 T2V 1.3B diffusion checkpoint** (not a random Wan I2V or 14B model).
   - **UMT5 XXL text encoder**, preferably a supported quantized GGUF to reduce memory.
   - **wan_2.1_vae.safetensors**.
4. Select the **diffusion**, **T5** and **VAE** files one by one using Android's file picker. RealFrame makes private local copies and validates basic container headers, so you need free storage at least as large as their combined file sizes plus a safety margin.
5. First attempt: **256×256**, **5 frames**, **4 sampling steps**, with a short prompt. Each video uses about 16 frames/s; 5 frames is only a tiny diagnostic clip. Keep the app open. Native `generate_video()` may take a very long time or trigger OOM on a 12GB phone.
6. If native inference and Android H.264 encoding both succeed, the MP4 appears in **Movies/RealFrameStudio**. Failed inference does not produce a fake or pre-generated video; the UI displays the actual error.
7. To create long films, generate additional clips, then use the existing **Clip Timeline** to join matching output MP4s. Character Vault/Production Planner help keep consistent descriptions but do not enforce face identity.

### Warning about real-world feasibility
**A successful GitHub APK build is NOT proof that on-device Wan T2V works on your Samsung.** The CPU models and their T5/VAEs may exceed memory or take an impractically long time. The Snapdragon NPU/QNN backend is NOT included. If local Wan fails, online hosted generation is the practical video option, with quotas. Do not claim offline video works until a model completes on the physical phone.

## Existing and retained capabilities
- Online third-party image/video AI via embedded HTTPS demos (no Nightmare installation, separate manual API token is not needed to open public demos).
- Native CPU image generator with **1–4 sequential local images per batch** and Gallery PNG auto-save; requires a compatible SD1.5 single-file checkpoint.
- **Native video bridge** with separate, user-imported Wan T2V, UMT5 and VAE components; CPU-only, unverified on device.
- Local Photo Lab for brightness, contrast, saturation.
- Offline still photo → MP4 (not generative AI).
- MP4 clip ordering/merging for identical codecs/formats.
- Character Vault, storyboard and long-film prompt manifests.
- Hybrid routing with manual/explicit cloud consent.
- Model manager with RAM and free-storage info.

## Not completed or guaranteed
- No verified on-device Wan video benchmark, no support promise for Snapdragon 8 Gen 3 / 12 GB.
- No Qualcomm NPU acceleration, no guaranteed 100% local video speed or image quality.
- No local image-to-video reference conditioning: the Wan bridge currently supports text-to-video only. Model compatibility requirements vary.
- No true multi-GPU parallelism; one heavy native inference job runs at a time across image/video to avoid out-of-memory.
- No automated orchestration of several hosted GPU Space jobs, no unlimited free cloud compute.
- No guaranteed character identity lock; prompt presets are not face embeddings.
- Local model downloads, redistribution rights and storage are user-managed.

## Build and tests
- Branch: `realframe-studio-v18`
- GitHub Actions: `.github/workflows/realframe-v18-build.yml`
- Android package: `studio.realframe.v18`.
- Native MIT stable-diffusion.cpp pinned commit: `b9d7176bf88fe465576e0aafbe47ba253ffcfc2c` from Nov 12, 2026.
- Android SDK 35, NDK 27.2.12479018, Java 17, CMake 3.22.1.
- CI runs static Python tests, pure Java hybrid routing and model-format smoke tests, Java media signature test, C++/Java Android compilation and signed APK verification.
- No test is a substitute for loading real model weights on the actual Samsung.
