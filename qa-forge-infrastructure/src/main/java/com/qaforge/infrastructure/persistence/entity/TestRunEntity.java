package com.qaforge.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Maps to {@code test_runs} (PRD §11.2). */
@Entity
@Table(name = "test_runs")
public class TestRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repository", nullable = false, length = 256)
    private String repository;

    @Column(name = "vcs_type", nullable = false, length = 32)
    private String vcsType = "github";

    @Column(name = "pr_number", length = 64)
    private String prNumber;

    @Column(name = "run_type", nullable = false, length = 32)
    private String runType;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "pass_rate", precision = 5, scale = 2)
    private BigDecimal passRate;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "outcome", length = 32)
    private String outcome;

    @Column(name = "gate_result", length = 16)
    private String gateResult;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestRunItemEntity> items = new ArrayList<>();

    protected TestRunEntity() {}

    public TestRunEntity(String repository, String vcsType, String prNumber, String runType, String triggeredBy,
                          int totalCount, int passedCount, int failedCount, int errorCount, int skippedCount,
                          BigDecimal passRate, Long durationMs, String outcome, String gateResult) {
        this.repository = repository;
        this.vcsType = vcsType == null ? "github" : vcsType;
        this.prNumber = prNumber;
        this.runType = runType;
        this.triggeredBy = triggeredBy;
        this.totalCount = totalCount;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.errorCount = errorCount;
        this.skippedCount = skippedCount;
        this.passRate = passRate;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.gateResult = gateResult;
    }

    public void addItem(TestRunItemEntity item) {
        items.add(item);
        item.setRun(this);
    }

    public UUID getId() { return id; }
    public String getRepository() { return repository; }
    public String getVcsType() { return vcsType; }
    public String getPrNumber() { return prNumber; }
    public String getRunType() { return runType; }
    public String getTriggeredBy() { return triggeredBy; }
    public int getTotalCount() { return totalCount; }
    public int getPassedCount() { return passedCount; }
    public int getFailedCount() { return failedCount; }
    public int getErrorCount() { return errorCount; }
    public int getSkippedCount() { return skippedCount; }
    public BigDecimal getPassRate() { return passRate; }
    public Long getDurationMs() { return durationMs; }
    public String getOutcome() { return outcome; }
    public String getGateResult() { return gateResult; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<TestRunItemEntity> getItems() { return items; }
}
