package com.qaforge.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Maps to {@code test_run_items} (PRD §11.3). */
@Entity
@Table(name = "test_run_items")
public class TestRunItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private TestRunEntity run;

    @Column(name = "test_case_id")
    private UUID testCaseId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Column(name = "test_layer", length = 32)
    private String testLayer;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "self_healed", nullable = false)
    private boolean selfHealed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TestRunItemEntity() {}

    public TestRunItemEntity(UUID testCaseId, String fileName, UUID scenarioId, String testLayer,
                              String status, String errorMessage, Long durationMs, int retryCount, boolean selfHealed) {
        this.testCaseId = testCaseId;
        this.fileName = fileName;
        this.scenarioId = scenarioId;
        this.testLayer = testLayer;
        this.status = status;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.retryCount = retryCount;
        this.selfHealed = selfHealed;
    }

    void setRun(TestRunEntity run) { this.run = run; }

    public UUID getId() { return id; }
    public TestRunEntity getRun() { return run; }
    public UUID getTestCaseId() { return testCaseId; }
    public String getFileName() { return fileName; }
    public UUID getScenarioId() { return scenarioId; }
    public String getTestLayer() { return testLayer; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Long getDurationMs() { return durationMs; }
    public int getRetryCount() { return retryCount; }
    public boolean isSelfHealed() { return selfHealed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
