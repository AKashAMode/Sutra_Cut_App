from fastapi import APIRouter, HTTPException

from models.schemas import RenderSegmentRequest, RenderSegmentResponse
from services.ffmpeg_service import project_dir, render_segment_clip
from services.icon_service import download_icon
from services.stock_service import cache_dir, download_asset

router = APIRouter()


@router.post("/render-segment", response_model=RenderSegmentResponse)
def render_segment(payload: RenderSegmentRequest) -> RenderSegmentResponse:
    project = project_dir(payload.projectId)
    output = project / "clips" / f"{payload.segmentId}.mp4"
    asset_path = None
    if payload.assetUrl:
        temp_dir = cache_dir(payload.projectId)
        if payload.visualType == "icon":
            asset_path = download_icon(payload.assetUrl, temp_dir / f"{payload.segmentId}.svg")
        else:
            suffix = ".mp4"
            if ".jpg" in payload.assetUrl or ".png" in payload.assetUrl:
                suffix = ".jpg"
            downloaded = download_asset(payload.assetUrl, temp_dir / f"{payload.segmentId}{suffix}")
            asset_path = str(downloaded) if downloaded else None
        if asset_path:
            asset_path = str(asset_path)
    try:
        caption_only = render_segment_clip(
            source_path=payload.sourcePath,
            start=payload.start,
            end=payload.end,
            caption_text=payload.captionText,
            output_path=output,
            asset_path=asset_path,
            visual_type=payload.visualType,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Segment render failed: {exc}") from exc
    return RenderSegmentResponse(clipPath=str(output), captionOnly=caption_only)
