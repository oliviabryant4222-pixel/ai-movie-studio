"""RealFrame 2.4: no fake 'video worked' verdict.
Static Android source wiring tests, NOT actual 12 GB Samsung performance tests.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
JAVA=APP/"src/main/java/studio/realframe"

class FinalVideoProofTests(unittest.TestCase):
    def test_video_verifier_opens_actual_media(self):
        v=(JAVA/"VideoOutputVerifier.java").read_text()
        for token in ("MediaExtractor", "setDataSource(context,galleryUri,null)",
                      "MediaFormat.KEY_MIME", '"video/avc"', "readSampleData(",
                      "getSampleTime()", "last<=first", "extractor.release()"):
            self.assertIn(token,v)
    def test_native_video_uses_verifier_and_stores_result(self):
        ui=(JAVA/"LocalVideoActivity.java").read_text()
        for item in ('"realframe_video_proof"', "VideoOutputVerifier.verify(this,movie",
                     "resultCount==n?\"SUCCESS\":\"PARTIAL\"",
                     'recordVideoEvidence("FAILED"', "TEST NATIVE WAN",
                     "Math.min(resultCount,5)", "getContentResolver().delete(movie,null,null)"):
            self.assertIn(item,ui)
        self.assertIn("LocalVideoEngine.generate(",ui)
    def test_local_api_never_claims_corrupt_mp4_is_valid(self):
        api=(JAVA/"LocalApiService.java").read_text()
        for item in ("VideoOutputVerifier.verify(this,", "verified.valid",
                     "getContentResolver().delete(completed,null,null)"):
            self.assertIn(item,api)
    def test_health_shows_both_offline_modes_and_true_motion_proof(self):
        test=(JAVA/"LocalProofActivity.java").read_text()
        health=(JAVA/"StudioHealthActivity.java").read_text()
        for item in ('"local_while_online_verified"', '"fully_offline_verified"'):
            self.assertIn(item,test)
            self.assertIn(item,health)
        self.assertIn('"realframe_video_proof"',health)
        self.assertIn('"last_status"',health)
        self.assertIn("LocalVideoActivity.class",health)
    def test_new_version(self):
        gradle=(APP/"build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v25"',gradle)
        self.assertIn('versionName = "2.6.0-local-engine-repair"',gradle)
        dash=(JAVA/"StudioDashboardActivity.java").read_text()
        self.assertIn('label("v2.6"',dash)

if __name__=="__main__":unittest.main()
