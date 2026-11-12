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
        self.assertIn('no native on-device ai engine',s.lower())

    def test_android_model_saf_reference(self):
        s=(JAVA/"ModelManagerActivity.java").read_text()
        for snippet in ["ACTION_OPEN_DOCUMENT", "ACTION_OPEN_DOCUMENT_TREE",
                        "takePersistableUriPermission","getSharedPreferences",
                        "String.join(", "backendReady()", "return false;"]:
            self.assertIn(snippet,s)

    def test_explicit_false_ready(self):
        s=(JAVA/"ModelManagerActivity.java").read_text()
        self.assertIn("local generative inference not bundled",s.lower())
        self.assertIn('putString("mode",value)',s)

    def test_manifest(self):
        root=ET.parse(APP/"AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        names=[x.get(ns+"name") for x in root.find("application").findall("activity")]
        self.assertIn(".ModelManagerActivity", names)

    def test_version(self):
        s=(ROOT/"android"/"app"/"build.gradle.kts").read_text()
        self.assertIn('versionName = "1.4.0-beta"',s)
        self.assertIn('applicationId = "studio.realframe.v14"',s)

if __name__=="__main__":
    unittest.main()
