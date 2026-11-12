"""RealFrame v1.4: hybrid routing & safe local model source registry.
Tests validate wiring; actual Android model inference is intentionally not bundled.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android"/"app"/"src"/"main"
JAVA=APP/"java"/"studio"/"realframe"

class HybridRoutingTests(unittest.TestCase):
    def test_hybrid_tab(self):
        s=(JAVA/"StudioDashboardActivity.java").read_text()
        self.assertIn('"Hybrid"',s)
        self.assertIn('drawHybrid()',s)
        self.assertIn('ModelManagerActivity.class',s)
        self.assertIn('local ai image (cpu beta)',s.lower())

    def test_android_model_saf_reference(self):
        s=(JAVA/"ModelManagerActivity.java").read_text()
        for snippet in ["ACTION_OPEN_DOCUMENT", "ACTION_OPEN_DOCUMENT_TREE",
                        "takePersistableUriPermission","getSharedPreferences",
                        "String.join(", "backendReady()", "LocalImageEngine.isAvailable()"]:
            self.assertIn(snippet,s)

    def test_explicit_false_ready(self):
        s=(JAVA/"ModelManagerActivity.java").read_text()
        self.assertIn("cpu text-to-image runtime installed",s.lower())
        self.assertIn('putString("mode",value)',s)

    def test_manifest(self):
        root=ET.parse(APP/"AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        names=[x.get(ns+"name") for x in root.find("application").findall("activity")]
        self.assertIn(".ModelManagerActivity", names)

    def test_version(self):
        s=(ROOT/"android"/"app"/"build.gradle.kts").read_text()
        self.assertTrue('versionName = "1.4.0-beta"' in s or 'versionName = "1.5.0-beta"' in s or 'versionName = "1.6.0-local-image-experimental"' in s or 'versionName = "1.7.0-hybrid-batch-beta"' in s or 'versionName = "1.8.0-local-video-experimental"' in s)
        self.assertTrue('applicationId = "studio.realframe.v14"' in s or 'applicationId = "studio.realframe.v15"' in s or 'applicationId = "studio.realframe.v16"' in s or 'applicationId = "studio.realframe.v17"' in s or 'applicationId = "studio.realframe.v18"' in s)

if __name__=="__main__":
    unittest.main()
