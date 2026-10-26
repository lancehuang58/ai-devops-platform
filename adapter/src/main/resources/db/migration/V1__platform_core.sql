CREATE TABLE symptom_type (
    symptom_type text PRIMARY KEY,
    description text NOT NULL
);

INSERT INTO symptom_type (symptom_type, description) VALUES
    ('SERVICE_UNAVAILABLE', 'service down'),
    ('HEALTH_CHECK_FAILED', 'health check failed'),
    ('DISK_USAGE_HIGH', 'disk usage high'),
    ('STORAGE_UNRESPONSIVE', 'storage unresponsive'),
    ('OTHER', 'unclassified');

CREATE TABLE monitor_source (
    source_id text PRIMARY KEY,
    source_type text NOT NULL,
    display_name text NOT NULL,
    normalizer_ref text NOT NULL,
    enabled boolean NOT NULL DEFAULT true
);

CREATE TABLE alert_delivery (
    delivery_id uuid PRIMARY KEY,
    source_id text NOT NULL REFERENCES monitor_source (source_id),
    received_at timestamptz NOT NULL,
    body_sha256 char(64) NOT NULL,
    raw_body jsonb NOT NULL,
    alert_count integer NOT NULL
);

CREATE TABLE alert_group (
    group_id uuid PRIMARY KEY,
    fingerprint char(64) NOT NULL,
    fingerprint_version text NOT NULL,
    active_fingerprint char(64),
    environment text NOT NULL,
    resource_id text NOT NULL,
    resource_resolution text NOT NULL,
    symptom_type text NOT NULL REFERENCES symptom_type (symptom_type),
    discriminator jsonb NOT NULL DEFAULT '{}',
    status text NOT NULL,
    is_flapping boolean NOT NULL DEFAULT false,
    group_epoch integer NOT NULL DEFAULT 1,
    severity_current text NOT NULL,
    severity_max text NOT NULL,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    last_firing_at timestamptz NOT NULL,
    last_state_change_at timestamptz NOT NULL,
    recovering_since timestamptz,
    reopen_until timestamptz,
    occurrence_count bigint NOT NULL DEFAULT 1,
    suppressed_count bigint NOT NULL DEFAULT 0,
    flap_window_start timestamptz,
    flap_transitions integer NOT NULL DEFAULT 0,
    item_id uuid,
    created_at timestamptz NOT NULL,
    closed_at timestamptz,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_alert_group_active_fp UNIQUE (active_fingerprint),
    CONSTRAINT ck_active_fp CHECK (
        (status = 'CLOSED' AND active_fingerprint IS NULL AND closed_at IS NOT NULL)
        OR (status <> 'CLOSED' AND active_fingerprint = fingerprint))
);

CREATE TABLE alert_receipt (
    receipt_id uuid PRIMARY KEY,
    source_id text NOT NULL REFERENCES monitor_source (source_id),
    source_event_id text NOT NULL,
    payload_sha256 char(64) NOT NULL,
    raw_alert jsonb NOT NULL,
    first_received_at timestamptz NOT NULL,
    last_received_at timestamptz NOT NULL,
    duplicate_count integer NOT NULL DEFAULT 0,
    normalize_status text NOT NULL,
    event_id uuid UNIQUE,
    fingerprint char(64),
    event_status text,
    observed_at timestamptz,
    group_id uuid REFERENCES alert_group (group_id),
    CONSTRAINT uq_alert_receipt_source_event UNIQUE (source_id, source_event_id)
);

CREATE SEQUENCE work_item_no_seq;

CREATE TABLE work_item (
    item_id uuid PRIMARY KEY,
    item_no text NOT NULL UNIQUE,
    item_type text NOT NULL,
    status text NOT NULL,
    closure_reason text,
    suggested_closure_reason text,
    title text NOT NULL,
    requestor_principal_id text NOT NULL,
    source_kind text NOT NULL,
    environment text NOT NULL,
    primary_resource_id text,
    blocked_reason text,
    signal_state text,
    record_completeness text NOT NULL,
    active_takeover_id uuid,
    duplicate_of uuid,
    row_version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    status_changed_at timestamptz NOT NULL,
    CONSTRAINT ck_work_item_closed CHECK (
        (status = 'CLOSED' AND closure_reason IS NOT NULL) OR status <> 'CLOSED')
);

CREATE INDEX ix_work_item_open_updated ON work_item (updated_at DESC) WHERE status <> 'CLOSED';
CREATE INDEX ix_work_item_status_updated ON work_item (status, updated_at DESC);
CREATE INDEX ix_work_item_resource ON work_item (primary_resource_id);

ALTER TABLE alert_group
    ADD CONSTRAINT fk_alert_group_item FOREIGN KEY (item_id) REFERENCES work_item (item_id);

