package com.videoapp.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.videoapp.exception.ApiException;
import com.videoapp.model.EdlEntry;
import com.videoapp.model.JobStatus;
import com.videoapp.model.Project;
import com.videoapp.model.RenderJob;
import com.videoapp.model.TranscriptSegment;
import com.videoapp.repository.EdlEntryRepository;
import com.videoapp.repository.JobRepository;
import com.videoapp.repository.ProjectRepository;
import com.videoapp.repository.TranscriptSegmentRepository;

@Service
public class JobQueueService {

    private static final Logger log = LoggerFactory.getLogger(JobQueueService.class);

    private final ProjectRepository projectRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final EdlEntryRepository edlEntryRepository;
    private final JobRepository jobRepository;
    private final PythonWorkerClient workerClient;

    public JobQueueService(
            ProjectRepository projectRepository,
            TranscriptSegmentRepository transcriptSegmentRepository,
            EdlEntryRepository edlEntryRepository,
            JobRepository jobRepository,
            PythonWorkerClient workerClient) {
        this.projectRepository = projectRepository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.edlEntryRepository = edlEntryRepository;
        this.jobRepository = jobRepository;
        this.workerClient = workerClient;
    }

    @Transactional
    public RenderJob enqueueRender(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        List<EdlEntry> entries = edlEntryRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        if (entries.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EDL is empty. Generate assets from the transcript first.");
        }
        RenderJob job = new RenderJob();
        job.setProjectId(projectId);
        job.setStatus(JobStatus.QUEUED);
        job.setMessage("Render job queued");
        job.setProgress(5);
        job = jobRepository.save(job);
        project.setStatus(JobStatus.QUEUED);
        project.setStatusMessage("Render job queued");
        project.setProgress(5);
        projectRepository.save(project);
        return job;
    }

    @Async("jobExecutor")
    public void runTranscription(String projectId) {
        try {
            Project project = requireProject(projectId);
            updateProject(project, JobStatus.VALIDATING, "Validating media with ffprobe", 10);
            PythonWorkerClient.ProbeResult probe = workerClient.probe(project.getSourcePath());
            if (probe == null || !probe.valid()) {
                failProject(projectId, probe == null ? "ffprobe failed" : probe.error());
                return;
            }
            project.setDurationSeconds(probe.duration());
            project.setWidth(probe.width());
            project.setHeight(probe.height());
            updateProject(project, JobStatus.TRANSCRIBING, "Transcribing audio", 25);

            PythonWorkerClient.TranscribeResult result = workerClient.transcribe(project.getSourcePath(), "");
            replaceTranscript(project, result);
            updateProject(project, JobStatus.COMPLETED, "Transcription complete", 100);
        } catch (Exception ex) {
            log.error("Transcription job failed for {}", projectId, ex);
            failProject(projectId, ex.getMessage());
        }
    }

    @Async("jobExecutor")
    public void runAssetMatch(String projectId) {
        try {
            Project project = requireProject(projectId);
            updateProject(project, JobStatus.MATCHING_ASSETS, "Matching b-roll and icons", 40);
            List<TranscriptSegment> segments = transcriptSegmentRepository.findByProjectIdOrderByStartTimeAsc(projectId);
            if (segments.isEmpty()) {
                failProject(projectId, "No transcript segments to match");
                return;
            }
            List<Map<String, Object>> payload = segments.stream()
                    .map(segment -> Map.<String, Object>of(
                            "segmentId", segment.getId(),
                            "text", segment.getText(),
                            "start", segment.getStartTime(),
                            "end", segment.getEndTime()))
                    .toList();
            PythonWorkerClient.MatchAssetsResult matched = workerClient.matchAssets(payload);
            replaceEdl(project, segments, matched);
            updateProject(project, JobStatus.COMPLETED, "EDL generated", 100);
        } catch (Exception ex) {
            log.error("Asset match failed for {}", projectId, ex);
            failProject(projectId, ex.getMessage());
        }
    }

