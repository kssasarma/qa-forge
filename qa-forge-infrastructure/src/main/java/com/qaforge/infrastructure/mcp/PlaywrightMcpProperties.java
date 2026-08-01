package com.qaforge.infrastructure.mcp;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaforge.mcp.playwright")
public record PlaywrightMcpProperties(
    boolean enabled,
    String command,
    List<String> args,
    long connectTimeoutMs,
    long executionTimeoutMs,
    int maxToolCalls
) {
    public PlaywrightMcpProperties {
        if (command == null || command.isBlank()) {
            command = "npx";
        }
        if (args == null) {
            args = List.of("@playwright/mcp@latest", "--headless");
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 10_000;
        }
        if (executionTimeoutMs <= 0) {
            executionTimeoutMs = 120_000;
        }
        if (maxToolCalls <= 0) {
            maxToolCalls = 50;
        }
    }
}
