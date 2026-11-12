"""v0.5 large-storyboard and movie-audit smoke tests (no GPU needed)."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


plan = load('realframe_plan_05', ROOT / 'tools' / 'plan.py')
audit_clips = load('realframe_audit_05', ROOT / 'tools' / 'audit_clips.py')


class ExtendedPlanTests(unittest.TestCase):
    def setUp(self):
        self.character = json.loads((ROOT / 'examples' / 'character.json').read_text())

    def test_long_movie_1800_clips_are_ordered(self):
        scenes = plan.plan(self.character, 3600, 2)['scenes']
        self.assertEqual(len(scenes), 1800)
        self.assertEqual(scenes[-1]['id'], 'clip_1800')
        self.assertEqual(scenes[-1]['depends_on'], 'clip_1799')
        self.assertEqual(scenes[-1]['output'], 'clips/clip_1800.mp4')

    def test_uneven_final_scene_duration(self):
        scenes = plan.plan(self.character, 7.5, 3)['scenes']
        self.assertEqual([s['seconds'] for s in scenes], [3, 3, 1.5])


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'requires ffmpeg and ffprobe')
class FilmAuditTests(unittest.TestCase):
    def _make_clip(self, folder, name, dimensions):
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
                        f'color=c=gray:s={dimensions}:r=12:d=0.5', '-an',
                        '-c:v', 'libx264', '-pix_fmt', 'yuv420p', str(folder / name)], check=True)

    def test_two_clips_compatible(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self._make_clip(folder, 'clip_001.mp4', '160x90')
            self._make_clip(folder, 'clip_002.mp4', '160x90')
            report = audit_clips.audit(tmp)
            self.assertTrue(report['merge_ready'])
            self.assertEqual(report['found_clips'], 2)
            self.assertAlmostEqual(report['observed_seconds'], 1.0, delta=0.2)

    def test_mismatched_dimensions_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self._make_clip(folder, 'clip_001.mp4', '160x90')
            self._make_clip(folder, 'clip_002.mp4', '90x160')
            report = audit_clips.audit(tmp)
            self.assertFalse(report['merge_ready'])
            self.assertTrue(any('differs' in w for w in report['warnings']))

    def test_missing_expected_clip_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self._make_clip(folder, 'clip_001.mp4', '160x90')
            manifest = folder / 'project.json'
            manifest.write_text(json.dumps({'target_seconds': 1.0, 'scenes': [
                {'output': 'clips/clip_001.mp4'}, {'output': 'clips/clip_002.mp4'}]}))
            report = audit_clips.audit(tmp, str(manifest))
            self.assertEqual(report['missing'], ['clip_002.mp4'])
            self.assertFalse(report['merge_ready'])


if __name__ == '__main__':
    unittest.main()
