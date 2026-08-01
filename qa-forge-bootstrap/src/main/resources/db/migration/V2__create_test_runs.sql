CREATE TABLE test_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository      VARCHAR(256)  NOT NULL,
    vcs_type        VARCHAR(32)   NOT NULL DEFAULT 'github',
    pr_number       VARCHAR(64),
    run_type        VARCHAR(32)   NOT NULL,
    triggered_by    VARCHAR(64),
    total_count     INT           NOT NULL DEFAULT 0,
    passed_count    INT           NOT NULL DEFAULT 0,
    failed_count    INT           NOT NULL DEFAULT 0,
    error_count     INT           NOT NULL DEFAULT 0,
    skipped_count   INT           NOT NULL DEFAULT 0,
    pass_rate       NUMERIC(5,2),
    duration_ms     BIGINT,
    outcome         VARCHAR(32),
    gate_result     VARCHAR(16),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tr_repository ON test_runs(repository);
CREATE INDEX idx_tr_created    ON test_runs(created_at DESC);
