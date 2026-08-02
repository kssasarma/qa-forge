package com.qaforge.infrastructure.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal {@code @SpringBootConfiguration} for {@code @DataJpaTest} slices in this module —
 * unlike {@code qa-forge-bootstrap}, this module has no main application class of its own for
 * Spring Boot's test context bootstrapper to discover by searching packages upwards.
 */
@SpringBootApplication
public class TestPersistenceApplication {
}
