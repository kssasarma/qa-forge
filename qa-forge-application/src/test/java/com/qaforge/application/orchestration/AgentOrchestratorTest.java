package com.qaforge.application.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.ImpactAssessment;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.model.ScenarioType;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.model.TestScenario;
import com.qaforge.domain.port.out.AcceptanceCriteriaPort;
import com.qaforge.domain.port.out.MergeRequestPort;
import com.qaforge.domain.port.out.PullRequestPort;
import com.qaforge.domain.port.out.TestFileStorePort;
import com.qaforge.domain.port.out.VcsChecksPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers AgentOrchestrator chaining, parallel generation, obsolescence detection, and error
 * propagation, per PRD §19.1 ("Mock all agents and ports. Assert correct chaining, parallel
 * generation, and error propagation."). */
class AgentOrchestratorTest {

    private final ContextGatheringAgent contextGatheringAgent = mock(ContextGatheringAgent.class);
    private final CodeAnalysisAgent codeAnalysisAgent = mock(CodeAnalysisAgent.class);
    private final TestPlanningAgent testPlanningAgent = mock(TestPlanningAgent.class);
    private final PlaywrightGenerationAgent playwrightGenerationAgent = mock(PlaywrightGenerationAgent.class);
    private final RestAssuredGenerationAgent restAssuredGenerationAgent = mock(RestAssuredGenerationAgent.class);
    private final DbValidationGenerationAgent dbValidationGenerationAgent = mock(DbValidationGenerationAgent.class);
    private final TestExecutionAgent testExecutionAgent = mock(TestExecutionAgent.class);
    private final TestRegistryAgent testRegistryAgent = mock(TestRegistryAgent.class);
    private final PullRequestPort pullRequestPort = mock(PullRequestPort.class);
    private final MergeRequestPort mergeRequestPort = mock(MergeRequestPort.class);
    private final AcceptanceCriteriaPort acceptanceCriteriaPort = mock(AcceptanceCriteriaPort.class);
    private final TestFileStorePort testFileStorePort = mock(TestFileStorePort.class);
    private final VcsChecksPort vcsChecksPort = mock(VcsChecksPort.class);
    private final Executor directExecutor = Runnable::run;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(
            contextGatheringAgent, codeAnalysisAgent, testPlanningAgent, playwrightGenerationAgent,
            restAssuredGenerationAgent, dbValidationGenerationAgent, testExecutionAgent, testRegistryAgent,
            pullRequestPort, mergeRequestPort, acceptanceCriteriaPort, testFileStorePort, vcsChecksPort,
            directExecutor, new SimpleMeterRegistry());
    }

    @Test
    void happyPathChainsAgentsGeneratesExecutesAndPersists() {
        AnalysisRequest request = analysisRequest();
        PullRequest pr = pullRequest();
        when(pullRequestPort.fetch(request.repositoryFullName(), request.prNumber())).thenReturn(pr);
        CodeDiff diff = new CodeDiff("diff", List.of(), false);
        when(pullRequestPort.fetchDiff(request.repositoryFullName(), request.prNumber())).thenReturn(diff);
        when(acceptanceCriteriaPort.fetch(pr)).thenReturn(Optional.empty());

        ContextSummary contextSummary = new ContextSummary("summary", List.of(), List.of(), List.of(), "LOW");
        when(contextGatheringAgent.run(pr, diff, Optional.empty())).thenReturn(contextSummary);

        ImpactAssessment impact = new ImpactAssessment(List.of(), List.of(), false, List.of(), List.of(), List.of());
        when(codeAnalysisAgent.run(contextSummary, diff)).thenReturn(impact);

        when(testRegistryAgent.findActive(request.repositoryFullName())).thenReturn(List.of());

        TestScenario scenario = scenario(ScenarioType.NEW, TestLayer.PLAYWRIGHT);
        when(testPlanningAgent.run(impact, List.of())).thenReturn(List.of(scenario));

        GeneratedTest generatedTest = new GeneratedTest(scenario.id(), "checkout_pr1234.spec.ts", "content", TestLayer.PLAYWRIGHT, List.of("checkout"));
        when(playwrightGenerationAgent.generate(scenario, request.prNumber())).thenReturn(generatedTest);

        ExecutionResult passedResult = new ExecutionResult(scenario.id(), generatedTest.fileName(), ExecutionStatus.PASSED, null, 500, 0);
        when(testExecutionAgent.execute(List.of(generatedTest), request.targetAppBaseUrl()))
            .thenReturn(new TestExecutionAgent.ExecutionOutcome(List.of(passedResult), Map.of()));

        AnalysisResult result = orchestrator.orchestrate(request);

        assertThat(result.outcome()).isEqualTo(AnalysisResult.OUTCOME_SUCCESS);
        assertThat(result.newTestsGenerated()).isEqualTo(1);
        assertThat(result.executionSummary().passed()).isEqualTo(1);
        assertThat(result.executionSummary().total()).isEqualTo(1);
        assertThat(result.generatedFiles().playwright()).containsExactly("checkout_pr1234.spec.ts");

        verify(vcsChecksPort).postPending(eq(pr), anyString(), any());
        verify(vcsChecksPort).postSuccess(eq(pr), anyString(), anyString());
        verify(testFileStorePort).writeAll(List.of(generatedTest), request.testOutputDirectory());
        verify(testRegistryAgent).persist(List.of(generatedTest), request.prNumber(), request.repositoryFullName(), pr.headSha());
        verify(testRegistryAgent).saveRun(any(TestRun.class));
    }

    @Test
    void deletedFileMatchingActiveTestTargetPathMarksItObsolete() {
        AnalysisRequest request = analysisRequest();
        PullRequest pr = pullRequest();
        when(pullRequestPort.fetch(any(), any())).thenReturn(pr);

        ChangedFile deletedFile = new ChangedFile("src/pages/Checkout.tsx", "DELETED", 0, 40, "patch");
        CodeDiff diff = new CodeDiff("diff", List.of(deletedFile), false);
        when(pullRequestPort.fetchDiff(any(), any())).thenReturn(diff);
        when(acceptanceCriteriaPort.fetch(pr)).thenReturn(Optional.empty());
        when(contextGatheringAgent.run(any(), any(), any()))
            .thenReturn(new ContextSummary("s", List.of(), List.of(), List.of(), "LOW"));
        when(codeAnalysisAgent.run(any(), any()))
            .thenReturn(new ImpactAssessment(List.of(), List.of(), false, List.of(), List.of(), List.of()));

        TestCase activeTest = new TestCase(
            UUID.randomUUID().toString(), "checkout_old_pr1.spec.ts", UUID.randomUUID().toString(), "Old checkout test",
            ScenarioType.NEW, TestLayer.PLAYWRIGHT, "Checkout", "/checkout", false, List.of(), "1",
            request.repositoryFullName(), "github", "sha", TestCase.STATUS_ACTIVE, ExecutionStatus.PASSED, 100L, 3,
            OffsetDateTime.now(), OffsetDateTime.now());
        when(testRegistryAgent.findActive(request.repositoryFullName())).thenReturn(List.of(activeTest));
        when(testPlanningAgent.run(any(), any())).thenReturn(List.of());
        when(testExecutionAgent.execute(List.of(), request.targetAppBaseUrl()))
            .thenReturn(new TestExecutionAgent.ExecutionOutcome(List.of(), Map.of()));

        AnalysisResult result = orchestrator.orchestrate(request);

        assertThat(result.markedObsolete()).isEqualTo(1);
        verify(testRegistryAgent).markObsolete(List.of("checkout_old_pr1.spec.ts"));
    }

    @Test
    void llmParseExceptionDuringContextGatheringReportsFailureAndReturnsErrorOutcome() {
        AnalysisRequest request = analysisRequest();
        PullRequest pr = pullRequest();
        when(pullRequestPort.fetch(any(), any())).thenReturn(pr);
        when(pullRequestPort.fetchDiff(any(), any())).thenReturn(new CodeDiff("diff", List.of(), false));
        when(acceptanceCriteriaPort.fetch(pr)).thenReturn(Optional.empty());
        when(contextGatheringAgent.run(any(), any(), any()))
            .thenThrow(new LlmParseException("ContextGatheringAgent", "not json"));

        AnalysisResult result = orchestrator.orchestrate(request);

        assertThat(result.outcome()).isEqualTo(AnalysisResult.OUTCOME_ERROR);
        verify(vcsChecksPort).postFailure(eq(pr), anyString(), anyString());
        verify(vcsChecksPort, never()).postSuccess(any(), any(), any());
        verify(testRegistryAgent, never()).persist(any(), any(), any(), any());
    }

    private AnalysisRequest analysisRequest() {
        return new AnalysisRequest("github", "acme/backend", "1234", "https://staging.acme.com",
            "/tmp/qa-forge-tests/acme-backend", null, "cli");
    }

    private PullRequest pullRequest() {
        return new PullRequest("1234", "github", "acme/backend", "Add checkout payment",
            "desc", List.of(), "main", "feature", "sha123", "rohan", List.of(), "https://github.com/acme/backend/pull/1234");
    }

    private TestScenario scenario(ScenarioType type, TestLayer layer) {
        return new TestScenario(UUID.randomUUID().toString(), "User completes checkout", "desc", "Checkout",
            type, layer, List.of("step"), "/checkout", false, null, null);
    }
}
