# RealFrame v2.6 — local inference repair + fixed Android app identity

## What was actually wrong
Earlier debug builds changed Android package name with every version (`studio.realframe.v22`, `.v23`, `.v24`, `.v25`). **Android treats those as separate apps**. Their large downloaded SD and Wan model weights are in each version's private sandbox, so a new app usually has no image/video model installed even when an older version did. The Generate button was disabled when the checkpoint was missing, giving no useful action. An APK compiling successfully was incorrectly interpreted as proof that models rendered on the target phone. The old v2.5 APK was CI debug signed.

This repair freezes `applicationId = "studio.realframe.v25"` and increments Android versionCode to 26, with actual rendering and errors made observable. A long-lived private signing certificate is required for subsequent same-package updates.

**Important certificate migration limitation:** the previously installed v2.5 app's Android debug signing certificate/private key was generated on a throwaway CI runner. It is NOT available for creating a signature-compatible update. Consequently **the first move from the old v2.5 APK to privately signed v2.6 cannot be an in-place installation**. Android correctly rejects different signer certificates. Export project/character data from the old app if available, then remove that old package once before installing the new permanent-signing version. That first migration clears old private model files, so one-time in-app downloads may be required again. **All later builds must reuse the same private release keystore and exact applicationId** to support normal tap-to-update. Never commit a signing keystore or its password to this public GitHub repo.

## Repair details
- Native `stable-diffusion.cpp` C++ errors/warnings are captured, with private model paths redacted, then surfaced in Android Device Health and local generation error messages.
- Local Image Generate is **never silently disabled due to missing weights**. Tapping it explains the missing native model and opens the licensed one-tap setup; it can also offer a separate, explicitly selected hosted service. It never silently falls back to online processing.
- Local WAN Video Generate similarly offers **one-tap WAN Q4 model installation** and an explicitly named **cinematic photo/story movie** fallback. Cinematic MP4 is not passed off as generative Wan motion.
- Native Wan attempts enable flash attention, tiled/direct VAE decode settings supported by the pinned upstream engine. These are attempts to reduce RAM pressure, NOT a claim that 4.73GB of weights fits reliably on a 12GB shared-memory Android phone.
- A free-RAM warning explains when the OS may kill the app and offers a lower-memory local story-to-MP4 alternative.
- Real image and Wan errors are saved in Device Health, with copyable diagnostics. Failed media is not counted as a success.
- Existing local image model URL and SHA-256 stay pinned to the verified HF SD1.5 Q4 file; Wan weight URLs and integrity checks remain unchanged.
- On first use **download license-protected weights inside the app**: SD1.5 Q4 about 1.75GB for images, optional Wan T2V 1.3B + Q4 T5 + VAE about 4.73GB for experimental real video.
- v2.6 native engine still CPU-only: no automatic Qualcomm NPU and no guaranteed unlimited free hosted AI, true photorealistic long motion or perfect character lock.

## Install / troubleshoot
1. On the previous v2.5 app open Advanced Workspace → Settings and export any available project/character backup before deleting the old app. Files exported to Gallery remain accessible.
2. v2.6 must first use a new permanent signing certificate; uninstall the legacy *same-package* v2.5 debug build once (Android signature mismatch). This first migration cannot preserve app-private models automatically.
3. Install the privately signed v2.6 APK; its applicationId will be frozen for future updates. Prefer a persistent signer backed up securely outside public GitHub.
4. Home → GET LOCAL AI READY → accept the licensed one-tap SD model setup. Ensure approx 2.5GB free storage. The image engine loads only after models have been installed to this version's private storage.
5. Start Local AI Image Studio and tap Generate. The button now gives an actionable setup prompt if weights are missing. For actual test use 256×256, 4 steps, 1 output. Leave the app open while CPU inference runs.
6. Device Health → real image proof, first with Internet ON, then optionally OFF. If it fails, use **Copy diagnostics** and inspect the exact native error. Do not call the app verified on Samsung from CI build alone.
7. Optional WAN: 4.73GB download, additional free RAM; try the 5-frame smoke test. If it fails or exceeds memory limits, use the clearly labeled local still-image movie composer, or choose online hosted video with explicit consent.
8. Future versionCode 27+ signed with the *identical* v2.6 signing key and unchanged applicationId will install over v2.6, keeping its weights/settings. Always test via on-device PackageManager before claiming upgrade reliability.

## Source/CI verification boundaries
Android CI checks actual ARM64 JNI compilation, APK signing, code-level tests, model URLs and safe model setup UI. An extra Linux CPU smoke workflow can attempt a real 1.75GB GGUF image-generation render, but does **not** validate Android phone memory/thermal/GPU performance. Users' Samsung phones cannot be remotely operated or viewed from this repository.

Status: RealFrame v2.6 repair candidate. User-reported Android results still required to diagnose hardware- and model-specific runtime failures. Never label it 100% tested on the physical device without those results.
