package com.qaforge.infrastructure.persistence.repository;

import com.qaforge.infrastructure.persistence.entity.TestRunItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunItemRepository extends JpaRepository<TestRunItemEntity, UUID> {

    List<TestRunItemEntity> findByRunId(UUID runId);
}
