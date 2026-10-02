from __future__ import annotations

import logging
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from models.schemas import HealthResponse
from routers import match_assets, render_final, render_segment, transcribe
from services import whisper_service

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

app = FastAPI(title="AI Video Auto-Editor Worker", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(transcribe.router)
app.include_router(match_assets.router)
app.include_router(render_segment.router)
app.include_router(render_final.router)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    ffmpeg_ok = shutil.which("ffmpeg") is not None and shutil.which("ffprobe") is not None
    whisper_ok = whisper_service.available()
    return HealthResponse(
        status="ok" if ffmpeg_ok and whisper_ok else "degraded",
        ffmpeg=ffmpeg_ok,
        whisper=whisper_ok,
        details={"model": whisper_service.WHISPER_MODEL},
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=False)
