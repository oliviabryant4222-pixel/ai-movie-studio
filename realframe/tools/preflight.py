#!/usr/bin/env python3
"""Static preflight. Not a replacement for an Android SDK build/device test."""
from __future__ import annotations
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / 'android'


def main() -> None:
    issues = []
    def require(cond, message):
        if not cond:
            issues.append(message)

    manifest_path = ANDROID / 'app/src/main/AndroidManifest.xml'
    gradle_path = ANDROID / 'app/build.gradle.kts'
    workflow_path = ROOT / '.github/workflows/android-debug.yml'
    for path in [manifest_path, gradle_path, workflow_path]:
        require(path.exists(), f'Missing {path.relative_to(ROOT)}')
    if issues:
        raise SystemExit('\n'.join('ERROR: ' + i for i in issues))

    gradle = gradle_path.read_text(encoding='utf-8')
    for key in ['compileSdk = 35', 'targetSdk = 35']:
        require(key in gradle, f'App gradle missing {key}')
    require(any(v in gradle for v in ['versionName = "1.1.0-test"', 'versionName = "1.2.0-beta"']), 'Unexpected versionName')
    require(any(v in gradle for v in ['applicationId = "studio.realframe.beta"', 'applicationId = "studio.realframe.v12"']),
            'Unexpected applicationId: verify package/manifest before publishing')

    root = ET.parse(manifest_path).getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    app = root.find('application')
    require(app is not None, 'Missing <application>')
    if app is not None:
        activities = app.findall('activity')
        require(any(a.get(ns+'name') == '.MainActivity' and a.get(ns+'exported') == 'true'
                    for a in activities), 'Launcher activity missing or incorrectly exported')
        services = app.findall('service')
        require(any(s.get(ns+'name') == '.RenderService' and
                    s.get(ns+'foregroundServiceType') == 'dataSync' for s in services),
                'Renderer service must declare dataSync FGS type')
    perms = {p.get(ns+'name') for p in root.findall('uses-permission')}
    for p in ['android.permission.INTERNET', 'android.permission.FOREGROUND_SERVICE',
              'android.permission.FOREGROUND_SERVICE_DATA_SYNC']:
        require(p in perms, f'Missing Android permission: {p}')

    base = ANDROID/'app/src/main/java/studio/realframe'
    required = ['MainActivity.java','RenderService.java','Renderer.java', 'StudioStore.java',
                'SecretVault.java','ClipStitcher.java','MediaSignature.java', 'WebStudioActivity.java']
    for name in required:
        path = base / name
        require(path.is_file(), f'Missing Java file: {name}')
        if path.is_file():
            content = path.read_text(encoding='utf-8')
            require(re.search(r'package\s+studio\.realframe\s*;', content) is not None,
                    f'Wrong package declaration: {name}')
    workflow = workflow_path.read_text(encoding='utf-8')
    require('assembleDebug' in workflow, 'CI workflow must actually build the debug APK')
    require('upload-artifact' in workflow, 'CI workflow must publish the output APK artifact')
    require('apksigner' in workflow, 'CI workflow should verify APK signature')

    if issues:
        for i in issues:
            print('ERROR:', i, file=sys.stderr)
        raise SystemExit(1)
    print('PASS: 18 static config/source checks. Android compile and device behavior NOT tested.')


if __name__ == '__main__':
    main()
