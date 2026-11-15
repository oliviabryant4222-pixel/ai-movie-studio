"""Run on CI: verify actual local model recovery UX, native diagnostics,
known WAN low-RAM warning and durable Android package identity.
This cannot pretend the user's Samsung was physically exercised.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"android/app/src/main/java/studio/realframe"
CPP=ROOT/"android/app/src/main/cpp"

class LocalRepairContractTests(unittest.TestCase):
    def test_stable_identity_and_bumped_version(self):
        gradle=(ROOT/"android/app/build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v25"',gradle)
        self.assertIn("versionCode = 26",gradle)
        self.assertIn('versionName = "2.6.0-local-engine-repair"',gradle)
    def test_missing_image_model_cannot_leave_dead_generate_button(self):
        ui=(JAVA/"LocalImageActivity.java").read_text()
        self.assertIn("generate.setEnabled(!running.get())",ui)
        self.assertIn("ONE-TAP MODEL SETUP",ui)
        self.assertIn("checkpoint.length()<16L*1024*1024",ui)
        self.assertIn("QuickStartActivity.class",ui)
        self.assertIn('getSharedPreferences("realframe_image_jobs"',ui)
        self.assertIn("LocalImageEngine.lastNativeIssue()",ui)
    def test_missing_wan_model_has_true_and_fallback_options(self):
        ui=(JAVA/"LocalVideoActivity.java").read_text()
        self.assertIn("generate.setEnabled(!busy.get())",ui)
        self.assertIn("ONE-TAP WAN MODEL SETUP",ui)
        self.assertIn("StoryMovieComposerActivity.class",ui)
        self.assertIn("LocalAutoFilmActivity.class",ui)
        self.assertIn("info.availMem<6_000_000_000L",ui)
        self.assertIn("LocalVideoEngine.lastNativeIssue()",ui)
        self.assertIn("Wan",ui)
    def test_native_sdk_errors_from_real_cpp_loader_not_fake_success(self):
        img=(CPP/"realframe_sd_jni.cpp").read_text()
        vid=(CPP/"realframe_video_jni.cpp").read_text()
        for key in ("sd_set_log_callback(", "rf_log_callback(",
                    "rf_latest_native_issue()", "rf_begin_native_diagnostics()",
                    "Java_studio_realframe_LocalImageEngine_nativeLastIssue",
                    "Java_studio_realframe_LocalVideoEngine_nativeLastIssue"):
            self.assertIn(key,img)
        for key in ("context.flash_attn=true", "context.vae_conv_direct=true",
                    "params.vae_tiling_params.enabled=true",
                    "rf_begin_native_diagnostics()"):
            self.assertIn(key,vid)
        self.assertIn("LocalImageEngine.lastNativeIssue()", (JAVA/"StudioHealthActivity.java").read_text())
    def test_first_use_weight_urls_and_hash_stay_pinned(self):
        image=(JAVA/"AutoModelDownloadService.java").read_text()
        video=(JAVA/"AutoVideoModelDownloadService.java").read_text()
        self.assertIn("c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b",image)
        self.assertIn("ModelFileInspector.inspect(",image)
        self.assertIn("ModelFileInspector.inspect(",video)

if __name__=="__main__": unittest.main()
