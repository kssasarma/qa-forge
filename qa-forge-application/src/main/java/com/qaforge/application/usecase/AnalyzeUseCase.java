package com.qaforge.application.usecase;

import com.qaforge.application.orchestration.AgentOrchestrator;
import com.qaforge.domain.model.AnalysisRequest;
import com.qaforge.domain.model.AnalysisResult;
import com.qaforge.domain.port.in.AnalyzePort;
import org.springframework.stereotype.Service;

@Service
public class AnalyzeUseCase implements AnalyzePort {

    private final AgentOrchestrator agentOrchestrator;

    public AnalyzeUseCase(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        return agentOrchestrator.orchestrate(request);
    }
}
