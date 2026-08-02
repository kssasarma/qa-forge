package com.qaforge.bootstrap.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async infrastructure: the {@code webhookExecutor} pool backs {@code @Async} webhook
 * processing (PRD §5.3 — POST /webhook/* return 202 immediately, default 4 threads via
 * {@code qaforge.async.webhook-thread-pool-size}), and {@code generationExecutor} backs the
 * parallel Playwright/RestAssured/DB generation fan-out in {@code AgentOrchestrator} (§9.2.1
 * step 8). {@code @EnableRetry} activates the {@code @Retryable}/{@code @Recover} methods on
 * the GitHub/GitLab adapters (§16.3).
 */
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

    @Bean("webhookExecutor")
    public Executor webhookExecutor(@Value("${qaforge.async.webhook-thread-pool-size:4}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("qaforge-webhook-");
        executor.initialize();
        return executor;
    }

    /**
     * Not independently configurable via {@code qaforge.*} properties (PRD §15 doesn't define
     * one) — a fixed small pool is enough for the generation fan-out, which is bounded by
     * {@code qaforge.generation.max-scenarios-per-pr} (default 10) scenarios per run.
     */
    @Bean("generationExecutor")
    public Executor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("qaforge-generation-");
        executor.initialize();
        return executor;
    }
}
