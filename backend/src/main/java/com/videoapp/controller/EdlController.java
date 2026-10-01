package com.videoapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.videoapp.dto.ProjectDtos.EdlDto;
import com.videoapp.dto.ProjectDtos.EdlUpdateRequest;
import com.videoapp.service.ProjectService;

@RestController
@RequestMapping("/api/projects/{id}/edl")
public class EdlController {

    private final ProjectService projectService;

    public EdlController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public EdlDto get(@PathVariable String id) {
        return projectService.getEdl(id);
    }

    @PutMapping
    public EdlDto update(@PathVariable String id, @RequestBody EdlUpdateRequest request) {
        return projectService.updateEdl(id, request);
    }

    @PostMapping("/generate")
    public EdlDto generate(@PathVariable String id) {
        return projectService.generateEdl(id);
    }
}
