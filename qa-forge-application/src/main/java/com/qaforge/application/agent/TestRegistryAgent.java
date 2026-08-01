package com.qaforge.application.agent;

import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.port.out.TestRegistryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Plain persistence-facing service, no LLM (PRD §8 project structure). Thin wrapper over {@link TestRegistryPort}. */
@Service
public class TestRegistryAgent {

    private final TestRegistryPort testRegistryPort;

    public TestRegistryAgent(TestRegistryPort testRegistryPort) {
        this.testRegistryPort = testRegistryPort;
    }

    public void persist(List<GeneratedTest> tests, String prNumber, String repository, String headSha) {
        testRegistryPort.saveAll(tests, prNumber, repository, headSha);
    }

    public List<TestCase> findActive(String repository) {
        return testRegistryPort.findActive(repository);
    }

    public List<TestCase> findByPr(String prNumber, String repository) {
        return testRegistryPort.findByPr(prNumber, repository);
    }

    public Optional<TestCase> findByFileName(String fileName) {
        return testRegistryPort.findByFileName(fileName);
    }

    public void markObsolete(List<String> fileNames) {
        if (!fileNames.isEmpty()) {
            testRegistryPort.markObsolete(fileNames);
        }
    }

    public void updateExecutionStats(String fileName, ExecutionStatus status, long durationMs) {
        testRegistryPort.updateExecutionStats(fileName, status, durationMs);
    }

    public TestRun saveRun(TestRun run) {
        return testRegistryPort.saveRun(run);
    }
}
