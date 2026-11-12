#!/usr/bin/env python3
"""LOCAL TEST ONLY: fake Nightmare endpoints, no AI rendering.

Run on development machine: python3 tools/mock_nightmare.py --port 8821
This creates a small synthetic PNG + MP4 so an Android controller can test
API connection, auth, and Gallery export without downloading any model.
NEVER expose this server to the internet; do not confuse with genuine inference.
"""
import argparse
import base64
import io
import json
import subprocess
import tempfile
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

# 1x1 valid PNG (synthetic test content)
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/VwkAAAAASUVORK5CYII=')


class TestServer(ThreadingHTTPServer):
    def __init__(self, addr, auth_token='local-test-token'):
        super().__init__(addr, Handler)
        self.token = auth_token
        self.folder = tempfile.TemporaryDirectory(prefix='realframe_mock_')
        self.mp4 = Path(self.folder.name) / 'test.mp4'

    def server_close(self):
        self.folder.cleanup()
        super().server_close()


class Handler(BaseHTTPRequestHandler):
    def answer(self, code, obj):
        raw = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        return self.headers.get('Authorization') == f'Bearer {self.server.token}'

    def authorized_webui(self):
        expected = base64.b64encode(('realframe:' + self.server.token).encode()).decode()
        return self.headers.get('Authorization') == 'Basic ' + expected

    def do_GET(self):
        if self.path == '/sdapi/v1/sd-models':
            if not self.authorized_webui():
                return self.answer(401, {'error': 'valid Basic credential required'})
            return self.answer(200, [{'title': 'synthetic-demo-NOT-AI'}])
        if self.path == '/info':
            return self.answer(200, {'app':'Mock Nightmare (NO AI)', 'rendering':False})
        if not self.authorized():
            return self.answer(401, {'error':'valid bearer token required'})
        if self.path == '/models':
            return self.answer(200, {'models':[{'id':'synthetic-test-only'}]})
        if self.path == '/flows':
            return self.answer(200, {'flows':['txt2img','mock_video']})
        if self.path.startswith('/flows/'):
            return self.answer(200, {'id':self.path.split('/')[-1], 'params':{}})
        if self.path.endswith('.png') and self.path.startswith('/results/'):
            return self.raw_media(PNG,'image/png')
        if self.path.endswith('.mp4') and self.path.startswith('/results/'):
            if not self.server.mp4.exists():
                proc = subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i','color=c=black:s=320x240:r=12',
                        '-t','1','-c:v','mpeg4','-q:v','8','-y',str(self.server.mp4)],
                        capture_output=True, text=True)
                if proc.returncode:
                    return self.answer(500,{'error':'FFmpeg required for mock MP4: '+proc.stderr[-250:]})
            return self.raw_media(self.server.mp4.read_bytes(),'video/mp4')
        return self.answer(404,{'error':'unknown endpoint'})

    def raw_media(self, payload, mime):
        self.send_response(200)
        self.send_header('Content-Type',mime)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        if self.path == '/sdapi/v1/txt2img':
            if not self.authorized_webui():
                return self.answer(401, {'error': 'valid Basic credential required'})
            n = int(self.headers.get('Content-Length', '0'))
            if n <= 0 or n > 500_000:
                return self.answer(413, {'error': 'invalid request length'})
            try:
                data = json.loads(self.rfile.read(n))
            except (ValueError, UnicodeDecodeError):
                return self.answer(400, {'error': 'malformed JSON'})
            if not data.get('prompt'):
                return self.answer(400, {'error': 'prompt required'})
            return self.answer(200, {'images': [base64.b64encode(PNG).decode()],
                                     'info': 'SYNTHETIC ONLY - NO AI'})
        if not self.authorized():
            return self.answer(401, {'error':'valid bearer token required'})
        if self.path != '/run':
            return self.answer(404,{'error':'unknown endpoint'})
        n = int(self.headers.get('Content-Length','0'))
        if n <= 0 or n > 500_000:
            return self.answer(413,{'error':'request invalid or too large for mock'})
        try:
            data = json.loads(self.rfile.read(n))
        except (ValueError, UnicodeDecodeError):
            return self.answer(400, {'error':'malformed JSON'})
        if not data.get('prompt') or not data.get('flow'):
            return self.answer(400,{'error':'flow and prompt are required'})
        path = 'test.mp4' if data['flow'] == 'mock_video' else 'test.png'
        self.answer(200, {'run':uuid.uuid4().hex, 'results':[{'url':'/results/mock/'+path}]})

    def log_message(self, fmt, *args):
        print('[mock, not real AI] '+fmt%args)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=8821)
    parser.add_argument('--token',default='local-test-token')
    args=parser.parse_args()
    with TestServer((args.host,args.port),args.token) as server:
        print('Synthetic/mock API — NO AI. URL: http://%s:%d, token: %s'%
              (args.host,args.port,args.token))
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print('Stopped')


if __name__=='__main__':
    main()
