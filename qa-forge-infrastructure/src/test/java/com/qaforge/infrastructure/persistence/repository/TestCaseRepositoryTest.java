package com.qaforge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.qaforge.infrastructure.persistence.entity.TestCaseEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PRD §19.2: repository slice test against a real PostgreSQL container. Not run in this
 * implementation environment (no Docker daemon available here — see
 * docs/IMPLEMENTATION_STATUS.md); runs in CI (.github/workflows/build.yml) where Docker is
 * available for Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.qaforge.infrastructure.persistence.entity")
@ComponentScan(basePackages = "com.qaforge.infrastructure.persistence.repository")
@Testcontainers
class TestCaseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    private final TestCaseRepository repository;

    @Autowired
    TestCaseRepositoryTest(TestCaseRepository repository) {
        this.repository = repository;
    }

    @Test
    void savesAndFindsByFileName() {
        TestCaseEntity entity = new TestCaseEntity(
            "checkout_pr1234.spec.ts", UUID.randomUUID(), "Checkout test", "NEW", "PLAYWRIGHT",
            "Checkout", "/checkout", false, "smoke,checkout", "1234", "acme/backend", "github", "sha1");
        repository.save(entity);

        assertThat(repository.findByFileName("checkout_pr1234.spec.ts")).isPresent();
        assertThat(repository.findByFileName("does-not-exist.spec.ts")).isEmpty();
    }

    @Test
    void findsByRepositoryAndStatusAndSupportsMarkingObsolete() {
        TestCaseEntity active = new TestCaseEntity(
            "active.spec.ts", UUID.randomUUID(), "Active test", "NEW", "PLAYWRIGHT",
            "Checkout", "/checkout", false, "", "1", "acme/backend", "github", "sha");
        TestCaseEntity other = new TestCaseEntity(
            "other-repo.spec.ts", UUID.randomUUID(), "Other repo test", "NEW", "PLAYWRIGHT",
            "Login", "/login", false, "", "1", "acme/other", "github", "sha");
        repository.save(active);
        repository.save(other);

        assertThat(repository.findByRepositoryAndStatus("acme/backend", "ACTIVE")).hasSize(1);
        assertThat(repository.findByRepositoryAndStatus("acme/backend", "OBSOLETE")).isEmpty();

        active.setStatus("OBSOLETE");
        repository.save(active);

        assertThat(repository.findByRepositoryAndStatus("acme/backend", "ACTIVE")).isEmpty();
        assertThat(repository.findByRepositoryAndStatus("acme/backend", "OBSOLETE")).hasSize(1);
    }

    @Test
    void updatesExecutionStats() {
        TestCaseEntity entity = new TestCaseEntity(
            "checkout_pr1.spec.ts", UUID.randomUUID(), "title", "NEW", "PLAYWRIGHT",
            "Checkout", "/checkout", false, "", "1", "acme/backend", "github", "sha");
        repository.save(entity);

        entity.setLastExecutionStatus("PASSED");
        entity.setLastExecutionMs(1200L);
        entity.setExecutionCount(entity.getExecutionCount() + 1);
        repository.save(entity);

        TestCaseEntity reloaded = repository.findByFileName("checkout_pr1.spec.ts").orElseThrow();
        assertThat(reloaded.getLastExecutionStatus()).isEqualTo("PASSED");
        assertThat(reloaded.getLastExecutionMs()).isEqualTo(1200L);
        assertThat(reloaded.getExecutionCount()).isEqualTo(1);
    }
}
