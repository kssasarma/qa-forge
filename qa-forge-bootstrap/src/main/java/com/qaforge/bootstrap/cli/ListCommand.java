package com.qaforge.bootstrap.cli;

import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.port.out.TestRegistryPort;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/** {@code qa list} (PRD §12.10). */
@Component
@CommandGroup(name = "qa", description = "QA Forge commands")
public class ListCommand {

    private final TestRegistryPort testRegistryPort;

    public ListCommand(TestRegistryPort testRegistryPort) {
        this.testRegistryPort = testRegistryPort;
    }

    @Command(name = {"qa", "list"}, description = "List registered test cases")
    public String qaList(
            @Option(longName = "repo", required = true, description = "repositoryFullName") String repo,
            @Option(longName = "status", defaultValue = "ACTIVE", description = "ACTIVE|OBSOLETE") String status,
            @Option(longName = "layer", defaultValue = "", description = "PLAYWRIGHT|REST_ASSURED|DB_VALIDATION") String layer) {

        List<TestCase> tests = testRegistryPort.findByStatus(repo, status).stream()
            .filter(tc -> layer.isBlank() || tc.testLayer() == TestLayer.valueOf(layer))
            .toList();

        if (tests.isEmpty()) {
            return "No test cases found for " + repo + " (status=" + status + ")";
        }

        return tests.stream()
            .map(tc -> "%-45s %-14s %-25s %-10s".formatted(
                tc.fileName(), tc.testLayer(), tc.userFlow(), tc.lastExecutionStatus()))
            .collect(Collectors.joining("\n"));
    }
}
