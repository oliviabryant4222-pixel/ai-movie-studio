# RealFrame AI Studio v1.9 — LOCAL *while online*, LOCAL offline, CLOUD online, HYBRID

## What the user asked for
**One Android APK that can run the phone's own AI models without Internet, keep running those SAME local models while the phone is connected to the Internet, and ALSO offer true hosted online AI and a hybrid choice.** No separate Nightmare Mobile installation is needed. v1.9 exposes these as distinct workflows.

| User action | Where actual AI computation happens | Internet required? | Cloud processing? |
|---|---|---|---|
| Local Image → Generate | Your phone's CPU / imported SD-compatible checkpoint | No | No |
| Local Video → Generate | Your phone's CPU / imported compatible Wan T2V + T5 + VAE (experimental) | No | No |
| **Local API → Start on phone** | Your phone's CPU, via in-app local browser or `127.0.0.1:8820` | **No. Works even with Internet turned on.** | No |
| **Local API → Share on trusted Wi-Fi** | Your phone's CPU; another LAN client submits a prompt | Same private LAN, not global Internet | No |
| Online Image/Video | Third-party hosted Hugging Face demo server | Yes | Yes; terms and quota apply |
| Hybrid → Local/Online | Prefer available local model, or ask before cloud | Only for cloud choice | Explicitly consented |

## NEW: Run locally from an IN-APP browser
1. Open **Home → On-device Local AI Server**.
2. Tap **Start LOCAL on this phone (internet on/off)**. A foreground notification shows the server is active.
3. Tap **Open local browser studio IN THIS APP**. The app automatically fills a securely randomized local API token; you do **not** need to copy or type an address or token to use this interface.
4. Select Image or Video, enter a prompt, dimensions, seed, and step count. Tap Generate locally.
5. Your job returns immediately with an ID. Track status until a PNG/MP4 download appears. Generated assets are also saved in Android Gallery.
6. You can turn Wi-Fi/mobile data **OFF** or keep Internet **ON**; on-phone model execution is the same.
7. Tap **Stop local API server** when finished. Foreground server uses battery even when idle.

Note: you must import licensed compatible model weights in the existing Local Image/Video Lab before any native inference is possible. The APK bundles a CPU diffusion runtime, not multiple gigabytes of model weights.

### Optional Wi-Fi sharing (not public Internet hosting)
1. Connect to your own private Wi-Fi network.
2. In Local API settings, tap **Share LOCAL AI on trusted Wi-Fi** and explicitly approve.
3. The app displays an address like `http://192.168.x.x:8820/`; copy the actual address shown, then open it on another device connected to the **same** Wi-Fi.
4. Copy the access token from RealFrame's native Local API settings and paste it in the browser. Or call the HTTP API with `X-RealFrame-Token`.
5. **Important:** LAN HTTP traffic is unencrypted. Do **not** expose it to public Internet via port-forwarding or use untrusted Wi-Fi. It's a private local-network service, not a secure remote cloud server.

### API reference
The device-local server uses port 8820. All `/v1/*` endpoints require the token header. The browser homepage `GET /` is intentionally public but contains no user/model data and requires a token for jobs.

- `GET /v1/status` — image/video native library and model-import availability, running mode, API busy status. Does not claim that inference has been benchmarked.
- `POST /v1/images/generations` — JSON example `{"prompt":"portrait of a fictional adult","width":256,"height":256,"steps":4,"n":1,"seed":13901}`. Responds with HTTP 202 and a `job id`. Only runs native CPU inference from the locally imported checkpoint; no cloud fallback.
- `POST /v1/videos/generations` — same body with `"frames":5`; requires 3 compatible Wan T2V model components. Experimental, may exhaust 12GB RAM. No cloud fallback.
- `GET /v1/jobs/<id>` — returns job status, error and `download_paths`.
- `GET /v1/jobs/<id>/media/0` — authenticated download of the generated PNG/MP4.
- `POST /v1/jobs/<id>/cancel` — requests cancellation of a running native model generation.

Security defaults: random 32-byte API token; token required even from localhost for all API routes; no CORS support; short request read timeouts; request path/header/body size limits; one API inference job at a time; default bind **127.0.0.1**; Wi-Fi bind **0.0.0.0** only after explicit user approval; no automatic cloud submission. Server stops when Android kills the foreground service or the user stops it. Jobs only persist in memory, but output media saved to Gallery remains.

## Retained features
- Online hosted image generation via Tongyi-MAI Z-Image Turbo; online Wan T2V + image-to-video hosted demos. External server uptime, model permissions and free GPU quotas are outside our control.
- Unified Creator for Image/Video, Local/Online/Hybrid, prompts, Character Vault, seeds, explicit cloud-data consent.
- Native stable-diffusion.cpp CPU inference: local text-to-image, 1–4 sequential images per batch, PNG Gallery export.
- Experimental native Wan2.1 T2V CPU bridge via `generate_video()`, Android H.264 MP4 Gallery encoder, three manually imported components.
- Offline Photo Lab, still→MP4, compatible MP4 clip assembly, Project/Storyboard, Character Vault, long-video prompt/continuity planning.

## NOT 100% verified/complete
1. **Real offline image and especially Wan T2V video generation have not been validated on the user's Snapdragon 8 Gen 3 / 12 GB Samsung device.** Successful CI compilation tests code wiring, not performance or compatibility with any specific model. Wan T2V may need more RAM than available or be impractically slow on CPU.
2. Snapdragon QNN/NPU acceleration, model download automation, native local image-to-video conditioning, face embedding/LoRA identity-lock and full automatic long-film generation are not finished.
3. Free online GPU access cannot be unlimited; hosted demos may require login, fail or impose content restrictions.
4. Wi-Fi LAN is not secure public Internet hosting. HTTPS, private VPN access and hardened authentication would be needed for Internet-wide sharing.
5. Native AI generation on the phone requires imported compatible model weights, adequate RAM and storage. Model compatibility cannot be guaranteed from a file extension/header alone.

## Build and test
- Branch: `realframe-studio-v19`.
- Workflow: `.github/workflows/realframe-v19-build.yml`.
- Android APK package: `studio.realframe.v19` — separate from older debug betas, allowing side-by-side installation. Model files need to be imported again into the new version.
- Android API35, NDK 27.2, CMake3.22.1, JDK17, arm64 CPU backend.
- Automated CI: Python source/integration tests, Java routing/security/media signature/checkpoint tests, Gradle NDK+APK compilation and APK signature validation.
- On-Samsung testing still required for local HTTP binding, Wi-Fi sharing, native image/video model loading, battery and thermal behavior.
