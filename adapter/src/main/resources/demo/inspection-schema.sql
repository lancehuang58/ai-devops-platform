CREATE TABLE IF NOT EXISTS demo_inspection (
 id UUID PRIMARY KEY, correlation_id UUID NOT NULL, actor_id VARCHAR(200) NOT NULL,
 service_id VARCHAR(64) NOT NULL, service_name VARCHAR(200) NOT NULL, health_status VARCHAR(20) NOT NULL,
 service_version VARCHAR(200) NOT NULL, observed_at TIMESTAMP(9) WITH TIME ZONE NOT NULL,
 summary VARCHAR(4000) NOT NULL, model_name VARCHAR(200) NOT NULL, probe_mocked BOOLEAN NOT NULL, model_mocked BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS demo_notification_outbox (
 event_id UUID PRIMARY KEY, report_id UUID NOT NULL REFERENCES demo_inspection(id),
 correlation_id UUID NOT NULL, occurred_at TIMESTAMP(9) WITH TIME ZONE NOT NULL, acknowledged BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS demo_event_receipt (
 event_id UUID PRIMARY KEY REFERENCES demo_notification_outbox(event_id), consumer_id VARCHAR(100) NOT NULL
);
