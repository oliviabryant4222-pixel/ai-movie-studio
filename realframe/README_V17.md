# RealFrame AI Studio v1.7 — Hybrid Create + genuine native local image batching

## Improvements shipped in v1.7
- **Unified Creator**: new dark-themed prompt-first screen selecting Image/Video, Online/Local/Hybrid, Character Vault preset, negative prompt and seed.
- **Privacy-first hybrid router** (separately unit-tested in Java): when a real local image model is imported and enough memory is available, Hybrid prefers the phone's CPU. If local inference isn't ready, it asks rather than uploading prompts without permission. Online always presents a cloud-data consent dialog. Local video explicitly displays **unsupported**, not fake generation.
- **Real native local image batch**: 1–4 requested images in the UI; the JNI bridge supports 1–8. Uses one loaded stable-diffusion.cpp model context and sequential `generate_image` calls to avoid repeated GB-scale model load and heavy parallel RAM contention. Each completed PNG is saved to Android Gallery before the next, so previously saved images survive later errors/cancel.
- **Cancel**: uses stable-diffusion.cpp's native cancellation hook; cancelling model load may not be immediate.
- **Model import hardening**: preflights the file header for .safetensors / .gguf / .ckpt, rejects obviously invalid/truncated files, monitors free storage even if file size is unknown. This is a container-header check, not a guarantee that the model architecture and tensors are compatible.
- **Prompt handoff**: Unified Creator forwards the same prompt to Local Image or the embedded third-party online demo; provider interface still requires you to paste and press Generate.
- Existing dark dashboard, photo editing, still→MP4, compatible clip joining, Storyboard, Character Vault, portable production planning and configured job queue retained.

## What to test on Samsung
1. Install signed debug APK v1.7 (a new app/package from previous betas).
2. **Unified Creator → Local → Image**. In Local Image Studio, import one single-file *compatible* SD1.5 checkpoint from your downloads. Downloading models is your responsibility; check licenses. Multi-GB local copy is necessary for native mmap.
3. Set resolution **256×256**, **4–8 steps**, batch **1** for the first smoke test. Keep the app open; the CPU may be slow or run out of RAM. If this works, try batch **2**, then batch **4**.
4. Check `Pictures/RealFrameStudio`: every successful batch image should already be saved. If you cancel, earlier outputs should remain.
5. **Unified Creator → Online → Image or Video**: confirm cloud privacy, then copy/paste prompt into the hosted Hugging Face demo. Free hosting has quotas, downtime and content rules.
6. **Unified Creator → Hybrid**: if the installed native engine, file and available RAM match requirements, it opens Local Image. Otherwise you explicitly choose local import or online cloud. Local AI Video is **not implemented**.

## Important limitations
- **Local image generation is experimental and has not been validated on your Samsung with real model weights.** A successful APK build and automatic mock tests are not a device-level diffusion benchmark.
- **CPU only.** No Snapdragon QNN/NPU acceleration; do not expect desktop GPU speeds.
- **Offline AI video diffusion is not implemented**. Offline video tools edit/encode existing assets. A still-photo→MP4 file is *not* AI animation.
- **No fully automatic hybrid fallback or true parallel GPU tasks**; mode selection is intentional and cloud usage always needs consent.
- **No guaranteed face-lock**. Character Vault and scene prompts improve reproducibility, not identity-lock across frames.
- Free online GPU capacity **is quota-limited** and controlled by external hosts. No unlimited GPU promise.
- This build includes a native `arm64-v8a` shared library and compiled upstream code but **does not include any pretrained model weight files**.

## Technical
- stable-diffusion.cpp pinned at `b9d7176bf88fe465576e0aafbe47ba253ffcfc2c`, MIT licensed, GGML CPU backend, native JNI CMake + Android NDK.
- App package: `studio.realframe.v17`, version `1.7.0-hybrid-batch-beta`.
- CI workflow: `.github/workflows/realframe-v17-build.yml`. It runs Python tests; pure Java hybrid routing and model inspector tests; native C++/Android compilation; and APK signature verification.
- `bash realframe/tools/fetch_native_engine.sh` clones upstream pinned source for local builds before Gradle `assembleDebug`.
- New work is isolated on branch `realframe-studio-v17`, leaving older versions intact.
