package com.qaforge.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * QA Forge entry point (PRD §5.1): a single Spring Boot application exposing the REST API,
 * webhook endpoints, dashboard, and the {@code qa} Spring Shell CLI.
 *
 * <p>{@code @ComponentScan}'s default base package is {@code com.qaforge.bootstrap} (this
 * class's package); {@code scanBasePackages} widens it to {@code com.qaforge} so the
 * application/infrastructure modules' {@code @Service}/{@code @Component} beans and every
 * {@code @ConfigurationProperties} record across the codebase are picked up.
 *
 * <p>JPA repository/entity scanning is configured on {@link com.qaforge.bootstrap.config.PersistenceConfig},
 * not here — putting {@code @EnableJpaRepositories}/{@code @EntityScan} directly on this
 * primary {@code @SpringBootConfiguration} class made {@code @WebMvcTest} slices pull in JPA
 * repository beans with no DataSource to back them (verified by an actual test failure); a
 * separate {@code @Configuration} class isn't part of a web slice's minimal context.
 */
@SpringBootApplication(scanBasePackages = "com.qaforge")
@ConfigurationPropertiesScan("com.qaforge")
public class QaForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaForgeApplication.class, args);
    }
}
