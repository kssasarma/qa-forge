package com.qaforge.bootstrap.rest;

import com.qaforge.bootstrap.rest.dto.RegressionRequestDto;
import com.qaforge.domain.model.RegressionResult;
import com.qaforge.domain.port.in.RegressionPort;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/regression} — synchronous regression suite run (PRD §12.2). */
@RestController
@RequestMapping("/api/v1/regression")
public class RegressionController {

    private final RegressionPort regressionPort;

    public RegressionController(RegressionPort regressionPort) {
        this.regressionPort = regressionPort;
    }

    @PostMapping
    public ResponseEntity<RegressionResult> runRegression(@Valid @RequestBody RegressionRequestDto request) {
        RegressionResult result = regressionPort.runRegression(request.toDomain("api"));
        return ResponseEntity.ok(result);
    }
}
