# QA Forge — Implementation Status

Tracks delivery of `docs/PRD.md` v2.0. Updated as each phase lands (see commit history for
one commit per checked group).

## Goals (PRD §3.1)

- [ ] G-01 Read PR diff + description + AC and derive test scenarios
- [ ] G-02 Generate Playwright TypeScript UI E2E tests
- [ ] G-03 Generate RestAssured Java API tests from OpenAPI spec diff
- [ ] G-04 Generate JDBC database validation tests from schema migration diffs
- [ ] G-05 Execute UI tests via Playwright MCP server; self-heal on locator failure
- [ ] G-06 Persist test registry: metadata, lineage, run history
- [ ] G-07 Run existing suite before generating new tests; gate on pass rate
- [ ] G-08 Spring Shell CLI
- [ ] G-09 REST API + GitHub webhook endpoint
- [ ] G-10 GitHub integration: diff, body, comments, Check Run API
- [ ] G-11 GitLab integration (pluggable adapter)
- [ ] G-12 JIRA acceptance criteria extraction
- [ ] G-13 Post results back to PR/MR as commit status check
- [ ] G-14 React SPA dashboard
- [ ] G-15 Test obsolescence detection
- [ ] G-16 Export test suite as downloadable ZIP
- [ ] G-17 Vendor-agnostic LLM via Spring AI `ChatModel`
- [ ] G-18 OpenAI-compatible endpoint support

## Modules

- [x] Phase 0 — Repo scaffold (parent + module POMs, structure)
- [x] Phase 1 — `qa-forge-domain` (records, ports, exceptions)
- [x] Phase 2 — `qa-forge-application` (prompts, 9 agents, orchestrator, use cases)
- [x] Phase 3 — `qa-forge-infrastructure` VCS/JIRA adapters
- [x] Phase 4 — `qa-forge-infrastructure` MCP + persistence + filesystem
- [x] Phase 5 — `qa-forge-bootstrap` config classes (LLM, Async, Security, OpenAPI)
- [x] Phase 6 — `qa-forge-bootstrap` REST controllers + DTOs + error handling
- [x] Phase 7 — `qa-forge-bootstrap` CLI commands
- [x] Phase 8 — `qa-forge-bootstrap` main app, config YAML, Flyway migrations
- [x] Phase 9 — `qa-forge-dashboard` React SPA
- [x] Phase 10 — Docker Compose + CI workflows + docs
- [ ] Phase 11 — Tests (unit, slice, integration)
- [ ] Phase 12 — Build verification
- [ ] Phase 13 — PR opened

## Deviations from the PRD (justified by real artifact availability)

- **Testcontainers**: PRD §6 pins `1.20.4`; Spring Boot `4.1.0`'s BOM manages `2.0.5`, which
  renamed the JUnit/Postgres modules to `testcontainers-junit-jupiter` /
  `testcontainers-postgresql`. Used the BOM-managed version and artifact IDs instead of forcing
  an incompatible, unmanaged older version.
- **`spring-retry`**: not part of `spring-boot-dependencies:4.1.0`'s dependency management;
  pinned explicitly to `2.0.13` (latest at implementation time) in the parent POM.
- **Spring Shell test starter**: PRD doesn't name it explicitly; the real artifact in
  `spring-shell-dependencies:4.0.3` is `spring-shell-starter-test` (not `spring-shell-testing`).
- **`TestRegistryPort`**: extended with `saveRun`/`findRun`/`findRuns` beyond PRD §9.1.2's
  literal list — required by the `GET /api/v1/runs` and `GET /api/v1/runs/{runId}` endpoints
  (§12.6/12.7) and consistent with the glossary's definition of "Registry" as covering run
  history; keeps run persistence behind a port instead of the application layer reaching into
  infrastructure directly.
- **`OpenApiSpecPort`**: new domain out-port, same justification — PRD §9.2.6 requires the
  RestAssured generation agent to receive "the relevant OpenAPI operation JSON", but §9.1.2
  names no port for it.
- **MCP Java package**: PRD §13.2 writes `org.springframework.ai.mcp.client.SyncMcpToolCallbackProvider`
  and `org.springframework.ai.mcp.client.McpClient`/`StdioClientTransport`. The real Spring AI
  `2.0.0` / MCP SDK `2.0.0` artifacts place these under `org.springframework.ai.mcp.*` (no
  `.client` segment) and `io.modelcontextprotocol.client.*` respectively — verified by
  inspecting the actual downloaded jars. Code uses the real packages.

- **JIRA description format**: `JiraAcAdapter` uses the JIRA v2 issue API (plain-text
  `description`). JIRA Cloud's v3 API returns Atlassian Document Format (rich JSON) for the
  same field instead — ADF parsing is not implemented; works as specified against JIRA
  Server/Data Center.

