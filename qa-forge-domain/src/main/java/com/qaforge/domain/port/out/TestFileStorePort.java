package com.qaforge.domain.port.out;

import com.qaforge.domain.model.GeneratedTest;
import java.util.List;

public interface TestFileStorePort {
    void write(GeneratedTest test, String outputDirectory);
    void writeAll(List<GeneratedTest> tests, String outputDirectory);
    void writePlaywrightConfig(String outputDirectory);
    byte[] exportZip(String repository);
}
