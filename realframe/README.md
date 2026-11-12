# RealFrame AI Studio v1.0 TEST — Android app source

**What this build actually does:** a native Android controller with Image/Video job forms, Character Vault, saved references, local render queue, 1–250 items per submitted batch, storyboards, dependency chaining, import/reorder videos, and MP4 merging for compatible source clips. Includes an **offline diagnostic** that creates a clearly labeled TEST image and TEST MP4 to check the queue/gallery/device playback — **these are not AI generations**.

**Real AI generation:** requires a separate, installed **Nightmare Mobile** model/API (which supplies local inference) or a configured Hugging Face Gradio Space whose API provides free capacity at the time of use. The APK **does not ship model weights or free GPU compute**. Hosted Space quotas and moderation cannot be bypassed, and some videos aren't supported on Snapdragon 8 Gen 3 (Wan 2.2 is an experimental/alpha option on some 8 Gen 3 builds).

**Build status:** Java/Python utilities have been checked; only GitHub Actions or Android Studio can confirm Android compilation. Until the workflow succeeds, do not call this a tested APK. User device tests remain necessary.

## First use on mobile

1. Open RealFrame Studio. On Home press **Generate offline TEST image** then **Generate offline TEST video**. Check Jobs → Open result. Both are synthetic diagnostics and require **no account/model/internet**.
2. Under Clips, import 2 matching MP4 clips or use 2 demo clips and press **Merge clips → MP4**. The merged file appears in Movies/RealFrameStudio.
3. For real phone AI, install Nightmare Mobile from upstream, download a compatible **image** model, enable Settings → Advanced → API → Listen on local network, and copy the displayed URL/token to RealFrame Settings. Use Test local API before Generating. For **video**, check the actual model support in Nightmare; most need 8 Elite, Wan 2.2 may be alpha on 8 Gen 3.
4. For optional cloud AI, visit a Gradio Space, inspect its **Use via API** schema, and enter the space `https://owner-name.hf.space`, endpoint, and JSON argument array. Free Spaces can be down, quota-limited, or moderated. The app does not supply a working fixed Space.
5. Choose an image/video flow and generate one real item, then try a batch and a storyboard. Keep app foreground for long jobs.

## Limitations
- Debug/test build, not a finished Play Store release; untested on a real Galaxy handset.
- No prepackaged AI weights, no unlimited free cloud GPU, and no guaranteed unrestricted hosting.
- Reference prompts/previous frames **help** identity consistency but do not guarantee it.
- Local rendering serial; cloud parallel GPUs/worker scheduling not implemented.
- Native MP4 join only for clips with matching audio/video codecs and geometry; FFmpeg normalization helper in `tools/stitch.py` for PC/Termux.
- On Android 15+ foreground-service `dataSync` may be time limited; interrupted jobs require manual retry.

## Source dependencies
No third-party Android dependencies beyond Android Gradle Plugin; the app uses native Android Java APIs. CI on GitHub installs Android SDK 35 and JDK 17.

## Privacy
Tokens in Android Keystore, no analytics, no server run by project. Cloud request content leaves your phone when you deliberately select Cloud mode. Local API should be LAN-only and token protected. Use fictional adults and respect model licenses and applicable laws.

## Build
See `BUILD_APK.md` for Android Studio or GitHub Actions. Download the workflow's debug APK artifact, unzip, and install `app-debug.apk` on your own phone.
