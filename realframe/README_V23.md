# RealFrame AI Studio 2.3 — Samsung release candidate

**Build status:** Source and CI are intended to produce an installable Android arm64 debug-signed APK. Device installation by the user is encouraging, but **not a claim that every requested feature is 100% technically supported or tested**.

## Release scope (one Android studio)
- Native local image AI via stable-diffusion.cpp and first-use, in-app, licensed SD1.5 Q4 model download.
- Internet ON + local processing; Internet OFF + fully offline generation after model setup; no automatic cloud uploads.
- Online hosted image/video workflows and Hybrid with explicit cloud consent, depending on external providers' login/quota/availability.
- On-device reference img2img and basic appearance/seed continuity. Character Vault and story planner.
- 1–8 image **sequential** native batch renders, Gallery export, local edit and reference tools.
- Experimental Wan 1.3B Q4 text-to-video native bridge with optional in-app three-component installer. True motion-video generation on 12GB phone **not guaranteed**, including with weights installed.
- Short scene AI keyframe generation + cinematic 2D pan/zoom/transition MP4 composer; existing video clips can be joined for longer movies.
- Private authenticated local browser API on 127.0.0.1:8820, opt-in private LAN sharing; offline-capable browser UI.
- Native 256×256 four-step **real device render** proof screen for LOCAL with Internet ON / OFF.

## New in v2.3: device health and disk management
- **Home → Device Health & Model Storage** or **Quick Start → DEVICE HEALTH**.
- Shows device model, Android version, free RAM/storage, whether native engines load, installed auto models, last local image proof status, and local API state.
- Copies a diagnostic report to clipboard *without including prompts, API tokens or private photos*.
- Cleans interrupted downloads, or selectively uninstalls only auto-downloaded SD1.5 and/or Wan weights after explicit confirmation.
- Safeguards against deletion of Gallery media, saved characters/projects and user-imported model checkpoints via canonical-path allowlist.
- Refuses cleanup while either model installer, local API server or shared native image/Wan inference job is running.
- New C++/JNI native busy check, Android UI tests and pure Java allowlist regression tests.

## Android build and installation
- Branch: `realframe-studio-v23`.
- CI: `.github/workflows/realframe-v23-build.yml`.
- Version: `2.3.0-device-health-rc`.
- Application ID: `studio.realframe.v23` (intentionally isolated from older debug-signed beta package; installation beside v2.2, NOT an in-place upgrade).
- APK artifact: `RealFrameStudio-v2_3-release-candidate-apk`, containing `RealFrameStudio-v2.3-rc-debug.apk` and matching SHA-256 checksum.
- APK uses **debug** signing from GitHub Actions, which does not provide a reliable production upgrade key across builds. Do not describe this as store-signed production release. For stable in-place upgrades, use a persistent confidential signing keystore in CI secrets and freeze a production application ID (requires release-key provisioning).
- Models are private to each Android application ID. Android sandbox isolation means v2.3 cannot silently copy model weights, Character Vault files or saved app-local settings out of the older v2.2 package. Gallery photos/MP4s exported to the Android MediaStore remain accessible through the normal file/gallery picker. Avoid uninstalling the working v2.2 until v2.3 passes your own test.

## Samsung final-acceptance matrix
- Confirm first start, dashboard, three modes, Gallery picker and AI model installers (including interruption/resume).
- After **in-app** SD model setup, run a **real** image render with Internet ON; then optionally with both Wi-Fi and mobile data OFF. The exact Native Proof test reports success/failure and elapsed time.
- Check portrait + permitted reference photo 256x256 4 steps batch1, then 8 sequential images if memory allows.
- Run Local Story Movie 2 scenes at 1 second each, check exported MP4 plays in Gallery. **Keyframe animation is not AI video motion diffusion**.
- If WAN weights successfully install, test 5 real frames at low resolution; log success, render time, memory and possible OOM. Installing weights does not verify model inference.
- Verify Online asks consent and respects provider quota; Hybrid never auto-uploads a local/private prompt. Check localhost API and optionally authenticated private LAN.
- For safe cleanup, stop all render jobs and local server; delete only selected automatic files; confirm Gallery and manually imported models still exist.

## Not honestly 100% complete
Full locally generated long high-fidelity moving-person videos, exact fixed identity across arbitrary scenes, Qualcomm NPU/QNN speedups, truly concurrent multi-GB video jobs, or unlimited/guaranteed free online GPU resources are **not** implemented/guaranteed and may be infeasible with 12 GB phone memory or third-party hosting constraints. More UI buttons cannot make those claims true.

**Overall:** A working, source-tested Android **release candidate** for a supported feature set, NOT a verified universal 100% final professional production release. Licensing remains bound to upstream engine and weight licenses.
