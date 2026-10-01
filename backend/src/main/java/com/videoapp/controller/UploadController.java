package com.videoapp.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.videoapp.dto.ProjectDtos.ProjectSummary;
import com.videoapp.service.ProjectService;

@RestController
@RequestMapping("/api")
public class UploadController {

    private final ProjectService projectService;

    public UploadController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/upload")
    public ProjectSummary upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        return projectService.upload(file, name);
    }
}
