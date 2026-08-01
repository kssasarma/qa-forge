package com.qaforge.bootstrap.rest;

import com.qaforge.bootstrap.rest.dto.TestCaseResponseDto;
import com.qaforge.bootstrap.rest.dto.TestsPageResponseDto;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.port.out.TestRegistryPort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/tests} — paginated test registry listing (PRD §12.5). */
@RestController
@RequestMapping("/api/v1/tests")
public class TestsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TestRegistryPort testRegistryPort;

    public TestsController(TestRegistryPort testRegistryPort) {
        this.testRegistryPort = testRegistryPort;
    }

    @GetMapping
    public TestsPageResponseDto list(
            @RequestParam String repository,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) TestLayer layer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<TestCase> filtered = testRegistryPort.findByStatus(repository, status).stream()
            .filter(tc -> layer == null || tc.testLayer() == layer)
            .toList();

        int fromIndex = Math.min(page * boundedSize, filtered.size());
        int toIndex = Math.min(fromIndex + boundedSize, filtered.size());
        List<TestCaseResponseDto> pageContent = filtered.subList(fromIndex, toIndex).stream()
            .map(TestCaseResponseDto::from)
            .toList();

        return TestsPageResponseDto.of(pageContent, filtered.size(), page, boundedSize);
    }
}
