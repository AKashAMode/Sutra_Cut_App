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
        log.exception("faster-whisper transcription failed for %s", media_path)
        raise RuntimeError(f"Whisper transcription failed: {exc}") from exc
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


def available() -> bool:
    try:
        from faster_whisper import WhisperModel  # noqa: F401

        return True
    except Exception:
        return False
