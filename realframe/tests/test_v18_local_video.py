"""v1.8 source wiring tests for experimental on-device video inference.
Compiling JNI/Android and running the tests does not benchmark a Wan model
on Snapdragon 8 Gen 3, nor guarantee it fits a 12GB mobile phone.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
CPP=APP/"src/main/cpp"
JAVA=APP/"src/main/java/studio/realframe"

class OfflineVideoPipelineTests(unittest.TestCase):
    def test_native_real_video_generation_and_frame_stream(self):
        s=(CPP/"realframe_video_jni.cpp").read_text()
        for token in ("new_sd_ctx(", "generate_video(", "free_sd_video_generation_result(",
                      "sd_cancel_generation(", "CallVoidMethod", "diffusion_model_path",
                      "t5xxl_path", "vae_path", "g_realframe_model_busy"):
            self.assertIn(token,s)
        self.assertNotIn("mock_frames",s)
    def test_codec_real_h264_media_store(self):
        s=(JAVA/"LocalVideoEncoder.java").read_text()
        for token in ("MediaCodec.createEncoderByType(\"video/avc\")",
                      "MediaMuxer(", "COLOR_FormatYUV420Flexible", "YUV_420_888",
                      "writeSampleData(", "BUFFER_FLAG_END_OF_STREAM",
                      "MediaStore.Video.Media.IS_PENDING", "Movies/RealFrameStudio"):
            self.assertIn(token,s)
    def test_three_component_import_and_inference(self):
        s=(JAVA/"LocalVideoActivity.java").read_text()
        for token in ("PICK_DIFFUSION", "PICK_T5", "PICK_VAE",
                      "ACTION_OPEN_DOCUMENT", "ModelFileInspector.inspect(",
                      "LocalVideoEngine.generate(", "LocalVideoEncoder(", "getAvailableBytes"):
            self.assertIn(token,s)
        self.assertIn("Source guidance: stable-diffusion.cpp",s)
    def test_joint_native_android_target(self):
        s=(CPP/"CMakeLists.txt").read_text()
        self.assertIn("realframe_video_jni.cpp",s)
        self.assertIn("realframe_sd_jni.cpp",s)
        h=(JAVA/"LocalVideoEngine.java").read_text()
        self.assertIn("System.loadLibrary(\"realframe_sd\")",h)
        self.assertIn("nativeGenerate(",h)
    def test_hybrid_video_and_online_still_accessible(self):
        s=(JAVA/"HybridRoutePolicy.java").read_text()
        self.assertIn("LOCAL_VIDEO",s)
        self.assertIn("videoEngineAndModelsInstalled",s)
        self.assertIn("Mode.ONLINE",s)
        u=(JAVA/"UnifiedCreateActivity.java").read_text()
        self.assertIn("case LOCAL_VIDEO",u)
        self.assertIn("confirmCloud(completed,video)",u)
        self.assertIn("LocalVideoActivity.class",u)
        d=(JAVA/"StudioDashboardActivity.java").read_text()
        self.assertIn("LocalVideoActivity.class",d)
        self.assertIn("WebStudioActivity.class",d)
    def test_manifest_version(self):
        doc=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        names=[a.get(ns+"name") for a in doc.find("application").findall("activity")]
        self.assertIn(".LocalVideoActivity",names)
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertTrue("studio.realframe.v18" in gradle or "studio.realframe.v19" in gradle or "studio.realframe.v20" in gradle or 'applicationId = "studio.realframe.v21"' in gradle)
        self.assertTrue('versionName = "1.8.0-local-video-experimental"' in gradle or 'versionName = "1.9.0-local-online-hybrid-beta"' in gradle or 'versionName = "2.0.0-one-tap-local-ai-beta"' in gradle or 'versionName = "2.1.0-reference-movie-beta"' in gradle)

if __name__=="__main__":
    unittest.main()
