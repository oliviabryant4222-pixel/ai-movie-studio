# RealFrame Studio v1.4 — Hybrid mode + local model manager

This is a tested-in-CI Android beta building upon v1.3.

## Working capabilities
- Online image/video generation via embedded third-party Hugging Face demos. Internet, server quotas and their moderation apply.
- Offline photo color adjustment and JPEG export, still-image-to-MP4, existing timeline clip merging.
- Character profiles, storyboards, clip queue and saved projects (legacy studio).
- NEW: **Hybrid Studio** page links online generation with device-local editing/workflows.
- NEW: **Model & Mode Manager** lets you choose *Online*, *Local* or *Hybrid*, inspect RAM/storage/ABIs and keep persistable Android Storage Access Framework (SAF) references to downloaded checkpoint file(s)/folder(s). It does not duplicate huge model files.

## NOT YET IMPLEMENTED — please read
**No native on-device diffusion inference is included.** Choosing Local mode or selecting an SDXL/SD1.5/FLUX model file cannot generate new AI imagery on its own: a licensed MNN or QNN native inference runtime, tokenizer, scheduler and model-specific loader must be integrated and Android-device-tested. Videos also require a suitable local video diffusion engine. Therefore true automatic hybrid AI job routing, 100% on-device text-to-video, truly parallel video generation and identity-locked character LoRAs are still incomplete.

No software can guarantee unlimited free hosted GPU. There is no cloud API key included.

## Realistic independent local engine path
- Study https://github.com/xororz/local-dream (supports SDXL NPU on Snapdragon 8 Gen 3+, subject to its license and model requirements).
- Study https://github.com/alibaba/MNN (open-source inference framework, plus MNN-Diffusion).
- Bring **a fully buildable and appropriately licensed** native runtime into this APK; do not copy closed/redistribution-restricted binaries.
- Ship only small app binaries; download/import GB-scale weights after explicit storage/rights checks.
- Benchmark on real Samsung device, capture thermal/RAM/speed metrics; no speed or quality promises without tests.
- For hybrid, implement a job renderer with explicit user consent for cloud uploads and fallback, not a silent transfer.

## Build
GitHub branch `realframe-studio-v14`, workflow `.github/workflows/realframe-v14-build.yml`. Builds Android 35 Java APK and uploads CI artifact. CI tests model-manager wiring and existing tools, but does not replace physical-phone tests or AI-generation tests.

## Privacy
On-device editing stays local. Model references are persisted as app preferences and SAF URIs. Online providers see uploaded media/prompts. No guarantees about data retention outside RealFrame: check provider policies. Adult-only fictional characters; do not produce intimate imagery of real people without consent.
