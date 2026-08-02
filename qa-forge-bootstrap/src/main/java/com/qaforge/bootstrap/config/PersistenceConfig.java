package com.qaforge.bootstrap.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA repository/entity scanning (not left to Spring Data JPA's auto-configuration
 * default) — verified by running the packaged jar that, unlike plain component scanning,
 * repository/entity scanning did not pick up {@code com.qaforge.infrastructure.persistence.*}
 * from {@code QaForgeApplication}'s {@code scanBasePackages} alone.
 *
 * <p>Kept on its own {@code @Configuration} class, separate from {@code QaForgeApplication},
 * so {@code @WebMvcTest} and other narrow test slices — which use only the primary
 * {@code @SpringBootConfiguration} plus their own filtered component types — don't
 * accidentally pull in JPA repository beans with no {@code DataSource} to back them.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.qaforge.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.qaforge.infrastructure.persistence.entity")
public class PersistenceConfig {
}
