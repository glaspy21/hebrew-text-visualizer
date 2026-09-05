package com.hebrewproject.repository;

import com.hebrewproject.model.IngestionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionMetadataRepository extends JpaRepository<IngestionMetadata, String> {
}
