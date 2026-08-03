# QA Forge

AI-powered, self-hosted test generation and regression management platform. QA Forge reads
pull request metadata (diff, description, acceptance criteria, reviewer comments), reasons
about what changed, generates Playwright TypeScript E2E tests, RestAssured Java API tests,
and JDBC database validation tests, executes UI tests via the Playwright MCP server, persists
everything in a versioned test registry, enforces a regression gate on subsequent PRs, and
presents run history and coverage through a React dashboard.

See `docs/PRD.md` for the full product specification.

## Implementation status

Tracked in `docs/IMPLEMENTATION_STATUS.md` — every PRD goal (G-01..G-18) and module is checked
off as it lands.

## Modules

| Module | Purpose |
|---|---|
| `qa-forge-domain` | Pure Java records + port interfaces. Zero Spring, zero I/O. |
| `qa-forge-application` | Agent orchestration, use cases. Depends only on Spring AI `ChatModel`. |
| `qa-forge-infrastructure` | Adapters: GitHub, GitLab, JIRA, Playwright MCP, PostgreSQL, filesystem. |
| `qa-forge-bootstrap` | Spring Boot entry point: REST API, CLI, dashboard hosting, config. |
| `qa-forge-dashboard` | React SPA (Vite + Tailwind + Recharts), built into `qa-forge-bootstrap`'s static resources. |

## Prerequisites

- Java 21 (Temurin recommended)
- Apache Maven 3.9.9+
- Node.js 20.18.0 LTS (for the dashboard build and the Playwright MCP server)
- PostgreSQL 16 (H2 is used automatically for local/dev/test)

## Quick start (backend only, H2, dashboard skipped)

```bash
mvn -pl qa-forge-domain,qa-forge-application,qa-forge-infrastructure,qa-forge-bootstrap -am spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY
```

## Full build (with dashboard)

```bash
cd qa-forge-dashboard && npm ci && npm run build && cd ..
mvn -Pdashboard clean verify
```

## Switching LLM providers

See `docs/PRD.md` section 7 ("LLM Provider Abstraction"). In short: swap the Spring AI
starter dependency in `qa-forge-bootstrap/pom.xml`, update the matching `spring.ai.*` block
in `application.yml`, and restart. No application code changes are required — every agent is
wired against the vendor-neutral `ChatModel` / `ChatClient` types only.

## Docker Compose (local)

```bash
docker compose up --build
```

Builds the image from the root `Dockerfile`, which bundles both runtimes QA Forge needs
(Java 21 + Node.js 20 — see PRD §22 C-07) rather than mounting a jar into a bare JRE image.
See `docker-compose.yml` for required environment variables (database + exactly one LLM
provider block).

## CI

`.github/workflows/build.yml` builds the dashboard, runs its Vitest suite, then builds and
tests every Maven module against a real PostgreSQL service container. It runs on every push
and pull request, and is also invoked as a reusable workflow (`workflow_call`) by the release
pipeline below. See `docs/consumer-workflow-example.yml` for the workflow a team calling QA
Forge's API would add to their *own* repository (PRD §20.3) — it isn't meant to run in this
repository, so it lives under `docs/` rather than `.github/workflows/`.

## Shipping a release

`.github/workflows/release.yml` is the ship-it pipeline: it re-runs the full CI gate, stamps
the release version into every Maven module, builds and smoke-tests a container image, pushes
it to `ghcr.io/<owner>/qa-forge`, and publishes a GitHub Release with the jar, a SHA-256
checksum, and a CycloneDX SBOM attached. Both the jar and the image carry a signed build
provenance attestation (`actions/attest-build-provenance`), verifiable with:

```bash
gh attestation verify qa-forge-bootstrap.jar --owner <owner>
gh attestation verify oci://ghcr.io/<owner>/qa-forge:<version> --owner <owner>
```

Two ways to trigger a release:

- **Push a tag**: `git tag v1.4.0 && git push origin v1.4.0`.
- **Manual dispatch**: run the *Release* workflow from the Actions tab (or
  `gh workflow run release.yml -f bump=patch`), picking `patch` / `minor` / `major` / `custom`.
  The workflow computes the next version from the latest `vX.Y.Z` tag, creates and pushes that
  tag itself, then proceeds — no local tagging needed.

A release never ships unless the same Postgres-backed Maven + Vitest suite that runs on every
PR passes first. The `publish-release` job runs under the `production` GitHub Environment, so
you can add required reviewers there (Settings → Environments) if you want a manual approval
gate before a build goes out; with no protection rules configured it's a no-op.

The first push to GHCR creates the `qa-forge` package as **private** by default — link it to
this repository and flip visibility in the package settings if it should be publicly pullable.

Version numbers are derived from git tags at build time and are not committed back to
`pom.xml` — the tree stays on `1.0.0-SNAPSHOT` between releases; `qa version` in a shipped jar
reports the real released version via the manifest's `Implementation-Version`.

## CLI

```bash
qa analyze --repo acme/backend --pr 1234 --base-url https://staging.acme.com
qa regression --repo acme/backend --pr 1234 --base-url https://staging.acme.com
qa list --repo acme/backend
qa run --repo acme/backend --base-url https://staging.acme.com
qa export --repo acme/backend --output ./acme-backend-tests.zip
qa version
```

## Testing

```bash
mvn verify
cd qa-forge-dashboard && npm test
```
