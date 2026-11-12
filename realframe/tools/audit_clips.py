#!/usr/bin/env python3
"""Audit rendered clips before merging them into a long film.

Run locally (e.g. Termux or desktop) with ffprobe installed. This checks file
presence, duration and technical consistency, NOT AI visual identity or safety.
"""
import argparse
import json
import pathlib
import subprocess
from typing import Any


def probe_video(path: pathlib.Path) -> dict[str, Any]:
    data = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)
    ]))
    video = next((s for s in data.get("streams", []) if s.get("codec_type") == "video"), None)
    if video is None:
        raise ValueError(f"No video stream in {path}")
    duration = float(data.get("format", {}).get("duration") or video.get("duration") or 0)
    return {
        "width": video.get("width"), "height": video.get("height"),
        "codec": video.get("codec_name"), "fps": video.get("avg_frame_rate"),
        "duration_seconds": round(duration, 3),
        "has_audio": any(s.get("codec_type") == "audio" for s in data.get("streams", [])),
    }


def audit(folder: str, manifest: str | None = None) -> dict:
    directory = pathlib.Path(folder).resolve()
    if not directory.is_dir():
        raise ValueError(f"Clips folder does not exist: {directory}")
    if manifest:
        planned = json.loads(pathlib.Path(manifest).read_text(encoding="utf-8"))
        scenes = planned.get("scenes", [])
        if not scenes:
            raise ValueError("Manifest has no scenes")
        targets = [directory / pathlib.Path(s["output"]).name for s in scenes]
    else:
        targets = sorted(directory.glob("*.mp4"))
    if not targets:
        raise ValueError("No clips found")

    report: dict[str, Any] = {"expected_clips": len(targets), "found_clips": 0,
                               "missing": [], "errors": [], "clips": [], "warnings": []}
    first_format = None
    for number, file in enumerate(targets, 1):
        if not file.is_file():
            report["missing"].append(file.name)
            continue
        try:
            entry = {"clip": number, "filename": file.name, **probe_video(file)}
            report["clips"].append(entry)
            report["found_clips"] += 1
            key = (entry["codec"], entry["width"], entry["height"], entry["fps"], entry["has_audio"])
            if first_format is None:
                first_format = key
            elif key != first_format:
                report["warnings"].append(f"{file.name}: codec/resolution/FPS/audio differs; use normalized stitch.py")
        except (ValueError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
            report["errors"].append(f"{file.name}: {exc}")
    report["observed_seconds"] = round(sum(c["duration_seconds"] for c in report["clips"]), 3)
    report["merge_ready"] = (len(targets) >= 2 and not report["missing"] and
                             not report["errors"] and not report["warnings"])
    if manifest:
        report["planned_seconds"] = planned.get("target_seconds")
        expected = planned.get("target_seconds")
        if isinstance(expected, (int, float)) and not report["missing"]:
            if abs(report["observed_seconds"] - expected) > max(2, expected * .05):
                report["warnings"].append("Rendered durations differ materially from planned seconds")
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--clips", required=True)
    p.add_argument("--manifest", help="Optional plan.py output for expected clip filenames")
    p.add_argument("--output", help="Optional path to save JSON report")
    args = p.parse_args()
    report = audit(args.clips, args.manifest)
    payload = json.dumps(report, indent=2, ensure_ascii=False)
    print(payload)
    if args.output:
        pathlib.Path(args.output).write_text(payload + "\n", encoding="utf-8")
    if report["missing"] or report["errors"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
