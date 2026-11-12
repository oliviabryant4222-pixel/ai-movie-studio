"""v1.3 hybrid UI source wiring smoke checks. Does not establish on-device inference."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "android/app/src/main/java/studio/realframe"
class HybridUiWiringTests(unittest.TestCase):
    def test_dashboard_provides_clear_online_offline(self):
        text = (MAIN/"StudioDashboardActivity.java").read_text()
        self.assertIn('render("Home")', text)
        self.assertIn('"Online"', text)
        self.assertIn('"Offline"', text)
        self.assertIn('online("image")', text)
        self.assertIn('online("video")', text)
        self.assertIn("OfflinePhotoLabActivity.class", text)
        self.assertIn("OfflineImageVideoActivity.class",text)
        self.assertIn("local text-to-image: experimental", text.lower().replace("native ", ""))
    def test_offline_photo_lab_exports_gallery_image(self):
        text = (MAIN/"OfflinePhotoLabActivity.java").read_text()
        for token in ["ACTION_OPEN_DOCUMENT", "ColorMatrixColorFilter", "MediaStore.Images.Media.EXTERNAL_CONTENT_URI", "image/jpeg", "IS_PENDING"]:
            self.assertIn(token, text)
    def test_legacy_nav_route(self):
        text = (MAIN/"MainActivity.java").read_text()
        self.assertIn('getStringExtra("open_tab")', text)
        self.assertIn('case "Dashboard"', text)
    def test_manifest_launcher_dashboard_and_photo_lab(self):
        path = ROOT/"android/app/src/main/AndroidManifest.xml"
        tree = ET.parse(path)
        namespace = "{http://schemas.android.com/apk/res/android}"
        acts = tree.getroot().find("application").findall("activity")
        self.assertTrue(any(a.get(namespace+"name")==".StudioDashboardActivity" and a.get(namespace+"exported")=="true" for a in acts))
        self.assertTrue(any(a.get(namespace+"name")==".OfflinePhotoLabActivity" for a in acts))
if __name__=="__main__":
    unittest.main()
