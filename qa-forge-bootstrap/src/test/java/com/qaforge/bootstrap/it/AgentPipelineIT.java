package com.qaforge.bootstrap.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.infrastructure.persistence.repository.TestCaseRepository;
import com.qaforge.infrastructure.persistence.repository.TestRunRepository;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PRD §19.4: full pipeline integration test. Runs the real Spring context (Flyway-migrated
 * PostgreSQL via Testcontainers, real REST layer on a random port) with only two seams
 * replaced, exactly as the PRD specifies: a WireMock server standing in for the GitHub API,
 * and a canned {@link ChatModel} standing in for the real LLM. {@code qaforge.mcp.playwright
 * .enabled=false} (already set by {@code application-test.yml}) keeps the execution agent off
 * a real Playwright MCP process, so its result also comes from the canned {@code ChatModel}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(AgentPipelineIT.FakeChatModelConfig.class)
class AgentPipelineIT {

    private static final String REPOSITORY = "acme/backend";
    private static final String PR_NUMBER = "42";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static WireMockServer githubApi;

    @BeforeAll
    static void startWireMock() {
        githubApi = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        githubApi.start();
    }

    @AfterAll
    static void stopWireMock() {
        githubApi.stop();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("qaforge.github.base-url", () -> githubApi.baseUrl());
    }

    @TestConfiguration
    static class FakeChatModelConfig {
        @Bean
        @Primary
        ChatModel fakeChatModel() {
            return new CannedResponseChatModel();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestRunRepository testRunRepository;

    @TempDir
    private Path outputDir;

    @Test
    void analyzeEndToEndPersistsGeneratedTestsAndReportsToGitHub() throws JsonProcessingException {
        stubGitHubPullRequest();
        stubGitHubFiles();
        stubGitHubComments();
        stubGitHubCheckRuns();

        AnalysisResult result = callAnalyze();

        assertThat(result.outcome()).isEqualTo(AnalysisResult.OUTCOME_SUCCESS);
        assertThat(result.newTestsGenerated()).isEqualTo(1);
        assertThat(result.executionSummary().total()).isEqualTo(1);
        assertThat(result.executionSummary().passed()).isEqualTo(1);
        assertThat(result.generatedFiles().playwright()).hasSize(1);

        assertThat(testCaseRepository.findByRepositoryAndStatus(REPOSITORY, "ACTIVE")).hasSize(1);
        assertThat(testRunRepository.findAll()).hasSize(1);

        githubApi.verify(2, postRequestedFor(urlEqualTo("/repos/acme/backend/check-runs")));
    }

    private AnalysisResult callAnalyze() throws JsonProcessingException {
        String requestBody = """
            {
              "vcsType": "github",
              "repositoryFullName": "%s",
              "prNumber": "%s",
              "targetAppBaseUrl": "http://localhost:9999",
              "testOutputDirectory": "%s"
            }
            """.formatted(REPOSITORY, PR_NUMBER, outputDir.toString().replace("\\", "\\\\"));

        ResponseEntity<String> response = RestClient.create()
            .post()
            .uri("http://localhost:{port}/api/v1/analyze", port)
            .header("Authorization", basicAuthHeader("qaforge", "changeme"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Parsed with a plain Jackson 2 ObjectMapper rather than RestClient's default message
        // converter negotiation, matching how the rest of this codebase (e.g. LlmJsonCaller)
        // deliberately owns its own Jackson 2 instance instead of relying on Boot 4.1's
        // ambient Jackson 3 stack.
        return new ObjectMapper().readValue(response.getBody(), AnalysisResult.class);
    }

    private String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private void stubGitHubPullRequest() {
        String prJson = """
            {
              "number": 42,
              "title": "Add checkout confirmation dialog",
              "body": "Adds a confirmation step before checkout completes.",
              "labels": [],
              "base": {"ref": "main", "sha": "base-sha"},
              "head": {"ref": "feature/checkout-confirm", "sha": "head-sha-123"},
              "user": {"login": "octocat"},
              "html_url": "https://github.com/acme/backend/pull/42"
            }
            """;
        githubApi.stubFor(get(urlEqualTo("/repos/acme/backend/pulls/42"))
            .withHeader("Accept", equalTo("application/vnd.github+json"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(prJson)));

        githubApi.stubFor(get(urlEqualTo("/repos/acme/backend/pulls/42"))
            .withHeader("Accept", equalTo("application/vnd.github.v3.diff"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("""
                diff --git a/src/checkout/Checkout.tsx b/src/checkout/Checkout.tsx
                index 111..222 100644
                --- a/src/checkout/Checkout.tsx
                +++ b/src/checkout/Checkout.tsx
                @@ -1,3 +1,6 @@
                +function ConfirmDialog() { return null; }
                """)));
    }

    private void stubGitHubFiles() {
        githubApi.stubFor(get(urlPathEqualTo("/repos/acme/backend/pulls/42/files"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                [
                  {"filename": "src/checkout/Checkout.tsx", "status": "modified", "additions": 3, "deletions": 0,
                   "patch": "@@ -1,3 +1,6 @@\\n+function ConfirmDialog() { return null; }"}
                ]
                """)));
    }

    private void stubGitHubComments() {
        githubApi.stubFor(get(urlPathEqualTo("/repos/acme/backend/issues/42/comments"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));
    }

    private void stubGitHubCheckRuns() {
        githubApi.stubFor(post(urlEqualTo("/repos/acme/backend/check-runs"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{}")));
    }
}
