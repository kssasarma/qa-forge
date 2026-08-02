package com.qaforge.bootstrap.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qaforge.bootstrap.error.GlobalExceptionHandler;
import com.qaforge.domain.exception.PrNotFoundException;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.domain.model.ExecutionSummary;
import com.qaforge.domain.model.GeneratedFilesSummary;
import com.qaforge.domain.port.in.AnalyzePort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** PRD §19.3: web slice test, mocking the in-port. */
@WebMvcTest(controllers = AnalyzeController.class)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class AnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzePort analyzePort;

    @Test
    @WithMockUser
    void analyzeReturns200OnSuccess() throws Exception {
        AnalysisResult result = new AnalysisResult(
            "run-1", "1234", "acme/backend", "SUCCESS", 2, 1, 0, 0,
            new ExecutionSummary(3, 3, 0, 0, 0, 100.0),
            new GeneratedFilesSummary(List.of("checkout_pr1234.spec.ts"), List.of(), List.of()),
            12345L);
        when(analyzePort.analyze(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/analyze")
                .contentType("application/json")
                .content("""
                    {
                      "vcsType": "github",
                      "repositoryFullName": "acme/backend",
                      "prNumber": "1234",
                      "targetAppBaseUrl": "https://staging.acme.com",
                      "testOutputDirectory": "/tmp/qa-forge-tests/acme-backend"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.outcome").value("SUCCESS"))
            .andExpect(jsonPath("$.newTestsGenerated").value(2))
            .andExpect(jsonPath("$.executionSummary.passRatePercent").value(100.0));
    }

    @Test
    @WithMockUser
    void analyzeReturns422WhenPrNotFound() throws Exception {
        when(analyzePort.analyze(any())).thenThrow(new PrNotFoundException("acme/backend", "9999"));

        mockMvc.perform(post("/api/v1/analyze")
                .contentType("application/json")
                .content("""
                    {
                      "vcsType": "github",
                      "repositoryFullName": "acme/backend",
                      "prNumber": "9999",
                      "targetAppBaseUrl": "https://staging.acme.com",
                      "testOutputDirectory": "/tmp/qa-forge-tests/acme-backend"
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("PR_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void analyzeReturns400WhenRepositoryMissing() throws Exception {
        mockMvc.perform(post("/api/v1/analyze")
                .contentType("application/json")
                .content("""
                    {
                      "vcsType": "github",
                      "prNumber": "1234",
                      "targetAppBaseUrl": "https://staging.acme.com",
                      "testOutputDirectory": "/tmp/qa-forge-tests/acme-backend"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
