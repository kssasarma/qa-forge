package com.qaforge.infrastructure.persistence.adapter;

import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.ScenarioType;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.model.TestRunItem;
import com.qaforge.domain.port.out.TestRegistryPort;
import com.qaforge.infrastructure.persistence.entity.TestCaseEntity;
import com.qaforge.infrastructure.persistence.entity.TestRunEntity;
import com.qaforge.infrastructure.persistence.entity.TestRunItemEntity;
import com.qaforge.infrastructure.persistence.repository.TestCaseRepository;
import com.qaforge.infrastructure.persistence.repository.TestRunRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link TestRegistryPort} over Spring Data JPA (PRD §9.1.2, §11). */
@Component
@Transactional
public class JpaTestRegistryAdapter implements TestRegistryPort {

    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;

    public JpaTestRegistryAdapter(TestCaseRepository testCaseRepository, TestRunRepository testRunRepository) {
        this.testCaseRepository = testCaseRepository;
        this.testRunRepository = testRunRepository;
    }

    @Override
    public void saveAll(List<GeneratedTest> tests, String prNumber, String repository, String headSha) {
        for (GeneratedTest test : tests) {
            TestCaseEntity entity = testCaseRepository.findByFileName(test.fileName()).orElse(null);
            if (entity == null) {
                entity = new TestCaseEntity(
                    test.fileName(), UUID.fromString(test.scenarioId()), test.fileName(), ScenarioType.NEW.name(),
                    test.layer().name(), firstTag(test), null, false,
                    String.join(",", test.tags()), prNumber, repository, "github", headSha);
            } else {
                entity.setStatus(TestCase.STATUS_ACTIVE);
            }
            testCaseRepository.save(entity);
        }
    }

    @Override
    public List<TestCase> findActive(String repository) {
        return findByStatus(repository, TestCase.STATUS_ACTIVE);
    }

    @Override
    public List<TestCase> findByStatus(String repository, String status) {
        return testCaseRepository.findByRepositoryAndStatus(repository, status)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<TestCase> findByPr(String prNumber, String repository) {
        return testCaseRepository.findByPrNumberAndRepository(prNumber, repository)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<TestCase> findByFileName(String fileName) {
        return testCaseRepository.findByFileName(fileName).map(this::toDomain);
    }

    @Override
    public void markObsolete(List<String> fileNames) {
        List<TestCaseEntity> entities = testCaseRepository.findByFileNameIn(fileNames);
        entities.forEach(e -> e.setStatus(TestCase.STATUS_OBSOLETE));
        testCaseRepository.saveAll(entities);
    }

    @Override
    public void updateExecutionStats(String fileName, ExecutionStatus status, long durationMs) {
        testCaseRepository.findByFileName(fileName).ifPresent(entity -> {
            entity.setLastExecutionStatus(status.name());
            entity.setLastExecutionMs(durationMs);
            entity.setExecutionCount(entity.getExecutionCount() + 1);
            testCaseRepository.save(entity);
        });
    }

    @Override
    public TestRun saveRun(TestRun run) {
        TestRunEntity entity = new TestRunEntity(
            run.repository(), run.vcsType(), run.prNumber(), run.runType(), run.triggeredBy(),
            run.totalCount(), run.passedCount(), run.failedCount(), run.errorCount(), run.skippedCount(),
            run.passRate() == null ? null : BigDecimal.valueOf(run.passRate()), run.durationMs(),
            run.outcome(), run.gateResult());

        for (TestRunItem item : run.items()) {
            UUID testCaseId = testCaseRepository.findByFileName(item.fileName()).map(TestCaseEntity::getId).orElse(null);
            entity.addItem(new TestRunItemEntity(
                testCaseId, item.fileName(),
                item.scenarioId() == null ? null : UUID.fromString(item.scenarioId()),
                item.testLayer() == null ? null : item.testLayer().name(),
                item.status().name(), item.errorMessage(), item.durationMs(), item.retryCount(), item.selfHealed()));
        }

        TestRunEntity saved = testRunRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<TestRun> findRun(String runId) {
        return testRunRepository.findById(UUID.fromString(runId)).map(this::toDomain);
    }

    @Override
    public List<TestRun> findRuns(String repository, String prNumber, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (prNumber != null && !prNumber.isBlank()) {
            return testRunRepository.findByRepositoryAndPrNumberOrderByCreatedAtDesc(repository, prNumber, pageRequest)
                .map(this::toDomain).getContent();
        }
        return testRunRepository.findByRepositoryOrderByCreatedAtDesc(repository, pageRequest)
            .map(this::toDomain).getContent();
    }

    private String firstTag(GeneratedTest test) {
        return test.tags() == null || test.tags().isEmpty() ? null : test.tags().get(0);
    }

    private TestCase toDomain(TestCaseEntity e) {
        return new TestCase(
            e.getId().toString(), e.getFileName(), e.getScenarioId().toString(), e.getScenarioTitle(),
            ScenarioType.valueOf(e.getScenarioType()), TestLayer.valueOf(e.getTestLayer()), e.getUserFlow(),
            e.getTargetPath(), e.isRequiresAuth(),
            e.getTags() == null || e.getTags().isBlank() ? List.of() : List.of(e.getTags().split(",")),
            e.getPrNumber(), e.getRepository(), e.getVcsType(), e.getHeadSha(), e.getStatus(),
            e.getLastExecutionStatus() == null ? null : ExecutionStatus.valueOf(e.getLastExecutionStatus()),
            e.getLastExecutionMs(), e.getExecutionCount(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private TestRun toDomain(TestRunEntity e) {
        List<TestRunItem> items = e.getItems().stream().map(this::toDomain).toList();
        return new TestRun(
            e.getId().toString(), e.getRepository(), e.getVcsType(), e.getPrNumber(), e.getRunType(),
            e.getTriggeredBy(), e.getTotalCount(), e.getPassedCount(), e.getFailedCount(), e.getErrorCount(),
            e.getSkippedCount(), e.getPassRate() == null ? null : e.getPassRate().doubleValue(), e.getDurationMs(),
            e.getOutcome(), e.getGateResult(), e.getCreatedAt(), items);
    }

    private TestRunItem toDomain(TestRunItemEntity e) {
        return new TestRunItem(
            e.getTestCaseId() == null ? null : e.getTestCaseId().toString(), e.getFileName(),
            e.getScenarioId() == null ? null : e.getScenarioId().toString(),
            e.getTestLayer() == null ? null : TestLayer.valueOf(e.getTestLayer()),
            ExecutionStatus.valueOf(e.getStatus()), e.getErrorMessage(),
            e.getDurationMs() == null ? 0 : e.getDurationMs(), e.getRetryCount(), e.isSelfHealed());
    }
}
