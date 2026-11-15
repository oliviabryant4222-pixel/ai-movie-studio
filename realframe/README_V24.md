# RealFrame AI Studio v2.4 — Samsung installable build

**Build classification:** one complete installable Android beta/engineering build for supported features, not a claim of unlimited hardware or an independently verified production app. 100% of every requested high-end feature (especially unlimited high-fidelity local video) cannot honestly be guaranteed on 12 GB mobile hardware.

## Unified Android creative studio (supported paths)
- **LOCAL fully offline**: native stable-diffusion.cpp on-device image generation, after first-use licensed SD 1.5 Q4 model installation inside app (about 1.75 GB). No cloud upload during local inference.
- **LOCAL with Internet ON**: runs the exact same native CPU model while Internet is connected. On-phone browser API via authenticated loopback localhost:8820 (opt-in trusted LAN sharing only). This mode is not a remote public Internet GPU service.
- **ONLINE**: separate hosted third-party image/video demos, provider quotas/login/rules apply.
- **HYBRID**: prefers local compatible inference; external hosted generation requires explicit user consent.
- Reference img2img from a chosen photo, Character Vault, prompt/seed presets, basic visual continuity (not exact identity lock).
- Up to 8 native images per local batch in one model session, generated **sequentially**, with Gallery export.
- Native local AI storyboard images → H.264 cinematic MP4 (still-image keyframes with pan/zoom/fades, not motion diffusion); gallery photo animations and MP4 clip editor/joiner.
- **Experimental Wan 2.1 T2V** CPU-local motion video: optional 3-part WAN Q4/UMT5/VAE weights automatically installed inside app (~4.73 GB). CPU native bridge with an H.264 encoder. 12 GB RAM compatibility/quality/temperature not guaranteed.
- Device Health / Model Storage: trusted diagnostics, safe automatic weight cleanup that leaves user-imported models, Gallery media and projects untouched.
- Cancelling and status reporting throughout native and installation paths; local-only API token and LAN permission.

## New for v2.4 — concrete runtime evidence
- **Real Wan smoke test button** in Local Video Lab: 256×256, 5 frames, 4 diffusion steps, fixed reproducible prompt/seed. A test only claims success if native Wan supplies all requested frames, Android writes MP4, and the saved Gallery video can actually be reopened as H.264 with increasing presentation timestamps. Partial native video jobs are labelled PARTIAL, never full success. Invalid MP4 is deleted and reported as failure.
- The same real MP4 validation also protects the local authenticated API's video result. This prevents a corrupt file from being reported as successful video generation.
- Distinct image proof records for **local inference while Internet ON** and **local inference fully offline**, based on actual native rendered pixels, not mere installation.
- Device Health now shows independent image/Internet statuses and saved native Wan video test results. Diagnostic clipboard copy excludes prompts and API tokens.
- CI adds regression checks that require actual video verifier usage (code-path tests are not physical-device quality benchmarks).

## Android APK
- Branch: `realframe-studio-v24`
- GitHub Actions workflow: `.github/workflows/realframe-v24-build.yml`
- Package ID: `studio.realframe.v24`
- Version: `2.4.0-samsung-build`
- Build artifact: `RealFrameStudio-v2_4-samsung-build-apk`
- APK: `RealFrameStudio-v2.4-samsung-debug.apk` plus SHA-256 checksum.
- Output from GitHub Actions is Android **debug-signed**. This is suitable for user-controlled sideload testing. It is NOT Play Store / production signed; production signing needs a long-lived signing key outside public repository.
- V2.4 installs separately from older v2.2/v2.3 versions to avoid breaking working installations or their private model data. Models belong to Android's app-private storage and thus need one initial install per separate app package. Gallery media exports remain accessible normally.

## How to verify on Samsung
1. Keep current working v2.2/v2.3 installed for safety, install v2.4 alongside it.
2. Home → GET LOCAL AI READY → licensed in-app first-run image model setup. Check 256px/4 steps real image generation with Internet ON, then disable data/Wi-Fi and run again. View two independent verdicts in Device Health.
3. Reference Image Studio: 1 reference, seed lock, 0.25 strength, batch1, optionally batch8. Verify Gallery images.
4. Automatic AI Story → MP4 and Photo Cinematic MP4: verify playable video via Gallery. These are not native motion diffusion.
5. Optional Local Video Lab → in-app one-tap Wan Q4 model setup → **TEST NATIVE WAN**. Watch RAM/thermal limits. Only a full all-frame H264-verified result is marked SUCCESS.
6. Test Online explicitly; validate Hybrid consent. Local API works with Internet available or unavailable and never automatically redirects to cloud.
7. Device Health → inspect local proof and video proof; safely clean only auto-installed weight files if needed. Existing user content remains intact.

## Limits that remain impossible to certify as 100% completed
Unrestricted cloud GPU, full high-fidelity offline long-form talking/moving human AI videos, perfect face lock over arbitrary scenes, high-throughput simultaneous heavy-model rendering and Snapdragon NPU/QNN acceleration are neither bundled nor guaranteed. Open-source licensing and hardware/model support constrain availability. Longer films can be assembled from short clips or keyframes, but the resulting length does not imply true generated long-motion continuity.

**Do not describe APK build success alone as proof of on-phone native video generation.** On-device proof buttons distinguish real results from UI/application startup. Keep both separate from general device smoke testing.
