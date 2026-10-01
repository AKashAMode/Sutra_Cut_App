from __future__ import annotations

import logging
import os
from pathlib import Path

import httpx

from services.ffmpeg_service import STORAGE_ROOT, cleanup

log = logging.getLogger("worker.stock")

PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY", "")
PIXABAY_API_KEY = os.environ.get("PIXABAY_API_KEY", "")


def _get(url: str, headers: dict | None = None, params: dict | None = None) -> dict | None:
    try:
        with httpx.Client(timeout=12.0, follow_redirects=True) as client:
            response = client.get(url, headers=headers, params=params)
            if response.status_code >= 400:
                log.warning("stock GET %s status=%s", url, response.status_code)
                return None
            return response.json()
    except Exception as exc:
        log.warning("stock GET failed %s: %s", url, exc)
        return None


def search_pexels(keyword: str) -> str | None:
    if not PEXELS_API_KEY:
        return None
    data = _get(
        "https://api.pexels.com/videos/search",
        headers={"Authorization": PEXELS_API_KEY},
        params={"query": keyword, "per_page": 3, "orientation": "landscape"},
    )
    if not data:
        return None
    videos = data.get("videos") or []
    for video in videos:
        files = sorted(video.get("video_files") or [], key=lambda item: item.get("width") or 0)
        for item in files:
            link = item.get("link")
            if link:
                return link
    return None


def search_pixabay(keyword: str) -> str | None:
    if not PIXABAY_API_KEY:
        return None
    data = _get(
        "https://pixabay.com/api/videos/",
        params={"key": PIXABAY_API_KEY, "q": keyword, "per_page": 3},
    )
    if not data:
        return None
    hits = data.get("hits") or []
    for hit in hits:
        videos = hit.get("videos") or {}
        for quality in ("tiny", "small", "medium"):
            item = videos.get(quality) or {}
            url = item.get("url")
            if url:
                return url
    return None


def find_broll(keyword: str) -> tuple[str | None, str]:
    url = search_pexels(keyword)
    if url:
        return url, "pexels"
    url = search_pexels(keyword)
    if url:
        return url, "pexels"
    url = search_pixabay(keyword)
    if url:
        return url, "pixabay"
    url = search_pixabay(keyword)
    if url:
        return url, "pixabay"
    return None, "placeholder"


def download_asset(url: str, dest: Path) -> Path | None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        with httpx.Client(timeout=20.0, follow_redirects=True) as client:
            with client.stream("GET", url) as response:
                if response.status_code >= 400:
                    return None
                with dest.open("wb") as handle:
                    for chunk in response.iter_bytes(chunk_size=1024 * 64):
                        handle.write(chunk)
        if dest.stat().st_size < 1024:
            cleanup(dest)
            return None
        return dest
    except Exception as exc:
        log.warning("asset download failed %s: %s", url, exc)
        cleanup(dest)
        return None


def cache_dir(project_id: str) -> Path:
    path = STORAGE_ROOT / project_id / "temp"
    path.mkdir(parents=True, exist_ok=True)
    return path
