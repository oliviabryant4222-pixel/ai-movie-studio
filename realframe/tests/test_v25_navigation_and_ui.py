"""RealFrame 2.5 navigation audit: all Java explicit screen routes resolve.
Source-level audit, not an Android emulator or physical-screen tap test.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"android/app"
SRC=APP/"src/main/java/studio/realframe"

class NavigationAndUiAudit(unittest.TestCase):
    def test_every_explicit_activity_route_registered(self):
        namespace="{http://schemas.android.com/apk/res/android}"
        root=ET.parse(APP/"src/main/AndroidManifest.xml").getroot()
        classes={a.get(namespace+"name").lstrip(".") for a in root.find("application").findall("activity")}
        self.assertGreater(len(classes),14)
        routed=set()
        for java in SRC.glob("*.java"):
            text=java.read_text(encoding="utf-8")
            patterns=[
                r"new\s+Intent\s*\(\s*this\s*,\s*(\w+)\.class\s*\)",
                r"open\s*\(\s*(\w+Activity)\.class\s*\)",
                r"StudioUi\.open\s*\(\s*this\s*,\s*(\w+)\.class\s*\)",
                r"new\s+Intent\s*\(\s*[A-Za-z_]+\.this\s*,\s*(\w+)\.class\s*\)",
            ]
            for pattern in patterns:
                routed.update(re.findall(pattern,text))
        unregistered=sorted(name for name in routed if name.endswith("Activity") and name not in classes)
        self.assertFalse(unregistered,f"Unregistered Android activity targets: {unregistered}")
        self.assertIn("LocalVideoActivity",routed)
        self.assertIn("QuickStartActivity",routed)
        self.assertIn("StudioHealthActivity",routed)

    def test_advanced_dashboard_tabs_map_to_real_mainactivity_routes(self):
        home=(SRC/"StudioDashboardActivity.java").read_text(encoding="utf-8")
        advanced=(SRC/"MainActivity.java").read_text(encoding="utf-8")
        destinations=set(re.findall(r'advanced\("([^"]+)"\)',home))
        valid=set(re.findall(r'case\s+"([^"]+)"\s*:',advanced))
        self.assertGreaterEqual(len(destinations),5)
        self.assertFalse(destinations-valid,
            f"Dashboard links to unsupported advanced pages: {sorted(destinations-valid)}")

    def test_premium_buttons_are_real_listeners_not_dead_art(self):
        common=(SRC/"StudioUi.java").read_text(encoding="utf-8")
        for part in ("RippleDrawable","TAP_GUARD_MS","setOnClickListener",
                     "performHapticFeedback","ActivityNotFoundException",
                     "setContentDescription","styleButton(", "styleTile("):
            self.assertIn(part,common)
        screens=[
            "StudioDashboardActivity.java","QuickStartActivity.java",
            "UnifiedCreateActivity.java","LocalVideoActivity.java",
            "LocalImageActivity.java","StudioHealthActivity.java",
            "ReferenceStudioActivity.java","LocalAutoFilmActivity.java",
            "StoryMovieComposerActivity.java","LocalApiActivity.java",
            "ModelManagerActivity.java","MainActivity.java",
            "OfflinePhotoLabActivity.java","OfflineImageVideoActivity.java",
            "ProductionPlannerActivity.java","WebStudioActivity.java",
            "LocalWebClientActivity.java",
        ]
        for name in screens:
            text=(SRC/name).read_text(encoding="utf-8")
            self.assertTrue(("StudioUi.styleButton(" in text or "StudioUi.styleTile(" in text),
                            f"Touchable UI not using shared button handlers: {name}")

    def test_online_buttons_have_recovery_and_manual_fallback(self):
        s=(SRC/"WebStudioActivity.java").read_text(encoding="utf-8")
        for part in ("onReceivedError(", "onReceivedHttpError(",
                     'action("↻ Retry"', 'action("☁ Alternate image"',
                     'action("◉ Local AI"', "callback.onReceiveValue(null)",
                     "https://huggingface.co/spaces/"):
            self.assertIn(part,s)
        self.assertIn("Internet and provider quotas apply",s)

    def test_home_video_shortcut_opens_correct_mode_not_forced_local_video(self):
        home=(SRC/"StudioDashboardActivity.java").read_text(encoding="utf-8")
        create=(SRC/"UnifiedCreateActivity.java").read_text(encoding="utf-8")
        self.assertIn('next.putExtra("output","video")',home)
        self.assertIn('"video".equalsIgnoreCase(getIntent().getStringExtra("output"))',create)
        self.assertIn("output.setSelection(1)",create)

    def test_ui_status_and_latest_version(self):
        s=(SRC/"StudioDashboardActivity.java").read_text(encoding="utf-8")
        self.assertIn('label("v2.6"',s)
        self.assertIn("StudioUi.bind(this,tab",s)
        self.assertIn("NEW IMAGE",s)
        gradle=(APP/"build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "studio.realframe.v25"',gradle)
        self.assertIn('versionName = "2.6.0-local-engine-repair"',gradle)

if __name__=="__main__":unittest.main()
