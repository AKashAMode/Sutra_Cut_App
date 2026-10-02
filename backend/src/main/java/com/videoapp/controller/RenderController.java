package com.videoapp.controller;

import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.videoapp.dto.ProjectDtos.JobStatusDto;
import com.videoapp.service.ProjectService;

@RestController
@RequestMapping("/api/projects/{id}")
public class RenderController {

    private final ProjectService projectService;

    public RenderController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/render")
    public JobStatusDto render(@PathVariable String id) {
        return projectService.startRender(id);
    }

    @GetMapping("/render/status")
    public JobStatusDto status(@PathVariable String id) {
        return projectService.renderStatus(id);
    }

    // download api
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        Path path = projectService.resolveDownload(id);
        FileSystemResource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"final-" + id + ".mp4\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    @GetMapping("/source")
    public ResponseEntity<Resource> source(@PathVariable String id) {
        Path path = projectService.resolveSource(id);
        FileSystemResource resource = new FileSystemResource(path);
        String filename = path.getFileName().toString();
        String contentType = filename.endsWith(".mp3") || filename.endsWith(".wav") || filename.endsWith(".m4a")
                ? "audio/mpeg"
                : "video/mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
