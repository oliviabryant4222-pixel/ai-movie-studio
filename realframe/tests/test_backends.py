"""Deterministic tests: only mock HTTP; no GPU endpoint or provider calls."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_backends", ROOT / "tools" / "verify_backends.py")
module = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class CheckBackendTests(unittest.TestCase):
    def test_local_reads_info_models_and_flows_with_token(self):
        seen = []
        def fetch(url, token):
            seen.append((url, token))
            return {"kind": url.split("/")[-1]}
        got = module.check_nightmare("http://192.168.1.30:8820/", "test-token", fetch)
        self.assertTrue(got.ok)
        self.assertEqual([u for u,t in seen], [
            "http://192.168.1.30:8820/info",
            "http://192.168.1.30:8820/models",
            "http://192.168.1.30:8820/flows",
        ])
        self.assertEqual([t for u,t in seen], ["", "test-token", "test-token"])

    def test_local_rejects_bad_base(self):
        with self.assertRaises(ValueError):
            module.clean_local_url("https://example.org/foo?q=test")

    def test_cloud_detects_real_endpoint(self):
        def fetch(url, token):
            return {"paths": {"/gradio_api/call/generate": {}, "/gradio_api/call/predict": {}}}
        got = module.check_gradio("https://author-space.hf.space", "/generate", fetch=fetch)
        self.assertTrue(got.ok)
        self.assertIn("/gradio_api/call/generate", got.report["available_paths"])

    def test_cloud_detects_missing_endpoint(self):
        got = module.check_gradio("https://author-space.hf.space", "/video",
                                  fetch=lambda *_: {"paths": {"/gradio_api/call/generate": {}}})
        self.assertFalse(got.ok)
        self.assertIn("absent", got.report["warning"])

    def test_cloud_must_be_hf_space(self):
        for url in ["http://owner-space.hf.space", "https://evil.com", "https://owner-space.hf.space.evil.com", "https://owner-space.hf.space/predict"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                module.clean_space_url(url)

    def test_cloud_rejects_bad_endpoint(self):
        with self.assertRaises(ValueError):
            module.check_gradio("https://author-space.hf.space", "/generate/../../delete", fetch=lambda *_: {})


if __name__ == "__main__":
    unittest.main()
