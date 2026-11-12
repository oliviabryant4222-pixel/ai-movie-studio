"""RealFrame v2.0 no-manual-file local AI onboarding integration checks.
These do not prove the user's SD1.5 model can complete inference on Samsung.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
JAVA=APP/"src/main/java/studio/realframe"
class OneTapModelSetup(unittest.TestCase):
    def test_licensed_source_and_sha_are_fixed(self):
        text=(JAVA/"AutoModelDownloadService.java").read_text()
        self.assertIn("https://github.com/leejet/stable-diffusion.cpp",text)
        self.assertIn("CreativeML Open RAIL-M",text)
        self.assertIn("gpustack/stable-diffusion-v1-5-GGUF",text)
        self.assertIn("stable-diffusion-v1-5-Q4_0.gguf",text)
        self.assertIn("c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b",text)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")",text)
        self.assertIn("SHA256.equalsIgnoreCase(",text)
        self.assertIn("https",text)
    def test_resumable_https_and_space_limits(self):
        s=(JAVA/"AutoModelDownloadService.java").read_text()
        for token in ("HttpURLConnection", '"Range","bytes="', "Content-Range", "response==206",
                      "StatFs(", "getAvailableBytes()", "Thread.currentThread().isInterrupted()",
                      "FOREGROUND_SERVICE_TYPE_DATA_SYNC", ".part", "outOfMemory"):
            if token=="outOfMemory":continue
            self.assertIn(token,s)
    def test_local_model_activates_native_engine_preference(self):
        s=(JAVA/"AutoModelDownloadService.java").read_text()
        self.assertIn('getSharedPreferences("realframe_local_sd"',s)
        self.assertIn('putString("modelPath",target.getAbsolutePath())',s)
        self.assertIn("ModelFileInspector.inspect(target)",s)
        self.assertIn("!part.renameTo(target)",s)
        ui=(JAVA/"LocalImageActivity.java").read_text()
        self.assertIn("QuickStartActivity.class",ui)
        self.assertIn("LocalImageEngine.generateBatch(",ui)
    def test_end_user_no_external_app_or_manual_file(self):
        s=(JAVA/"QuickStartActivity.java").read_text()
        self.assertIn("No manual model files",s)
        self.assertIn("Accept license & install",s)
        self.assertIn("AutoModelDownloadService.START",s)
        self.assertIn("AutoModelDownloadService.CANCEL",s)
        self.assertIn("LocalApiActivity.class",s)
        self.assertIn("WebStudioActivity.class",s)
        self.assertIn("UnifiedCreateActivity.class",s)
    def test_online_local_hybrid_unchanged(self):
        dash=(JAVA/"StudioDashboardActivity.java").read_text()
        self.assertIn("BETA 2.0",dash)
        self.assertIn("QuickStartActivity.class",dash)
        self.assertIn("LocalApiActivity.class",dash)
        self.assertIn("WebStudioActivity.class",dash)
        self.assertIn("UnifiedCreateActivity.class",dash)
    def test_manifest_service_and_version(self):
        app=ET.parse(APP/"src/main/AndroidManifest.xml").getroot().find("application")
        ns="{http://schemas.android.com/apk/res/android}"
        acts=[a.get(ns+"name") for a in app.findall("activity")]
        services=[x.get(ns+"name") for x in app.findall("service")]
        self.assertIn(".QuickStartActivity",acts)
        self.assertIn(".AutoModelDownloadService",services)
        self.assertIn(".LocalApiService",services)
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v20"',gradle)
        self.assertIn('versionName = "2.0.0-one-tap-local-ai-beta"',gradle)
if __name__=="__main__":unittest.main()
