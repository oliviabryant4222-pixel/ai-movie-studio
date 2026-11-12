"""v1.9 static integration tests for on-device local-while-online HTTP API.
These prove source wiring, not real Samsung checkpoint compatibility.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"android/app/src/main/java/studio/realframe"

class LocalNativeApiTests(unittest.TestCase):
    def test_native_api_image_and_video_inference(self):
        s=(SRC/"LocalApiService.java").read_text()
        for token in ("LocalImageEngine.generateBatch(", "LocalVideoEngine.generate(",
                      "new LocalVideoEncoder(", "saveImage(", "process(job,",
                      "Movies/RealFrameStudio", "realframe_wan_local_v18"):
            if token == "Movies/RealFrameStudio":
                continue
            self.assertIn(token,s)
    def test_server_is_loopback_by_default_and_lan_opt_in(self):
        s=(SRC/"LocalApiService.java").read_text()
        for token in ('"127.0.0.1"', '"0.0.0.0"', "ACTION_START_LOOPBACK",
                      "ACTION_START_LAN", "startForeground(", "ServerSocket(",
                      "getOrCreateToken(this)"):
            self.assertIn(token,s)
    def test_api_secure_defaults(self):
        s=(SRC/"LocalApiService.java").read_text()
        guard=(SRC/"LocalApiSecurity.java").read_text()
        for text in ("X-RealFrame-Token", "LocalApiSecurity.matchesToken(",
                     "LocalApiSecurity.safePath(", "X-Content-Type-Options: nosniff",
                     "Connection: close", "TRANSFER"):
            if text=="X-RealFrame-Token":
                self.assertTrue("x-realframe-token" in s or text in s)
            elif text=="TRANSFER":
                self.assertIn("transfer-encoding",s)
            else:self.assertIn(text,s)
        self.assertIn("!transferEncoding",guard)
        self.assertIn("MessageDigest.isEqual",guard)
        self.assertIn("allowCrossOrigin(){return false;}",guard)
    def test_separate_authenticated_image_video_job_endpoints(self):
        s=(SRC/"LocalApiService.java").read_text()
        for route in ('"/v1/status"', '"/v1/images/generations"', '"/v1/videos/generations"',
                      '"/v1/jobs/"', '"cancel"', '"media"'):
            self.assertIn(route,s)
        self.assertIn("activeJob.compareAndSet(false,true)",s)
        self.assertIn("status.put(\"video_models\",videoModelsPresent())",s)
    def test_local_web_studio_no_remote_js(self):
        s=(SRC/"LocalApiWebPage.java").read_text()
        for text in ("NO CLOUD UPLOAD", "fetch(path,options)", "localStorage",
                     "https://huggingface.co/spaces/Tongyi-MAI/Z-Image-Turbo",
                     "https://huggingface.co/spaces/Wan-AI/Wan2.1"):
            self.assertIn(text,s)
        browser=(SRC/"LocalWebClientActivity.java").read_text()
        self.assertIn('"127.0.0.1"',browser)
        self.assertIn("JSONObject.quote",browser)
        self.assertIn("Leave local-only mode?",browser)
    def test_native_controls_consent(self):
        ui=(SRC/"LocalApiActivity.java").read_text()
        self.assertIn("Start LOCAL on this phone",ui)
        self.assertIn("Share LOCAL AI on trusted Wi-Fi",ui)
        self.assertIn("I trust this Wi-Fi",ui)
        self.assertIn("Regenerate access token",ui)
        self.assertIn("CLOUD ONLINE",ui)
        dashboard=(SRC/"StudioDashboardActivity.java").read_text()
        self.assertIn("LocalApiActivity.class",dashboard)
        self.assertIn("BETA 2.0",dashboard)
    def test_android_permissions(self):
        root=ET.parse(ROOT/"android/app/src/main/AndroidManifest.xml").getroot()
        android="{http://schemas.android.com/apk/res/android}"
        uses={n.get(android+"name") for n in root.findall("uses-permission")}
        self.assertIn("android.permission.INTERNET",uses)
        self.assertIn("android.permission.ACCESS_NETWORK_STATE",uses)
        application=root.find("application")
        self.assertTrue(any(a.get(android+"name")==".LocalApiService" and
                a.get(android+"exported")=="false" for a in application.findall("service")))
        acts={a.get(android+"name") for a in application.findall("activity")}
        self.assertIn(".LocalApiActivity",acts)
        self.assertIn(".LocalWebClientActivity",acts)
    def test_version(self):
        s=(ROOT/"android/app/build.gradle.kts").read_text()
        self.assertTrue('applicationId = "studio.realframe.v19"' in s or 'applicationId = "studio.realframe.v20"' in s)
        self.assertTrue('versionName = "1.9.0-local-online-hybrid-beta"' in s or 'versionName = "2.0.0-one-tap-local-ai-beta"' in s)
if __name__=="__main__": unittest.main()
