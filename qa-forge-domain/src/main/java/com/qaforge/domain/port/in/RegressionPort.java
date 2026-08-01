package com.qaforge.domain.port.in;

import com.qaforge.domain.model.RegressionRequest;
import com.qaforge.domain.model.RegressionResult;

public interface RegressionPort {
    RegressionResult runRegression(RegressionRequest request);
}
