package com.qaforge.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * QA Forge entry point (PRD §5.1): a single Spring Boot application exposing the REST API,
 * webhook endpoints, dashboard, and the {@code qa} Spring Shell CLI.
 *
 * <p>{@code @ComponentScan}'s default base package is {@code com.qaforge.bootstrap} (this
 * class's package); {@code scanBasePackages} widens it to {@code com.qaforge} so the
 * application/infrastructure modules' {@code @Service}/{@code @Component} beans and every
 * {@code @ConfigurationProperties} record across the codebase are picked up.
 *
 * <p>{@code @EnableJpaRepositories}/{@code @EntityScan} are explicit (not left to Spring Data
 * JPA's auto-configuration default) — verified by running the packaged jar that, unlike plain
 * component scanning, repository/entity scanning did not pick up
 * {@code com.qaforge.infrastructure.persistence.*} from {@code scanBasePackages} alone.
 */
@SpringBootApplication(scanBasePackages = "com.qaforge")
@ConfigurationPropertiesScan("com.qaforge")
@EnableJpaRepositories(basePackages = "com.qaforge.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.qaforge.infrastructure.persistence.entity")
public class QaForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaForgeApplication.class, args);
    }
}
