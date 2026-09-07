package com.hebrewproject.repository;

import com.hebrewproject.model.Root;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RootRepository extends JpaRepository<Root, Long> {

    // Get-or-create key for ingestion: each primitive/resolved root's Strong's
    // ID is unique (see Root.strongId's @Column(unique = true)), so this is
    // how ingestion checks "has this root already been created by an earlier
    // word in this same run" before inserting a duplicate.
    Optional<Root> findByStrongId(String strongId);
}
