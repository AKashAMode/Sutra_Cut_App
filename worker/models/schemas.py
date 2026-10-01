from typing import Any

from pydantic import BaseModel, Field


class ProbeRequest(BaseModel):
    mediaPath: str


class ProbeResponse(BaseModel):
    valid: bool
    error: str | None = None
    duration: float | None = None
    width: int | None = None
    height: int | None = None
    codec: str | None = None
    hasAudio: bool = False
    hasVideo: bool = False


class WordStamp(BaseModel):
    word: str
    start: float
    end: float


class TranscriptSegment(BaseModel):
    start: float
    end: float
    text: str
    language: str | None = None
    words: list[WordStamp] = Field(default_factory=list)


class TranscribeRequest(BaseModel):
    mediaPath: str
    language: str = ""


class TranscribeResponse(BaseModel):
    language: str | None = None
    duration: float | None = None
    segments: list[TranscriptSegment] = Field(default_factory=list)


class MatchSegment(BaseModel):
    segmentId: str
    text: str
    start: float
    end: float


class MatchAssetsRequest(BaseModel):
    segments: list[MatchSegment]


class MatchedAsset(BaseModel):
    segmentId: str
    keyword: str
    visualType: str
    assetSource: str
    assetUrl: str | None = None


class MatchAssetsResponse(BaseModel):
    assets: list[MatchedAsset]


class RenderSegmentRequest(BaseModel):
    projectId: str
    segmentId: str
    sourcePath: str
    start: float
    end: float
    captionText: str
    visualType: str = "caption"
    assetUrl: str = ""
    keyword: str = ""


class RenderSegmentResponse(BaseModel):
    clipPath: str
    captionOnly: bool = False


class FinalClip(BaseModel):
    clipPath: str
    captionText: str = ""
    start: float = 0
    end: float = 0


class RenderFinalRequest(BaseModel):
    projectId: str
    clips: list[FinalClip]
    sourcePath: str = ""


class RenderFinalResponse(BaseModel):
    outputPath: str
    duration: float | None = None


class HealthResponse(BaseModel):
    status: str
    ffmpeg: bool
    whisper: bool
    details: dict[str, Any] = Field(default_factory=dict)
