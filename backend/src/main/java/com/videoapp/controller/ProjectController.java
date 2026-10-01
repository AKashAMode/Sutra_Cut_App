package com.videoapp.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.videoapp.dto.ProjectDtos.ProjectSummary;
import com.videoapp.dto.ProjectDtos.RenameRequest;
import com.videoapp.service.ProjectService;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectSummary> list() {
        return projectService.listProjects();
    }

    @GetMapping("/{id}")
    public ProjectSummary get(@PathVariable String id) {
        return projectService.getProject(id);
    }

    @PutMapping("/{id}")
    public ProjectSummary rename(@PathVariable String id, @RequestBody RenameRequest request) {
        return projectService.rename(id, request == null ? null : request.name());
    }
}
