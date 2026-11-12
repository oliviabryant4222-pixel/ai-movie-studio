# v1.1 release notes

- Connect AI onboarding: local image model discovery (`/sdapi/v1/sd-models`), first real image from `/sdapi/v1/txt2img`, saved video flow picker from `/flows`, first real video submission.
- Fixes: header validation prevents saving HTML/JSON as PNG/MP4 and uses actual file extension.
- Image generation panel supports direct `a1111` route with steps/CFG and all prior batch options.
- Restore/export project metadata JSON via Android document picker. Doesn't bundle media binaries or credentials.
- Mock A1111 Basic-auth image contract test (synthetic response, NOT generated AI).
- GitHub Actions Java media header test + 28 Python tests + APK build steps.

**Not yet completed**: embedding models/NPU backend; cloud GPU availability; multi-GPU parallel inference; guaranteed character identity; image/video model runtime device validation; normalized multi-codec in-phone long video output. The next physical phone test must generate actual image using Nightmare Mobile integration.