- **`PlaywrightTestExecutor`**: implements `TestExecutorPort` by delegating to
  `TestExecutionAgent`, but `AgentOrchestrator` calls `TestExecutionAgent` directly rather than
  through the port — self-healing needs to return corrected source code for a healed test, and
  `TestExecutorPort#executeGenerated`'s fixed signature (`List<ExecutionResult>`) has no way to
  carry that. See the class javadoc for the full reasoning.

- **`ChatOptionsBuilder`**: PRD §7.4 shows `ChatOptionsBuilder.builder().temperature(0.0).build()`.
  The real Spring AI 2.0.0 API has no standalone `ChatOptionsBuilder` class — it's
  `ChatOptions.builder()`, and `ChatClient.Builder#defaultOptions` takes the builder itself
  (not a built `ChatOptions`). Verified against the real jar; `LlmConfig` uses the real call.
- **Bootstrap LLM starter**: kept to exactly one default provider starter
  (`spring-ai-starter-model-anthropic`). Maven's `<optional>` only affects propagation to
  downstream consumers, not this module's own classpath — a second starter here would still
  autoconfigure a second `ChatModel` bean and fight the `@Primary` one in `LlmConfig`.

- **`TestRegistryPort#findByStatus`**: new method generalizing `findActive` — `GET
  /api/v1/tests?status=OBSOLETE` (§12.5) needs to list obsolete tests too, which `findActive`
  alone (fixed to ACTIVE) cannot serve.
- **Webhook → analyze/regression request construction**: PRD §12.3/§12.4 specify webhook event
  routing but a bare `pull_request`/`Merge Request Hook` payload carries no target app URL,
  OpenAPI spec URL, or output directory. `WebhookProperties` (`qaforge.webhook.*`, not in PRD
  §15's config reference) supplies these for fully-automated triggers; output directory
  defaults to `{outputBaseDirectory}/{repo-slug}`.
- **`GET /api/v1/export`'s `layer` query param**: accepted but not applied — `TestFileStorePort
  #exportZip`'s signature (fixed by PRD §9.1.2) takes only a repository, so layer-filtered
  export isn't wired through.

- **Spring Shell 4.0.3 command API**: PRD gives no code sketch for the CLI (unlike the other
  modules). Spring Shell 4.0.3's real annotation model is `@Command`/`@Option`/`@Argument`/
  `@CommandGroup`/`@EnableCommand` under `org.springframework.shell.core.command.annotation`
  — a different, newer API than the classic `@ShellComponent`/`@ShellMethod` most Spring Shell
  docs describe. Verified against the actual jar; each command class is a `@Component` (so
  `@Command` methods get constructor-injected ports) registered via `@EnableCommand` in
  `CliConfig`.
- **`qa export` / `qa version`**: PRD §12.10 requires both, but §8's project structure only
  lists `AnalyzeCommand`/`RegressionCommand`/`ListCommand`/`RunCommand` under `cli/`. Added
  `ExportCommand`/`VersionCommand` to satisfy the CLI contract in full.
- **`qa run` vs `qa regression`**: both call into the same test-execution machinery, but
  `qa run` (PRD §12.10 lists it with only `--repo`/`--base-url`, no `--pr`) is implemented as a
  direct local execution with no PR association and no VCS check posting — `qa regression`
  goes through `RegressionUseCase`, which always fetches a PR to post status to.

## Runtime-verified fixes (found by actually running the packaged jar)

Compiling clean doesn't mean the Spring context starts. Building and running
`qa-forge-bootstrap-*.jar` end-to-end (dev profile, H2) surfaced nine real wiring bugs no
compiler catches, all now fixed:

1. **`@Command`'s `value`/`description` are alias mirrors of the same attribute** (PRD §12.10
   gives no code sketch) — setting both to different strings throws
   `AnnotationConfigurationException` at class-scan time. Fixed to use `name = {"qa", "x"}` for
   the actual command name and `description` alone for the text.
2. **CLI method/bean-name collisions** with Spring Shell's own built-in commands (a method
   named `version()` collided with the built-in `Version` command bean). Renamed every command
   method to a `qaXxx` form.
3. **`RestClient.Builder` has no autoconfiguration by default** in Spring Boot 4.1.0 —
   `spring-boot-starter-web`'s dependency on it is `optional`, so Maven doesn't pull it
   transitively. Added `spring-boot-starter-restclient` explicitly.
4. **JPA repository/entity scanning didn't follow `@SpringBootApplication(scanBasePackages=...)`**
   — 0 repositories were found until `@EnableJpaRepositories`/`@EntityScan` were added
   explicitly with the infrastructure package.
