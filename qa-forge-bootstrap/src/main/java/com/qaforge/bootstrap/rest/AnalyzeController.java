package com.qaforge.bootstrap.rest;

import com.qaforge.bootstrap.rest.dto.AnalyzeRequestDto;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.domain.port.in.AnalyzePort;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/analyze} — synchronous full analyze pipeline (PRD §12.1). */
@RestController
@RequestMapping("/api/v1/analyze")
public class AnalyzeController {

    private final AnalyzePort analyzePort;

    public AnalyzeController(AnalyzePort analyzePort) {
        this.analyzePort = analyzePort;
    }

    @PostMapping
    public ResponseEntity<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequestDto request) {
        AnalysisResult result = analyzePort.analyze(request.toDomain("api"));
        return ResponseEntity.ok(result);
    }
}