CREATE TABLE work_item_label (
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    label_id text NOT NULL,
    label_name text NOT NULL,
    label_kind text NOT NULL,
    PRIMARY KEY (item_id, label_id)
);

CREATE TABLE case_event (
    event_id uuid NOT NULL,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    seq integer NOT NULL,
    event_type text NOT NULL,
    detail text,
    occurred_at timestamptz NOT NULL,
    aggregate_version bigint NOT NULL,
    PRIMARY KEY (item_id, seq)
);

CREATE TABLE work_activity (
    activity_id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    activity_kind text NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz
);

CREATE TABLE approval (
    approval_id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    status text NOT NULL,
    approver text,
    self_approved boolean NOT NULL DEFAULT false,
    authorization_source text,
    plan_hash text,
    created_at timestamptz NOT NULL,
    decided_at timestamptz
);

CREATE TABLE closure_draft (
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    version integer NOT NULL,
    summary text NOT NULL,
    suggested_reason text NOT NULL,
    producer text NOT NULL,
    confirmed_by text,
    confirmed_at timestamptz,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (item_id, version)
);

CREATE TABLE external_sync_request (
    request_id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    destination text NOT NULL,
    operation text NOT NULL,
    payload_hash char(64) NOT NULL,
    status text NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    max_retries integer NOT NULL DEFAULT 5,
    next_attempt_at timestamptz NOT NULL,
    dedup_key text NOT NULL UNIQUE,
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL
);

CREATE TABLE external_sync_attempt (
    attempt_id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES external_sync_request (request_id),
    attempted_at timestamptz NOT NULL,
    outcome text NOT NULL,
    error_class text
);

CREATE TABLE incident_candidate (
    candidate_id uuid PRIMARY KEY,
    active_key text UNIQUE,
    rule_id text NOT NULL,
    environment text NOT NULL,
    upstream_resource_id text NOT NULL,
    status text NOT NULL,
    hold_until timestamptz,
    item_id uuid REFERENCES work_item (item_id),
    created_at timestamptz NOT NULL
);

CREATE TABLE incident_candidate_member (
    candidate_id uuid NOT NULL REFERENCES incident_candidate (candidate_id),
    group_id uuid NOT NULL REFERENCES alert_group (group_id),
    PRIMARY KEY (candidate_id, group_id)
);

CREATE TABLE notification_outbox (
    notification_id uuid PRIMARY KEY,
    dedup_key text NOT NULL UNIQUE,
    kind text NOT NULL,
    body text NOT NULL,
    status text NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE work_task (
    task_id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    task_kind text NOT NULL,
    dispatching boolean NOT NULL DEFAULT false,
    run_after timestamptz NOT NULL,
    status text NOT NULL,
    claimed_by text,
    lease_until timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    dedup_key text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_work_task_ready ON work_task (run_after) WHERE status = 'READY';
CREATE UNIQUE INDEX ux_work_task_one_claimed_per_item ON work_task (item_id) WHERE status = 'CLAIMED';

CREATE TABLE resource_lock (
    resource_id varchar(200) PRIMARY KEY,
    holder_item_id uuid NOT NULL REFERENCES work_item (item_id),
    holder_phase varchar(32) NOT NULL,
    owner_instance varchar(100) NOT NULL,
    owner_epoch bigint NOT NULL,
    lease_until timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    acquired_at timestamptz NOT NULL
);

CREATE TABLE resource_lock_waiter (
    waiter_id uuid PRIMARY KEY,
    resource_id varchar(200) NOT NULL,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    enqueued_at timestamptz NOT NULL,
    status varchar(16) NOT NULL
);

CREATE TABLE manual_takeover (
    takeover_id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES work_item (item_id),
    status text NOT NULL,
    operator_id text NOT NULL,
    tool_name text NOT NULL,
    model_name text NOT NULL,
    reason text NOT NULL,
    from_status text NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz
);

CREATE UNIQUE INDEX ux_takeover_active ON manual_takeover (item_id) WHERE status = 'ACTIVE';

CREATE TABLE takeover_package (
    package_id uuid PRIMARY KEY,
    takeover_id uuid NOT NULL REFERENCES manual_takeover (takeover_id),
    version integer NOT NULL,
    body bytea NOT NULL,
    sha256 char(64) NOT NULL,
    created_by text NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (takeover_id, version)
);

CREATE TABLE takeover_writeback (
    writeback_id uuid PRIMARY KEY,
    takeover_id uuid NOT NULL REFERENCES manual_takeover (takeover_id),
    version integer NOT NULL,
    summary text NOT NULL,
    root_cause text NOT NULL,
    actions text NOT NULL,
    evidence text NOT NULL,
    created_by text NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (takeover_id, version)
);
