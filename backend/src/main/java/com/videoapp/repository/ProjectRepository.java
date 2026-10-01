package com.videoapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.videoapp.model.Project;

public interface ProjectRepository extends JpaRepository<Project, String> {
}
