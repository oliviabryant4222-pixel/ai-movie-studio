"""RealFrame native smoke test is an actual on-device image inference path.

This code-path test cannot verify physical Samsung rendering or any provider
runtime. It protects against pretending an Android build is a passed AI render.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
JAVA=APP/"src/main/java/studio/realframe"

class LocalProofContractTests(unittest.TestCase):
    def test_private_activity_is_registered(self):
        manifest=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        activities={a.get(ns+"name"):a.get(ns+"exported")
                    for a in manifest.find("application").findall("activity")}
        self.assertEqual(activities.get(".LocalProofActivity"),"false")
    def test_quickstart_exposes_proof(self):
        src=(JAVA/"QuickStartActivity.java").read_text()
        self.assertIn("LocalProofActivity.class",src)
        self.assertIn("Internet ON / OFF",src)
    def test_proof_uses_actual_native_model_not_cloud(self):
        src=(JAVA/"LocalProofActivity.java").read_text()
        self.assertIn("LocalImageEngine.generate(",src)
        self.assertIn("FAST LOW-RAM TEST (128px / 1 step)",src)
        self.assertIn("startTest(256,4)",src)
        self.assertIn("pixels.length!=size*size",src)
        self.assertIn("Bitmap.createBitmap(pixels,size,size",src)
        self.assertIn("LocalInferenceProbe.classify(",src)
        self.assertIn("internetAvailable()",src)
        self.assertIn("LocalImageEngine.cancel()",src)
        self.assertNotIn("HttpURLConnection",src)
        self.assertNotIn("WebStudioActivity",src)
        self.assertNotIn("startService(",src)
        self.assertIn("LOCAL TEST NOT VERIFIED",src)
        self.assertIn('putString("last_result","FAILED")',src)
        self.assertIn("LocalImageEngine.lastNativeIssue()",src)
    def test_actions_runs_pure_java_probe(self):
        yml=(ROOT.parent/".github/workflows/realframe-v21-build.yml").read_text()
        self.assertIn("TestLocalInferenceProbe.java",yml)
        self.assertIn("LocalInferenceProbe.java",yml)

if __name__=="__main__":
    unittest.main()
