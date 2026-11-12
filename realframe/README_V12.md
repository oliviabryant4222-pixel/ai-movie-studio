# RealFrame AI Studio v1.2 — in-app image and video generation

This Android beta has a **Free AI** tab that embeds real, public Hugging Face Spaces for image and video generation. It needs **no separate Nightmare app, local API address, or token** for public demo use.

## 1-minute setup
1. Build/install the v1.2 debug APK from GitHub Actions.
2. Open **Free AI**, select **Image AI** (Z-Image Turbo) or **Video AI** (Wan 2.2).
3. Type prompt in the app, press **Copy prompt**, then paste into the model's prompt field.
4. Run the hosted generator; download the output inside the embedded browser. Media saves to Android **Downloads** where supported.
5. In RealFrame, use **Clips** to import downloaded MP4 files and assemble a longer video. Use **Characters** to keep a stable textual character recipe.
6. If the embedded browser fails to authenticate, render, or save a blob file, press **Open browser** to use the same demo in Chrome.

## No misleading promises
- Free generation is on third-party hosted servers, **not offline native AI inference**.
- Quotas, waitlists, downtime, content policies and copyright/model licenses belong to each hosting provider.
- Web demo settings may change without notice.
- **No automatic batch dispatch or character-lock is provided inside the embedded demo**. The app retains legacy job queues for configured integrations and a local MP4 clip editor.
- Offline test media is diagnostic, not AI-generated. The legacy Local backend still needs an external compatible engine; this beta does not secretly bundle one.
- Other cloud integration and any real on-device diffusion engine are distinct follow-up features.

## Privacy
Cloud prompts and uploaded photos leave your phone and are processed by the selected Hugging Face Space. Avoid uploading personal/private images without consent. Never generate non-consensual intimate images.

## APK update
v1.2 uses package ID `studio.realframe.v12`, intentionally different from older beta versions so previous installations remain intact.

## Build
Branch `realframe-studio-v12`; workflow `.github/workflows/realframe-v12-build.yml`.
APK builds with JDK17 / Gradle8.9 / Android SDK35; CI runs existing utility tests and signature verification.
