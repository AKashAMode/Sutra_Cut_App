package com.videoapp.model;

public enum JobStatus {
    QUEUED,
    VALIDATING,
    TRANSCRIBING,
    MATCHING_ASSETS,
    RENDERING_SEGMENTS,
    RENDERING_FINAL,
    COMPLETED,
    FAILED
}
