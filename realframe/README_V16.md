# RealFrame AI Studio v1.6 — actual CPU-local image engine (experimental)

## What changed
This version is not just a placeholder model manager. It integrates **MIT-licensed stable-diffusion.cpp** through a **real arm64-v8a Android NDK/JNI library**. The function `LocalImageEngine.nativeGenerate` creates a diffusion context with a compatible user-imported model, calls `generate_image`, and writes the actual locally generated pixels to the phone Gallery.

The source is pinned to `leejet/stable-diffusion.cpp@b9d7176bf88fe465576e0aafbe47ba253ffcfc2c` and uses its GGML CPU backend. Native source is downloaded at build time using `bash realframe/tools/fetch_native_engine.sh`. Upstream stable-diffusion.cpp license is MIT; preserve upstream notices. This build does **not** include downloaded model weights.

### Test local image generation
1. Install the v1.6 APK on a compatible **arm64 Android 10+** phone.
2. Open **Offline → Local AI Image (CPU beta)**.
3. Tap **Import single-file SD checkpoint**, choose a compatible downloaded **SD1.5 single-file .safetensors / .ckpt** checkpoint, and let it copy into RealFrame's private model cache. You must have the rights to use the checkpoint. Import takes storage equivalent to the full model file, plus a safety margin. Merely choosing a model URI in Model Manager does not import it.
4. Use **256×256** and **4 or 8 steps** for the initial smoke test. Keep the app open. Image generation can be extremely slow or run out of RAM.
5. Press **Generate on this device**. Native code does not use Internet. Save the generated PNG to **Pictures/RealFrameStudio**.
6. Test whether a photo appears in the gallery. If the process closes, it's likely model incompatibility or out-of-memory; report phone model, model name/size, chosen resolution/steps.

### Online and hybrid
- Online hosted image/video demos still work from **Online**. Providers' GPU quotas/content rules still apply.
- **Hybrid → Choose local or online image** asks before choosing which image engine to use. Selecting local does not upload prompts. Selecting online uses third-party servers.
- Advanced configurable job queue, Character Vault, offline Photo Lab, still-photo→MP4, clip concatenation and long-video storyboard remain.

## What remains incomplete (critical)
- Native CPU text-to-image **has NOT been benchmarked or device-tested on Samsung**. A successful Android CI build verifies compilation, **not** successful checkpoint loading/rendering.
- Only **experimental arm64 CPU** inference. **No Qualcomm NPU/QNN optimization**, GPU acceleration or guaranteed SDXL throughput in this build.
- **No offline AI text-to-video or image-to-video renderer**. Only offline video editing / still-photo encoder.
- No bundled or automatic licensed model download. Splitted DiT model packs (Wan/FLUX) need extra tokenizer/text encoder/VAE component loaders and aren't ready through the single-file import UI.
- **No automatic hybrid background fallback** or truly parallel local rendering. Hosted provider limits still apply.
- Fictional adult character recipes and scene prompts improve continuity guidance but are not mathematical identity locking.

## Build
Branch `realframe-studio-v16`; workflow `.github/workflows/realframe-v16-build.yml`.
Manual local setup: JDK17, Gradle8.9, Android SDK35, NDK27.2.12479018, CMake3.22.1, bash/git and network connection for pinned stable-diffusion.cpp code. Run:
```
bash realframe/tools/fetch_native_engine.sh
cd realframe/android
gradle --no-daemon assembleDebug
```
The apk will contain compiled native model inference binaries but **no model weights**; not a full offline video generator.
