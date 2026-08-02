package com.qaforge.application.orchestration;

import com.qaforge.application.agent.CodeAnalysisAgent;
import com.qaforge.application.agent.ContextGatheringAgent;
import com.qaforge.application.agent.DbValidationGenerationAgent;
import com.qaforge.application.agent.PlaywrightGenerationAgent;
import com.qaforge.application.agent.RestAssuredGenerationAgent;
import com.qaforge.application.agent.TestExecutionAgent;
import com.qaforge.application.agent.TestPlanningAgent;
import com.qaforge.application.agent.TestRegistryAgent;
import com.qaforge.domain.exception.LlmParseException;
import com.qaforge.domain.model.AcceptanceCriteria;
import com.qaforge.domain.model.AnalysisRequest;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.ContextSummary;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.ExecutionSummary;
import com.qaforge.domain.model.GeneratedFilesSummary;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.ImpactAssessment;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.model.ScenarioType;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.model.TestRunItem;
import com.qaforge.domain.model.TestScenario;
import com.qaforge.domain.port.out.AcceptanceCriteriaPort;
import com.qaforge.domain.port.out.MergeRequestPort;
import com.qaforge.domain.port.out.PullRequestPort;
import com.qaforge.domain.port.out.TestFileStorePort;
import com.qaforge.domain.port.out.VcsChecksPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Wires every agent and port for the analyze pipeline (PRD §9.2.1 / §10.1): gather context,
 * assess impact, plan scenarios, generate tests in parallel, execute the Playwright subset,
 * self-heal, persist, and report back to the VCS.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String CHECK_NAME = "QA Forge — Analyze";

    private final ContextGatheringAgent contextGatheringAgent;
    private final CodeAnalysisAgent codeAnalysisAgent;
    private final TestPlanningAgent testPlanningAgent;
    private final PlaywrightGenerationAgent playwrightGenerationAgent;
    private final RestAssuredGenerationAgent restAssuredGenerationAgent;
    private final DbValidationGenerationAgent dbValidationGenerationAgent;
    private final TestExecutionAgent testExecutionAgent;
    private final TestRegistryAgent testRegistryAgent;
    private final PullRequestPort pullRequestPort;
    private final MergeRequestPort mergeRequestPort;
    private final AcceptanceCriteriaPort acceptanceCriteriaPort;
    private final TestFileStorePort testFileStorePort;
    private final VcsChecksPort vcsChecksPort;
    private final Executor generationExecutor;
    private final MeterRegistry meterRegistry;

    public AgentOrchestrator(ContextGatheringAgent contextGatheringAgent, CodeAnalysisAgent codeAnalysisAgent,
                              TestPlanningAgent testPlanningAgent, PlaywrightGenerationAgent playwrightGenerationAgent,
                              RestAssuredGenerationAgent restAssuredGenerationAgent,
                              DbValidationGenerationAgent dbValidationGenerationAgent,
                              TestExecutionAgent testExecutionAgent, TestRegistryAgent testRegistryAgent,
                              PullRequestPort pullRequestPort, MergeRequestPort mergeRequestPort,
                              AcceptanceCriteriaPort acceptanceCriteriaPort, TestFileStorePort testFileStorePort,
                              VcsChecksPort vcsChecksPort, Executor generationExecutor, MeterRegistry meterRegistry) {
        this.contextGatheringAgent = contextGatheringAgent;
        this.codeAnalysisAgent = codeAnalysisAgent;
        this.testPlanningAgent = testPlanningAgent;
        this.playwrightGenerationAgent = playwrightGenerationAgent;
        this.restAssuredGenerationAgent = restAssuredGenerationAgent;
        this.dbValidationGenerationAgent = dbValidationGenerationAgent;
        this.testExecutionAgent = testExecutionAgent;
        this.testRegistryAgent = testRegistryAgent;
        this.pullRequestPort = pullRequestPort;
        this.mergeRequestPort = mergeRequestPort;
        this.acceptanceCriteriaPort = acceptanceCriteriaPort;
        this.testFileStorePort = testFileStorePort;
        this.vcsChecksPort = vcsChecksPort;
        this.generationExecutor = generationExecutor;
        this.meterRegistry = meterRegistry;
    }

    public AnalysisResult orchestrate(AnalysisRequest request) {
        MDC.put("repository", request.repositoryFullName());
        MDC.put("prNumber", request.prNumber());
        MDC.put("vcsType", request.vcsType());
        MDC.put("triggeredBy", request.triggeredBy());

        Timer.Sample sample = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();
        String runId = UUID.randomUUID().toString();
        String outcome = AnalysisResult.OUTCOME_SUCCESS;
        PullRequest pr = null;

        try {
            pr = fetchPullRequest(request);
            vcsChecksPort.postPending(pr, CHECK_NAME, null);

            CodeDiff diff = fetchDiff(request);
            Optional<AcceptanceCriteria> ac = acceptanceCriteriaPort.fetch(pr);

            ContextSummary contextSummary = contextGatheringAgent.run(pr, diff, ac);
            ImpactAssessment impactAssessment = codeAnalysisAgent.run(contextSummary, diff);

            List<TestCase> existingTests = testRegistryAgent.findActive(request.repositoryFullName());
            List<TestScenario> scenarios = testPlanningAgent.run(impactAssessment, existingTests);

            List<TestScenario> actionable = scenarios.stream()
                .filter(s -> s.type() != ScenarioType.SKIP)
                .toList();
            int skippedScenarios = scenarios.size() - actionable.size();

            List<GeneratedTest> generatedTests = generateInParallel(actionable, request, impactAssessment);

            List<GeneratedTest> playwrightTests = generatedTests.stream()
                .filter(t -> t.layer() == TestLayer.PLAYWRIGHT)
                .toList();

            TestExecutionAgent.ExecutionOutcome executionOutcome = testExecutionAgent.execute(playwrightTests, request.targetAppBaseUrl());
            List<GeneratedTest> finalTests = applyHealing(generatedTests, executionOutcome.healedTests());

            testFileStorePort.writePlaywrightConfig(request.testOutputDirectory());
            testFileStorePort.writeAll(finalTests, request.testOutputDirectory());
            testRegistryAgent.persist(finalTests, request.prNumber(), request.repositoryFullName(), pr.headSha());

            int markedObsolete = detectAndMarkObsolescence(diff, existingTests, request.repositoryFullName());

            ExecutionSummary executionSummary = summarize(executionOutcome.results());
            recordExecutionMetrics(request.repositoryFullName(), executionOutcome.results());

            long durationMs = System.currentTimeMillis() - start;
            saveRun(request, pr, executionOutcome.results(), finalTests, durationMs);

            if (executionSummary.total() > 0 && executionSummary.passed() < executionSummary.total()) {
                outcome = AnalysisResult.OUTCOME_PARTIAL_FAILURE;
            }

            AnalysisResult result = new AnalysisResult(
                runId,
                request.prNumber(),
                request.repositoryFullName(),
                outcome,
                (int) actionable.stream().filter(s -> s.type() == ScenarioType.NEW).count(),
                (int) actionable.stream().filter(s -> s.type() == ScenarioType.REGRESSION_UPDATE).count(),
                skippedScenarios,
                markedObsolete,
                executionSummary,
                groupFilesByLayer(finalTests),
                durationMs
            );

            reportOutcome(pr, result);
            return result;
        } catch (LlmParseException e) {
            log.error("Agent {} returned unparseable JSON", e.getAgentName(), e);
            if (pr != null) {
                vcsChecksPort.postFailure(pr, CHECK_NAME, "QA Forge agent " + e.getAgentName() + " failed to produce valid output");
            }
            return partialFailure(runId, request, start);
        } catch (RuntimeException e) {
            log.error("Analyze pipeline failed for {} PR {}", request.repositoryFullName(), request.prNumber(), e);
            if (pr != null) {
                vcsChecksPort.postFailure(pr, CHECK_NAME, "QA Forge analysis failed: " + e.getMessage());
            }
            return partialFailure(runId, request, start);
        } finally {
            sample.stop(meterRegistry.timer("qaforge.analysis.duration",
                "repository", request.repositoryFullName(), "outcome", outcome, "vcs_type", request.vcsType()));
            MDC.clear();
        }
    }

    private PullRequest fetchPullRequest(AnalysisRequest request) {
        if ("gitlab".equalsIgnoreCase(request.vcsType())) {
            return mergeRequestPort.fetch(request.repositoryFullName(), request.prNumber());
        }
        return pullRequestPort.fetch(request.repositoryFullName(), request.prNumber());
    }

    private CodeDiff fetchDiff(AnalysisRequest request) {
        if ("gitlab".equalsIgnoreCase(request.vcsType())) {
            return mergeRequestPort.fetchDiff(request.repositoryFullName(), request.prNumber());
        }
        return pullRequestPort.fetchDiff(request.repositoryFullName(), request.prNumber());
    }

    private List<GeneratedTest> generateInParallel(List<TestScenario> scenarios, AnalysisRequest request,
                                                     ImpactAssessment impactAssessment) {
        List<CompletableFuture<GeneratedTest>> futures = new ArrayList<>();

        for (TestScenario scenario : scenarios) {
            switch (scenario.layer()) {
                case PLAYWRIGHT -> futures.add(CompletableFuture.supplyAsync(
                    () -> playwrightGenerationAgent.generate(scenario, request.prNumber()), generationExecutor));
                case REST_ASSURED -> {
                    if (request.openApiSpecUrl() != null && !request.openApiSpecUrl().isBlank()) {
                        futures.add(CompletableFuture.supplyAsync(
                            () -> restAssuredGenerationAgent.generate(scenario, request.openApiSpecUrl()), generationExecutor));
                    }
                }
                case DB_VALIDATION -> {
                    if (!impactAssessment.changedDbTables().isEmpty()) {
                        futures.add(CompletableFuture.supplyAsync(
                            () -> dbValidationGenerationAgent.generate(scenario), generationExecutor));
                    }
                }
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private List<GeneratedTest> applyHealing(List<GeneratedTest> tests, java.util.Map<String, GeneratedTest> healed) {
        if (healed.isEmpty()) {
            return tests;
        }
        return tests.stream().map(t -> healed.getOrDefault(t.fileName(), t)).toList();
    }

    private int detectAndMarkObsolescence(CodeDiff diff, List<TestCase> existingTests, String repository) {
        List<String> deletedPaths = diff.changedFiles().stream()
            .filter(f -> "DELETED".equalsIgnoreCase(f.changeType()))
            .map(f -> f.filePath().toLowerCase(Locale.ROOT))
            .toList();
        if (deletedPaths.isEmpty()) {
            return 0;
        }

        List<String> obsoleteFileNames = existingTests.stream()
            .filter(tc -> TestCase.STATUS_ACTIVE.equals(tc.status()))
            .filter(tc -> matchesAnyDeletedPath(tc, deletedPaths))
            .map(TestCase::fileName)
            .toList();

        testRegistryAgent.markObsolete(obsoleteFileNames);
        if (!obsoleteFileNames.isEmpty()) {
            meterRegistry.counter("qaforge.tests.obsoleted", "repository", repository)
                .increment(obsoleteFileNames.size());
        }
        return obsoleteFileNames.size();
    }

    private boolean matchesAnyDeletedPath(TestCase testCase, List<String> deletedPaths) {
        String targetPath = testCase.targetPath() == null ? "" : testCase.targetPath().toLowerCase(Locale.ROOT);
        String userFlow = testCase.userFlow() == null ? "" : testCase.userFlow().toLowerCase(Locale.ROOT);
        for (String deletedPath : deletedPaths) {
            String fileStem = deletedPath.substring(deletedPath.lastIndexOf('/') + 1)
                .replaceFirst("\\.[a-z0-9]+$", "");
            if ((!targetPath.isBlank() && (deletedPath.contains(targetPath) || targetPath.contains(fileStem)))
                || (!userFlow.isBlank() && deletedPath.contains(userFlow.replace(' ', '_')))) {
                return true;
            }
        }
        return false;
    }

    private ExecutionSummary summarize(List<ExecutionResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(r -> r.status() == ExecutionStatus.PASSED).count();
        int failed = (int) results.stream().filter(r -> r.status() == ExecutionStatus.FAILED).count();
        int errors = (int) results.stream().filter(r -> r.status() == ExecutionStatus.ERROR).count();
        int selfHealed = (int) results.stream().filter(r -> r.retryCount() > 0).count();
        double passRate = total == 0 ? 100.0 : (passed * 100.0) / total;
        return new ExecutionSummary(total, passed, failed, errors, selfHealed, passRate);
    }

    private void recordExecutionMetrics(String repository, List<ExecutionResult> results) {
        for (ExecutionResult result : results) {
            meterRegistry.counter("qaforge.execution.result",
                "repository", repository, "layer", "PLAYWRIGHT", "status", result.status().name()).increment();
        }
        long selfHealed = results.stream().filter(r -> r.retryCount() > 0).count();
        if (selfHealed > 0) {
            meterRegistry.counter("qaforge.self_healed", "repository", repository).increment(selfHealed);
        }
    }

    private GeneratedFilesSummary groupFilesByLayer(List<GeneratedTest> tests) {
        return new GeneratedFilesSummary(
            fileNamesForLayer(tests, TestLayer.PLAYWRIGHT),
            fileNamesForLayer(tests, TestLayer.REST_ASSURED),
            fileNamesForLayer(tests, TestLayer.DB_VALIDATION)
        );
    }

    private List<String> fileNamesForLayer(List<GeneratedTest> tests, TestLayer layer) {
        return tests.stream().filter(t -> t.layer() == layer).map(GeneratedTest::fileName).collect(Collectors.toList());
    }

    private void saveRun(AnalysisRequest request, PullRequest pr, List<ExecutionResult> results,
                          List<GeneratedTest> finalTests, long durationMs) {
        ExecutionSummary summary = summarize(results);
        List<TestRunItem> items = results.stream()
            .map(r -> new TestRunItem(null, r.fileName(), r.scenarioId(), TestLayer.PLAYWRIGHT,
                r.status(), r.errorMessage(), r.durationMs(), r.retryCount(), r.retryCount() > 0))
            .toList();

        String runOutcome = summary.total() == 0 || summary.passed() == summary.total()
            ? "PASSED" : (summary.passed() > 0 ? "PARTIAL_FAILURE" : "FAILED");

        TestRun run = new TestRun(
            null, request.repositoryFullName(), request.vcsType(), request.prNumber(),
            TestRun.RUN_TYPE_INCREMENTAL, request.triggeredBy(),
            summary.total(), summary.passed(), summary.failed(), summary.errors(), 0,
            summary.passRatePercent(), durationMs, runOutcome, null, OffsetDateTime.now(), items);

        testRegistryAgent.saveRun(run);

        for (GeneratedTest test : finalTests) {
            meterRegistry.counter("qaforge.tests.generated",
                "repository", request.repositoryFullName(), "layer", test.layer().name(),
                "scenario_type", "generated").increment();
        }
    }

    private void reportOutcome(PullRequest pr, AnalysisResult result) {
        String summary = "Generated %d new, updated %d, skipped %d, obsoleted %d. Execution: %d/%d passed (%.1f%%)".formatted(
            result.newTestsGenerated(), result.updatedTests(), result.skippedScenarios(), result.markedObsolete(),
            result.executionSummary().passed(), result.executionSummary().total(), result.executionSummary().passRatePercent());

        if (AnalysisResult.OUTCOME_SUCCESS.equals(result.outcome())) {
            vcsChecksPort.postSuccess(pr, CHECK_NAME, summary);
        } else {
            vcsChecksPort.postFailure(pr, CHECK_NAME, summary);
        }
    }

    private AnalysisResult partialFailure(String runId, AnalysisRequest request, long start) {
        return new AnalysisResult(
            runId, request.prNumber(), request.repositoryFullName(), AnalysisResult.OUTCOME_ERROR,
            0, 0, 0, 0,
            new ExecutionSummary(0, 0, 0, 0, 0, 0.0),
            new GeneratedFilesSummary(List.of(), List.of(), List.of()),
            System.currentTimeMillis() - start
        );
    }
}
