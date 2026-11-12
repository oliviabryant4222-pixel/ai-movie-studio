#!/usr/bin/env python3
"""Join generated clips into one MP4, normalizing size/FPS first.
Requires ffmpeg/ffprobe installed. Video audio is dropped for predictable merges.
"""
import argparse
import pathlib
import shutil
import subprocess
import tempfile


def run(args):
    subprocess.run(args, check=True)


def stitch(clips_folder: str, output: str, width: int, height: int, fps: int, music: str | None = None):
    if not shutil.which("ffmpeg"):
        raise RuntimeError("ffmpeg not found; install it before stitching")
    if min(width, height, fps) <= 0 or width % 2 or height % 2:
        raise ValueError("width/height must be positive even integers and fps must be positive")
    clip_paths = sorted(pathlib.Path(clips_folder).glob("*.mp4"))
    if not clip_paths:
        raise ValueError("No MP4 files in clips folder")
    output_path = pathlib.Path(output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="realframe-") as temp:
        tmp = pathlib.Path(temp)
        normalized = []
        filt = (f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2,"
                f"fps={fps},format=yuv420p,setsar=1")
        for i, input_path in enumerate(clip_paths):
            norm = tmp / f"clip_{i:05d}.mp4"
            print(f"Normalizing clip {i+1}/{len(clip_paths)}: {input_path.name}")
            run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(input_path),
                 "-vf", filt, "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                 "-movflags", "+faststart", str(norm)])
            normalized.append(norm)
        concat_manifest = tmp / "files.txt"
        concat_manifest.write_text("".join(f"file '{p.name}'\n" for p in normalized), encoding="utf-8")
        merged = tmp / "merged.mp4"
        run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0",
             "-i", str(concat_manifest), "-c", "copy", "-movflags", "+faststart", str(merged)])
        if music:
            # Audio loop runs only as long as video. Music not required for image/video generation.
            run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(merged),
                 "-stream_loop", "-1", "-i", str(pathlib.Path(music).resolve()),
                 "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "160k",
                 "-shortest", "-movflags", "+faststart", str(output_path)])
        else:
            shutil.copyfile(merged, output_path)
    print(f"Merged {len(clip_paths)} clips -> {output_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--clips", required=True, help="Folder containing numbered MP4 clips")
    parser.add_argument("--output", required=True)
    parser.add_argument("--width", type=int, default=720)
    parser.add_argument("--height", type=int, default=1280)
    parser.add_argument("--fps", type=int, default=24)
    parser.add_argument("--music", help="Optional background audio file")
    a = parser.parse_args()
    stitch(a.clips, a.output, a.width, a.height, a.fps, a.music)


if __name__ == "__main__":
    main()
