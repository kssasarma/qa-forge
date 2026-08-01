# QA Forge — AI-Powered Automated Test Generation Platform

## Product Requirements Document · v2.0 · 2026-08-02

> **Purpose of this document:** A complete, implementation-ready specification. Any AI coding
> agent (Claude Code, GitHub Copilot, Cursor) must be able to produce a production-quality
> codebase solely from this PRD without asking clarifying questions. Every ambiguous decision
> is resolved here with a stated rationale. There are no phases — this is the full product spec.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Problem Statement](#2-problem-statement)
3. [Goals and Non-Goals](#3-goals-and-non-goals)
4. [Personas and Use Cases](#4-personas-and-use-cases)
5. [System Architecture](#5-system-architecture)
6. [Technology Stack (Exact Versions)](#6-technology-stack)
7. [LLM Provider Abstraction](#7-llm-provider-abstraction)
8. [Project Structure](#8-project-structure)
9. [Module Specifications](#9-module-specifications)
10. [Agent Pipeline Design](#10-agent-pipeline-design)
11. [Data Models and Database Schema](#11-data-models-and-database-schema)
12. [API Contract — REST and CLI](#12-api-contract)
13. [MCP Integration Specification](#13-mcp-integration-specification)
14. [Test Generation Specification](#14-test-generation-specification)
15. [Configuration Reference](#15-configuration-reference)
16. [Error Handling and Resilience](#16-error-handling-and-resilience)
17. [Security](#17-security)
18. [Observability and Monitoring](#18-observability-and-monitoring)
19. [Testing Strategy](#19-testing-strategy)
20. [CI/CD Integration](#20-cicd-integration)
21. [Non-Functional Requirements](#21-non-functional-requirements)
22. [Known Constraints and Limitations](#22-known-constraints-and-limitations)
23. [Glossary](#23-glossary)

---

## 1. Overview

**QA Forge** is an AI-powered, self-hosted test generation and regression management
platform. It reads pull request metadata (code diff, description, acceptance criteria,
reviewer comments), reasons about what changed, generates three classes of tests — Playwright
TypeScript end-to-end tests, RestAssured Java API tests, and JDBC database validation tests
— executes the UI tests via the Playwright MCP server, persists everything in a versioned
test registry, enforces a regression gate on subsequent PRs, and presents run history and
coverage maps through a React dashboard.

QA Forge is delivered as a **single Spring Boot 4.1.0 application** exposing both a Spring
Shell 4.0.3 CLI and HTTP endpoints (webhooks, REST API, dashboard), runnable on any machine
with Java 21 and Node.js 20. The LLM layer is **fully vendor-agnostic**: any provider
supported by Spring AI 2.0.0 — including Anthropic, OpenAI, Azure OpenAI, Ollama (local),
Gemini, Mistral, and any OpenAI-compatible endpoint — can be activated by changing one
dependency and one config block, with zero application-code changes.

---

## 2. Problem Statement

In most engineering teams, QA engineers must:

- Manually read every PR and infer which user flows are affected
- Write Playwright / RestAssured / JDBC tests from scratch for each change
- Keep regression suites up to date as the codebase evolves
- Block release pipelines when regression fails — with no automatic understanding of root cause

This is slow, error-prone, and does not scale. New features ship without coverage; existing
tests rot silently; testers spend more time chasing context than writing tests.

**QA Forge automates the context-gathering, impact analysis, planning, and generation
phases**, leaving QA engineers to review AI-generated tests, approve them, and focus on
exploratory and edge-case testing.

---

## 3. Goals and Non-Goals

### 3.1 Goals

| ID | Goal |
|----|------|
| G-01 | Read PR diff + description + acceptance criteria and derive test scenarios |
| G-02 | Generate idiomatic Playwright TypeScript (`.spec.ts`) UI end-to-end tests |
| G-03 | Generate RestAssured Java (`.java`) API integration tests from OpenAPI spec diff |
| G-04 | Generate JDBC database validation tests from schema migration diffs |
| G-05 | Execute UI tests via Playwright MCP server; self-heal on locator failure |
| G-06 | Persist a test registry: metadata, lineage, run history for every generated test |
| G-07 | Run the full existing suite before generating new tests; gate on configurable pass rate |
| G-08 | Expose a Spring Shell CLI for developer use |
| G-09 | Expose a REST API and GitHub webhook endpoint for CI/CD use |
| G-10 | Integrate with GitHub: PR diff, body, comments, Check Run API |
| G-11 | Integrate with GitLab: merge request diff, body, comments (pluggable adapter) |
| G-12 | Integrate with JIRA for acceptance criteria extraction |
| G-13 | Post results back to the PR/MR as a commit status check |
| G-14 | React SPA dashboard: test coverage map, run history, per-test timeline |
| G-15 | Test obsolescence detection: mark tests OBSOLETE when their target code is deleted |
| G-16 | Export complete test suite as a downloadable ZIP |
| G-17 | Support any LLM via Spring AI 2.0.0's ChatModel abstraction — zero code change to swap |
| G-18 | Support OpenAI-compatible endpoints (Groq, LM Studio, vLLM, Together.ai, etc.) |

### 3.2 Non-Goals

| ID | Non-Goal | Rationale |
|----|----------|-----------|
| NG-01 | Visual regression (pixel-diff screenshots) | Playwright accessibility tree is sufficient; vision models are costly |
| NG-02 | Multi-tenant SaaS hosting | Self-hosted, single-team tool |
| NG-03 | Storing generated test files in JFrog Artifactory | Test *code* belongs in git, not binary artifact registries |
| NG-04 | Replacing human test review | AI generates; humans approve before merging test code |
| NG-05 | Mobile app testing (Appium, Detox) | Out of scope for web-first tooling |

---

## 4. Personas and Use Cases

### Persona A — QA Engineer (Priya)

- Receives Slack notification when a PR is opened
- Runs `qa analyze --repo acme/backend --pr 1234 --base-url https://staging.acme.com`
- Reviews generated `.spec.ts` and `.java` test files in `~/.qa-forge/tests/acme-backend/`
- Approves or edits before merging test files into the test repository

### Persona B — Developer (Rohan)

- Opens a PR; GitHub Actions calls the QA Forge webhook automatically
- Sees a GitHub Check on the PR: "QA Forge — Regression: 23/23 passed ✓"
- Does not write UI or API tests manually

### Persona C — Tech Lead (Satya)

- Views the React dashboard at `https://qa-forge.internal`
- Sees coverage map: which features have Playwright, RestAssured, and DB tests
- Identifies gaps in test coverage before release

### Persona D — CI/CD Pipeline (automated)

- Calls `POST /api/v1/analyze` on PR opened
- Calls `POST /api/v1/regression` on PR ready-for-review
- Reads JSON response; fails the pipeline if `gateResult == "BLOCKED"`

---

## 5. System Architecture

### 5.1 Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          QA Forge Application                               │
│                                                                              │
│  ┌───────────────┐  ┌──────────────────────────────────────────────────┐   │
│  │ Spring Shell  │  │                  Agent Pipeline                    │   │
│  │ CLI (4.0.3)   │  │                                                    │   │
│  └───────┬───────┘  │  ContextGatheringAgent                             │   │
│          │           │       ↓                                             │   │
│  ┌───────┴───────┐  │  CodeAnalysisAgent                                  │   │
│  │  REST API     │  │       ↓                                             │   │
│  │  Webhook      │  │  TestPlanningAgent                                  │   │
│  │  Controller   │  │       ↓ (parallel)                                 │   │
│  └───────┬───────┘  │  ┌─────────────────┬──────────────────────────┐   │   │
│          │           │  │PlaywrightGen    │RestAssuredGen │ DbValid  │   │   │
│          │           │  │Agent            │Agent          │ Agent    │   │   │
│          └──────────►│  └────────┬────────┴──────┬────────┴────┬─────┘   │   │
│                      │           ↓               ↓             ↓          │   │
│                      │  TestExecution    (written to disk)  (written      │   │
│                      │  Agent                               to disk)      │   │
│                      │       ↓ (via Playwright MCP)                       │   │
│                      │  TestRegistry                                       │   │
│                      │  Agent → PostgreSQL                                 │   │
│                      └──────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Integrations: GitHubAdapter · GitLabAdapter · JiraAdapter           │   │
│  │               GitHubChecksAdapter · LocalTestFileStore               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌────────────────────────────────────┐                                      │
│  │  React SPA Dashboard (served by   │                                      │
│  │  Spring Boot as static resources) │                                      │
│  └────────────────────────────────────┘                                      │
└────────────────────────────────────────────────────────────────────────────┘
       │                    │                          │
       ▼                    ▼                          ▼
  GitHub/GitLab        Playwright MCP           LLM Provider
  REST APIs            (STDIO child             (Anthropic · OpenAI ·
  JIRA REST API        process, Node.js)        Azure · Ollama · Gemini
                                                · Mistral · Any OAIC)
```

### 5.2 Architectural Style

QA Forge follows **Hexagonal Architecture (Ports and Adapters)**:

- **Domain** (`qa-forge-domain`): Pure Java records and port interfaces. Zero Spring,
  zero I/O, zero LLM imports. Framework-agnostic by design.
- **Application** (`qa-forge-application`): Agent orchestration and use-case services.
  Injects `ChatModel` (Spring AI interface) and domain ports only.
- **Infrastructure** (`qa-forge-infrastructure`): Spring-managed adapters. Implements every
  out-port: GitHub, GitLab, JIRA, Playwright MCP, PostgreSQL, local filesystem.
- **Bootstrap** (`qa-forge-bootstrap`): Spring Boot entry point, REST/CLI/dashboard, LLM
  provider configuration, security, observability wiring.

### 5.3 Concurrency Model

- CLI commands are synchronous (block until complete)
- REST `POST /analyze` and `POST /regression` are **synchronous** (client waits; use a
  reasonable HTTP timeout of 180s)
- `POST /webhook/github` and `POST /webhook/gitlab` return `202 Accepted` immediately;
  processing runs on a dedicated `@Async` thread pool named `webhookExecutor` (configurable
  thread count, default 4)
- Generation agents (Playwright, RestAssured, DB) run **in parallel** using
  `CompletableFuture` after the planning agent finishes

---

## 6. Technology Stack

All versions are pinned exactly. An implementing agent must not substitute alternatives.

| Component | Artifact / Package | Version |
|-----------|-------------------|---------|
| Java | Eclipse Temurin | **21.0.3 LTS** |
| Build tool | Apache Maven | **3.9.9** |
| Spring Boot | `spring-boot-starter-parent` | **4.1.0** |
| Spring Framework | (managed by Boot) | **7.0.8** |
| Spring AI BOM | `spring-ai-bom` | **2.0.0** |
| Spring Shell | `spring-shell-starter` | **4.0.3** |
| Spring Security | (managed by Boot) | **7.1.0** |
| Spring Data JPA | (managed by Boot) | **2026.0.0** |
| Flyway | (managed by Boot) | **10.20.1** |
| PostgreSQL JDBC | (managed by Boot) | **42.7.4** |
| H2 (tests) | (managed by Boot) | **2.3.232** |
| Testcontainers | (managed by Boot) | **1.20.4** |
| JUnit 5 | (managed by Boot) | **5.11.4** |
| Jackson | (managed by Boot — Jackson 3) | **3.0.0** |
| Micrometer | (managed by Boot) | **1.15.0** |
| OpenTelemetry Java agent | standalone jar | **2.8.0** |
| Springdoc OpenAPI | `springdoc-openapi-starter-webmvc-ui` | **3.0.0** |
| JSpecify | `org.jspecify:jspecify` | **1.0.0** |
| Playwright MCP (Node) | `@playwright/mcp` (npm) | **latest** |
| Playwright Test (Node) | `@playwright/test` (npm) | **1.49.0** |
| Node.js (host dependency) | — | **20.18.0 LTS** |
| React (dashboard) | `react` + `react-dom` (npm) | **19.0.0** |
| Vite (dashboard build) | `vite` (npm) | **6.0.0** |
| Tailwind CSS (dashboard) | `tailwindcss` (npm) | **4.0.0** |
| Recharts (dashboard) | `recharts` (npm) | **2.15.0** |

> **Jakarta EE note:** Spring Boot 4.x uses Jakarta EE 11. All imports use `jakarta.*`.
> Never use `javax.*`.
>
> **Jackson 3 note:** Spring Boot 4.x ships Jackson 3. Use `com.fasterxml.jackson.core.*`
> as before — package names are unchanged; only the major version bumped.

---

## 7. LLM Provider Abstraction

This section is the canonical reference for the vendor-agnostic LLM design. No other
section overrides it.

### 7.1 Core Rule

**The only Spring AI type that `qa-forge-application` and `qa-forge-domain` may reference
is `org.springframework.ai.chat.model.ChatModel`.** No vendor-specific class
(`AnthropicChatModel`, `OpenAiChatModel`, `OllamaChatModel`, etc.) may appear outside
`qa-forge-bootstrap`.

`ChatClient` is assembled in `LlmConfig` (bootstrap) from the injected `ChatModel` and
passed into agents as a plain `ChatClient` reference.

### 7.2 Supported Providers

| Provider | Starter Artifact ID (groupId: `org.springframework.ai`) | Key Config Properties |
|----------|--------------------------------------------------------|----------------------|
| Anthropic Claude | `spring-ai-starter-model-anthropic` | `spring.ai.anthropic.api-key`, `spring.ai.anthropic.chat.options.model` |
| OpenAI | `spring-ai-starter-model-openai` | `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model` |
| Azure OpenAI | `spring-ai-starter-model-azure-openai` | `spring.ai.azure.openai.api-key`, `spring.ai.azure.openai.endpoint`, `spring.ai.azure.openai.chat.options.deployment-name` |
| Ollama (local) | `spring-ai-starter-model-ollama` | `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model` |
| Google Vertex AI (Gemini) | `spring-ai-starter-model-vertex-ai-gemini` | `spring.ai.vertex.ai.gemini.project-id`, `spring.ai.vertex.ai.gemini.location`, `spring.ai.vertex.ai.gemini.chat.options.model` |
| Mistral AI | `spring-ai-starter-model-mistral-ai` | `spring.ai.mistralai.api-key`, `spring.ai.mistralai.chat.options.model` |
| **Any OpenAI-compatible endpoint** | `spring-ai-starter-model-openai` | `spring.ai.openai.base-url=<provider-url>`, `spring.ai.openai.api-key=<key>` |

### 7.3 OpenAI-Compatible Endpoints

The OpenAI starter works unmodified against any server that implements the OpenAI
`/v1/chat/completions` API. Examples:

```yaml
# Groq (fast inference, Llama / Mixtral models)
spring.ai.openai.base-url: https://api.groq.com/openai
spring.ai.openai.api-key: ${GROQ_API_KEY}
spring.ai.openai.chat.options.model: llama-3.3-70b-versatile

# LM Studio (local, no key needed)
spring.ai.openai.base-url: http://localhost:1234/v1
spring.ai.openai.api-key: not-needed
spring.ai.openai.chat.options.model: local-model

# vLLM (self-hosted)
spring.ai.openai.base-url: http://vllm-host:8000/v1
spring.ai.openai.api-key: ${VLLM_API_KEY}
spring.ai.openai.chat.options.model: meta-llama/Llama-3.1-70B-Instruct

# Together.ai
spring.ai.openai.base-url: https://api.together.xyz/v1
spring.ai.openai.api-key: ${TOGETHER_API_KEY}
spring.ai.openai.chat.options.model: meta-llama/Llama-3.3-70B-Instruct-Turbo
```

### 7.4 LLM Configuration in `qa-forge-bootstrap`

```java
// LlmConfig.java
@Configuration
public class LlmConfig {

    /**
     * Builds the primary ChatClient from whichever ChatModel is on the classpath.
     * When multiple provider starters are present, qualify the desired model
     * with @Qualifier on the ChatModel parameter.
     *
     * Temperature is set to 0.0 globally; agents that need creativity
     * must override via ChatOptionsBuilder in their own call.
     */
    @Bean
    @Primary
    public ChatClient primaryChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(
                ChatOptionsBuilder.builder()
                    .temperature(0.0)
                    .build()
            )
            .build();
    }
}
```

### 7.5 Multi-Provider Routing (Optional / Advanced)

If a deployer wants to route different agents to different models (e.g., cheap model for
planning, frontier model for generation), they declare named beans:

```java
// MultiProviderLlmConfig.java  — only needed when multiple starters present
@Configuration
@ConditionalOnProperty(name = "qaforge.ai.multi-provider.enabled", havingValue = "true")
public class MultiProviderLlmConfig {

    @Bean("planningChatClient")
    public ChatClient planningChatClient(@Qualifier("openAiChatModel") ChatModel model) {
        return ChatClient.builder(model).build(); // cheap model for planning
    }

    @Bean("generationChatClient")
    public ChatClient generationChatClient(@Qualifier("anthropicChatModel") ChatModel model) {
        return ChatClient.builder(model).build(); // frontier model for generation
    }
}
```

Agents are designed to accept a `ChatClient` via constructor injection; they do not care
which provider it wraps.

### 7.6 Switching Providers — Step-by-Step

To swap from Anthropic to Ollama (example), a deployer:

1. In `pom.xml` (bootstrap module): replace `spring-ai-starter-model-anthropic` with
   `spring-ai-starter-model-ollama`
2. In `application.yml`: remove `spring.ai.anthropic.*`, add `spring.ai.ollama.*`
3. Remove `ANTHROPIC_API_KEY` env var; set `OLLAMA_BASE_URL` if non-default
4. Rebuild and restart — zero application code changes

### 7.7 Minimum Model Requirements

Agents rely on structured JSON output (no tools). The model chosen must support:
- System prompts
- Minimum 8,000-token context window
- Instruction-following capable of outputting pure JSON

Recommended minimum: any GPT-4-class, Claude 3-class, Llama 3.1 70B+, or Mistral Large.

---

## 8. Project Structure

```
qa-forge/
├── pom.xml                                    ← Parent POM (packaging = pom)
│
├── qa-forge-domain/                           ← Zero Spring, zero I/O
│   ├── pom.xml
│   └── src/main/java/com/qaforge/domain/
│       ├── model/
│       │   ├── PullRequest.java
│       │   ├── CodeDiff.java
│       │   ├── ChangedFile.java
│       │   ├── AcceptanceCriteria.java
│       │   ├── ContextSummary.java
│       │   ├── ImpactAssessment.java
│       │   ├── ImpactedFlow.java
│       │   ├── TestScenario.java
│       │   ├── ScenarioType.java             ← enum: NEW | REGRESSION_UPDATE | SKIP
│       │   ├── TestLayer.java                ← enum: PLAYWRIGHT | REST_ASSURED | DB_VALIDATION
│       │   ├── GeneratedTest.java
│       │   ├── ExecutionResult.java
│       │   ├── ExecutionStatus.java          ← enum: PASSED | FAILED | SKIPPED | ERROR
│       │   ├── TestCase.java
│       │   ├── TestRun.java
│       │   ├── AnalysisRequest.java
│       │   └── RegressionRequest.java
│       ├── port/
│       │   ├── in/
│       │   │   ├── AnalyzePort.java
│       │   │   └── RegressionPort.java
│       │   └── out/
│       │       ├── PullRequestPort.java
│       │       ├── MergeRequestPort.java     ← GitLab equivalent
│       │       ├── AcceptanceCriteriaPort.java
│       │       ├── VcsChecksPort.java        ← post status check to GitHub/GitLab
│       │       ├── TestRegistryPort.java
│       │       ├── TestExecutorPort.java
│       │       └── TestFileStorePort.java
│       └── exception/
│           ├── QaForgeException.java
│           ├── PrNotFoundException.java
│           ├── TestExecutionException.java
│           ├── RegistryConflictException.java
│           └── LlmParseException.java
│
├── qa-forge-application/                      ← Spring + Spring AI (ChatModel only)
│   ├── pom.xml
│   └── src/main/java/com/qaforge/application/
│       ├── agent/
│       │   ├── ContextGatheringAgent.java
│       │   ├── CodeAnalysisAgent.java
│       │   ├── TestPlanningAgent.java
│       │   ├── PlaywrightGenerationAgent.java
│       │   ├── RestAssuredGenerationAgent.java
│       │   ├── DbValidationGenerationAgent.java
│       │   ├── TestExecutionAgent.java        ← uses Playwright MCP tools
│       │   ├── TestRegistryAgent.java         ← plain service, no LLM
│       │   └── SelfHealingLocatorAgent.java   ← retries failed locators
│       ├── orchestration/
│       │   └── AgentOrchestrator.java
│       ├── usecase/
│       │   ├── AnalyzeUseCase.java
│       │   └── RegressionUseCase.java
│       └── prompt/
│           ├── ContextGatheringPrompts.java
│           ├── CodeAnalysisPrompts.java
│           ├── TestPlanningPrompts.java
│           ├── PlaywrightGenerationPrompts.java
│           ├── RestAssuredGenerationPrompts.java
│           ├── DbValidationGenerationPrompts.java
│           └── ExecutionPrompts.java
│
├── qa-forge-infrastructure/                   ← Spring adapters
│   ├── pom.xml
│   └── src/main/java/com/qaforge/infrastructure/
│       ├── github/
│       │   ├── GitHubPrAdapter.java          ← implements PullRequestPort
│       │   ├── GitHubChecksAdapter.java      ← implements VcsChecksPort
│       │   └── GitHubProperties.java
│       ├── gitlab/
│       │   ├── GitLabMrAdapter.java          ← implements MergeRequestPort
│       │   ├── GitLabChecksAdapter.java
│       │   └── GitLabProperties.java
│       ├── jira/
│       │   ├── JiraAcAdapter.java            ← implements AcceptanceCriteriaPort
│       │   └── JiraProperties.java
│       ├── mcp/
│       │   ├── PlaywrightMcpConfig.java
│       │   └── PlaywrightTestExecutor.java   ← implements TestExecutorPort
│       ├── persistence/
│       │   ├── entity/
│       │   │   ├── TestCaseEntity.java
│       │   │   ├── TestRunEntity.java
│       │   │   └── TestRunItemEntity.java
│       │   ├── repository/
│       │   │   ├── TestCaseRepository.java
│       │   │   ├── TestRunRepository.java
│       │   │   └── TestRunItemRepository.java
│       │   └── adapter/
│       │       └── JpaTestRegistryAdapter.java ← implements TestRegistryPort
│       └── filesystem/
│           └── LocalTestFileStore.java         ← implements TestFileStorePort
│
└── qa-forge-bootstrap/                         ← Entry point, config, controllers
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/qaforge/bootstrap/
        │   │   ├── QaForgeApplication.java
        │   │   ├── config/
        │   │   │   ├── LlmConfig.java
        │   │   │   ├── AsyncConfig.java
        │   │   │   ├── SecurityConfig.java
        │   │   │   └── OpenApiConfig.java
        │   │   ├── rest/
        │   │   │   ├── AnalyzeController.java
        │   │   │   ├── RegressionController.java
        │   │   │   ├── TestsController.java
        │   │   │   ├── RunsController.java
        │   │   │   ├── ExportController.java
        │   │   │   ├── WebhookController.java
        │   │   │   └── dto/          ← Request/Response records
        │   │   ├── cli/
        │   │   │   ├── AnalyzeCommand.java
        │   │   │   ├── RegressionCommand.java
        │   │   │   ├── ListCommand.java
        │   │   │   └── RunCommand.java
        │   │   └── error/
        │   │       └── GlobalExceptionHandler.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-dev.yml
        │       ├── application-prod.yml
        │       ├── db/migration/
        │       │   ├── V1__create_test_cases.sql
        │       │   ├── V2__create_test_runs.sql
        │       │   └── V3__create_test_run_items.sql
        │       └── static/                   ← React build output (generated by Vite)
        └── test/java/com/qaforge/bootstrap/
            ├── rest/
            └── integration/

qa-forge-dashboard/                            ← React SPA (separate build)
├── package.json
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── pages/
    │   ├── DashboardPage.tsx
    │   ├── CoveragePage.tsx
    │   ├── RunsPage.tsx
    │   └── TestDetailPage.tsx
    ├── components/
    │   ├── CoverageMap.tsx
    │   ├── RunHistoryChart.tsx
    │   ├── TestCaseTable.tsx
    │   └── StatusBadge.tsx
    └── api/
        └── qaForgeApi.ts               ← typed fetch client for REST endpoints
```

> **Dashboard build integration:** The Vite build outputs to
> `qa-forge-bootstrap/src/main/resources/static`. The Maven `exec-maven-plugin` runs
> `npm run build` in `qa-forge-dashboard` before packaging. Spring Boot serves the SPA
> from `/` (all non-API paths). The API is served from `/api/v1/*`.

---

## 9. Module Specifications

### 9.1 `qa-forge-domain` — Domain Model

#### 9.1.1 Core Records

```java
// PullRequest.java — used for both GitHub PRs and GitLab MRs
public record PullRequest(
    String id,                     // PR/MR number as string
    String vcsType,                // "github" | "gitlab"
    String repositoryFullName,     // e.g. "acme/backend"
    String title,
    String description,
    List<String> labels,
    String baseBranch,
    String headBranch,
    String headSha,
    String authorLogin,
    List<String> reviewerComments,
    String webUrl                  // link to the PR/MR
) {}

// CodeDiff.java
public record CodeDiff(
    String rawDiff,
    List<ChangedFile> changedFiles,
    boolean truncated              // true when diff exceeded token budget
) {}

// ChangedFile.java
public record ChangedFile(
    String filePath,
    String changeType,             // "ADDED" | "MODIFIED" | "DELETED" | "RENAMED"
    int linesAdded,
    int linesDeleted,
    String patchText
) {}

// AcceptanceCriteria.java
public record AcceptanceCriteria(
    String source,                 // "github_body" | "gitlab_body" | "jira" | "derived"
    String rawText,
    List<String> parsedCriteria
) {}

// ContextSummary.java — output of ContextGatheringAgent
public record ContextSummary(
    String prSummary,
    List<String> changedAreas,
    List<String> acceptanceCriteriaLines,
    List<String> reviewerConcerns,
    String estimatedRisk           // "LOW" | "MEDIUM" | "HIGH"
) {}

// ImpactedFlow.java
public record ImpactedFlow(
    String flow,
    String reason,
    String severity                // "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
) {}

// ImpactAssessment.java — output of CodeAnalysisAgent
public record ImpactAssessment(
    List<ImpactedFlow> impactedUserFlows,
    List<String> safeToSkipFlows,
    boolean backendOnlyChange,
    List<String> uiPagesAffected,
    List<String> newApiEndpoints,
    List<String> changedDbTables
) {}

// TestScenario.java — output of TestPlanningAgent
public record TestScenario(
    String id,                     // UUID v4
    String title,
    String description,
    String userFlow,
    ScenarioType type,
    TestLayer layer,               // which generator to use
    List<String> steps,
    String targetPath,             // relative URL for Playwright; path template for RestAssured
    boolean requiresAuth,
    String openApiOperationId,     // null unless layer == REST_ASSURED
    String dbTable                 // null unless layer == DB_VALIDATION
) {}

// GeneratedTest.java — output of any generation agent
public record GeneratedTest(
    String scenarioId,
    String fileName,
    String content,                // source code (TypeScript or Java or SQL)
    TestLayer layer,
    List<String> tags
) {}

// ExecutionResult.java
public record ExecutionResult(
    String scenarioId,
    String fileName,
    ExecutionStatus status,
    String errorMessage,
    long durationMs,
    int retryCount
) {}

// AnalysisRequest.java
public record AnalysisRequest(
    String vcsType,                // "github" | "gitlab"
    String repositoryFullName,
    String prNumber,
    String targetAppBaseUrl,
    String testOutputDirectory,
    String openApiSpecUrl,         // optional; enables RestAssured generation
    String triggeredBy             // "cli" | "webhook"
) {}
```

#### 9.1.2 Port Interfaces

```java
// in/AnalyzePort.java
public interface AnalyzePort {
    AnalysisResult analyze(AnalysisRequest request);
}

// in/RegressionPort.java
public interface RegressionPort {
    RegressionResult runRegression(RegressionRequest request);
}

// out/PullRequestPort.java
public interface PullRequestPort {
    PullRequest fetch(String repositoryFullName, String prNumber);
    CodeDiff fetchDiff(String repositoryFullName, String prNumber);
}

// out/MergeRequestPort.java (GitLab)
public interface MergeRequestPort {
    PullRequest fetch(String projectPath, String mrIid);
    CodeDiff fetchDiff(String projectPath, String mrIid);
}

// out/AcceptanceCriteriaPort.java
public interface AcceptanceCriteriaPort {
    Optional<AcceptanceCriteria> fetch(PullRequest pr);
}

// out/VcsChecksPort.java
public interface VcsChecksPort {
    void postPending(PullRequest pr, String checkName, String detailsUrl);
    void postSuccess(PullRequest pr, String checkName, String summary);
    void postFailure(PullRequest pr, String checkName, String summary);
}

// out/TestRegistryPort.java
public interface TestRegistryPort {
    void saveAll(List<GeneratedTest> tests, String prNumber, String repository, String headSha);
    List<TestCase> findActive(String repository);
    List<TestCase> findByPr(String prNumber, String repository);
    Optional<TestCase> findByFileName(String fileName);
    void markObsolete(List<String> fileNames);
    void updateExecutionStats(String fileName, ExecutionStatus status, long durationMs);
}

// out/TestExecutorPort.java
public interface TestExecutorPort {
    List<ExecutionResult> executeGenerated(List<GeneratedTest> tests, String baseUrl);
    List<ExecutionResult> executeExisting(List<TestCase> tests, String baseUrl);
}

// out/TestFileStorePort.java
public interface TestFileStorePort {
    void write(GeneratedTest test, String outputDirectory);
    void writeAll(List<GeneratedTest> tests, String outputDirectory);
    void writePlaywrightConfig(String outputDirectory);
    byte[] exportZip(String repository);
}
```

---

### 9.2 `qa-forge-application` — Agent Implementations

#### 9.2.1 AgentOrchestrator

The orchestrator wires all agents and ports sequentially (with parallelism at generation):

```java
@Service
public class AgentOrchestrator {

    // All injected via constructor
    private final ContextGatheringAgent contextAgent;
    private final CodeAnalysisAgent analysisAgent;
    private final TestPlanningAgent planningAgent;
    private final PlaywrightGenerationAgent playwrightAgent;
    private final RestAssuredGenerationAgent restAssuredAgent;
    private final DbValidationGenerationAgent dbValidationAgent;
    private final TestExecutionAgent executionAgent;
    private final TestRegistryAgent registryAgent;
    private final PullRequestPort pullRequestPort;
    private final MergeRequestPort mergeRequestPort;
    private final AcceptanceCriteriaPort acPort;
    private final TestRegistryPort registryPort;
    private final TestFileStorePort fileStorePort;
    private final VcsChecksPort vcsChecksPort;

    public AnalysisResult orchestrate(AnalysisRequest request) {
        // 1. Post "pending" check to VCS
        // 2. Fetch PR and diff from the correct VCS adapter
        // 3. Fetch ACs
        // 4. Run ContextGatheringAgent
        // 5. Run CodeAnalysisAgent
        // 6. Load existing registry for deduplication
        // 7. Run TestPlanningAgent
        // 8. Fan-out generation in parallel (CompletableFuture)
        //      a. PlaywrightGenerationAgent for PLAYWRIGHT scenarios
        //      b. RestAssuredGenerationAgent for REST_ASSURED scenarios
        //         (only if openApiSpecUrl present)
        //      c. DbValidationGenerationAgent for DB_VALIDATION scenarios
        //         (only if changedDbTables non-empty)
        // 9. Collect all GeneratedTests
        // 10. Run TestExecutionAgent on Playwright tests only
        //     (RestAssured and DB tests are written to disk, not executed here)
        // 11. Apply self-healing if any execution result is FAILED
        // 12. Write all files via TestFileStorePort
        // 13. Persist via TestRegistryAgent
        // 14. Post success/failure check to VCS
        // 15. Detect obsolescence: compare changedFiles deletions vs active registry
        // 16. Return AnalysisResult
    }
}
```

#### 9.2.2 ContextGatheringAgent

**System prompt (complete — do not truncate):**

```
You are a senior QA analyst. You receive these inputs:
- Pull request title, description (markdown), reviewer comments
- A summary of changed files (paths, added/deleted line counts)
- Optional acceptance criteria text

Produce exactly this JSON structure and nothing else:
{
  "prSummary": "<one paragraph, plain English, what changed and why>",
  "changedAreas": ["<specific feature or page name>", ...],
  "acceptanceCriteriaLines": ["<each individual AC as its own string>", ...],
  "reviewerConcerns": ["<any concern with testing relevance>", ...],
  "estimatedRisk": "LOW | MEDIUM | HIGH"
}

Rules:
- Output ONLY the JSON object. No markdown fences. No preamble.
- changedAreas must be specific: "Checkout > Payment step", not "frontend".
- If AC text is empty, derive it from the description and title.
- estimatedRisk is HIGH when core auth, payment, or data flows are touched.
```

#### 9.2.3 CodeAnalysisAgent

**System prompt:**

```
You are a senior full-stack engineer. You receive a ContextSummary JSON and a raw git diff.

Produce exactly this JSON and nothing else:
{
  "impactedUserFlows": [
    { "flow": "<name>", "reason": "<why impacted>", "severity": "CRITICAL|HIGH|MEDIUM|LOW" }
  ],
  "safeToSkipFlows": ["<flow name>", ...],
  "backendOnlyChange": true | false,
  "uiPagesAffected": ["<route or page name>", ...],
  "newApiEndpoints": ["<METHOD /path>", ...],
  "changedDbTables": ["<table name>", ...]
}

Rules:
- Output ONLY the JSON object.
- A flow is impacted when the diff touches its view, controller, service, or API contract.
- If backendOnlyChange is true, uiPagesAffected must be empty.
- Be conservative: when in doubt, mark as impacted.
- Scan Flyway migration files to populate changedDbTables.
```

#### 9.2.4 TestPlanningAgent

**System prompt:**

```
You are a QA Lead. You receive:
1. An ImpactAssessment JSON
2. A list of existing test cases: [{fileName, scenarioTitle, userFlow, layer}, ...]

Produce exactly this JSON and nothing else:
{
  "scenarios": [
    {
      "id": "<UUID v4>",
      "title": "<short title>",
      "description": "<one sentence: what this test validates>",
      "userFlow": "<flow name>",
      "type": "NEW | REGRESSION_UPDATE | SKIP",
      "layer": "PLAYWRIGHT | REST_ASSURED | DB_VALIDATION",
      "steps": ["<step 1>", ...],
      "targetPath": "<relative URL for PLAYWRIGHT; operation summary for REST_ASSURED; table name for DB_VALIDATION>",
      "requiresAuth": true | false,
      "openApiOperationId": "<operationId or null>",
      "dbTable": "<table name or null>"
    }
  ]
}

Rules:
- Output ONLY the JSON object.
- Assign layer = PLAYWRIGHT for any UI user flow.
- Assign layer = REST_ASSURED only for API-only changes listed in newApiEndpoints.
- Assign layer = DB_VALIDATION only for changes listed in changedDbTables.
- Do NOT duplicate a scenario already in the registry for the same flow and layer.
- type = REGRESSION_UPDATE when an existing test covers the same flow but the flow changed.
- type = SKIP for flows in safeToSkipFlows.
- Limit to 10 scenarios maximum per request.
- Steps must be plain English. Max 8 steps per scenario.
- requiresAuth is true when the flow needs a logged-in user.
```

#### 9.2.5 PlaywrightGenerationAgent

**System prompt:**

```
You are a senior Playwright TypeScript automation engineer.
You receive a TestScenario JSON. Produce a complete Playwright TypeScript spec file.

Requirements:
- Import: import { test, expect } from '@playwright/test';
- Use the Page Object Model: define a simple page class inline above the test blocks.
- Locator priority: getByRole > getByLabel > getByText > getByTestId. Never use CSS or XPath.
- Wrap all assertions with expect().
- beforeEach: navigate to process.env.PLAYWRIGHT_BASE_URL + targetPath.
- If requiresAuth is true, beforeEach must also log in:
    await page.goto('/login');
    await page.getByLabel('Email').fill(process.env.TEST_USER_EMAIL!);
    await page.getByLabel('Password').fill(process.env.TEST_USER_PASSWORD!);
    await page.getByRole('button', { name: 'Sign in' }).click();
- Tag with scenario tags via test.describe name.
- First line must be exactly: // @qa-forge generated — scenario:<scenarioId>
- Output ONLY the TypeScript source. No markdown fences.
```

#### 9.2.6 RestAssuredGenerationAgent

**System prompt:**

```
You are a senior Java test engineer specialising in REST API testing with RestAssured.
You receive a TestScenario JSON (layer = REST_ASSURED) and the relevant OpenAPI operation
JSON for openApiOperationId.

Produce a complete Java test class using:
- JUnit 5 (@ExtendWith, @Test)
- REST Assured 5.x (io.rest-assured:rest-assured:5.5.0)
- Import: import static io.restassured.RestAssured.*;
- Base URI: System.getenv("QA_FORGE_BASE_URL")
- Use @BeforeEach to set RestAssured.baseURI
- If requiresAuth: authenticate via Bearer token fetched from /api/auth/token using
  credentials from TEST_USER_EMAIL / TEST_USER_PASSWORD env vars
- Class name: <PascalCase(title)>Test
- Test method name: <camelCase(first step)>
- Assertions: use assertThat() from Hamcrest and response.then().statusCode()
- First line must be: // @qa-forge generated — scenario:<scenarioId>
- Output ONLY the Java source. No markdown fences.
```

#### 9.2.7 DbValidationGenerationAgent

**System prompt:**

```
You are a senior Java engineer writing database validation tests.
You receive a TestScenario JSON (layer = DB_VALIDATION) and the table name.

Produce a complete Java test class using:
- JUnit 5
- Spring Boot @DataJdbcTest or plain @SpringBootTest with JdbcTemplate injection
- Assertions with assertThat() from AssertJ (org.assertj:assertj-core)
- DataSource URL from QAFORGE_DB_URL env var
- Class name: <PascalCase(dbTable)>DbValidationTest
- Validates: row counts, mandatory column constraints, foreign key integrity,
  and any business rule described in the scenario steps
- First line must be: // @qa-forge generated — scenario:<scenarioId>
- Output ONLY the Java source. No markdown fences.
```

#### 9.2.8 TestExecutionAgent

**Responsibility:** Execute Playwright (`PLAYWRIGHT` layer) tests only via Playwright MCP
tools. RestAssured and DB tests are written to disk — execution of those is delegated to
the consuming team's test runner.

```java
@Service
public class TestExecutionAgent {

    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider playwrightToolProvider;
    private final int maxToolCalls;    // from qaforge.mcp.playwright.max-tool-calls

    public List<ExecutionResult> execute(List<GeneratedTest> playwrightTests, String baseUrl) {
        // For each test, build a condensed scenario description from the file name and tags
        // Call chatClient with playwrightToolProvider, temperature=0.0
        // Parse JSON array response into List<ExecutionResult>
        // If any result is FAILED, invoke SelfHealingLocatorAgent
    }
}
```

#### 9.2.9 SelfHealingLocatorAgent

When `TestExecutionAgent` receives a `FAILED` result with an error message containing
"Element not found" or "strict mode violation", it invokes `SelfHealingLocatorAgent`:

**System prompt:**

```
You are a Playwright expert. A test failed because a locator was not found.
You receive: the failing test TypeScript source, the error message, and the page's
accessibility tree snapshot (from the browser_snapshot tool).

Your task:
1. Use browser_snapshot to get the current accessibility tree.
2. Identify alternative locators from the accessibility tree that match the intent.
3. Rewrite ONLY the failing locator line with the best alternative.
4. Produce the corrected TypeScript source.
5. Output the corrected source only. No markdown fences.
```

The self-healed test replaces the original file on disk and in the registry with
`retryCount = 1`.

---

## 10. Agent Pipeline Design

### 10.1 End-to-End Sequence (Analyze)

```
Trigger (CLI / REST / Webhook)
    │
    ├─ VcsChecksPort.postPending()                 → GitHub/GitLab Check: pending
    │
    ├─ PullRequestPort.fetch()                     → GitHub/GitLab API
    ├─ PullRequestPort.fetchDiff()                 → GitHub/GitLab API (truncate > 8k tokens)
    ├─ AcceptanceCriteriaPort.fetch()              → GitHub body / JIRA API
    │
    ├─ ContextGatheringAgent.run()                 → LLM → ContextSummary
    ├─ CodeAnalysisAgent.run()                     → LLM → ImpactAssessment
    │
    ├─ TestRegistryPort.findActive()               → PostgreSQL
    │
    ├─ TestPlanningAgent.run()                     → LLM → List<TestScenario>
    │
    ├─── CompletableFuture.allOf() [parallel] ─────────────────────────────────
    │    ├─ PlaywrightGenerationAgent per PLAYWRIGHT scenario   → LLM → GeneratedTest
    │    ├─ RestAssuredGenerationAgent per REST_ASSURED scenario → LLM → GeneratedTest
    │    └─ DbValidationGenerationAgent per DB_VALIDATION scenario → LLM → GeneratedTest
    │
    ├─ TestExecutionAgent.execute(playwrightTests) → LLM + Playwright MCP → ExecutionResults
    │    └─ SelfHealingLocatorAgent (per FAILED result)         → LLM + Playwright MCP
    │
    ├─ TestFileStorePort.writeAll()                → local filesystem
    ├─ TestRegistryPort.saveAll()                  → PostgreSQL
    │
    ├─ ObsolescenceCheck: compare deleted files vs active registry → markObsolete()
    │
    ├─ VcsChecksPort.postSuccess/postFailure()     → GitHub/GitLab Check: result
    │
    └─ Return AnalysisResult
```

### 10.2 Regression Sequence

```
Trigger (CLI / REST / Webhook)
    │
    ├─ PullRequestPort.fetch()                     → get headSha and PR context
    ├─ VcsChecksPort.postPending()
    │
    ├─ TestRegistryPort.findActive()               → all active tests for repository
    ├─ TestExecutionAgent.executeExisting()        → LLM + Playwright MCP
    │
    ├─ Compute passRatePercent
    ├─ If passRatePercent < threshold:
    │    └─ VcsChecksPort.postFailure() → gateResult = "BLOCKED" → return
    │
    ├─ VcsChecksPort.postSuccess()
    └─ Return RegressionResult (gateResult = "OPEN")
```

### 10.3 JSON Parse Retry Policy

Every agent wraps its LLM call and JSON parse in this pattern:

```
attempt 1 → call LLM → try parse JSON
    if fails → append "\n\nIMPORTANT: Output ONLY valid JSON. No other text."
attempt 2 → call LLM → try parse JSON
    if fails → throw LlmParseException(agentName, rawResponse)
```

`LlmParseException` is caught by `AgentOrchestrator`, which records the failure in the
run and returns a partial `AnalysisResult` with `outcome = "PARTIAL_FAILURE"`.

---

## 11. Data Models and Database Schema

### 11.1 Flyway Migration — V1: `test_cases`

```sql
-- V1__create_test_cases.sql
CREATE TABLE test_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name           VARCHAR(512)   NOT NULL UNIQUE,
    scenario_id         UUID           NOT NULL,
    scenario_title      VARCHAR(1024)  NOT NULL,
    scenario_type       VARCHAR(32)    NOT NULL,  -- NEW | REGRESSION_UPDATE
    test_layer          VARCHAR(32)    NOT NULL,  -- PLAYWRIGHT | REST_ASSURED | DB_VALIDATION
    user_flow           VARCHAR(512),
    target_path         VARCHAR(512),
    requires_auth       BOOLEAN        NOT NULL DEFAULT FALSE,
    tags                TEXT,                     -- comma-separated
    pr_number           VARCHAR(64),
    repository          VARCHAR(256)   NOT NULL,
    vcs_type            VARCHAR(32)    NOT NULL DEFAULT 'github',
    head_sha            VARCHAR(40),
    status              VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | OBSOLETE
    last_execution_status VARCHAR(32),
    last_execution_ms   BIGINT,
    execution_count     INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tc_repository_status  ON test_cases(repository, status);
CREATE INDEX idx_tc_pr                 ON test_cases(pr_number);
CREATE INDEX idx_tc_layer              ON test_cases(test_layer);
```

### 11.2 Flyway Migration — V2: `test_runs`

```sql
-- V2__create_test_runs.sql
CREATE TABLE test_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository      VARCHAR(256)  NOT NULL,
    vcs_type        VARCHAR(32)   NOT NULL DEFAULT 'github',
    pr_number       VARCHAR(64),
    run_type        VARCHAR(32)   NOT NULL,  -- REGRESSION | INCREMENTAL
    triggered_by    VARCHAR(64),             -- "cli" | "webhook"
    total_count     INT           NOT NULL DEFAULT 0,
    passed_count    INT           NOT NULL DEFAULT 0,
    failed_count    INT           NOT NULL DEFAULT 0,
    error_count     INT           NOT NULL DEFAULT 0,
    skipped_count   INT           NOT NULL DEFAULT 0,
    pass_rate       NUMERIC(5,2),
    duration_ms     BIGINT,
    outcome         VARCHAR(32),  -- PASSED | FAILED | PARTIAL_FAILURE | ERROR
    gate_result     VARCHAR(16),  -- OPEN | BLOCKED
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tr_repository ON test_runs(repository);
CREATE INDEX idx_tr_created    ON test_runs(created_at DESC);
```

### 11.3 Flyway Migration — V3: `test_run_items`

```sql
-- V3__create_test_run_items.sql
CREATE TABLE test_run_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID          NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
    test_case_id    UUID          REFERENCES test_cases(id),
    file_name       VARCHAR(512)  NOT NULL,
    scenario_id     UUID,
    test_layer      VARCHAR(32),
    status          VARCHAR(32)   NOT NULL,  -- PASSED | FAILED | SKIPPED | ERROR
    error_message   TEXT,
    duration_ms     BIGINT,
    retry_count     INT           NOT NULL DEFAULT 0,
    self_healed     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tri_run    ON test_run_items(run_id);
CREATE INDEX idx_tri_status ON test_run_items(status);
```

### 11.4 JPA Entity Example

```java
@Entity
@Table(name = "test_cases")
public class TestCaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 512)
    private String fileName;
    @Column(nullable = false)
    private UUID scenarioId;
    @Column(nullable = false, length = 1024)
    private String scenarioTitle;
    private String scenarioType;
    private String testLayer;
    private String userFlow;
    private String targetPath;
    private boolean requiresAuth;
    private String tags;
    private String prNumber;
    @Column(nullable = false, length = 256)
    private String repository;
    private String vcsType;
    private String headSha;
    @Column(nullable = false)
    private String status = "ACTIVE";
    private String lastExecutionStatus;
    private Long lastExecutionMs;
    private int executionCount;
    @CreationTimestamp private OffsetDateTime createdAt;
    @UpdateTimestamp  private OffsetDateTime updatedAt;
    // Lombok @Data or manual getters/setters
}
```

---

## 12. API Contract

Base path: `/api/v1`
All request/response bodies: `application/json; charset=UTF-8`

### 12.1 POST `/api/v1/analyze`

Trigger full analysis for a PR: gather context → plan → generate → execute → store.

**Request:**
```json
{
  "vcsType": "github",
  "repositoryFullName": "acme/backend",
  "prNumber": "1234",
  "targetAppBaseUrl": "https://staging.acme.com",
  "testOutputDirectory": "/var/qa-forge/tests/acme-backend",
  "openApiSpecUrl": "https://staging.acme.com/v3/api-docs"
}
```

`openApiSpecUrl` is optional. Omitting it disables RestAssured generation.

**Response 200:**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "prNumber": "1234",
  "repository": "acme/backend",
  "outcome": "SUCCESS",
  "newTestsGenerated": 4,
  "updatedTests": 1,
  "skippedScenarios": 2,
  "markedObsolete": 0,
  "executionSummary": {
    "total": 5,
    "passed": 4,
    "failed": 1,
    "errors": 0,
    "selfHealed": 1,
    "passRatePercent": 80.0
  },
  "generatedFiles": {
    "playwright": ["checkout_payment_pr1234.spec.ts"],
    "restAssured": ["PlaceOrderApiTest.java"],
    "dbValidation": []
  },
  "durationMs": 43210
}
```

**Error responses:**

| HTTP | `errorCode` | Condition |
|------|------------|-----------|
| 400 | `INVALID_REQUEST` | Missing required field |
| 401 | `UNAUTHORIZED` | Bad credentials |
| 422 | `PR_NOT_FOUND` | PR does not exist |
| 502 | `UPSTREAM_ERROR` | LLM or Playwright MCP error |
| 504 | `TIMEOUT` | Pipeline exceeded 180s |

---

### 12.2 POST `/api/v1/regression`

Run the full active test suite against a target URL.

**Request:**
```json
{
  "vcsType": "github",
  "repositoryFullName": "acme/backend",
  "prNumber": "1235",
  "targetAppBaseUrl": "https://staging.acme.com"
}
```

**Response 200:**
```json
{
  "runId": "...",
  "outcome": "PASSED",
  "total": 23,
  "passed": 23,
  "failed": 0,
  "selfHealed": 0,
  "passRatePercent": 100.0,
  "durationMs": 125000,
  "gateResult": "OPEN",
  "blockedReason": null
}
```

`gateResult` = `"OPEN"` when `passRatePercent >= qaforge.regression.pass-rate-threshold`.
Otherwise `"BLOCKED"` with `blockedReason` = `"Pass rate 78.0% below threshold 90.0%"`.

---

### 12.3 POST `/api/v1/webhook/github`

Receives GitHub `pull_request` webhook events.

- Validates `X-Hub-Signature-256` (HMAC-SHA256 with `qaforge.github.webhook-secret`)
- `pull_request.opened` → async `analyze`
- `pull_request.ready_for_review` → async `regression`, then `analyze`
- `pull_request.synchronize` → async `analyze`
- All other events → `204 No Content`
- Valid events → `202 Accepted` (async processing)

Required headers: `X-GitHub-Event`, `X-Hub-Signature-256`, `X-GitHub-Delivery`

---

### 12.4 POST `/api/v1/webhook/gitlab`

Receives GitLab `Merge Request Hook` events.

- Validates `X-Gitlab-Token` header against `qaforge.gitlab.webhook-token`
- `opened` and `update` → async `analyze`
- `approved` → async `regression`
- Returns `202 Accepted`

---

### 12.5 GET `/api/v1/tests`

List test cases for a repository.

**Query params:**

| Param | Required | Default | Description |
|-------|----------|---------|-------------|
| `repository` | Yes | — | e.g. `acme/backend` |
| `status` | No | `ACTIVE` | `ACTIVE` or `OBSOLETE` |
| `layer` | No | all | `PLAYWRIGHT`, `REST_ASSURED`, `DB_VALIDATION` |
| `page` | No | `0` | Zero-based page number |
| `size` | No | `20` | Page size, max 100 |

**Response 200:**
```json
{
  "tests": [
    {
      "id": "...",
      "fileName": "checkout_payment_pr1234.spec.ts",
      "scenarioTitle": "User completes checkout with a credit card",
      "layer": "PLAYWRIGHT",
      "userFlow": "Checkout",
      "prNumber": "1234",
      "status": "ACTIVE",
      "tags": ["smoke", "checkout"],
      "lastExecutionStatus": "PASSED",
      "lastExecutionMs": 3210,
      "executionCount": 7,
      "createdAt": "2026-08-02T10:00:00Z",
      "updatedAt": "2026-08-02T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

---

### 12.6 GET `/api/v1/runs/{runId}`

Fetch a test run with all items.

**Response 200:**
```json
{
  "runId": "...",
  "repository": "acme/backend",
  "prNumber": "1234",
  "runType": "INCREMENTAL",
  "triggeredBy": "webhook",
  "outcome": "PASSED",
  "gateResult": "OPEN",
  "total": 5,
  "passed": 5,
  "failed": 0,
  "passRatePercent": 100.0,
  "durationMs": 43210,
  "createdAt": "2026-08-02T10:00:00Z",
  "items": [
    {
      "fileName": "checkout_payment_pr1234.spec.ts",
      "layer": "PLAYWRIGHT",
      "status": "PASSED",
      "durationMs": 3210,
      "retryCount": 0,
      "selfHealed": false,
      "errorMessage": null
    }
  ]
}
```

---

### 12.7 GET `/api/v1/runs`

List runs for a repository.

**Query params:** `repository` (required), `prNumber` (optional), `page`, `size`

---

### 12.8 GET `/api/v1/export`

Download all active test files for a repository as a ZIP.

**Query params:** `repository` (required), `layer` (optional)

**Response:** `application/zip`, file name `{repository-slug}-tests.zip`

---

### 12.9 GET `/actuator/health` and `/actuator/info`

Spring Boot Actuator endpoints. Always exposed. Show MCP connection status in health.

---

### 12.10 CLI Commands (Spring Shell 4.0.3)

All commands are in the `qa` command group.

```
qa analyze
  --repo   <repositoryFullName>    required
  --pr     <prNumber>              required
  --base-url <url>                 required
  --vcs    <github|gitlab>         default: github
  --output-dir <path>              default: from config
  --openapi-spec <url>             optional; enables RestAssured generation

qa regression
  --repo   <repositoryFullName>    required
  --pr     <prNumber>              required
  --base-url <url>                 required
  --vcs    <github|gitlab>         default: github

qa list
  --repo   <repositoryFullName>    required
  --status <ACTIVE|OBSOLETE>       default: ACTIVE
  --layer  <PLAYWRIGHT|REST_ASSURED|DB_VALIDATION>  optional

qa run
  --repo   <repositoryFullName>    required
  --base-url <url>                 required

qa export
  --repo   <repositoryFullName>    required
  --output <path>                  where to save the ZIP

qa version
```

---

## 13. MCP Integration Specification

### 13.1 Playwright MCP Server Lifecycle

The Playwright MCP server runs as a child process managed by Spring Boot.

On `ApplicationReadyEvent`:
1. Verify `node` is on `PATH`; throw `IllegalStateException` if not
2. Verify `npx` is available; throw `IllegalStateException` if not
3. Launch the MCP server via `StdioClientTransport`
4. Call `client.initialize()` (MCP handshake)
5. Log available tools at `INFO` level

On `ContextClosedEvent`:
1. Call `client.close()`
2. Terminate the child process

### 13.2 Spring AI MCP Client Configuration

```java
// PlaywrightMcpConfig.java
@Configuration
public class PlaywrightMcpConfig {

    @Bean
    @ConditionalOnProperty(
        name = "qaforge.mcp.playwright.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public McpSyncClient playwrightMcpClient(PlaywrightMcpProperties props) {
        var transport = StdioClientTransport.builder()
            .command(props.command())
            .args(props.args())
            .build();
        var client = McpClient.sync(transport)
            .requestTimeout(Duration.ofMillis(props.executionTimeoutMs()))
            .build();
        client.initialize();
        return client;
    }

    @Bean
    @ConditionalOnBean(McpSyncClient.class)
    public SyncMcpToolCallbackProvider playwrightToolProvider(McpSyncClient client) {
        return new SyncMcpToolCallbackProvider(client);
    }
}
```

> Spring AI 2.0.0 imports:
> `org.springframework.ai.mcp.client.McpClient`
> `org.springframework.ai.mcp.client.StdioClientTransport`
> `org.springframework.ai.mcp.client.SyncMcpToolCallbackProvider`

### 13.3 MCP Server Launch Configuration

```yaml
qaforge:
  mcp:
    playwright:
      enabled: true
      command: "npx"
      args:
        - "@playwright/mcp@latest"
        - "--headless"
        - "--output-dir"
        - "/tmp/playwright-traces"
      connect-timeout-ms: 10000
      execution-timeout-ms: 120000
      max-tool-calls: 50
```

---

## 14. Test Generation Specification

### 14.1 Playwright TypeScript Output Format

```typescript
// @qa-forge generated — scenario:550e8400-e29b-41d4-a716-446655440000
import { test, expect } from '@playwright/test';

class CheckoutPage {
  constructor(private readonly page: import('@playwright/test').Page) {}

  async fillCardDetails(number: string, expiry: string, cvv: string) {
    await this.page.getByLabel('Card number').fill(number);
    await this.page.getByLabel('Expiry').fill(expiry);
    await this.page.getByLabel('CVV').fill(cvv);
  }
}

test.describe('Checkout > Payment @smoke @checkout @pr-1234', () => {
  let checkoutPage: CheckoutPage;

  test.beforeEach(async ({ page }) => {
    await page.goto(process.env.PLAYWRIGHT_BASE_URL + '/checkout');
    checkoutPage = new CheckoutPage(page);
  });

  test('User completes checkout with a valid credit card', async ({ page }) => {
    await checkoutPage.fillCardDetails('4111111111111111', '12/28', '123');
    await page.getByRole('button', { name: 'Place Order' }).click();
    await expect(
      page.getByRole('heading', { name: 'Order Confirmed' })
    ).toBeVisible();
  });
});
```

### 14.2 File Naming Convention

```
{snake_case_scenario_title}_pr{prNumber}.spec.ts        ← Playwright
{PascalCase(scenarioTitle)}Test.java                    ← RestAssured
{PascalCase(dbTable)}DbValidationTest.java              ← DB Validation
```

### 14.3 Output Directory Layout

```
{testOutputDirectory}/
├── playwright/
│   ├── smoke/
│   │   └── login_flow_pr1200.spec.ts
│   └── checkout/
│       ├── checkout_payment_pr1234.spec.ts
│       └── order_confirmation_pr1234.spec.ts
├── rest-assured/
│   └── src/test/java/com/qaforge/generated/
│       └── PlaceOrderApiTest.java
├── db-validation/
│   └── src/test/java/com/qaforge/generated/
│       └── OrdersDbValidationTest.java
└── playwright.config.ts          ← generated once; never overwritten
```

### 14.4 Playwright Config (`playwright.config.ts`)

Written once on first run; never overwritten:

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './playwright',
  timeout: 30_000,
  retries: 1,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
```

### 14.5 Obsolescence Detection

After any analyze run, QA Forge compares:
- `changedFiles` where `changeType == "DELETED"` (from `CodeDiff`)
- Active `test_cases` where `fileName` references code in the deleted files
  (matched by scanning the `targetPath` or `userFlow` against deleted paths)

Matched test cases are updated to `status = "OBSOLETE"` and a summary is included in
the `AnalysisResult` as `markedObsolete` count.

---

## 15. Configuration Reference

### 15.1 `application.yml` (base)

```yaml
spring:
  application:
    name: qa-forge
  datasource:
    url: ${QAFORGE_DB_URL:jdbc:h2:mem:qaforgedev;DB_CLOSE_DELAY=-1}
    username: ${QAFORGE_DB_USER:sa}
    password: ${QAFORGE_DB_PASSWORD:}
    driver-class-name: ${QAFORGE_DB_DRIVER:org.h2.Driver}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000
  jpa:
    hibernate:
      ddl-auto: validate        # Flyway owns the schema
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        dialect: ${QAFORGE_DB_DIALECT:org.hibernate.dialect.H2Dialect}
  flyway:
    enabled: true
    locations: classpath:db/migration
  mvc:
    static-path-pattern: /**
  web:
    resources:
      static-locations: classpath:/static/
  # ── LLM Provider: include ONLY the desired starter in pom.xml and configure here
  # ai:
  #   anthropic:
  #     api-key: ${ANTHROPIC_API_KEY}
  #     chat.options.model: claude-sonnet-4-6
  #   openai:
  #     api-key: ${OPENAI_API_KEY}
  #     chat.options.model: gpt-4o
  #   ollama:
  #     base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  #     chat.options.model: llama3.3:70b
  security:
    user:
      name: ${QAFORGE_API_USER:qaforge}
      password: ${QAFORGE_API_PASSWORD:changeme}
      roles: API

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

server:
  port: ${PORT:8080}
  error:
    include-message: never

qaforge:
  github:
    base-url: https://api.github.com
    token: ${GITHUB_TOKEN:}
    webhook-secret: ${GITHUB_WEBHOOK_SECRET:}
  gitlab:
    base-url: ${GITLAB_BASE_URL:https://gitlab.com}
    token: ${GITLAB_TOKEN:}
    webhook-token: ${GITLAB_WEBHOOK_TOKEN:}
  jira:
    enabled: ${JIRA_ENABLED:false}
    base-url: ${JIRA_BASE_URL:}
    token: ${JIRA_TOKEN:}
    project-key: ${JIRA_PROJECT_KEY:}
  mcp:
    playwright:
      enabled: ${MCP_PLAYWRIGHT_ENABLED:true}
      command: "npx"
      args:
        - "@playwright/mcp@latest"
        - "--headless"
        - "--output-dir"
        - "${PLAYWRIGHT_TRACES_DIR:/tmp/playwright-traces}"
      connect-timeout-ms: 10000
      execution-timeout-ms: 120000
      max-tool-calls: 50
  regression:
    pass-rate-threshold: ${QAFORGE_PASS_RATE_THRESHOLD:90.0}
    max-existing-tests-per-run: 100
  generation:
    max-scenarios-per-pr: 10
    output-base-directory: ${QAFORGE_OUTPUT_DIR:/tmp/qa-forge-tests}
    playwright-config-template: "classpath:templates/playwright.config.ts.template"
  async:
    webhook-thread-pool-size: ${QAFORGE_WEBHOOK_THREADS:4}
  ai:
    multi-provider:
      enabled: ${QAFORGE_MULTI_PROVIDER:false}
```

### 15.2 `application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:qaforgedev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.H2Dialect
  h2:
    console:
      enabled: true
      path: /h2-console
  ai:
    openai:                             # swap this block for your preferred local provider
      base-url: http://localhost:1234/v1
      api-key: not-needed
      chat:
        options:
          model: local-model

qaforge:
  mcp:
    playwright:
      enabled: false                    # disable in dev unless explicitly testing execution
  github:
    token: ${GITHUB_TOKEN:}
```

### 15.3 `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${QAFORGE_DB_URL}
    username: ${QAFORGE_DB_USER}
    password: ${QAFORGE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}

logging:
  pattern:
    console: >
      {"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%p",
       "traceId":"%X{traceId}","spanId":"%X{spanId}",
       "repository":"%X{repository}","prNumber":"%X{prNumber}",
       "logger":"%logger","message":"%m"}%n
```

### 15.4 Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `QAFORGE_DB_URL` | Prod | PostgreSQL JDBC URL |
| `QAFORGE_DB_USER` | Prod | Database username |
| `QAFORGE_DB_PASSWORD` | Prod | Database password |
| **LLM — use exactly one block:** | | |
| `ANTHROPIC_API_KEY` | If using Anthropic | Claude API key |
| `OPENAI_API_KEY` | If using OpenAI | OpenAI API key |
| `AZURE_OPENAI_API_KEY` + `AZURE_OPENAI_ENDPOINT` | If using Azure OpenAI | |
| `GROQ_API_KEY` | If using Groq (OpenAI compat) | Set `spring.ai.openai.base-url` to Groq |
| `OLLAMA_BASE_URL` | If using Ollama | default: `http://localhost:11434` |
| `GOOGLE_CLOUD_PROJECT` + `GOOGLE_CLOUD_LOCATION` | If using Gemini | |
| `MISTRAL_AI_API_KEY` | If using Mistral | |
| **VCS:** | | |
| `GITHUB_TOKEN` | GitHub integration | PAT with `repo` scope |
| `GITHUB_WEBHOOK_SECRET` | GitHub webhooks | HMAC shared secret |
| `GITLAB_TOKEN` | GitLab integration | PAT with `api` scope |
| `GITLAB_WEBHOOK_TOKEN` | GitLab webhooks | Token set in GitLab webhook config |
| **JIRA (optional):** | | |
| `JIRA_BASE_URL` | If `JIRA_ENABLED=true` | e.g. `https://acme.atlassian.net` |
| `JIRA_TOKEN` | If `JIRA_ENABLED=true` | JIRA personal access token |
| **App:** | | |
| `QAFORGE_API_USER` | No | Basic auth username (default: `qaforge`) |
| `QAFORGE_API_PASSWORD` | Yes (prod) | Basic auth password |
| `QAFORGE_OUTPUT_DIR` | No | Where test files are written |
| `QAFORGE_PASS_RATE_THRESHOLD` | No | Regression gate threshold (default: `90.0`) |
| `MCP_PLAYWRIGHT_ENABLED` | No | Set `false` to skip Playwright execution |

---

## 16. Error Handling and Resilience

### 16.1 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PrNotFoundException.class)
    public ResponseEntity<ErrorResponse> prNotFound(PrNotFoundException ex) {
        return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse("PR_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(LlmParseException.class)
    public ResponseEntity<ErrorResponse> llmParse(LlmParseException ex) {
        log.error("LLM parse failure in agent {}: {}", ex.getAgentName(), ex.getRawResponse());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("LLM_PARSE_ERROR",
                "Agent " + ex.getAgentName() + " returned invalid JSON"));
    }

    @ExceptionHandler(QaForgeException.class)
    public ResponseEntity<ErrorResponse> qaForge(QaForgeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("UPSTREAM_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

public record ErrorResponse(
    String errorCode,
    String message,
    String timestamp
) {
    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, Instant.now().toString());
    }
}
```

### 16.2 LLM Response Parsing (all agents)

```java
protected <T> T parseLlmJson(String response, Class<T> type) {
    String cleaned = response.strip()
        .replaceAll("(?s)^```[a-z]*\\n?", "")
        .replaceAll("\\n?```$", "")
        .strip();
    try {
        return objectMapper.readValue(cleaned, type);
    } catch (JsonProcessingException first) {
        // Retry once with stricter prompt
        throw new LlmParseException(getClass().getSimpleName(), cleaned, first);
    }
}
```

### 16.3 GitHub/GitLab API Resilience

All `RestClient` calls to GitHub and GitLab APIs use Spring Retry:

```java
@Retryable(
    retryFor = { RestClientException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0)
)
public PullRequest fetch(String repo, String pr) { ... }
```

### 16.4 Webhook Async Error Handling

When async webhook processing fails:

1. Exception is logged with full context (repository, prNumber, deliveryId)
2. If `VcsChecksPort` is available: post a "failure" check to the PR
3. The `202 Accepted` already returned; no retry mechanism — the CI pipeline can re-trigger

### 16.5 Playwright MCP Timeout

If a Playwright MCP tool call exceeds `execution-timeout-ms`:
- The scenario is marked `ERROR` with message `"Playwright tool call timeout"`
- Remaining scenarios continue
- The `TestRunItem` has `status = ERROR`

---

## 17. Security

### 17.1 REST API Authentication

HTTP Basic authentication with a single configured user (see env vars). In future
deployments, replace with an API key header checked via `OncePerRequestFilter`.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/webhook/**").permitAll()  // HMAC-validated separately
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/", "/index.html", "/assets/**").permitAll()  // dashboard
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

### 17.2 Webhook Signature Validation

```java
// In WebhookController, before processing any GitHub event:
private void validateGithubSignature(String signature, byte[] body) {
    if (signature == null || !signature.startsWith("sha256=")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing signature");
    }
    String expected = "sha256=" + hmacSha256(webhookSecret, body);
    if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8))) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
    }
}
```

### 17.3 Secret Hygiene

- All secrets via environment variables only; never in YAML files
- Spring Boot sanitized fields: extend `management.endpoint.env.additional-keys-to-sanitize`
  with `token`, `api-key`, `webhook-secret`, `password`
- Secrets must never appear in log lines; use MDC only for non-sensitive identifiers

### 17.4 SSRF Mitigation (Spring Boot 4.1.0)

```yaml
spring:
  http:
    client:
      ssrf-mitigation:
        enabled: true
        allow-loopback: ${QAFORGE_ALLOW_LOOPBACK:false}
```

Enable `allow-loopback: true` only in local dev when targeting `localhost`.

---

## 18. Observability and Monitoring

### 18.1 Custom Micrometer Metrics

Register via `MeterRegistry` injection in `AgentOrchestrator` and agents:

| Metric | Type | Tags |
|--------|------|------|
| `qaforge.analysis.duration` | Timer | `repository`, `outcome`, `vcs_type` |
| `qaforge.regression.duration` | Timer | `repository`, `gate_result` |
| `qaforge.tests.generated` | Counter | `repository`, `layer`, `scenario_type` |
| `qaforge.tests.obsoleted` | Counter | `repository` |
| `qaforge.execution.result` | Counter | `repository`, `layer`, `status` |
| `qaforge.regression.pass_rate` | Gauge | `repository` |
| `qaforge.llm.calls` | Counter | `agent`, `provider`, `outcome` |
| `qaforge.llm.retries` | Counter | `agent` |
| `qaforge.self_healed` | Counter | `repository` |
| `qaforge.mcp.tool_calls` | Counter | `tool_name`, `status` |

### 18.2 MDC Logging

Set at the start of every orchestration call; clear in `finally`:

```java
MDC.put("repository", request.repositoryFullName());
MDC.put("prNumber", request.prNumber());
MDC.put("vcsType", request.vcsType());
MDC.put("triggeredBy", request.triggeredBy());
```

### 18.3 OpenTelemetry (Production)

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
```

Spans are automatically created for each controller method, each agent call, and each
Playwright MCP tool invocation.

---

## 19. Testing Strategy

### 19.1 Unit Tests

- **All agents:** Mock `ChatClient` to return canned JSON strings. Assert correct record
  parsing, retry behaviour on malformed JSON, and graceful degradation.
- **All adapters:** Mock `RestClient` / `JdbcTemplate`. Assert correct mapping from API
  response to domain record.
- **`AgentOrchestrator`:** Mock all agents and ports. Assert correct chaining,
  parallel generation, and error propagation.
- Coverage target: **≥ 80% line coverage** on `qa-forge-application`.

### 19.2 Repository Slice Tests

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TestCaseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    // Test CRUD, pagination, status updates
}
```

### 19.3 Web Slice Tests

```java
@WebMvcTest(AnalyzeController.class)
class AnalyzeControllerTest {
    @MockitoBean AnalyzePort analyzePort;

    @Test
    void analyze_returns200_onSuccess() { ... }

    @Test
    void analyze_returns422_whenPrNotFound() { ... }

    @Test
    void analyze_returns400_whenRepoMissing() { ... }
}
```

### 19.4 Integration Test

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AgentPipelineIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // Wire a WireMock server for GitHub API
    // Wire a mock ChatModel that returns canned JSON (no real LLM calls)
    // Set qaforge.mcp.playwright.enabled=false
    // Call POST /api/v1/analyze and assert full AnalysisResult JSON
    // Assert test_cases rows created in PostgreSQL
}
```

### 19.5 Dashboard Tests

- Vitest unit tests for React components
- Playwright component tests for `CoverageMap` and `RunHistoryChart`
- Run as part of `npm test` in `qa-forge-dashboard`

---

## 20. CI/CD Integration

### 20.1 QA Forge Self-Build Pipeline

```yaml
# .github/workflows/build.yml
name: Build, Test, Package

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: qaforge_test
          POSTGRES_USER: qaforge
          POSTGRES_PASSWORD: qaforge
        ports: ["5432:5432"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: maven }
      - uses: actions/setup-node@v4
        with: { node-version: '20.18.0' }
      - name: Build dashboard
        run: cd qa-forge-dashboard && npm ci && npm run build
      - name: Maven build + test
        run: mvn -B verify -Dspring.profiles.active=test
        env:
          QAFORGE_DB_URL: jdbc:postgresql://localhost:5432/qaforge_test
          QAFORGE_DB_USER: qaforge
          QAFORGE_DB_PASSWORD: qaforge
          QAFORGE_DB_DRIVER: org.postgresql.Driver
          QAFORGE_DB_DIALECT: org.hibernate.dialect.PostgreSQLDialect
          MCP_PLAYWRIGHT_ENABLED: "false"
      - name: Upload JAR
        uses: actions/upload-artifact@v4
        with:
          name: qa-forge-jar
          path: qa-forge-bootstrap/target/qa-forge-bootstrap-*.jar
```

### 20.2 Docker Compose (Local Development)

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: qaforge
      POSTGRES_USER: qaforge
      POSTGRES_PASSWORD: qaforge
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]

  qa-forge:
    image: eclipse-temurin:21-jre-alpine
    command: java -jar /app/qa-forge.jar
    volumes:
      - ./qa-forge-bootstrap/target/qa-forge-bootstrap-*.jar:/app/qa-forge.jar
      - ./tests:/var/qa-forge/tests
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      QAFORGE_DB_URL: jdbc:postgresql://postgres:5432/qaforge
      QAFORGE_DB_USER: qaforge
      QAFORGE_DB_PASSWORD: qaforge
      QAFORGE_DB_DRIVER: org.postgresql.Driver
      QAFORGE_DB_DIALECT: org.hibernate.dialect.PostgreSQLDialect
      # Set exactly one LLM block:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL: gpt-4o
      GITHUB_TOKEN: ${GITHUB_TOKEN}
      QAFORGE_API_PASSWORD: ${QAFORGE_API_PASSWORD}
    depends_on: [postgres]

volumes:
  pgdata:
```

### 20.3 Consumer Workflow (teams using QA Forge)

```yaml
# .github/workflows/qa-forge.yml  (add to the target application repo)
name: QA Forge

on:
  pull_request:
    types: [opened, synchronize, ready_for_review]

jobs:
  qa-forge:
    runs-on: ubuntu-latest
    steps:
      - name: Regression gate (on ready_for_review)
        if: github.event.action == 'ready_for_review'
        run: |
          RESULT=$(curl -sf \
            -u ${{ secrets.QAFORGE_USER }}:${{ secrets.QAFORGE_PASSWORD }} \
            -X POST https://qa-forge.internal/api/v1/regression \
            -H "Content-Type: application/json" \
            -d '{
              "vcsType": "github",
              "repositoryFullName": "${{ github.repository }}",
              "prNumber": "${{ github.event.pull_request.number }}",
              "targetAppBaseUrl": "https://staging.example.com"
            }')
          echo "$RESULT" | jq .
          GATE=$(echo "$RESULT" | jq -r '.gateResult')
          [ "$GATE" = "OPEN" ] || (echo "Regression gate BLOCKED" && exit 1)

      - name: Generate new tests
        run: |
          curl -sf \
            -u ${{ secrets.QAFORGE_USER }}:${{ secrets.QAFORGE_PASSWORD }} \
            -X POST https://qa-forge.internal/api/v1/analyze \
            -H "Content-Type: application/json" \
            -d '{
              "vcsType": "github",
              "repositoryFullName": "${{ github.repository }}",
              "prNumber": "${{ github.event.pull_request.number }}",
              "targetAppBaseUrl": "https://staging.example.com",
              "testOutputDirectory": "/var/qa-forge/tests/${{ github.repository }}",
              "openApiSpecUrl": "https://staging.example.com/v3/api-docs"
            }' | jq .
```

---

## 21. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | Full analyze pipeline (5 scenarios, all layers) | < 90 seconds |
| Performance | Regression suite (20 existing Playwright tests) | < 3 minutes |
| Performance | API response for `GET /api/v1/tests` (20 items) | < 200ms |
| Availability | Single-instance uptime | 99% |
| Scalability | Max concurrent webhook events without queuing | 4 (configurable) |
| Reliability | LLM JSON parse auto-retry | 1 retry per agent |
| Reliability | GitHub/GitLab API retry | 3 attempts, exponential backoff |
| Security | No secrets in logs or responses | Zero tolerance |
| Security | Webhook HMAC validation | Mandatory in production |
| Observability | Structured JSON logs in production | Mandatory |
| Observability | Micrometer metrics for all agents | Mandatory |
| Maintainability | Test coverage on `qa-forge-application` | ≥ 80% lines |
| Portability | Runs on Java 21, Linux AMD64, Docker | Verified in CI |
| LLM Portability | Zero code change to swap LLM provider | Guaranteed by architecture |
| LLM Portability | Works with any OpenAI-compatible endpoint | Guaranteed by OpenAI starter config |

---

## 22. Known Constraints and Limitations

| ID | Constraint | Impact | Mitigation |
|----|------------|--------|------------|
| C-01 | Playwright MCP cannot natively traverse Shadow DOM | AI-generated tests fail on web-component-based UIs | Prefer `data-testid` attributes; document in each generated spec header |
| C-02 | Large diffs (>8,000 tokens) are truncated before sending to LLM | Inaccurate impact analysis on very large PRs | Truncate at 8,000 tokens; set `truncated = true` in `CodeDiff`; note in `prSummary` |
| C-03 | LLM non-determinism: same PR may yield slightly different scenarios on re-run | Registry deduplication may miss subtle duplicates | Deduplicate by `hash(scenarioTitle + userFlow + layer)` case-insensitively |
| C-04 | Playwright MCP executes tests serially | Slow for suites > 20 tests | Limit to 10 new scenarios per run; existing test execution batched in groups of 20 |
| C-05 | RestAssured and DB validation tests are written to disk only; not executed by QA Forge | No execution feedback for API/DB tests | Teams run these via their own Maven/Gradle test lifecycle |
| C-06 | No built-in git commit of generated test files | Tests must be manually moved into version control | Provide `qa export` command and ZIP download via API; CI guidance in docs |
| C-07 | Requires Node.js 20 on the same host as the JVM | Additional host dependency | Document as prerequisite; provide Docker image with both runtimes |
| C-08 | Models with < 8,000-token context may struggle with complex diffs | Incomplete scenario generation | Documented in Section 7.7; enforce minimum model requirement |

---

## 23. Glossary

| Term | Definition |
|------|------------|
| **AC** | Acceptance Criteria — conditions that define when a story or PR is complete |
| **Agent** | A Spring service that calls an LLM via `ChatClient` with a specific typed system prompt |
| **ChatModel** | Spring AI 2.0.0 interface (`org.springframework.ai.chat.model.ChatModel`) implemented by every LLM provider; the only AI abstraction used in application code |
| **DB Validation Test** | A JUnit 5 test that validates data integrity, constraints, and business rules in the database after a schema migration |
| **Gate** | Regression pass-rate threshold below which a PR is blocked from merging |
| **MCP** | Model Context Protocol — a JSON-RPC protocol by Anthropic that standardises how LLMs invoke external tools |
| **OAIC** | OpenAI-Compatible — any HTTP server that implements the `/v1/chat/completions` API (Groq, LM Studio, vLLM, Together.ai, etc.) |
| **Playwright MCP** | Microsoft's MCP server that exposes Playwright browser automation tools to LLMs via STDIO |
| **Registry** | PostgreSQL-backed store of all generated test cases and their execution history |
| **Regression Gate** | A pass/fail decision based on `passRatePercent >= threshold`; result is `"OPEN"` or `"BLOCKED"` |
| **RestAssured Test** | A JUnit 5 test that calls HTTP API endpoints and asserts status codes and response bodies using REST Assured |
| **Scenario** | A `TestScenario` record produced by `TestPlanningAgent`; maps one user intent to one spec file |
| **Self-Healing** | Automatic re-writing of a failing locator by `SelfHealingLocatorAgent` using the live accessibility tree |
| **STDIO Transport** | MCP transport where the server is a child process and data flows over stdin/stdout |
| **Test Layer** | Enum (`PLAYWRIGHT`, `REST_ASSURED`, `DB_VALIDATION`) that controls which generation agent handles a scenario |

---

*Document version: 2.0 | Last updated: 2026-08-02*
*All features are in scope for a single delivery. No phased deferral.*
