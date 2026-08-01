package com.qaforge.infrastructure.persistence.repository;

import com.qaforge.infrastructure.persistence.entity.TestRunEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRunEntity, UUID> {

    Page<TestRunEntity> findByRepositoryOrderByCreatedAtDesc(String repository, Pageable pageable);

    Page<TestRunEntity> findByRepositoryAndPrNumberOrderByCreatedAtDesc(String repository, String prNumber, Pageable pageable);
}