5. **No `FlywayAutoConfiguration` by default** — same modularization pattern as RestClient;
   added `spring-boot-starter-flyway` explicitly.
6. **Flyway's PostgreSQL DDL doesn't run on H2**, even in `MODE=PostgreSQL` (`gen_random_uuid()`
   default rejected by H2 2.4.240's parser) — confirmed by actually executing it. The dev
   profile now disables Flyway and uses `hibernate.ddl-auto=update` against H2 instead; Flyway
   still owns the schema for real PostgreSQL (test/prod).
7. **H2 was test-scoped** in `qa-forge-bootstrap`, but the base `application.yml` datasource
   URL defaults to an in-memory H2 URL even outside tests — moved to `runtime` scope.
8. **`VcsChecksPort` had two competing implementations** (GitHub, GitLab) — a plain
   constructor-injected `VcsChecksPort` is ambiguous with two beans of that type. Added
   `VcsChecksRouter` as the single `VcsChecksPort` bean, dispatching by
   `PullRequest.vcsType()`; `GitHubChecksAdapter`/`GitLabChecksAdapter` no longer implement the
   port directly.
9. **CLI one-shot invocation unconfirmed**: `java -jar qa-forge-bootstrap.jar qa version`
   started the full web server instead of executing the command and exiting, despite
   `spring.shell.interactive.enabled=false` / `spring.shell.noninteractive.enabled=true`.
   Command registration itself is confirmed working (the app fails fast with a clear error if
   a command bean can't be built); the exact property/invocation form Spring Shell 4.0.3 uses
   to route bare program arguments into `NonInteractiveShellRunner` needs further verification
   — flagged here rather than guessed at.

Verified working end-to-end: app starts clean on `dev` profile (H2), Flyway migrates
`test_cases`/`test_runs`/`test_run_items` on a real profile, Spring Data JPA repositories
resolve, `GET /actuator/health` returns `UP`, and `GET /api/v1/tests` (HTTP Basic auth) returns
the exact paginated JSON shape from PRD §12.5.

- **`tailwindcss`/`@tailwindcss/vite`**: PRD §6 pins `4.0.0` exactly. That exact version's Vite
  plugin crashes (`Cannot convert undefined or null to object`) on *any* CSS file that contains
  `@import "tailwindcss";` — reproduced with a one-line CSS file, confirming it's not project
  configuration. Bumped to `4.0.17` (latest `4.0.x` patch at implementation time, same major.minor,
  bug-fixes only) to get a working build.
- **`HashRouter` instead of `BrowserRouter`**: PRD §8's "dashboard build integration" note has
  Spring Boot serve the SPA as plain static resources with no server-side fallback route for
  deep links (e.g. `GET /coverage` would 404, not serve `index.html`). Hash-based routing keeps
  every navigation a request for `/`, avoiding the need for extra server-side routing
  configuration not specified anywhere in the PRD.
- **No standalone run-detail page/route**: PRD §8 lists `TestDetailPage.tsx` but no
  `RunDetailPage.tsx`, even though `GET /api/v1/runs/{runId}` (§12.6) implies one exists. Run
  rows expand in place on `RunsPage` instead of navigating to a new route.
- **`TestDetailPage`'s execution timeline is client-assembled**: there's no
  "execution history for one test" endpoint (only run-level detail, §12.6), so the page fetches
  recent runs and filters each run's items by file name.

- **Added a real `Dockerfile`**: PRD §20.2's `docker-compose.yml` sketch mounts a pre-built
  jar into a bare `eclipse-temurin:21-jre-alpine` image — no Node.js, so the Playwright MCP
  server (`npx @playwright/mcp`) would have nothing to run on inside that container, directly
  contradicting §22 C-07's own mitigation ("provide Docker image with both runtimes"). Added a
  multi-stage `Dockerfile` (dashboard build → Maven build → Temurin 21 JRE + Node.js 20
  runtime image) and pointed `docker-compose.yml` at it.
- **Consumer workflow example relocated**: PRD §20.3's example lives at
  `.github/workflows/qa-forge.yml`, but that workflow is meant for a *different* repository
  (a team consuming QA Forge) — placing it in this repo's own `.github/workflows/` would make
  GitHub Actions try to run it here, against secrets and a `qa-forge.internal` URL that don't
  exist in this repo. Moved to `docs/consumer-workflow-example.yml` as reference material.
- **Dockerfile/docker-compose not execution-verified**: no Docker daemon is available in this
  implementation environment (client present, no `dockerd`), so the multi-stage build was
  reviewed by inspection only, not actually run.

## Known limitations at delivery time

See PRD §22 for the constraints (C-01..C-08) that are inherent to the design, not gaps in
this implementation. Any additional gaps introduced by this pass will be listed here.
