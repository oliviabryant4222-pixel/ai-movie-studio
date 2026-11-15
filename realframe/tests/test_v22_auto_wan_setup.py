"""RealFrame 2.2 optional Wan Q4 pack installer wiring and privacy contract.
Only source tests: physical Samsung Wan inference is NOT verified by CI.
"""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
JAVA=APP/"src/main/java/studio/realframe"

class OneTapWanTests(unittest.TestCase):
    def test_apache_licensed_sources_hashes(self):
        s=(JAVA/"AutoVideoModelDownloadService.java").read_text()
        for expected in (
            "off-grid-ai/Wan2.1-T2V-1.3B-GGUF",
            "city96/umt5-xxl-encoder-gguf",
            "Comfy-Org/Wan-2.1",
            "Wan2.1-T2V-1.3B-Q4_K_S.gguf",
            "umt5-xxl-encoder-Q4_K_M.gguf",
            "wan_2.1_vae.safetensors",
            "65181afff758fba25cf311399ccb0638a746f8e0e6533e07a84a5a23e0c12318",
            "17cf97a5bbbc60a646d6105b832b6f657ce904a8a1ad970e4b59df0c67584a40",
            "2fc39d31359a4b0a64f55876d8ff7fa8d780956ae2cb13463b0223e15148976b",
            "MessageDigest.getInstance(\"SHA-256\")",
            "Apache-2.0",
        ):
            self.assertIn(expected,s)

    def test_safe_resumable_installer(self):
        s=(JAVA/"AutoVideoModelDownloadService.java").read_text()
        for needle in ("getSharedPreferences(PREFS,MODE_PRIVATE)",
                       '"Range","bytes="+existing+"-"',
                       "Content-Range","status==206",
                       "HttpURLConnection","getAvailableBytes",
                       "renameTo(to)","ModelFileInspector.inspect(to)",
                       "m.sha.equalsIgnoreCase(sha256(partial))",
                       "getFilesDir()", "FOREGROUND_SERVICE_TYPE_DATA_SYNC",
                       "cancel.get()", "stopForeground(", "CANCEL"):
            self.assertIn(needle,s)

    def test_video_ui_warns_not_verified(self):
        s=(JAVA/"LocalVideoActivity.java").read_text()
        for needle in ("AutoVideoModelDownloadService.START",
                       "AutoVideoModelDownloadService.CANCEL",
                       "Accept licenses & install",
                       "SHA-256",
                       "12 GB",
                       "isWifi()",
                       "LocalVideoEngine.generate("):
            self.assertIn(needle,s)

    def test_manifest_service_not_exported(self):
        ns="{http://schemas.android.com/apk/res/android}"
        app=ET.parse(APP/"src/main/AndroidManifest.xml").getroot().find("application")
        svcs={a.get(ns+"name"):a for a in app.findall("service")}
        svc=svcs.get(".AutoVideoModelDownloadService")
        self.assertIsNotNone(svc)
        self.assertEqual(svc.get(ns+"exported"),"false")
        self.assertEqual(svc.get(ns+"foregroundServiceType"),"dataSync")

    def test_new_version_identifiers(self):
        s=(APP/"build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v22"',s)
        self.assertIn('versionName = "2.2.0-auto-wan-beta"',s)
        s=(JAVA/"StudioDashboardActivity.java").read_text()
        self.assertIn("BETA 2.2",s)

if __name__=="__main__":unittest.main()
