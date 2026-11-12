# RealFrame AI Studio v1.5 — production planner

## What's genuinely implemented
- Runs as **one Android APK** with native dark dashboard, Online, Offline and Hybrid workspaces.
- **Online image/video** uses hosted Hugging Face Space pages. Each provider controls availability, credits and content restrictions.
- **Offline tools** include a photo color lab, still-photo-to-MP4 encoder, clip concatenation for matching formats, Character Vault, and project queue.
- **New offline long-film production planner**: choose an existing fictional-adult character preset; set identity, setting, seed, target duration (up to 3600s) and clip length (2–10s), generate a deterministic scene prompt pack with dependencies, copy individual/all prompts, persist plan locally, and export JSON through Android's document picker.
- 5 second clips × 24 make a 2 minute film if the provider outputs those clips; this tool does not synthesize video by itself.
- **Model Manager** stores a persistable file or folder URI and online/local/hybrid preference. It does NOT load or execute diffusion models.

## Incomplete (do not market as ready)
- **Fully offline AI text-to-image and image-to-video are not working** in this APK. A compatible native Qualcomm QNN or MNN backend and multi-gigabyte model weights are required.
- Automatic clips-to-video generation is not automatic: users still generate/download each AI clip with a hosted demo or configured engine, then import clips for assembling.
- Automatic reference-image-conditioning, face-identity embedding, LoRA and per-clip camera/motion continuity are not integrated.
- True parallel heavy GPU workers are not bundled and a free hosted GPU cannot be guaranteed unlimited.
- A portable JSON scene plan is an editing manifest; it is not a playable MP4.

## Authentic local engine feasibility
The open-source Local Dream repository https://github.com/xororz/local-dream offers Snapdragon 8 Gen 3 SDXL image generation, but is CC BY-NC 4.0 and uses Qualcomm QNN native libraries. Integrating its model runtime into our APK requires source and native toolchain compatibility work, proper licensing/attribution, and physical device verification. Don't claim integration without compiling and running it.

For local video, there is no comparable lightweight universal model guaranteed on a 12GB phone; suitable quantized models and run profiles must be benchmarked. Nightmare Mobile is an existing alternative but the user explicitly wants a standalone APK.

## Use
1. Install v1.5 APK.
2. Open **Hybrid → Long-video production** to create/edit/export storyboard JSON. Use Character Vault to save identity.
3. Tap **Open AI video generator**, use Copy Current scene and paste prompt on provider, download MP4 outputs.
4. Open **Editor → Clips** to import compatible videos, reorder and join.
5. Offline **Photo Lab** and **Still Image → MP4** work without external APIs.

## Build
GitHub branch `realframe-studio-v15`, workflow `.github/workflows/realframe-v15-build.yml`.
GitHub CI validates Python tests, Java header tests, Android compile, APK signature.
Physical Samsung smoke test still necessary.

## User privacy
Model-selection links stored locally, hosted AI prompts and uploaded photos leave the phone. Only share personal photos with consent. Keep fictional adult characters 18+.
