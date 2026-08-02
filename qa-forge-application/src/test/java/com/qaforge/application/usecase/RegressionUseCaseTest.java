package com.qaforge.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaforge.application.agent.TestExecutionAgent;
import com.qaforge.application.agent.TestRegistryAgent;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.model.RegressionRequest;
import com.qaforge.domain.model.RegressionResult;
import com.qaforge.domain.model.ScenarioType;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.port.out.MergeRequestPort;
import com.qaforge.domain.port.out.PullRequestPort;
import com.qaforge.domain.port.out.VcsChecksPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PRD §10.2/§12.2: gateResult = OPEN when passRatePercent >= threshold, else BLOCKED. */
class RegressionUseCaseTest {

    private final PullRequestPort pullRequestPort = mock(PullRequestPort.class);
    private final MergeRequestPort mergeRequestPort = mock(MergeRequestPort.class);
    private final VcsChecksPort vcsChecksPort = mock(VcsChecksPort.class);
    private final TestRegistryAgent testRegistryAgent = mock(TestRegistryAgent.class);
    private final TestExecutionAgent testExecutionAgent = mock(TestExecutionAgent.class);

    private RegressionUseCase useCase(double threshold) {
        return new RegressionUseCase(pullRequestPort, mergeRequestPort, vcsChecksPort, testRegistryAgent,
            testExecutionAgent, new SimpleMeterRegistry(), threshold);
    }

    @Test
    void gateIsOpenWhenPassRateMeetsThreshold() {
        RegressionRequest request = new RegressionRequest("github", "acme/backend", "1235", "https://staging.acme.com", "cli");
        PullRequest pr = pullRequest();
        when(pullRequestPort.fetch(request.repositoryFullName(), request.prNumber())).thenReturn(pr);

        List<TestCase> activeTests = List.of(testCase("a.spec.ts"), testCase("b.spec.ts"));
        when(testRegistryAgent.findActive(request.repositoryFullName())).thenReturn(activeTests);
        when(testExecutionAgent.executeExisting(activeTests, request.targetAppBaseUrl())).thenReturn(List.of(
            new ExecutionResult("s1", "a.spec.ts", ExecutionStatus.PASSED, null, 100, 0),
            new ExecutionResult("s2", "b.spec.ts", ExecutionStatus.PASSED, null, 100, 0)));

        RegressionResult result = useCase(90.0).runRegression(request);

        assertThat(result.gateResult()).isEqualTo("OPEN");
        assertThat(result.passRatePercent()).isEqualTo(100.0);
        assertThat(result.blockedReason()).isNull();
        verify(vcsChecksPort).postSuccess(any(), any(), any());
    }

    @Test
    void gateIsBlockedWhenPassRateBelowThreshold() {
        RegressionRequest request = new RegressionRequest("github", "acme/backend", "1235", "https://staging.acme.com", "cli");
        PullRequest pr = pullRequest();
        when(pullRequestPort.fetch(request.repositoryFullName(), request.prNumber())).thenReturn(pr);

        List<TestCase> activeTests = List.of(testCase("a.spec.ts"), testCase("b.spec.ts"));
        when(testRegistryAgent.findActive(request.repositoryFullName())).thenReturn(activeTests);
        when(testExecutionAgent.executeExisting(activeTests, request.targetAppBaseUrl())).thenReturn(List.of(
            new ExecutionResult("s1", "a.spec.ts", ExecutionStatus.PASSED, null, 100, 0),
            new ExecutionResult("s2", "b.spec.ts", ExecutionStatus.FAILED, "assertion failed", 100, 0)));

        RegressionResult result = useCase(90.0).runRegression(request);

        assertThat(result.gateResult()).isEqualTo("BLOCKED");
        assertThat(result.passRatePercent()).isEqualTo(50.0);
        assertThat(result.blockedReason()).contains("50.0%").contains("90.0%");
        verify(vcsChecksPort).postFailure(any(), any(), any());
    }

    private PullRequest pullRequest() {
        return new PullRequest("1235", "github", "acme/backend", "title", "desc", List.of(), "main",
            "feature", "sha", "rohan", List.of(), "https://github.com/acme/backend/pull/1235");
    }

    private TestCase testCase(String fileName) {
        return new TestCase(UUID.randomUUID().toString(), fileName, UUID.randomUUID().toString(), "title",
            ScenarioType.NEW, TestLayer.PLAYWRIGHT, "Checkout", "/checkout", false, List.of(), "1",
            "acme/backend", "github", "sha", TestCase.STATUS_ACTIVE, null, null, 0,
            OffsetDateTime.now(), OffsetDateTime.now());
    }
}
