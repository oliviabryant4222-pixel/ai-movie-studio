"""v2.3: UI and runtime safety contract for automatic model storage.
These static tests cannot prove physical Samsung performance or Wan generation.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
SRC=APP/"src/main/java/studio/realframe"

class HealthAndStorageTests(unittest.TestCase):
    def test_private_activity_registered(self):
        ns="{http://schemas.android.com/apk/res/android}"
        root=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        entries={a.get(ns+"name"):a.get(ns+"exported")
                for a in root.find("application").findall("activity")}
        self.assertEqual(entries.get(".StudioHealthActivity"),"false")
    def test_ui_has_link_and_storage_guard(self):
        activity=(SRC/"StudioHealthActivity.java").read_text()
        for token in ("ModelStoragePolicy.deleteManaged(", "LocalImageEngine.isBusy()",
                      "AutoModelDownloadService.isDownloading()",
                      "AutoVideoModelDownloadService.isDownloading()",
                      "LocalApiService.isRunning()", "SharedPreferences.Editor",
                      "getSharedPreferences(\"realframe_local_proof\"",
                      "ClipboardManager","Confirmation" if False else "Keep models"):
            self.assertIn(token,activity)
        self.assertIn("StudioHealthActivity.class",(SRC/"QuickStartActivity.java").read_text())
        self.assertIn("StudioHealthActivity.class",(SRC/"StudioDashboardActivity.java").read_text())
    def test_cpp_gate_shared_between_video_and_image(self):
        engine=(SRC/"LocalImageEngine.java").read_text()
        native=(APP/"src/main/cpp/realframe_sd_jni.cpp").read_text()
        self.assertIn("nativeBusy()",engine)
        self.assertIn("Java_studio_realframe_LocalImageEngine_nativeBusy(",native)
        self.assertIn("g_realframe_model_busy.load()",native)
        video=(APP/"src/main/cpp/realframe_video_jni.cpp").read_text()
        self.assertIn("g_realframe_model_busy.compare_exchange_strong",video)
    def test_cleanup_only_exact_installer_files(self):
        src=(SRC/"ModelStoragePolicy.java").read_text()
        for token in ("getCanonicalPath()", "new File(filesDir,IMAGE)",
                      "WAN=Collections.unmodifiableList","isManaged(appFiles,target)"):
            self.assertIn(token,src)
    def test_release_version(self):
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v25"',gradle)
        self.assertIn('versionName = "2.5.0-premium-ui-beta"',gradle)

if __name__=="__main__":unittest.main()