    @Async("jobExecutor")
    public void runFinalRender(String jobId) {
        RenderJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        String projectId = job.getProjectId();
        try {
            Project project = requireProject(projectId);
            updateJob(job, JobStatus.RENDERING_SEGMENTS, "Rendering dirty segments", 20);
            updateProject(project, JobStatus.RENDERING_SEGMENTS, "Rendering dirty segments", 20);

            List<EdlEntry> entries = edlEntryRepository.findByProjectIdOrderBySortOrderAsc(projectId);
            int dirtyCount = (int) entries.stream().filter(EdlEntry::isDirty).count();
            int done = 0;
            for (EdlEntry entry : entries) {
                if (!entry.isDirty() && entry.getClipPath() != null && !entry.getClipPath().isBlank()) {
                    continue;
                }
                Map<String, Object> payload = Map.of(
                        "projectId", projectId,
                        "segmentId", entry.getId(),
                        "sourcePath", project.getSourcePath(),
                        "start", entry.getStartTime(),
                        "end", entry.getEndTime(),
                        "captionText", entry.getCaptionText(),
                        "visualType", entry.getVisualType() == null ? "caption" : entry.getVisualType(),
                        "assetUrl", entry.getAssetUrl() == null ? "" : entry.getAssetUrl(),
                        "keyword", entry.getKeyword() == null ? "" : entry.getKeyword());
                PythonWorkerClient.RenderSegmentResult rendered = workerClient.renderSegment(payload);
                entry.setClipPath(rendered.clipPath());
                if (rendered.captionOnly()) {
                    entry.setVisualType("caption");
                    entry.setAssetSource("placeholder");
                }
                entry.setDirty(false);
                edlEntryRepository.save(entry);
                done++;
                int progress = 20 + (int) ((done / (double) Math.max(dirtyCount, 1)) * 50);
                updateJob(job, JobStatus.RENDERING_SEGMENTS, "Rendered segment " + done, progress);
            }

            updateJob(job, JobStatus.RENDERING_FINAL, "Concatenating clips and burning captions", 80);
            updateProject(project, JobStatus.RENDERING_FINAL, "Concatenating clips and burning captions", 80);

            List<Map<String, Object>> clips = edlEntryRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                    .map(entry -> Map.<String, Object>of(
                            "clipPath", entry.getClipPath() == null ? "" : entry.getClipPath(),
                            "captionText", entry.getCaptionText(),
                            "start", entry.getStartTime(),
                            "end", entry.getEndTime()))
                    .toList();
            PythonWorkerClient.RenderFinalResult finalResult = workerClient.renderFinal(Map.of(
                    "projectId", projectId,
                    "clips", clips,
                    "sourcePath", project.getSourcePath()));

            project.setFinalVideoPath(finalResult.outputPath());
            updateProject(project, JobStatus.COMPLETED, "Final video ready", 100);
            job.setOutputPath(finalResult.outputPath());
            updateJob(job, JobStatus.COMPLETED, "Final video ready", 100);
        } catch (Exception ex) {
            log.error("Final render failed for job {}", jobId, ex);
            failJob(jobId, ex.getMessage());
            failProject(projectId, ex.getMessage());
        }
    }

    private void replaceTranscript(Project project, PythonWorkerClient.TranscribeResult result) {
        List<TranscriptSegment> existing = transcriptSegmentRepository.findByProjectIdOrderByStartTimeAsc(project.getId());
        transcriptSegmentRepository.deleteAll(existing);
        if (result == null || result.segments() == null) {
            return;
        }
        for (PythonWorkerClient.TranscriptSegmentResult item : result.segments()) {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setProject(project);
            segment.setStartTime(item.start());
            segment.setEndTime(item.end());
            segment.setText(item.text() == null ? "" : item.text().trim());
            segment.setLanguage(item.language() == null ? result.language() : item.language());
            if (item.words() != null) {
                StringBuilder words = new StringBuilder("[");
                for (int i = 0; i < item.words().size(); i++) {
                    PythonWorkerClient.WordStamp word = item.words().get(i);
                    if (i > 0) {
                        words.append(",");
                    }
                    words.append("{\"word\":\"")
                            .append(escape(word.word()))
                            .append("\",\"start\":")
                            .append(word.start())
                            .append(",\"end\":")
                            .append(word.end())
                            .append("}");
                }
                words.append("]");
                segment.setWordsJson(words.toString());
            }
            transcriptSegmentRepository.save(segment);
        }
    }

    private void replaceEdl(
            Project project,
            List<TranscriptSegment> segments,
            PythonWorkerClient.MatchAssetsResult matched) {
        List<EdlEntry> existing = edlEntryRepository.findByProjectIdOrderBySortOrderAsc(project.getId());
        edlEntryRepository.deleteAll(existing);
        Map<String, PythonWorkerClient.MatchedAsset> bySegment = Map.of();
        if (matched != null && matched.assets() != null) {
            bySegment = matched.assets().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            PythonWorkerClient.MatchedAsset::segmentId,
                            asset -> asset,
                            (left, right) -> left));
        }
        int order = 0;
        for (TranscriptSegment segment : segments) {
            EdlEntry entry = new EdlEntry();
            entry.setProject(project);
            entry.setSortOrder(order++);
            entry.setStartTime(segment.getStartTime());
            entry.setEndTime(segment.getEndTime());
            entry.setTranscriptText(segment.getText());
            entry.setCaptionText(segment.getText());
            PythonWorkerClient.MatchedAsset asset = bySegment.get(segment.getId());
            if (asset != null) {
                entry.setKeyword(asset.keyword());
                entry.setVisualType(asset.visualType() == null ? "caption" : asset.visualType());
                entry.setAssetSource(asset.assetSource());
                entry.setAssetUrl(asset.assetUrl());
            } else {
                entry.setKeyword("caption");
                entry.setVisualType("caption");
                entry.setAssetSource("placeholder");
            }
            entry.setDirty(true);
            edlEntryRepository.save(entry);
        }
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void updateProject(Project project, JobStatus status, String message, int progress) {
        project.setStatus(status);
        project.setStatusMessage(message);
        project.setProgress(progress);
        projectRepository.save(project);
        log.info("project={} stage={} progress={} message={}", project.getId(), status, progress, message);
    }

    private void updateJob(RenderJob job, JobStatus status, String message, int progress) {
        job.setStatus(status);
        job.setMessage(message);
        job.setProgress(progress);
        jobRepository.save(job);
    }

    private void failProject(String projectId, String message) {
        projectRepository.findById(projectId).ifPresent(project -> {
            project.setStatus(JobStatus.FAILED);
            project.setStatusMessage(truncate(message));
            projectRepository.save(project);
        });
    }

    private void failJob(String jobId, String message) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setMessage(truncate(message));
            jobRepository.save(job);
        });
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 1800 ? message.substring(0, 1800) : message;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
