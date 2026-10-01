from fastapi import APIRouter, HTTPException

from models.schemas import ProbeRequest, ProbeResponse, TranscribeRequest, TranscribeResponse
from services import ffmpeg_service, whisper_service

router = APIRouter()


@router.post("/probe", response_model=ProbeResponse)
def probe(payload: ProbeRequest) -> ProbeResponse:
    result = ffmpeg_service.probe(payload.mediaPath)
    return ProbeResponse(**result)


@router.post("/transcribe", response_model=TranscribeResponse)
def transcribe(payload: TranscribeRequest) -> TranscribeResponse:
    probe_result = ffmpeg_service.probe(payload.mediaPath)
    if not probe_result.get("valid"):
        raise HTTPException(status_code=400, detail=probe_result.get("error") or "Invalid media")
    try:
        result = whisper_service.transcribe(payload.mediaPath, payload.language)
        return TranscribeResponse(**result)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {exc}") from exc
