"""RealFrame v1.7 safety, JNI image batching and unified UI source checks.
These tests do not claim rendered image model compatibility on Samsung.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
SRC=APP/"src/main/java/studio/realframe"

class HybridBatchWiringTests(unittest.TestCase):
    def test_jni_batch_keeps_one_context(self):
        cpp=(APP/"src/main/cpp/realframe_sd_jni.cpp").read_text()
        batch=cpp.split("Java_studio_realframe_LocalImageEngine_nativeGenerateBatch",1)[1]
        for token in ("Slot slot;", "new_sd_ctx(", "generate_image(", "free_sd_image_generation_result(",
                      "CallVoidMethod", "g_cancel_requested", "batchCount > 8", "params.seed="):
            self.assertIn(token,batch)
        self.assertEqual(batch.count("new_sd_ctx("),1)
        self.assertIn("g_busy.compare_exchange_strong",cpp)

    def test_batch_java_api_and_auto_gallery_save(self):
        java=(SRC/"LocalImageEngine.java").read_text()
        screen=(SRC/"LocalImageActivity.java").read_text()
        for token in ("BatchListener", "nativeGenerateBatch(", "generateBatch("):
            self.assertIn(token,java)
        for token in ("batchList", "LocalImageEngine.generateBatch(", "saveLocalPng(", "IS_PENDING",
                      "onFrame", "No cloud upload"):
            if token == "onFrame":
                self.assertIn("onFrame",java)
            else:
                self.assertIn(token,screen)

    def test_unified_creator_requires_cloud_consent(self):
        ui=(SRC/"UnifiedCreateActivity.java").read_text()
        policy=(SRC/"HybridRoutePolicy.java").read_text()
        for phrase in ("Cloud processing permission", "setPositiveButton(\"Continue Online\"",
                       "HybridRoutePolicy.decide(", "LocalImageActivity.class",
                       "WebStudioActivity.class", "getSharedPreferences(\"realframe_model_manager\"", "Character Vault"):
            if phrase=="Character Vault":
                self.assertIn("presets.addAll(new StudioStore(this).characters())",ui)
            else:
                self.assertIn(phrase,ui)
        self.assertIn("if (mode == Mode.LOCAL)",policy)
        self.assertIn("UNSUPPORTED_LOCAL_VIDEO",policy)

    def test_activities_manifest_and_dashboard(self):
        m=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        names=[a.get(ns+"name") for a in m.find("application").findall("activity")]
        self.assertIn(".UnifiedCreateActivity",names)
        self.assertIn(".LocalImageActivity",names)
        dashboard=(SRC/"StudioDashboardActivity.java").read_text()
        self.assertIn("UnifiedCreateActivity.class",dashboard)

    def test_app_version(self):
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertIn('versionName = "1.7.0-hybrid-batch-beta"',gradle)
        self.assertIn('applicationId = "studio.realframe.v17"',gradle)

if __name__=="__main__":
    unittest.main()
