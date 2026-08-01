package com.qaforge.domain.port.out;

import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestRun;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed store of test cases and run history (PRD §23 glossary: "Registry").
 *
 * <p>The {@code saveRun}/{@code findRun}/{@code findRuns} methods are a documented extension
 * beyond PRD §9.1.2's port list: sections 12.6/12.7 require querying persisted runs, and the
 * glossary defines the registry as covering "run history" — so run persistence belongs on this
 * port rather than introducing infrastructure access from the application layer. Likewise
 * {@code findByStatus} generalizes {@code findActive} so {@code GET /api/v1/tests}
 * (PRD §12.5) can list {@code OBSOLETE} tests too, which the fixed-ACTIVE {@code findActive}
 * alone cannot serve.
 */
public interface TestRegistryPort {

    void saveAll(List<GeneratedTest> tests, String prNumber, String repository, String headSha);

    List<TestCase> findActive(String repository);

    List<TestCase> findByStatus(String repository, String status);

    List<TestCase> findByPr(String prNumber, String repository);

    Optional<TestCase> findByFileName(String fileName);

    void markObsolete(List<String> fileNames);

    void updateExecutionStats(String fileName, ExecutionStatus status, long durationMs);

    TestRun saveRun(TestRun run);

    Optional<TestRun> findRun(String runId);

    List<TestRun> findRuns(String repository, String prNumber, int page, int size);
}
