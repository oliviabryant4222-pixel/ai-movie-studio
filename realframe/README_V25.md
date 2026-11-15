# RealFrame AI Studio 2.5 — premium UI and navigation-quality build

**Target:** Native Android studio interface for Samsung: image and video, Local while Internet ON, Fully Offline after first model setup, Online providers, and Hybrid with explicit cloud consent.

## UX improvements added
- A redesigned premium dark Home interface: high-contrast cyan/violet gradient hero, two prominent **New Image / New Video** action buttons, readable feature cards, responsive bottom navigation, and clear version badge.
- Real ripple/touch feedback, Android accessibility descriptions, readable contrasting button labels, and a 480 ms anti-accidental-double-tap guard on primary buttons across the main screens: Home, Quick Start, Unified Create, native image/video editors, reference studio, story movie, offline composer, model manager, Device Health, local API, advanced project workspace, production planner, hosted online studio, and local web view.
- Navigation restores the previously selected dashboard section across Android Activity recreation; pressing Back from another Home tab returns to Home rather than closing the application.
- **Advanced project workspace** now shares a consistent modern dark theme and no longer falsely says fully offline model generation is missing.
- Image model availability is indicated on Home; "installed" does not imply a successful local render; Device Health offers physical render tests.
- Online studio offers explicit **Retry**, **Alternate Image** provider, and **Local AI** buttons when a hosted third-party Space fails. Provider/network errors are shown rather than leaving the UI blank or incorrectly claiming the page loaded. File-picker callback is cancelled correctly if Android cannot launch the picker.
- Local browser Back button uses density-correct touch targets.
- **Device Health → Check app screen and service routes** performs a real on-phone Android PackageManager inspection of all major declared screens and services (manifest registration only, not a substitute for actual UI clicks or inference).
- Source-level automated navigation contract checks: explicit activity intents and dashboard-to-advanced tabs correspond to declared application screens; all main feature pages use non-empty click handlers; hosted fallback/error controls exist.

## Current functionality carried forward
- Local stable-diffusion.cpp CPU image inference; automatic licensed on-device SD1.5 Q4 model installation (~1.75GB) once per separate package, Internet not needed after that.
- Local-while-Internet-ON inference + local API/browser; offline mode; separately hosted Online demos and Hybrid consent.
- Image batches 1–8 outputs (sequential), reference img2img, Character Vault, storyboard/scene persistence and basic continuity.
- Local AI still keyframes and cinematic photo pan/zoom/fade MP4; offline photo editor, timeline and clip joining.
- Experimental on-device Wan T2V with optional automated ~4.73GB model installer, on-device 5-frame smoke test and readback of H264 video samples; still not a guaranteed native Wan success on 12GB shared RAM.
- Device health and strict on-device safe auto-model cleanup; no automatic deletion of imported weights, photo Gallery or saved projects.

## Key reliability boundaries
- A **successful CI APK build** only proves the Android classes compiled, native shared library packaged, signature validated, and regression tests ran. It cannot certify all buttons actually respond on this user's Samsung, nor prove exact face identity, unconstrained real-motion high-fidelity AI videos, 100% GPU utilization, mobile NPU acceleration, unrestricted parallel video inference, or unlimited free cloud compute.
- External online providers may be offline, require login, quotas or change their site layouts. Retry and manually choose alternatives, never quietly upload LOCAL prompts.
- Keep CPU-heavy models sequential on a 12GB phone. Native image/video tests in Device Health are the right way to check the actual phone's results.
- Android UI code is programmatic Java. No external Flutter, Compose or third-party styling package is required.

## Build, install, verification
- Branch: `realframe-studio-v25`.
- Android package: `studio.realframe.v25`, version `2.5.0-premium-ui-beta`. Deliberately installs **alongside older debug-signed builds**, not an in-place upgrade. The older debug-signing key is not available for a safe app update.
- Workflow: `.github/workflows/realframe-v25-build.yml`, artifact `RealFrameStudio-v2_5-premium-ui-apk` contains a debug-signed APK and SHA256.
- On Samsung, tap each primary action and the six bottom tabs; check Android Back behaviour, Gallery photo pickers, start/pause installers, actual Local image and WAN demo, Edit screen, Video timeline, online external status and link selection, and Device Health route audit.
- For a long-lived signed production release under one stable application ID, generate a **secret persistent release signing key**, provision it privately to CI, and run full Android instrumentation tests on real devices. Production release cannot ethically be called done without these.

**Current label: v2.5 Premium UI Beta build candidate.** Verified feature surface is not a guarantee of all original high-end hardware/model requirements.
