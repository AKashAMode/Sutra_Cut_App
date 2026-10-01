package com.videoapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.videoapp.model.RenderJob;

public interface JobRepository extends JpaRepository<RenderJob, String> {

    List<RenderJob> findByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<RenderJob> findFirstByProjectIdOrderByCreatedAtDesc(String projectId);
}
