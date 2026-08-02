CREATE TABLE test_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name           VARCHAR(512)   NOT NULL UNIQUE,
    scenario_id         UUID           NOT NULL,
    scenario_title      VARCHAR(1024)  NOT NULL,
    scenario_type       VARCHAR(32)    NOT NULL,
    test_layer          VARCHAR(32)    NOT NULL,
    user_flow           VARCHAR(512),
    target_path         VARCHAR(512),
    requires_auth       BOOLEAN        NOT NULL DEFAULT FALSE,
    tags                TEXT,
    pr_number           VARCHAR(64),
    repository          VARCHAR(256)   NOT NULL,
    vcs_type            VARCHAR(32)    NOT NULL DEFAULT 'github',
    head_sha            VARCHAR(40),
    status              VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
    last_execution_status VARCHAR(32),
    last_execution_ms   BIGINT,
    execution_count     INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tc_repository_status  ON test_cases(repository, status);
CREATE INDEX idx_tc_pr                 ON test_cases(pr_number);
CREATE INDEX idx_tc_layer              ON test_cases(test_layer);
