package com.qaforge.infrastructure.persistence.repository;

import com.qaforge.infrastructure.persistence.entity.TestCaseEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCaseEntity, UUID> {

    List<TestCaseEntity> findByRepositoryAndStatus(String repository, String status);

    List<TestCaseEntity> findByPrNumberAndRepository(String prNumber, String repository);

    Optional<TestCaseEntity> findByFileName(String fileName);

    List<TestCaseEntity> findByFileNameIn(List<String> fileNames);
}
