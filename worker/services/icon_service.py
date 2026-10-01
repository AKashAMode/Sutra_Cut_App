from __future__ import annotations

import logging
from pathlib import Path

import httpx

from services.ffmpeg_service import cleanup

log = logging.getLogger("worker.icon")

ICONIFY_API = "https://api.iconify.design"


def search_iconify(keyword: str) -> str | None:
    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.get(
                f"{ICONIFY_API}/search",
                params={"query": keyword, "limit": 1},
            )
            if response.status_code >= 400:
                return None
            data = response.json()
            icons = data.get("icons") or []
            if not icons:
                return None
            icon = icons[0]
            return f"{ICONIFY_API}/{icon}.svg?color=%23ffffff"
    except Exception as exc:
        log.warning("iconify search failed: %s", exc)
        return None


def download_icon(url: str, dest: Path) -> Path | None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        with httpx.Client(timeout=10.0, follow_redirects=True) as client:
            response = client.get(url)
            if response.status_code >= 400 or not response.content:
                return None
            dest.write_bytes(response.content)
            png_path = dest.with_suffix(".png")
            from services.ffmpeg_service import FFMPEG, run

            run(
                [
                    FFMPEG, "-y",
                    "-background", "none",
                    "-i", str(dest),
                    "-vf", "scale=256:256:force_original_aspect_ratio=decrease",
                    str(png_path),
                ],
                timeout=20,
            )
            cleanup(dest)
            return png_path if png_path.exists() else None
    except Exception as exc:
        log.warning("icon download failed: %s", exc)
        cleanup(dest)
        return None
