#!/usr/bin/env python3
"""Generate a consistent-character storyboard JSON with clip dependencies.
No image/video model inference is performed.
"""
import argparse
import json
import math
import pathlib
from datetime import datetime, timezone


def plan(character: dict, seconds: float, clip_seconds: float, independent: bool=False) -> dict:
    if seconds <= 0 or clip_seconds <= 0:
        raise ValueError("Both seconds and clip_seconds must be positive")
    if int(character.get("age", 0)) < 18:
        raise ValueError("Character must be an adult (18+)")
    clips = []
    count = math.ceil(seconds / clip_seconds)
    for i in range(count):
        num = i + 1
        duration = round(min(clip_seconds, seconds - i * clip_seconds), 3)
        scene = {
            "id": f"clip_{num:03d}", "order": num, "seconds": duration,
            "character": character["name"], "model": character.get("model"),
            "prompt": ". ".join(x for x in [
                character.get("identity_prompt", ""), character.get("wardrobe_prompt", ""),
                character.get("visual_style", ""), character.get("scene_theme", ""),
                "Natural movement, believable motion, consistent clothing and identity"
            ] if x),
            "motion_prompt": "Gentle natural head and hand movement, realistic blinking, stable camera",
            "seed": int(character.get("seed", 42)) + i,
            "start_frame": "master_reference_image" if i == 0 or independent else f"clip_{i:03d}_last_frame",
            "depends_on": None if i == 0 or independent else f"clip_{i:03d}",
            "output": f"clips/clip_{num:03d}.mp4", "status": "not_started"
        }
        clips.append(scene)
    return {
        "project": "RealFrame Studio", "created_utc": datetime.now(timezone.utc).isoformat(),
        "character": character, "target_seconds": seconds, "clip_seconds": clip_seconds,
        "note": "This is a shot plan, not generated media. Sequential clips use last-frame conditioning when supported.",
        "scenes": clips
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--character", required=True)
    p.add_argument("--seconds", type=float, required=True)
    p.add_argument("--clip-seconds", type=float, default=5)
    p.add_argument("--independent", action="store_true", help="Independent scenes can be scheduled in parallel on multiple renderers")
    p.add_argument("--output", default="storyboard.json")
    a = p.parse_args()
    ch = json.loads(pathlib.Path(a.character).read_text(encoding="utf-8"))
    result = plan(ch, a.seconds, a.clip_seconds, a.independent)
    pathlib.Path(a.output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Planned {len(result['scenes'])} clips ({a.seconds:g} seconds) into {a.output}")


if __name__ == "__main__":
    main()
