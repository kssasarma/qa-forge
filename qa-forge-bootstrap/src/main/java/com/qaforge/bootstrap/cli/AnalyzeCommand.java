package com.qaforge.bootstrap.cli;

import com.qaforge.domain.model.AnalysisRequest;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.domain.port.in.AnalyzePort;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/** {@code qa analyze} (PRD §12.10). */
@Component
@CommandGroup(name = "qa", prefix = "qa", description = "QA Forge commands")
public class AnalyzeCommand {

    private final AnalyzePort analyzePort;
    private final String defaultOutputBaseDirectory;

    public AnalyzeCommand(AnalyzePort analyzePort,
                           @Value("${qaforge.generation.output-base-directory:/tmp/qa-forge-tests}") String defaultOutputBaseDirectory) {
        this.analyzePort = analyzePort;
        this.defaultOutputBaseDirectory = defaultOutputBaseDirectory;
    }

    @Command(value = "analyze", description = "Analyze a PR/MR and generate tests")
    public String analyze(
            @Option(longName = "repo", required = true, description = "repositoryFullName, e.g. acme/backend") String repo,
            @Option(longName = "pr", required = true, description = "PR/MR number") String pr,
            @Option(longName = "base-url", required = true, description = "target application base URL") String baseUrl,
            @Option(longName = "vcs", defaultValue = "github", description = "github|gitlab") String vcs,
            @Option(longName = "output-dir", defaultValue = "", description = "test output directory") String outputDir,
            @Option(longName = "openapi-spec", defaultValue = "", description = "OpenAPI spec URL; enables RestAssured generation") String openApiSpec) {

        String resolvedOutputDir = outputDir.isBlank()
            ? Path.of(defaultOutputBaseDirectory, repo.replace('/', '-')).toString()
            : outputDir;

        AnalysisRequest request = new AnalysisRequest(
            vcs, repo, pr, baseUrl, resolvedOutputDir, openApiSpec.isBlank() ? null : openApiSpec, "cli");

        AnalysisResult result = analyzePort.analyze(request);
        return formatResult(result);
    }

    private String formatResult(AnalysisResult result) {
        return """
            QA Forge — Analyze %s
            Repository: %s | PR: %s | Outcome: %s
            New tests: %d | Updated: %d | Skipped: %d | Obsoleted: %d
            Execution: %d/%d passed (%.1f%%), %d self-healed
            Duration: %dms
            """.formatted(
                result.runId(), result.repository(), result.prNumber(), result.outcome(),
                result.newTestsGenerated(), result.updatedTests(), result.skippedScenarios(), result.markedObsolete(),
                result.executionSummary().passed(), result.executionSummary().total(),
                result.executionSummary().passRatePercent(), result.executionSummary().selfHealed(),
                result.durationMs());
    }
}
