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
- [ ] Phase 3 — `qa-forge-infrastructure` VCS/JIRA adapters
- [ ] Phase 4 — `qa-forge-infrastructure` MCP + persistence + filesystem
- [ ] Phase 5 — `qa-forge-bootstrap` config classes (LLM, Async, Security, OpenAPI)
- [ ] Phase 6 — `qa-forge-bootstrap` REST controllers + DTOs + error handling
- [ ] Phase 7 — `qa-forge-bootstrap` CLI commands
- [ ] Phase 8 — `qa-forge-bootstrap` main app, config YAML, Flyway migrations
- [ ] Phase 9 — `qa-forge-dashboard` React SPA
- [ ] Phase 10 — Docker Compose + CI workflows + docs
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

## Known limitations at delivery time

See PRD §22 for the constraints (C-01..C-08) that are inherent to the design, not gaps in
this implementation. Any additional gaps introduced by this pass will be listed here.
