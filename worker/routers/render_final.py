from fastapi import APIRouter, HTTPException

from models.schemas import RenderFinalRequest, RenderFinalResponse
from services.ffmpeg_service import concatenate, project_dir

router = APIRouter()


@router.post("/render-final", response_model=RenderFinalResponse)
def render_final(payload: RenderFinalRequest) -> RenderFinalResponse:
    project = project_dir(payload.projectId)
    output = project / "final.mp4"
    try:
        duration = concatenate([clip.clipPath for clip in payload.clips], output)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Final render failed: {exc}") from exc
    return RenderFinalResponse(outputPath=str(output), duration=duration)
