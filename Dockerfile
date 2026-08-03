# Multi-stage build producing a single image with both runtimes QA Forge needs (PRD §22 C-07:
# "Requires Node.js 20 on the same host as the JVM ... provide Docker image with both
# runtimes"). PRD §20.2's docker-compose.yml sketch mounts a jar into a bare eclipse-temurin
# JRE image with no Node.js at all — the Playwright MCP server (npx @playwright/mcp) would
# have nothing to run on. This Dockerfile is the actual "image with both runtimes."

FROM node:20.18.0-bookworm-slim AS dashboard-build
WORKDIR /workspace/qa-forge-dashboard
COPY qa-forge-dashboard/package*.json ./
RUN npm ci
COPY qa-forge-dashboard/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS java-build
# Set by the release workflow to the released semantic version (e.g. 1.4.0) so the packaged
# jar's MANIFEST.MF Implementation-Version — and therefore `qa version` — reports the real
# shipped version instead of the source tree's permanent 1.0.0-SNAPSHOT. Left empty for local
# `docker compose up --build`, which just keeps the SNAPSHOT version.
ARG APP_VERSION=""
WORKDIR /workspace
COPY pom.xml ./
COPY qa-forge-domain/pom.xml qa-forge-domain/pom.xml
COPY qa-forge-application/pom.xml qa-forge-application/pom.xml
COPY qa-forge-infrastructure/pom.xml qa-forge-infrastructure/pom.xml
COPY qa-forge-bootstrap/pom.xml qa-forge-bootstrap/pom.xml
RUN mvn -B -q dependency:go-offline || true
COPY qa-forge-domain qa-forge-domain
COPY qa-forge-application qa-forge-application
COPY qa-forge-infrastructure qa-forge-infrastructure
COPY qa-forge-bootstrap qa-forge-bootstrap
COPY --from=dashboard-build /workspace/qa-forge-dashboard/../qa-forge-bootstrap/src/main/resources/static qa-forge-bootstrap/src/main/resources/static
RUN if [ -n "$APP_VERSION" ]; then \
      mvn -B -q versions:set -DnewVersion="$APP_VERSION" -DprocessAllModules=true -DgenerateBackupPoms=false; \
    fi
RUN mvn -B -q -pl qa-forge-domain,qa-forge-application,qa-forge-infrastructure,qa-forge-bootstrap -am package -DskipTests

FROM eclipse-temurin:21.0.3_9-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g npm@10 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=java-build /workspace/qa-forge-bootstrap/target/qa-forge-bootstrap.jar /app/qa-forge.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/qa-forge.jar"]
