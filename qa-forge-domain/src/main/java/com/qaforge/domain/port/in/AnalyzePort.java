package com.qaforge.domain.port.in;

import com.qaforge.domain.model.AnalysisRequest;
import com.qaforge.domain.model.AnalysisResult;

public interface AnalyzePort {
    AnalysisResult analyze(AnalysisRequest request);
}
