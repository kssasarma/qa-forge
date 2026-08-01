package com.qaforge.bootstrap.cli;

import com.qaforge.domain.model.RegressionRequest;
import com.qaforge.domain.model.RegressionResult;
import com.qaforge.domain.port.in.RegressionPort;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/** {@code qa regression} (PRD §12.10). */
@Component
@CommandGroup(name = "qa", prefix = "qa", description = "QA Forge commands")
public class RegressionCommand {

    private final RegressionPort regressionPort;

    public RegressionCommand(RegressionPort regressionPort) {
        this.regressionPort = regressionPort;
    }

    @Command(value = "regression", description = "Run the full active test suite against a target URL")
    public String regression(
            @Option(longName = "repo", required = true, description = "repositoryFullName, e.g. acme/backend") String repo,
            @Option(longName = "pr", required = true, description = "PR/MR number") String pr,
            @Option(longName = "base-url", required = true, description = "target application base URL") String baseUrl,
            @Option(longName = "vcs", defaultValue = "github", description = "github|gitlab") String vcs) {

        RegressionResult result = regressionPort.runRegression(new RegressionRequest(vcs, repo, pr, baseUrl, "cli"));
        return formatResult(result);
    }

    private String formatResult(RegressionResult result) {
        return """
            QA Forge — Regression %s
            Outcome: %s | Gate: %s%s
            %d/%d passed (%.1f%%), %d self-healed
            Duration: %dms
            """.formatted(
                result.runId(), result.outcome(), result.gateResult(),
                result.blockedReason() == null ? "" : " — " + result.blockedReason(),
                result.passed(), result.total(), result.passRatePercent(), result.selfHealed(),
                result.durationMs());
    }
}
