"""Static source tests for the experimental native diffusion bridge.
These are not proof of image output on the user's specific Snapdragon phone.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
J=APP/"src/main/java/studio/realframe"
CPP=APP/"src/main/cpp"

class NativeEngineWiringTests(unittest.TestCase):
    def test_stable_cpp_is_an_actual_inference_adapter(self):
        s=(CPP/"realframe_sd_jni.cpp").read_text(encoding="utf-8")
        for token in ("new_sd_ctx(", "generate_image(", "free_sd_image_generation_result(",
                      "sd_cancel_generation(", "GetStringUTFChars", "NewIntArray"):
            self.assertIn(token,s)
        self.assertNotIn("fake_sample",s)
    def test_java_engine_routes_native(self):
        s=(J/"LocalImageEngine.java").read_text(encoding="utf-8")
        for key in ("System.loadLibrary(\"realframe_sd\")",
                    "private static native int[] nativeGenerate",
                    "nativeCancel", "isAvailable()"):
            self.assertIn(key,s)
    def test_real_device_image_workflow(self):
        s=(J/"LocalImageActivity.java").read_text(encoding="utf-8")
        for key in ("ACTION_OPEN_DOCUMENT", "model_cache",
                    "LocalImageEngine.generateBatch(", "MediaStore.Images.Media",
                    "IS_PENDING", "Bitmap.createBitmap"):
            self.assertIn(key,s)
    def test_native_sources_pinned(self):
        s=(ROOT/"tools/fetch_native_engine.sh").read_text(encoding="utf-8")
        self.assertIn('PIN="b9d7176bf88fe465576e0aafbe47ba253ffcfc2c"',s)
        cmake=(CPP/"CMakeLists.txt").read_text()
        self.assertIn("add_subdirectory(",cmake)
        self.assertIn("stable-diffusion",cmake)
    def test_manifest_and_version(self):
        root=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        names=[a.get(ns+"name") for a in root.find("application").findall("activity")]
        self.assertIn(".LocalImageActivity",names)
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertTrue('versionName = "1.6.0-local-image-experimental"' in gradle or 'versionName = "1.7.0-hybrid-batch-beta"' in gradle)
        self.assertIn("externalNativeBuild",gradle)
        self.assertIn('abiFilters.add("arm64-v8a")',gradle)
    def test_hybrid_requires_explicit_cloud_choice(self):
        s=(J/"StudioDashboardActivity.java").read_text()
        self.assertIn("chooseHybrid()",s)
        self.assertIn("LOCAL • This phone CPU",s)
        self.assertIn("ONLINE • Cloud AI",s)

if __name__=="__main__": unittest.main()
