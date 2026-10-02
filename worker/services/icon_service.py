from __future__ import annotations

import logging
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

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
            icon_path = icon.replace(":", "/", 1)
            return f"{ICONIFY_API}/{icon_path}.svg?color=%23ffffff"
    except Exception as exc:
        log.warning("iconify search failed: %s", exc)
        return None


def download_icon(url: str, dest: Path) -> Path | None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    url_parts = urlsplit(url)
    path_parts = url_parts.path.rsplit("/", 1)
    filename = path_parts[-1]
    if filename.endswith(".svg") and ":" in filename:
        collection, icon_name = filename[:-4].rsplit(":", 1)
        parent = path_parts[0] if len(path_parts) == 2 else ""
        normalized_path = f"{parent}/{collection}/{icon_name}.svg"
        url = urlunsplit(url_parts._replace(path=normalized_path))
    try:
        with httpx.Client(timeout=10.0, follow_redirects=True) as client:
            response = client.get(url)
            if response.status_code >= 400 or not response.content:
                return None
            png_path = dest.with_suffix(".png")
            import resvg_py

            png_path.write_bytes(
                resvg_py.svg_to_bytes(
                    svg_string=response.text,
                    width=256,
                    height=256,
                )
            )
            return png_path if png_path.exists() else None
    except Exception as exc:
        log.warning("icon download failed: %s", exc)
        cleanup(dest)
        return None
