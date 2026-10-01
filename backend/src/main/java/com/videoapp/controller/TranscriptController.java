package com.videoapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.videoapp.dto.ProjectDtos.TranscriptDto;
import com.videoapp.dto.ProjectDtos.TranscriptUpdateRequest;
import com.videoapp.service.ProjectService;

@RestController
@RequestMapping("/api/projects/{id}/transcript")
public class TranscriptController {

    private final ProjectService projectService;

    public TranscriptController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public TranscriptDto get(@PathVariable String id) {
        return projectService.getTranscript(id);
    }

    @PutMapping
    public TranscriptDto update(@PathVariable String id, @RequestBody TranscriptUpdateRequest request) {
        return projectService.updateTranscript(id, request);
    }
}
