package com.qaforge.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Maps to {@code test_cases} (PRD §11.1). */
@Entity
@Table(name = "test_cases")
public class TestCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false, unique = true, length = 512)
    private String fileName;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "scenario_title", nullable = false, length = 1024)
    private String scenarioTitle;

    @Column(name = "scenario_type", nullable = false, length = 32)
    private String scenarioType;

    @Column(name = "test_layer", nullable = false, length = 32)
    private String testLayer;

    @Column(name = "user_flow", length = 512)
    private String userFlow;

    @Column(name = "target_path", length = 512)
    private String targetPath;

    @Column(name = "requires_auth", nullable = false)
    private boolean requiresAuth;

    @Column(name = "tags")
    private String tags;

    @Column(name = "pr_number", length = 64)
    private String prNumber;

    @Column(name = "repository", nullable = false, length = 256)
    private String repository;

    @Column(name = "vcs_type", nullable = false, length = 32)
    private String vcsType = "github";

    @Column(name = "head_sha", length = 40)
    private String headSha;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "last_execution_status", length = 32)
    private String lastExecutionStatus;

    @Column(name = "last_execution_ms")
    private Long lastExecutionMs;

    @Column(name = "execution_count", nullable = false)
    private int executionCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TestCaseEntity() {}

    public TestCaseEntity(String fileName, UUID scenarioId, String scenarioTitle, String scenarioType,
                           String testLayer, String userFlow, String targetPath, boolean requiresAuth,
                           String tags, String prNumber, String repository, String vcsType, String headSha) {
        this.fileName = fileName;
        this.scenarioId = scenarioId;
        this.scenarioTitle = scenarioTitle;
        this.scenarioType = scenarioType;
        this.testLayer = testLayer;
        this.userFlow = userFlow;
        this.targetPath = targetPath;
        this.requiresAuth = requiresAuth;
        this.tags = tags;
        this.prNumber = prNumber;
        this.repository = repository;
        this.vcsType = vcsType == null ? "github" : vcsType;
        this.headSha = headSha;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public UUID getScenarioId() { return scenarioId; }
    public String getScenarioTitle() { return scenarioTitle; }
    public String getScenarioType() { return scenarioType; }
    public String getTestLayer() { return testLayer; }
    public String getUserFlow() { return userFlow; }
    public String getTargetPath() { return targetPath; }
    public boolean isRequiresAuth() { return requiresAuth; }
    public String getTags() { return tags; }
    public String getPrNumber() { return prNumber; }
    public String getRepository() { return repository; }
    public String getVcsType() { return vcsType; }
    public String getHeadSha() { return headSha; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastExecutionStatus() { return lastExecutionStatus; }
    public void setLastExecutionStatus(String lastExecutionStatus) { this.lastExecutionStatus = lastExecutionStatus; }
    public Long getLastExecutionMs() { return lastExecutionMs; }
    public void setLastExecutionMs(Long lastExecutionMs) { this.lastExecutionMs = lastExecutionMs; }
    public int getExecutionCount() { return executionCount; }
    public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
