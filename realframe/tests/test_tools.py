"""Tests for working, platform-independent RealFrame utilities."""
import importlib.util
import json
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

spec = importlib.util.spec_from_file_location("realframe_plan", ROOT / "tools" / "plan.py")
plan_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plan_module)
spec2 = importlib.util.spec_from_file_location("realframe_stitch", ROOT / "tools" / "stitch.py")
stitch_module = importlib.util.module_from_spec(spec2)
spec2.loader.exec_module(stitch_module)


class PlanTests(unittest.TestCase):
    def setUp(self):
        self.character = json.loads((ROOT / "examples" / "character.json").read_text())

    def test_twelve_five_second_scenes(self):
        manifest = plan_module.plan(self.character, 60, 5)
        scenes = manifest["scenes"]
        self.assertEqual(len(scenes), 12)
        self.assertEqual(sum(s["seconds"] for s in scenes), 60)
        self.assertIsNone(scenes[0]["depends_on"])
        self.assertEqual(scenes[1]["depends_on"], "clip_001")
        self.assertEqual(scenes[-1]["depends_on"], "clip_011")
        self.assertEqual(scenes[-1]["output"], "clips/clip_012.mp4")

    def test_short_final_clip(self):
        scenes = plan_module.plan(self.character, 12.5, 5)["scenes"]
        self.assertEqual([s["seconds"] for s in scenes], [5, 5, 2.5])

    def test_independent_scenes_have_no_deps(self):
        scenes = plan_module.plan(self.character, 20, 5, independent=True)["scenes"]
        self.assertTrue(all(s["depends_on"] is None for s in scenes))

    def test_underage_profile_rejected(self):
        character = dict(self.character, age=17)
        with self.assertRaises(ValueError):
            plan_module.plan(character, 10, 5)

    def test_invalid_seconds(self):
        with self.assertRaises(ValueError):
            plan_module.plan(self.character, 0, 5)


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "ffmpeg and ffprobe unavailable")
class ClipStitchTests(unittest.TestCase):
    def test_normalized_merged_video_has_correct_duration(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = pathlib.Path(tmp) / "clips"
            folder.mkdir()
            for idx, dims in enumerate([(160, 90), (90, 160)], start=1):
                filename = folder / f"clip_{idx:03d}.mp4"
                subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                                "-f", "lavfi", "-i", f"color=c=black:s={dims[0]}x{dims[1]}:r=12:d=0.5",
                                "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", str(filename)],
                               check=True)
            output = pathlib.Path(tmp) / "final.mp4"
            stitch_module.stitch(str(folder), str(output), 128, 192, 12)
            self.assertTrue(output.is_file())
            probe = json.loads(subprocess.check_output([
                "ffprobe", "-v", "quiet", "-show_streams", "-show_format", "-of", "json", str(output)
            ]))
            video = next(s for s in probe["streams"] if s["codec_type"] == "video")
            self.assertEqual((video["width"], video["height"]), (128, 192))
            self.assertAlmostEqual(float(probe["format"]["duration"]), 1.0, delta=0.12)


if __name__ == "__main__":
    unittest.main()
