"""Static + mock network contract tests; no Android compile or actual GPU inference."""
import importlib.util
import json
import subprocess
import sys
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec=importlib.util.spec_from_file_location(name,ROOT/'tools'/filename)
    mod=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

mock = module('mock_nightmare','mock_nightmare.py')


class PreflightTests(unittest.TestCase):
    def test_preflight_validates_project(self):
        proc=subprocess.run([sys.executable,str(ROOT/'tools/preflight.py')],capture_output=True,text=True)
        self.assertEqual(proc.returncode,0,proc.stderr)
        self.assertIn('NOT tested',proc.stdout)

    def test_cancel_feature_hooked_up(self):
        java=(ROOT/'android/app/src/main/java/studio/realframe/StudioStore.java').read_text()
        activity=(ROOT/'android/app/src/main/java/studio/realframe/MainActivity.java').read_text()
        self.assertIn('cancelWaitingJob(',java)
        self.assertIn('cancelWaitingJobs(',java)
        self.assertIn('Cancel all waiting',activity)
        self.assertIn('Cancel this job',activity)


class MockApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=mock.TestServer(('127.0.0.1',0))
        cls.thread=threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base='http://127.0.0.1:%d'%cls.server.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.thread.join(timeout=3)
        cls.server.server_close()

    def api(self, path, token='local-test-token', payload=None):
        headers={'Authorization':'Bearer '+token}
        if payload is not None:
            headers['Content-Type']='application/json'
        data=json.dumps(payload).encode() if payload is not None else None
        return urlopen(Request(self.base+path, data=data, headers=headers),timeout=5)

    def test_info_public(self):
        with urlopen(self.base+'/info',timeout=5) as r:
            self.assertIn('NO AI',json.load(r)['app'])

    def test_auth_required_for_models(self):
        with self.assertRaises(HTTPError) as err:
            self.api('/models',token='wrong')
        self.assertEqual(err.exception.code,401)

    def test_models_and_flows(self):
        with self.api('/flows') as r:
            self.assertIn('txt2img',json.load(r)['flows'])
        with self.api('/models') as r:
            self.assertIn('synthetic-test-only',str(json.load(r)))

    def test_synthetic_image_endpoint(self):
        with self.api('/run',payload={'flow':'txt2img','prompt':'synthetic test only'}) as r:
            result=json.load(r)
        with self.api(result['results'][0]['url']) as r:
            self.assertEqual(r.headers.get_content_type(),'image/png')
            self.assertTrue(r.read().startswith(b'\x89PNG'))

    def test_synthetic_mp4_endpoint(self):
        with self.api('/run',payload={'flow':'mock_video','prompt':'synthetic test only'}) as r:
            result=json.load(r)
        with self.api(result['results'][0]['url']) as r:
            self.assertEqual(r.headers.get_content_type(),'video/mp4')
            data=r.read()
            self.assertIn(b'ftyp',data[:32])

    def test_invalid_request(self):
        with self.assertRaises(HTTPError) as err:
            self.api('/run',payload={'prompt':'missing flow'})
        self.assertEqual(err.exception.code,400)


if __name__=='__main__':
    unittest.main()
