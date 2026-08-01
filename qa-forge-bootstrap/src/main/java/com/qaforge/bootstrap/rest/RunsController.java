package com.qaforge.bootstrap.rest;

import com.qaforge.bootstrap.rest.dto.RunDetailResponseDto;
import com.qaforge.bootstrap.rest.dto.RunSummaryResponseDto;
import com.qaforge.bootstrap.rest.dto.RunsPageResponseDto;
import com.qaforge.domain.model.TestRun;
import com.qaforge.domain.port.out.TestRegistryPort;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/runs} and {@code GET /api/v1/runs/{runId}} (PRD §12.6/§12.7). */
@RestController
@RequestMapping("/api/v1/runs")
public class RunsController {

    private final TestRegistryPort testRegistryPort;

    public RunsController(TestRegistryPort testRegistryPort) {
        this.testRegistryPort = testRegistryPort;
    }

    @GetMapping("/{runId}")
    public ResponseEntity<RunDetailResponseDto> getRun(@PathVariable String runId) {
        return testRegistryPort.findRun(runId)
            .map(RunDetailResponseDto::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public RunsPageResponseDto list(
            @RequestParam String repository,
            @RequestParam(required = false) String prNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<TestRun> runs = testRegistryPort.findRuns(repository, prNumber, page, size);
        List<RunSummaryResponseDto> summaries = runs.stream().map(RunSummaryResponseDto::from).toList();
        return RunsPageResponseDto.of(summaries, summaries.size(), page, size);
    }
}
