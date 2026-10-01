package com.videoapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.videoapp.model.EdlEntry;

public interface EdlEntryRepository extends JpaRepository<EdlEntry, String> {

    List<EdlEntry> findByProjectIdOrderBySortOrderAsc(String projectId);
}
