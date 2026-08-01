package com.qaforge.bootstrap.cli;

import com.qaforge.application.agent.TestExecutionAgent;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.port.out.TestRegistryPort;
import java.util.List;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * {@code qa run} (PRD §12.10) — an ad-hoc local execution of the active suite against a URL,
 * with no PR/MR association and no VCS check posting. Distinct from {@code qa regression},
 * which is PR-gated and reports back to the PR/MR; this is the "just run my current suite"
 * command PRD §12.10 lists with only {@code --repo}/{@code --base-url} (no {@code --pr}).
 */
@Component
@CommandGroup(name = "qa", prefix = "qa", description = "QA Forge commands")
public class RunCommand {

    private final TestRegistryPort testRegistryPort;
    private final TestExecutionAgent testExecutionAgent;

    public RunCommand(TestRegistryPort testRegistryPort, TestExecutionAgent testExecutionAgent) {
        this.testRegistryPort = testRegistryPort;
        this.testExecutionAgent = testExecutionAgent;
    }

    @Command(value = "run", description = "Run the active suite locally against a target URL")
    public String run(
            @Option(longName = "repo", required = true, description = "repositoryFullName") String repo,
            @Option(longName = "base-url", required = true, description = "target application base URL") String baseUrl) {

        List<TestCase> activeTests = testRegistryPort.findActive(repo);
        if (activeTests.isEmpty()) {
            return "No active test cases for " + repo;
        }

        List<ExecutionResult> results = testExecutionAgent.executeExisting(activeTests, baseUrl);
        long passed = results.stream().filter(r -> r.status() == ExecutionStatus.PASSED).count();
        return "Ran %d tests against %s: %d/%d passed".formatted(results.size(), baseUrl, passed, results.size());
    }
}
