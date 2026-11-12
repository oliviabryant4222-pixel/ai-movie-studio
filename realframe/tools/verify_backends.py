#!/usr/bin/env python3
"""Read-only health checks for RealFrame's Nightmare local API and Gradio Spaces.

Does NOT generate media, consume GPU time, or circumvent provider limits.
Uses Python standard library and works with HTTP / HTTPS endpoints.
"""
import argparse
import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


@dataclass
class Result:
    backend: str
    ok: bool
    report: dict


def request_json(url: str, token: str = "", timeout: int = 15):
    headers = {"User-Agent": "RealFrameStudio-verify/0.4"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.loads(response.read(3_000_000).decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"{exc.code} from {url}: check token/URL/server. {exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Cannot connect to {url}: {exc.reason}") from exc


def clean_local_url(url: str):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("Local URL should be http://<your-phone-LAN-IP>:8820")
    if parsed.username or parsed.password or parsed.path not in {"", "/"} or parsed.query or parsed.fragment:
        raise ValueError("Local URL must be a base address without credentials, paths or query")
    return url.rstrip("/")


def check_nightmare(url: str, token: str = "", fetch=request_json) -> Result:
    base = clean_local_url(url)
    info = fetch(f"{base}/info", "")
    models = fetch(f"{base}/models", token)
    flows = fetch(f"{base}/flows", token)
    return Result("Nightmare Local", True, {"base": base, "info": info, "models": models, "flows": flows})


def clean_space_url(url: str):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".hf.space"):
        raise ValueError("Cloud Space URL must be https://owner-project.hf.space")
    if parsed.username or parsed.password or parsed.path not in {"", "/"} or parsed.query or parsed.fragment:
        raise ValueError("Cloud Space URL must not include a path, login, query or fragment")
    if parsed.hostname.count(".") != 2 or parsed.hostname.startswith("."):
        raise ValueError("Cloud Space URL should be a direct *.hf.space subdomain")
    return url.rstrip("/")


def check_gradio(url: str, endpoint: str, token: str = "", fetch=request_json) -> Result:
    base = clean_space_url(url)
    if not endpoint.startswith("/") or not endpoint[1:].replace("_", "").replace("-", "").isalnum():
        raise ValueError("Endpoint must be a name like /predict, copied from Use via API")
    spec = fetch(f"{base}/gradio_api/openapi.json", token)
    api_paths = sorted(p for p in spec.get("paths", {}) if p.startswith("/gradio_api/call/"))
    # A Space may be alive while an endpoint is missing or private; don't mark it healthy.
    target = f"/gradio_api/call{endpoint}"
    return Result("Hugging Face Gradio", target in api_paths, {
        "base": base, "configured_endpoint": target,
        "available_paths": api_paths,
        "warning": None if target in api_paths else "Configured endpoint absent in OpenAPI schema; use Space's Use via API guide",
    })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="backend", required=True)
    local = sub.add_parser("local", help="Check local Nightmare Mobile API")
    local.add_argument("--url", default="http://127.0.0.1:8820")
    local.add_argument("--token", default="")
    cloud = sub.add_parser("space", help="Check one Gradio Space OpenAPI schema")
    cloud.add_argument("--url", required=True)
    cloud.add_argument("--endpoint", required=True)
    cloud.add_argument("--token", default="")
    args = parser.parse_args()
    if args.backend == "local":
        result = check_nightmare(args.url, args.token)
    else:
        result = check_gradio(args.url, args.endpoint, args.token)
    print(json.dumps({"backend": result.backend, "ok": result.ok, "report": result.report}, indent=2))
    if not result.ok:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
