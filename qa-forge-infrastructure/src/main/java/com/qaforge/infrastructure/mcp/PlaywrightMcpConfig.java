package com.qaforge.infrastructure.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the Playwright MCP server as a STDIO child process (PRD §13.1/§13.2).
 *
 * <p>Uses the real Spring AI 2.0.0 / MCP SDK 2.0.0 API surface: {@code McpClient},
 * {@code StdioClientTransport}, and {@code SyncMcpToolCallbackProvider} live under
 * {@code io.modelcontextprotocol.client.*} / {@code org.springframework.ai.mcp.*} — not the
 * {@code org.springframework.ai.mcp.client.*} path shown in PRD §13.2 (verified against the
 * downloaded jars; see docs/IMPLEMENTATION_STATUS.md).
 *
 * <p>{@link McpSyncClient} implements {@link AutoCloseable}; Spring Boot's default
 * destroy-method inference calls {@code close()} on container shutdown, satisfying the
 * {@code ContextClosedEvent} → {@code client.close()} requirement in PRD §13.1 without extra
 * wiring.
 */
@Configuration
public class PlaywrightMcpConfig {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightMcpConfig.class);

    @Bean
    @ConditionalOnProperty(name = "qaforge.mcp.playwright.enabled", havingValue = "true", matchIfMissing = true)
    public McpSyncClient playwrightMcpClient(PlaywrightMcpProperties props) {
        verifyOnPath(props.command());

        ServerParameters serverParameters = ServerParameters.builder(props.command())
            .args(props.args())
            .build();
        StdioClientTransport transport = new StdioClientTransport(
            serverParameters, new JacksonMcpJsonMapper(JsonMapper.builder().build()));

        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofMillis(props.executionTimeoutMs()))
            .initializationTimeout(Duration.ofMillis(props.connectTimeoutMs()))
            .build();

        client.initialize();
        List<McpSchema.Tool> tools = client.listTools().tools();
        log.info("Playwright MCP server ready with {} tools: {}", tools.size(),
            tools.stream().map(McpSchema.Tool::name).toList());
        return client;
    }

    @Bean
    @ConditionalOnBean(McpSyncClient.class)
    public SyncMcpToolCallbackProvider playwrightToolProvider(McpSyncClient playwrightMcpClient) {
        return new SyncMcpToolCallbackProvider(playwrightMcpClient);
    }

    /**
     * When {@code qaforge.mcp.playwright.enabled=false} (e.g. the dev profile), no
     * {@link McpSyncClient} bean exists. {@code TestExecutionAgent} and
     * {@code SelfHealingLocatorAgent} still require a {@link SyncMcpToolCallbackProvider}
     * unconditionally, so this no-tools fallback keeps the application context startable.
     */
    @Bean
    @ConditionalOnMissingBean(SyncMcpToolCallbackProvider.class)
    public SyncMcpToolCallbackProvider noopPlaywrightToolProvider() {
        return new SyncMcpToolCallbackProvider(List.of());
    }

    private void verifyOnPath(String command) {
        try {
            Process process = new ProcessBuilder(isWindows() ? "where" : "which", command)
                .redirectErrorStream(true)
                .start();
            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited || process.exitValue() != 0) {
                throw new IllegalStateException(
                    "'" + command + "' was not found on PATH; QA Forge requires Node.js 20+ to run the Playwright MCP server");
            }
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to verify '" + command + "' is on PATH", e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
