# RealFrame AI Studio 2.2 — automatic local video model setup (BETA)

**Status: Android build candidate, NOT a verified 100% finished release.**

This branch builds on v2.1's stable-diffusion.cpp on-device **image** generation, reference img2img, AI storyboard keyframes → MP4, offline editor, Online hosted demos, Hybrid consent, and Internet-ON local browser/API. v2.2 adds a first-use **ONE-TAP WAN VIDEO MODEL PACK** setup, without manually managing individual model files.

## Three modes retained
- **LOCAL:** On-device CPU model, no cloud prompt upload. Internet can be ON or OFF once model weights are installed.
- **ONLINE:** Hosted demo/provider, subject to provider terms, access and quotas. User explicitly chooses it and confirms external processing.
- **HYBRID:** Prefers compatible local generation when model/RAM are available. It never silently sends local prompts or private reference images to hosted services. Uses a separate consent step before cloud processing.
- **LOCAL WHILE INTERNET ON:** The native app or authenticated local web portal uses the phone's CPU despite Wi-Fi/mobile data being ON. Loopback at `http://127.0.0.1:8820`; LAN sharing opt-in, private Wi-Fi only. This is not a public Internet server, and no claim is made about global remote hosting. Do not forward port 8820.

## NEW: ONE-TAP WAN 1.3B Q4 VIDEO WEIGHTS

Open **Local Video Lab → ONE-TAP INSTALL Wan Q4 PACK**. Accept the relevant Apache-2.0 model licenses and storage/data warning. The app automatically fetches three files to its private directory:

| Component | Model source | Size (approx) | SHA-256 prefix |
| --- | --- | ---: | --- |
| Wan2.1 T2V 1.3B Q4 diffusion | off-grid-ai/Wan2.1-T2V-1.3B-GGUF | 816 MB | `65181afff758` |
| UMT5-XXL Q4 text encoder | city96/umt5-xxl-encoder-gguf | 3.66 GB | `17cf97a5bbbc` |
| Wan2.1 VAE | Comfy-Org/Wan-2.1 | 254 MB | `2fc39d31359a` |

- Downloads happen **inside the app** over HTTPS; no Nightmare, manually imported files or API tokens.
- The installer checks published, hard-coded SHA-256 hashes, uses private storage, retries HTTP Range downloads where the host supports it, checks free disk capacity, supports pause/resume, and activates all component paths **together** after verification. Weights are not packaged into the APK.
- Approximately **4.73 GB** download and at least **5.5 GB free storage** recommended, more for MP4 exports.
- Existing advanced manual import remains available for compatible alternate files.
- **Important:** Installing weights is not equivalent to successfully rendering native video. The CPU-only Wan adapter can run out of memory or take excessive time on a 12GB Android phone. No Snapdragon NPU acceleration or offline long-form photorealistic generated motion has been certified.
- The earlier image one-tap installer remains separate (~1.75 GB SD1.5 Q4), with its own license.

## Test plan
1. Build the v2.2 APK using `.github/workflows/realframe-v22-build.yml` (APK artifact `RealFrameStudio-v2_2-auto-wan-beta-apk`).
2. Install and launch on Android. Verify Home / Online / Local / Hybrid / Library / model setup and camera-gallery picker screens.
3. Image one-tap: accept license; allow the app's initial SD1.5 model transfer. Run native **On-Device AI Proof** with Internet ON, then OFF. Success means a real JNI-generated **256×256, 4-step image** appears; error means local inference is **not verified**. Record elapsed time, RAM, and any error.
4. Reference Studio: test reference img2img using a permitted image; keep strength 0.25, batch 1. Identity consistency is not guaranteed.
5. Local Video Lab: optionally accept the Wan model license and download the 3 files inside the app. Start with 256×256, 5 frames, 4 steps. If model cannot load or phone OOMs, **do not** claim video works.
6. Story → Movie (2 AI keyframes, 1 second per scene) and non-AI photo movie composer (2 photos) produce local H.264 MP4s; neither is a fully generative motion-video substitute.
7. Online hosted demos may require login or quota. Hybrid must request consent before external provider contact. Local API can be tested over localhost with Internet ON/OFF; optional private Wi-Fi LAN requires a secret token.
8. Check MP4 outputs, playability, thermal throttling, model resume/sha verification and cancel behavior. CI checks code, not physical device video performance.

## Real remaining limitations
- No measured physical-device CPU/GPU/NPU benchmarks, especially on the targeted Samsung.
- No guarantee the large Wan encoder and decoder complete real local text-to-video within 12 GB RAM.
- No trained FaceID / LoRA identity lock, fully automatic high-fidelity long AI films, true simultaneous heavy GPU jobs, or unlimited free cloud compute.
- App's cloud integrations use third-party demos rather than a contracted, guaranteed inference API.
- Debug APK must not be called a stable signed consumer release without device QA, production signing and release validation.

Code is on branch `realframe-studio-v22`. Package `studio.realframe.v22`, version `2.2.0-auto-wan-beta`. Retain previous installed beta until your actual on-device tests pass.
