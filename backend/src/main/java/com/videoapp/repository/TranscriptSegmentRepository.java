package com.videoapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.videoapp.model.TranscriptSegment;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, String> {

    List<TranscriptSegment> findByProjectIdOrderByStartTimeAsc(String projectId);
}
