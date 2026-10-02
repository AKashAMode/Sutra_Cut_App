package com.videoapp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.videoapp.dto.ProjectDtos.EdlDto;
import com.videoapp.dto.ProjectDtos.EdlSegmentDto;
import com.videoapp.dto.ProjectDtos.EdlUpdateRequest;
import com.videoapp.dto.ProjectDtos.JobStatusDto;
import com.videoapp.dto.ProjectDtos.ProjectSummary;
import com.videoapp.dto.ProjectDtos.TranscriptDto;
import com.videoapp.dto.ProjectDtos.TranscriptSegmentDto;
import com.videoapp.dto.ProjectDtos.TranscriptUpdateRequest;
import com.videoapp.dto.ProjectDtos.TranscriptWord;
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
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".mp4", ".mov", ".mkv", ".webm", ".mp3", ".wav", ".m4a", ".aac", ".ogg");

    private final ProjectRepository projectRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final EdlEntryRepository edlEntryRepository;
    private final JobRepository jobRepository;
    private final JobQueueService jobQueueService;
    private final AssetFallbackService assetFallbackService;
    private final JsonMapper jsonMapper;
    private final Path storageRoot;

    public ProjectService(
            ProjectRepository projectRepository,
            TranscriptSegmentRepository transcriptSegmentRepository,
            EdlEntryRepository edlEntryRepository,
            JobRepository jobRepository,
            JobQueueService jobQueueService,
            AssetFallbackService assetFallbackService,
            JsonMapper jsonMapper,
            @Value("${app.storage.root}") String storageRoot) {
        this.projectRepository = projectRepository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.edlEntryRepository = edlEntryRepository;
        this.jobRepository = jobRepository;
        this.jobQueueService = jobQueueService;
        this.assetFallbackService = assetFallbackService;
        this.jsonMapper = jsonMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public List<ProjectSummary> listProjects() {
        return projectRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(ProjectSummary::from)
                .toList();
    }

    public ProjectSummary getProject(String id) {
        return ProjectSummary.from(requireProject(id));
    }

    @Transactional
    public ProjectSummary rename(String id, String name) {
        Project project = requireProject(id);
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        project.setName(name.trim());
        return ProjectSummary.from(projectRepository.save(project));
    }

    public ProjectSummary upload(MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A media file is required");
        }
        String original = file.getOriginalFilename() == null ? "upload.mp4" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported media type: " + extension);
        }
        Project project = new Project();
        project.setName(name == null || name.isBlank() ? stripExtension(original) : name.trim());
        project.setOriginalFilename(original);
        project.setContentType(file.getContentType());
        project.setFileSizeBytes(file.getSize());
        project.setStatus(JobStatus.QUEUED);
        project.setStatusMessage("Upload received");
        project.setProgress(5);
        project = projectRepository.save(project);

        Path projectDir = storageRoot.resolve(project.getId());
        try {
            Files.createDirectories(projectDir);
            Path destination = projectDir.resolve("source" + extension);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            project.setSourcePath(destination.toString());
            projectRepository.save(project);
            log.info("stage=upload project={} size={} path={}", project.getId(), file.getSize(), destination);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store upload: " + ex.getMessage());
        }
        jobQueueService.runTranscription(project.getId());
        return ProjectSummary.from(project);
    }

    public TranscriptDto getTranscript(String projectId) {
        requireProject(projectId);
        List<TranscriptSegmentDto> segments = transcriptSegmentRepository
                .findByProjectIdOrderByStartTimeAsc(projectId)
                .stream()
                .map(this::toTranscriptDto)
                .toList();
        return new TranscriptDto(projectId, segments);
    }

    @Transactional
    public TranscriptDto updateTranscript(String projectId, TranscriptUpdateRequest request) {
        Project project = requireProject(projectId);
        if (request == null || request.segments() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transcript segments are required");
        }
        for (TranscriptSegmentDto incoming : request.segments()) {
            if (incoming.id() == null) {
                continue;
            }
            TranscriptSegment segment = transcriptSegmentRepository.findById(incoming.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Segment not found: " + incoming.id()));
            if (!segment.getProject().getId().equals(projectId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Segment does not belong to this project");
            }
            segment.setText(incoming.text() == null ? "" : incoming.text().trim());
            if (incoming.start() >= 0) {
                segment.setStartTime(incoming.start());
            }
            if (incoming.end() > segment.getStartTime()) {
                segment.setEndTime(incoming.end());
            }
            transcriptSegmentRepository.save(segment);
        }
        project.setStatusMessage("Transcript updated");
        projectRepository.save(project);
        return getTranscript(projectId);
    }

    public EdlDto getEdl(String projectId) {
        requireProject(projectId);
        List<EdlSegmentDto> segments = edlEntryRepository.findByProjectIdOrderBySortOrderAsc(projectId)
                .stream()
                .map(EdlSegmentDto::from)
                .toList();
        return new EdlDto(projectId, segments);
    }

    public EdlDto generateEdl(String projectId) {
        requireProject(projectId);
        jobQueueService.runAssetMatch(projectId);
        Project project = requireProject(projectId);
        project.setStatus(JobStatus.MATCHING_ASSETS);
        project.setStatusMessage("Matching visuals");
        project.setProgress(30);
        projectRepository.save(project);
        return getEdl(projectId);
    }

    @Transactional
    public EdlDto updateEdl(String projectId, EdlUpdateRequest request) {
        requireProject(projectId);
        if (request == null || request.segments() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EDL segments are required");
        }
        for (EdlSegmentDto incoming : request.segments()) {
            if (incoming.id() == null) {
                continue;
            }
            EdlEntry entry = edlEntryRepository.findById(incoming.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EDL entry not found: " + incoming.id()));
            if (!entry.getProject().getId().equals(projectId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EDL entry does not belong to this project");
            }
            boolean changed = false;
            if (incoming.captionText() != null && !incoming.captionText().equals(entry.getCaptionText())) {
                entry.setCaptionText(incoming.captionText());
                changed = true;
            }
            if (incoming.transcriptText() != null && !incoming.transcriptText().equals(entry.getTranscriptText())) {
                entry.setTranscriptText(incoming.transcriptText());
                changed = true;
            }
            if (incoming.visualType() != null && !incoming.visualType().equals(entry.getVisualType())) {
                entry.setVisualType(incoming.visualType());
                changed = true;
            }
            if (incoming.assetUrl() != null && !incoming.assetUrl().equals(entry.getAssetUrl())) {
                entry.setAssetUrl(incoming.assetUrl());
                changed = true;
            }
            if (incoming.assetSource() != null) {
                entry.setAssetSource(incoming.assetSource());
            }
            if (incoming.keyword() != null && !incoming.keyword().equals(entry.getKeyword())) {
                entry.setKeyword(incoming.keyword());
                changed = true;
            }
            if (entry.getAssetUrl() == null || entry.getAssetUrl().isBlank()) {
                entry.setVisualType(assetFallbackService.resolveVisualType(entry.getVisualType(), entry.getAssetUrl()));
            }
            if (changed) {
                entry.setDirty(true);
            }
            edlEntryRepository.save(entry);
        }
        return getEdl(projectId);
    }

    public JobStatusDto startRender(String projectId) {
        RenderJob job = jobQueueService.enqueueRender(projectId);
        jobQueueService.runFinalRender(job.getId());
        return JobStatusDto.from(job, false);
    }

    public JobStatusDto renderStatus(String projectId) {
        return jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .map(job -> JobStatusDto.from(job, job.getOutputPath() != null && !job.getOutputPath().isBlank()))
                .orElseGet(() -> JobStatusDto.fromProject(requireProject(projectId)));
    }

    public Path resolveDownload(String projectId) {
        Project project = requireProject(projectId);
        if (project.getFinalVideoPath() == null || project.getFinalVideoPath().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Final video is not ready");
        }
        Path path = Path.of(project.getFinalVideoPath());
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Rendered file is missing from disk");
        }
        return path;
    }

    public Path resolveSource(String projectId) {
        Project project = requireProject(projectId);
        if (project.getSourcePath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Source media is missing");
        }
        Path path = Path.of(project.getSourcePath());
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Source file is missing from disk");
        }
        return path;
    }

    private TranscriptSegmentDto toTranscriptDto(TranscriptSegment segment) {
        List<TranscriptWord> words = new ArrayList<>();
        if (segment.getWordsJson() != null && !segment.getWordsJson().isBlank()) {
            try {
                words = jsonMapper.readValue(segment.getWordsJson(), new TypeReference<>() {
                });
            } catch (Exception ex) {
                log.warn("Unable to parse word timestamps for segment {}", segment.getId());
            }
        }
        return TranscriptSegmentDto.from(segment, words);
    }

    private Project requireProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return filename.substring(index).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }
}
