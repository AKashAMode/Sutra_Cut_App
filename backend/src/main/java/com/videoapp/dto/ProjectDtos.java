package com.videoapp.dto;

import java.time.Instant;
import java.util.List;

import com.videoapp.model.EdlEntry;
import com.videoapp.model.JobStatus;
import com.videoapp.model.Project;
import com.videoapp.model.RenderJob;
import com.videoapp.model.TranscriptSegment;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectSummary(
            String id,
            String name,
            String originalFilename,
            Double durationSeconds,
            JobStatus status,
            String statusMessage,
            Integer progress,
            boolean hasFinalVideo,
            Instant createdAt,
            Instant updatedAt) {
        public static ProjectSummary from(Project project) {
            return new ProjectSummary(
                    project.getId(),
                    project.getName(),
                    project.getOriginalFilename(),
                    project.getDurationSeconds(),
                    project.getStatus(),
                    project.getStatusMessage(),
                    project.getProgress(),
                    project.getFinalVideoPath() != null && !project.getFinalVideoPath().isBlank(),
                    project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }

    public record TranscriptWord(String word, double start, double end) {
    }

    public record TranscriptSegmentDto(
            String id,
            double start,
            double end,
            String text,
            String language,
            List<TranscriptWord> words) {
        public static TranscriptSegmentDto from(TranscriptSegment segment, List<TranscriptWord> words) {
            return new TranscriptSegmentDto(
                    segment.getId(),
                    segment.getStartTime(),
                    segment.getEndTime(),
                    segment.getText(),
                    segment.getLanguage(),
                    words);
        }
    }

    public record TranscriptDto(String projectId, List<TranscriptSegmentDto> segments) {
    }

    public record TranscriptUpdateRequest(List<TranscriptSegmentDto> segments) {
    }

    public record EdlSegmentDto(
            String id,
            double start,
            double end,
            String transcriptText,
            String captionText,
            String visualType,
            String assetSource,
            String assetUrl,
            String keyword,
            boolean dirty) {
        public static EdlSegmentDto from(EdlEntry entry) {
            return new EdlSegmentDto(
                    entry.getId(),
                    entry.getStartTime(),
                    entry.getEndTime(),
                    entry.getTranscriptText(),
                    entry.getCaptionText(),
                    entry.getVisualType(),
                    entry.getAssetSource(),
                    entry.getAssetUrl(),
                    entry.getKeyword(),
                    entry.isDirty());
        }
    }

    public record EdlDto(String projectId, List<EdlSegmentDto> segments) {
    }

    public record EdlUpdateRequest(List<EdlSegmentDto> segments) {
    }

    public record JobStatusDto(
            String jobId,
            String projectId,
            JobStatus status,
            String message,
            Integer progress,
            boolean downloadReady) {
        public static JobStatusDto from(RenderJob job, boolean downloadReady) {
            return new JobStatusDto(
                    job.getId(),
                    job.getProjectId(),
                    job.getStatus(),
                    job.getMessage(),
                    job.getProgress(),
                    downloadReady);
        }

        public static JobStatusDto fromProject(Project project) {
            return new JobStatusDto(
                    null,
                    project.getId(),
                    project.getStatus(),
                    project.getStatusMessage(),
                    project.getProgress(),
                    project.getFinalVideoPath() != null && !project.getFinalVideoPath().isBlank());
        }
    }

    public record RenameRequest(String name) {
    }
}
