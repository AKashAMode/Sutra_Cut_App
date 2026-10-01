from __future__ import annotations

import logging
import os
from functools import lru_cache

log = logging.getLogger("worker.whisper")

WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "tiny")
WHISPER_DEVICE = os.environ.get("WHISPER_DEVICE", "cpu")
WHISPER_COMPUTE = os.environ.get("WHISPER_COMPUTE", "int8")


@lru_cache(maxsize=1)
def _model():
    from faster_whisper import WhisperModel

    log.info("Loading faster-whisper model=%s device=%s", WHISPER_MODEL, WHISPER_DEVICE)
    return WhisperModel(WHISPER_MODEL, device=WHISPER_DEVICE, compute_type=WHISPER_COMPUTE)


def transcribe(media_path: str, language: str = "") -> dict:
    try:
        model = _model()
        kwargs = {
            "word_timestamps": True,
            "vad_filter": True,
            "beam_size": 1,
        }
        if language:
            kwargs["language"] = language
        segments_iter, info = model.transcribe(media_path, **kwargs)
        detected = getattr(info, "language", language or "en")
        duration = getattr(info, "duration", None)
        segments = []
        for item in segments_iter:
            words = []
            for word in item.words or []:
                words.append(
                    {
                        "word": word.word.strip(),
                        "start": float(word.start or item.start or 0),
                        "end": float(word.end or item.end or 0),
                    }
                )
            text = (item.text or "").strip()
            if not text:
                continue
            segments.append(
                {
                    "start": float(item.start or 0),
                    "end": float(item.end or 0),
                    "text": text,
                    "language": detected,
                    "words": words,
                }
            )
    except Exception as exc:
        log.warning("faster-whisper unavailable, using caption fallback: %s", exc)
        from services.ffmpeg_service import probe

        info = probe(media_path)
        duration = info.get("duration") or 4.0
        detected = language or "mixed"
        segments = _fallback_segments(duration, detected)
    if not segments:
        segments = [
            {
                "start": 0.0,
                "end": float(duration or 1.0),
                "text": "No speech detected",
                "language": detected,
                "words": [],
            }
        ]
    log.info(
        "stage=transcribe input=%s language=%s segments=%s duration=%s",
        media_path,
        detected,
        len(segments),
        duration,
    )
    return {"language": detected, "duration": duration, "segments": segments}


def _fallback_segments(duration: float, language: str) -> list[dict]:
    chunk = 4.0
    segments = []
    start = 0.0
    index = 1
    while start < duration:
        end = min(duration, start + chunk)
        segments.append(
            {
                "start": round(start, 2),
                "end": round(end, 2),
                "text": f"Segment {index} — edit this caption",
                "language": language,
                "words": [],
            }
        )
        start = end
        index += 1
    return segments


def available() -> bool:
    try:
        from faster_whisper import WhisperModel  # noqa: F401

        return True
    except Exception:
        return False
