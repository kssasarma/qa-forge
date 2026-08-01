CREATE TABLE test_run_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID          NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
    test_case_id    UUID          REFERENCES test_cases(id),
    file_name       VARCHAR(512)  NOT NULL,
    scenario_id     UUID,
    test_layer      VARCHAR(32),
    status          VARCHAR(32)   NOT NULL,
    error_message   TEXT,
    duration_ms     BIGINT,
    retry_count     INT           NOT NULL DEFAULT 0,
    self_healed     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tri_run    ON test_run_items(run_id);
CREATE INDEX idx_tri_status ON test_run_items(status);
