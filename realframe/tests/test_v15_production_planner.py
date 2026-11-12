"""RealFrame v1.5 static feature wiring tests — not a test of real AI inference."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"android/app/src/main/java/studio/realframe"

class ProductionPlannerSmokeTests(unittest.TestCase):
    def test_plan_contains_continuity_references(self):
        s=(SRC/"ProductionPlannerActivity.java").read_text()
        for token in ('"realframe-scene-manifest-v2"', '"dependsOn"', '"startFrameReference"',
                      '"durationSeconds"', '"characterId"', '"prompt"', '"status","planned"',
                      '"targetOutput",id+".mp4"'):
            self.assertIn(token,s)
    def test_plan_is_portable_local_json(self):
        s=(SRC/"ProductionPlannerActivity.java").read_text()
        for token in ('ACTION_CREATE_DOCUMENT','application/json','ClipboardManager','getSharedPreferences',
                      'new StudioStore(this).characters()', 'startActivityForResult'):
            self.assertIn(token,s)
    def test_dashboard_link_and_manifest(self):
        s=(SRC/"StudioDashboardActivity.java").read_text()
        self.assertIn("ProductionPlannerActivity.class",s)
        root=ET.parse(ROOT/"android/app/src/main/AndroidManifest.xml").getroot()
        ns="{http://schemas.android.com/apk/res/android}"
        entries=[a.get(ns+"name") for a in root.find("application").findall("activity")]
        self.assertIn(".ProductionPlannerActivity",entries)
    def test_package(self):
        s=(ROOT/"android/app/build.gradle.kts").read_text()
        self.assertIn('applicationId = "studio.realframe.v15"',s)
        self.assertIn('versionName = "1.5.0-beta"',s)

if __name__ == '__main__':unittest.main()
