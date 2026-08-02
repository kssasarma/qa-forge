package com.qaforge.application.usecase;

import com.qaforge.application.agent.TestExecutionAgent;
import com.qaforge.application.agent.TestRegistryAgent;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.model.RegressionRequest;
import com.qaforge.domain.model.RegressionResult;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.model.TestRunItem;
import com.qaforge.domain.port.in.RegressionPort;
import com.qaforge.domain.port.out.MergeRequestPort;
import com.qaforge.domain.port.out.PullRequestPort;
import com.qaforge.domain.port.out.VcsChecksPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Runs the full active test suite against a target URL and enforces the regression gate (PRD §10.2). */
@Service
public class RegressionUseCase implements RegressionPort {

    private static final Logger log = LoggerFactory.getLogger(RegressionUseCase.class);
    private static final String CHECK_NAME = "QA Forge — Regression";

    private final PullRequestPort pullRequestPort;
    private final MergeRequestPort mergeRequestPort;
    private final VcsChecksPort vcsChecksPort;
    private final TestRegistryAgent testRegistryAgent;
    private final TestExecutionAgent testExecutionAgent;
    private final MeterRegistry meterRegistry;
    private final double passRateThreshold;

    public RegressionUseCase(PullRequestPort pullRequestPort, MergeRequestPort mergeRequestPort,
                              VcsChecksPort vcsChecksPort, TestRegistryAgent testRegistryAgent,
                              TestExecutionAgent testExecutionAgent, MeterRegistry meterRegistry,
                              @Value("${qaforge.regression.pass-rate-threshold:90.0}") double passRateThreshold) {
        this.pullRequestPort = pullRequestPort;
        this.mergeRequestPort = mergeRequestPort;
        this.vcsChecksPort = vcsChecksPort;
        this.testRegistryAgent = testRegistryAgent;
        this.testExecutionAgent = testExecutionAgent;
        this.meterRegistry = meterRegistry;
        this.passRateThreshold = passRateThreshold;
    }

    @Override
    public RegressionResult runRegression(RegressionRequest request) {
        MDC.put("repository", request.repositoryFullName());
        MDC.put("prNumber", request.prNumber());
        MDC.put("vcsType", request.vcsType());
        MDC.put("triggeredBy", request.triggeredBy());

        Timer.Sample sample = Timer.start(meterRegistry);
        String runId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        String gateResult = TestRun.GATE_OPEN;

        try {
            PullRequest pr = fetchPullRequest(request);
            vcsChecksPort.postPending(pr, CHECK_NAME, null);

            List<TestCase> activeTests = testRegistryAgent.findActive(request.repositoryFullName());
            List<ExecutionResult> results = testExecutionAgent.executeExisting(activeTests, request.targetAppBaseUrl());

            int total = results.size();
            int passed = (int) results.stream().filter(r -> r.status() == ExecutionStatus.PASSED).count();
            int failed = (int) results.stream().filter(r -> r.status() == ExecutionStatus.FAILED || r.status() == ExecutionStatus.ERROR).count();
            int selfHealed = (int) results.stream().filter(r -> r.retryCount() > 0).count();
            double passRatePercent = total == 0 ? 100.0 : (passed * 100.0) / total;

            String blockedReason = null;
            if (passRatePercent < passRateThreshold) {
                gateResult = TestRun.GATE_BLOCKED;
                blockedReason = "Pass rate %.1f%% below threshold %.1f%%".formatted(passRatePercent, passRateThreshold);
            }

            long durationMs = System.currentTimeMillis() - start;
            String outcome = gateResult.equals(TestRun.GATE_OPEN) ? "PASSED" : "FAILED";

            persistRun(request, results, total, passed, failed, passRatePercent, durationMs, outcome, gateResult);

            meterRegistry.gauge("qaforge.regression.pass_rate",
                io.micrometer.core.instrument.Tags.of("repository", request.repositoryFullName()), passRatePercent);

            if (TestRun.GATE_BLOCKED.equals(gateResult)) {
                vcsChecksPort.postFailure(pr, CHECK_NAME, blockedReason);
            } else {
                vcsChecksPort.postSuccess(pr, CHECK_NAME, "Regression: %d/%d passed (%.1f%%)".formatted(passed, total, passRatePercent));
            }

            for (TestCase testCase : activeTests) {
                results.stream()
                    .filter(r -> r.fileName().equals(testCase.fileName()))
                    .findFirst()
                    .ifPresent(r -> testRegistryAgent.updateExecutionStats(testCase.fileName(), r.status(), r.durationMs()));
            }

            return new RegressionResult(runId, outcome, total, passed, failed, selfHealed, passRatePercent, durationMs, gateResult, blockedReason);
        } catch (RuntimeException e) {
            log.error("Regression run failed for {} PR {}", request.repositoryFullName(), request.prNumber(), e);
            long durationMs = System.currentTimeMillis() - start;
            return new RegressionResult(runId, "ERROR", 0, 0, 0, 0, 0.0, durationMs, TestRun.GATE_BLOCKED,
                "Regression pipeline error: " + e.getMessage());
        } finally {
            sample.stop(meterRegistry.timer("qaforge.regression.duration",
                "repository", request.repositoryFullName(), "gate_result", gateResult));
            MDC.clear();
        }
    }

    private PullRequest fetchPullRequest(RegressionRequest request) {
        if ("gitlab".equalsIgnoreCase(request.vcsType())) {
            return mergeRequestPort.fetch(request.repositoryFullName(), request.prNumber());
        }
        return pullRequestPort.fetch(request.repositoryFullName(), request.prNumber());
    }

    private void persistRun(RegressionRequest request, List<ExecutionResult> results, int total, int passed,
                             int failed, double passRatePercent, long durationMs, String outcome, String gateResult) {
        List<TestRunItem> items = results.stream()
            .map(r -> new TestRunItem(null, r.fileName(), r.scenarioId(), TestLayer.PLAYWRIGHT,
                r.status(), r.errorMessage(), r.durationMs(), r.retryCount(), r.retryCount() > 0))
            .toList();

        TestRun run = new TestRun(
            null, request.repositoryFullName(), request.vcsType(), request.prNumber(),
            TestRun.RUN_TYPE_REGRESSION, request.triggeredBy(),
            total, passed, failed, 0, 0, passRatePercent, durationMs, outcome, gateResult,
            OffsetDateTime.now(), items);

        testRegistryAgent.saveRun(run);
    }
}
