ALTER TABLE work_activity ADD COLUMN detail text;

CREATE TABLE probe_result (
    probe_result_id uuid PRIMARY KEY,
    instance_id text NOT NULL,
    channel text NOT NULL,
    verdict text NOT NULL,
    reason text NOT NULL,
    disposition text NOT NULL,
    observed_at timestamptz NOT NULL
);

CREATE INDEX ix_probe_result_instance ON probe_result (instance_id, observed_at);
