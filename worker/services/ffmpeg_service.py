from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
from pathlib import Path

log = logging.getLogger("worker.ffmpeg")

STORAGE_ROOT = Path(os.environ.get("STORAGE_ROOT", str(Path(__file__).resolve().parents[2] / "storage")))
FFMPEG = shutil.which("ffmpeg") or "ffmpeg"
FFPROBE = shutil.which("ffprobe") or "ffprobe"


def run(cmd: list[str], timeout: int = 300) -> subprocess.CompletedProcess[str]:
    log.info("cmd=%s", " ".join(cmd))
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        log.error("ffmpeg failed code=%s stderr=%s", result.returncode, result.stderr[-2000:])
        raise RuntimeError(result.stderr.strip() or f"Command failed: {' '.join(cmd)}")
    return result


def probe(media_path: str) -> dict:
    if not Path(media_path).exists():
        return {"valid": False, "error": f"File not found: {media_path}"}
    try:
        result = subprocess.run(
            [
                FFPROBE,
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                media_path,
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except FileNotFoundError:
        return {"valid": False, "error": "ffprobe is not installed"}
    if result.returncode != 0:
        return {"valid": False, "error": result.stderr.strip() or "Corrupt or unsupported media"}
    try:
        data = json.loads(result.stdout or "{}")
    except json.JSONDecodeError:
        return {"valid": False, "error": "Unable to parse ffprobe output"}

    streams = data.get("streams") or []
    fmt = data.get("format") or {}
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    duration = None
    if fmt.get("duration"):
        duration = float(fmt["duration"])
    elif video and video.get("duration"):
        duration = float(video["duration"])
    width = int(video["width"]) if video and video.get("width") else None
    height = int(video["height"]) if video and video.get("height") else None
    codec = (video or audio or {}).get("codec_name")
    size = Path(media_path).stat().st_size
    log.info(
        "stage=probe input=%s size=%s duration=%s codec=%s",
        media_path,
        size,
        duration,
        codec,
    )
    return {
        "valid": True,
        "error": None,
        "duration": duration,
        "width": width,
        "height": height,
        "codec": codec,
        "hasAudio": audio is not None,
        "hasVideo": video is not None,
    }


def project_dir(project_id: str) -> Path:
    path = STORAGE_ROOT / project_id
    path.mkdir(parents=True, exist_ok=True)
    (path / "clips").mkdir(exist_ok=True)
    (path / "temp").mkdir(exist_ok=True)
    return path


def cleanup(path: str | Path) -> None:
    try:
        Path(path).unlink(missing_ok=True)
    except OSError as exc:
        log.warning("temp cleanup failed %s: %s", path, exc)


def escape_ass(text: str) -> str:
    return (
        (text or "")
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("\n", "\\N")
    )


def write_ass(captions: list[tuple[float, float, str]], dest: Path, width: int = 1280, height: int = 720) -> Path:
    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {width}
PlayResY: {height}
WrapStyle: 2
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Noto Sans,54,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,3,0,2,40,40,48,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    lines = [header]
    for start, end, text in captions:
        if not text:
            continue
        lines.append(
            f"Dialogue: 0,{_ass_time(start)},{_ass_time(end)},Default,,0,0,0,,{escape_ass(text)}\n"
        )
    dest.write_text("".join(lines), encoding="utf-8")
    return dest


def _ass_time(seconds: float) -> str:
    seconds = max(0.0, float(seconds))
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = seconds % 60
    return f"{hours}:{minutes:02d}:{secs:05.2f}"


def render_segment_clip(
    source_path: str,
    start: float,
    end: float,
    caption_text: str,
    output_path: Path,
    asset_path: str | None = None,
    visual_type: str = "caption",
) -> bool:
    duration = max(0.4, float(end) - float(start))
    width, height = 1280, 720
    output_path.parent.mkdir(parents=True, exist_ok=True)
    ass_path = output_path.with_suffix(".ass")
    write_ass([(0, duration, caption_text)], ass_path, width, height)
    caption_only = False
    ass_filter = f"ass={_ffmpeg_path(ass_path)}"

    try:
        if asset_path and Path(asset_path).exists() and visual_type in {"broll", "icon"}:
            _render_with_overlay(source_path, start, duration, asset_path, visual_type, ass_filter, output_path)
        else:
            caption_only = True
            _render_caption_only(source_path, start, duration, ass_filter, output_path)
    finally:
        cleanup(ass_path)
        if asset_path and str(asset_path).startswith(str(STORAGE_ROOT)) and "temp" in str(asset_path):
            cleanup(asset_path)

    size = output_path.stat().st_size if output_path.exists() else 0
    log.info(
        "stage=render-segment output=%s size=%s duration=%s caption_only=%s",
        output_path,
        size,
        duration,
        caption_only,
    )
    return caption_only


def _render_caption_only(source_path: str, start: float, duration: float, ass_filter: str, output_path: Path) -> None:
    has_video = probe(source_path).get("hasVideo")
    if has_video:
        cmd = [
            FFMPEG, "-y",
            "-ss", f"{start:.3f}",
            "-t", f"{duration:.3f}",
            "-i", source_path,
            "-vf", f"scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,fps=30,{ass_filter}",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-shortest",
            str(output_path),
        ]
    else:
        cmd = [
            FFMPEG, "-y",
            "-f", "lavfi",
            "-i", f"color=c=0x111827:s=1280x720:d={duration:.3f}:r=30",
            "-ss", f"{start:.3f}",
            "-t", f"{duration:.3f}",
            "-i", source_path,
            "-filter_complex", f"[0:v]{ass_filter}[v]",
            "-map", "[v]",
            "-map", "1:a:0?",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-shortest",
            str(output_path),
        ]
    run(cmd, timeout=180)


def _render_with_overlay(
    source_path: str,
    start: float,
    duration: float,
    asset_path: str,
    visual_type: str,
    ass_filter: str,
    output_path: Path,
) -> None:
    if visual_type == "icon":
        filter_complex = (
            "[1:v]scale=220:220:force_original_aspect_ratio=decrease[icon];"
            f"[0:v]scale=1280:720,fps=30[base];[base][icon]overlay=W-w-48:48,{ass_filter}[v]"
        )
    else:
        filter_complex = (
            f"[1:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,fps=30[bg];"
            f"[bg]{ass_filter}[v]"
        )
    cmd = [
        FFMPEG, "-y",
        "-ss", f"{start:.3f}",
        "-t", f"{duration:.3f}",
        "-i", source_path,
        "-stream_loop", "-1",
        "-t", f"{duration:.3f}",
        "-i", asset_path,
        "-filter_complex", filter_complex,
        "-map", "[v]",
        "-map", "0:a:0?",
        "-c:v", "libx264",
        "-preset", "veryfast",
        "-crf", "23",
        "-c:a", "aac",
        "-b:a", "128k",
        "-shortest",
        str(output_path),
    ]
    run(cmd, timeout=180)


def concatenate(clip_paths: list[str], output_path: Path) -> float | None:
    valid = [p for p in clip_paths if p and Path(p).exists()]
    if not valid:
        raise RuntimeError("No rendered clips to concatenate")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    list_file = output_path.with_suffix(".txt")
    list_file.write_text("".join(f"file '{p}'\n" for p in valid), encoding="utf-8")
    try:
        run(
            [
                FFMPEG, "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", str(list_file),
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "22",
                "-c:a", "aac",
                "-movflags", "+faststart",
                str(output_path),
            ],
            timeout=300,
        )
    finally:
        cleanup(list_file)
    info = probe(str(output_path))
    log.info(
        "stage=render-final output=%s size=%s duration=%s",
        output_path,
        output_path.stat().st_size if output_path.exists() else 0,
        info.get("duration"),
    )
    return info.get("duration")


def _ffmpeg_path(path: Path) -> str:
    return str(path).replace("\\", "/").replace(":", "\\:").replace("'", "\\'")
