# RealFrame AI Studio v2.0 — ONE-TAP LOCAL AI (no manual model files)

**This release is an Android beta, NOT a fully verified finished local AI movie studio.** It retains all three processing modes: **Fully Local**, **Online (cloud providers)** and **Hybrid**, plus a fourth access pattern: **Local AI while Internet is ON** (phone's own local API/web server, no remote inference).

## What was already implemented (kept; NOT duplicated)
- Phone-local image inference engine: MIT-licensed [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp), arm64 CPU backend, from the v1.6+ beta. Requires licensed model weights.
- Phone-hosted, optional authenticated local HTTP server from v1.9. On-phone `127.0.0.1:8820` works whether Internet is ON or OFF. Explicit opt-in trusted private LAN sharing lets another device use the phone's native engine. The local server does **not** expose itself to public Internet; remote Internet access would need a separately secured VPN/HTTPS proxy.
- Separate hosted online AI image/video demos; network access/provider quotas and rules apply. Hybrid mode never silently sends a local-only prompt to the cloud.
- Character Vault, batch prompts, offline photo lab, image→MP4 and clip editing.

## New in v2.0: zero manual model file handling for local AI images
- **Quick Start** button is prominent on Home, Online, Offline, Hybrid, Local Image and Model Manager screens.
- User accepts model license and taps once to let the **app automatically download its 1.75 GB Stable Diffusion v1.5 quantized GGUF model**. No separate AI app, no manual model link, no token, no file-picking or copying needed.
- The model installer is a foreground service that can resume a partially transferred HTTPS file, checks free disk space, confirms the download matches the expected SHA-256 and checks GGUF format before activating the local image generator.
- The model remains private to RealFrame and is installed to the existing `realframe_local_sd/modelPath` setting shared by native image generation and phone-local web API. After the initial download, **image inference happens on the phone**, whether or not mobile data/Wi-Fi is connected.
- Users may still import their own models manually as an advanced option.

### Model & licenses
- Native source: `leejet/stable-diffusion.cpp`, MIT license (already integrated into APK).
- Pre-selected model weights: `gpustack/stable-diffusion-v1-5-GGUF` / `stable-diffusion-v1-5-Q4_0.gguf`, approximately 1.75 GB, CreativeML Open RAIL-M license. Model page: https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF
- SHA-256: `c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b`.
- **Model weights are NOT packaged into this APK.** An initial ~1.75 GB transfer cannot be skipped. They are downloaded automatically WITHIN THE APP so the user need not manually download or manage files. The user explicitly approves the license and cellular-data usage warning. The model host may be unavailable or block the download; the app displays an error instead of pretending the model was installed.

## Quick start (Roman Urdu)
1. v2.0 APK install karo.
2. Home par `ONE-TAP LOCAL AI SETUP → GET LOCAL AI READY` kholo.
3. `Install LOCAL Image AI inside app` press karo, license accept karo. Phone ko Wi-Fi par rakho aur ~2.5 GB free storage rakho. RealFrame model ko automatically download aur verify karega. User ko koi alag file manually download ya select nahi karni.
4. `Generate LOCAL AI image` kholo. Start with 256×256, 4 steps, batch 1. Agar image successfully generate ho toh Gallery mein save hogi.
5. Internet ON hone ke bawajood local mode mein prompt aur model inference phone par hoti hai. `On-device Local AI Server` se apni local web access bhi use kar sakte ho. `ONLINE` mode doosri, hosted AI service hai.

## Honest limitations
- Successful Android CI build is **not** a Samsung real-model benchmark. SD1.5 may take significant time or run out of memory. No integrated Qualcomm QNN/NPU acceleration.
- **Local AI video** still has an experimental native Wan T2V CPU adapter requiring **several extra large files**. It is NOT one-tap installed, not known to fit in 12GB RAM and NOT guaranteed to generate a video.
- Character presets are not mathematical face identity locking; local video generation, unrestricted free cloud GPU and automatic multi-GPU parallel jobs are not complete.
- Local while online does **not** mean a remote public-cloud GPU generates the image or that a phone creates Internet service without security. It means the phone itself is the local AI server while it remains connected.

Build: `realframe-studio-v20` branch, `.github/workflows/realframe-v20-build.yml`; v2.0 test APK package `studio.realframe.v20`. Automated tests validate code paths, not actual model weights or end-device performance.
