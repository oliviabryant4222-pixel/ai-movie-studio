# RealFrame Studio — source architecture v0.3

```
                  Android Java UI
        / Generate / Characters / Storyboard /
                  Jobs / Clips
                         |
                StudioStore (AtomicFile)
                         |
                RenderService (FGS)
               single threaded queue
              dependency-aware ready job
                         |
                     Renderer
                 /               \
     Local Nightmare API    Optional Gradio Space
       Snapdragon image       quota-limited hosted
       rendering backend       image/video backend
                  \               /
                  MediaStore outputs
                         |
               Clip Timeline & Joiner
            MediaExtractor / MediaMuxer
                         |
                    MP4 Gallery
```

## Current status

- Character Vault: saved name, adult prompt, outfit and persistent reference URI. **No trained face embedding or strict visual identity guarantee**.
- Images: local Nightmare `txt2img` or configurable Gradio Space endpoint.
- Video: configurable Gradio Space endpoint, or local if an actually supported device/model video flow exists. Upstream presently lists Snapdragon 8 Elite+ for local video, so 8 Gen 3 needs cloud/import to meet user's video goal.
- Queue: persistent JSON records; service launches on user request, runs jobs serially while allowed by Android. A queued scene's `dependsOn` ID must be in `done` state before execution. Bad dependencies block children; interrupted jobs are never silently rerun.
- Long-form: UI repeats a list of shot prompts into up to 1,800 planned clips per batch; scene index + project id attached to each job; video output added to Clips gallery timeline. Clip-to-clip reference via preceding MP4 final frame when API supports an image input. Video lengths are controlled by provider's model, not by the storyboard estimate.
- Rendering: local and cloud adapters do not perform true multi-GPU parallel generation; queue is serial. A future multi-worker bridge can add parallel jobs, but must have genuine compute capacity and quotas.
- Clip joiner: Android remux (matching codec, geometry/audio) and Python FFmpeg normalizing stitcher for mixed MP4s. Full video editor with transitions, real audio timeline, trim/scaling in Android remains future work.
- Security: local API exposed only on trusted LAN; cloud Space must be `https://*.hf.space`; token stored AndroidKeystore AES-GCM and only sent to selected Space / HuggingFace, not any arbitrary response host. Generated images/videos stored in Gallery, intermediate last frames in private app cache.
- Limits: Android 15+ 6hr/24hr budget for `dataSync` foreground services; ZeroGPU free account 5min/day as of Nov 2026. Project does not provision free dedicated GPUs.

## Build/test boundaries

- Python storyboard and FFmpeg stitching tests run in CI-independent local environment.
- Native Android source **not compiled** here; Android SDK/Gradle installers were unreachable. GitHub Actions `android-debug.yml` offers build path on a user's repo. This code is a development prototype; compile failures and hardware API differences still need device validation.

## Next worthwhile milestones

1. Compile and install v0.3 on the target device; fix any build/runtime defects first.
2. Test actual Nightmare image API and one real public Gradio image/video endpoint; keep provider argument presets separately without unsafe unlimited-free promises.
3. Improve queue recovery with provider-specific `event_id` resume, timeout/cancel handling and progress polling.
4. Add robust media normalize/trim/transition on Android using Media3 Transformer, persistent project scene metadata and cross-scene identity checks.
5. Add configurable renderer worker registry, with real parallel scheduling only when independent GPU workers are available.


## v0.5 implementation notes
- Local API discovery: `/info`, `/models`, `/flows`, `/flows/{id}` and optional per-node `params` to control video workflows. Actual model compatibility is decided by the installed Nightmare engine and its model license.
- Corrected the outdated assumption that all Nightmare local video requires Snapdragon 8 Elite: upstream currently lists Wan 2.2 14B and MiniMax H3 for 8 Gen 3 + 12 GB, subject to limits and regional licensing.
- Separate Storyboard export uses `projectId`, sorts by `sceneIndex`, and only becomes enabled when all scenes have results.
- Cloud API validation uses `/gradio_api/openapi.json`, skips image upload for text-only templates, and finds the expected media extension/mime when Spaces return mixed thumbnails and clips.
- Provider connector diagnostics: `tools/verify_backends.py`; deterministic mock tests without GPU consumption.
- Still not done: production Android build+device test, direct on-device AI inference embed, model-weight distribution, universal video normalization, actual multi-GPU parallelism, perfect identity validation and live Space integration tests.

## v0.5 implementation notes
- `StudioStore.putJobs(batch)` makes each submitted batch one atomic append transaction; prevents partial 250-job or 1,800-scene plans.
- `StudioStore.retryProject(projectId)` resets failed/cancelled/interrupted/blocked scenes and preserves dependencies. A timed-out remote job may still render on the remote service, so retry can duplicate output.
- Native Generate screen supports either single prompt variations or one prompt per line × variations (250 per submitted batch).
- Jobs and imported-clip list use 30-entry pagination to prevent rendering thousands of Android views.
- `tools/audit_clips.py` audits file completeness and codecs/FPS/dimensions before Termux/desktop FFmpeg stitching. It does not check facial identity consistency.
- On-phone Java/Android compilation is unverified. Android SDK and Gradle are not available here, and physical phone testing has not occurred.
