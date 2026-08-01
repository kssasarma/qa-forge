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

- [ ] Phase 0 — Repo scaffold (parent + module POMs, structure)
- [ ] Phase 1 — `qa-forge-domain` (records, ports, exceptions)
- [ ] Phase 2 — `qa-forge-application` (prompts, 9 agents, orchestrator, use cases)
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

## Known limitations at delivery time

See PRD §22 for the constraints (C-01..C-08) that are inherent to the design, not gaps in
this implementation. Any additional gaps introduced by this pass will be listed here.
