ALTER TABLE pis_v2.report_distribution
    ADD COLUMN IF NOT EXISTS requested_by_ref VARCHAR(128);
ALTER TABLE pis_v2.report_distribution
    ADD COLUMN IF NOT EXISTS delivery_reference VARCHAR(256);
ALTER TABLE pis_v2.report_distribution
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(128);

ALTER TABLE pis_v2.report_print_record
    ADD COLUMN IF NOT EXISTS requested_by_ref VARCHAR(128);
ALTER TABLE pis_v2.report_print_record
    ADD COLUMN IF NOT EXISTS device_job_reference VARCHAR(256);
ALTER TABLE pis_v2.report_print_record
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(128);
ALTER TABLE pis_v2.report_print_record
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(2000);

CREATE TABLE IF NOT EXISTS pis_v2.report_output_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    result_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_report_output_idempotency UNIQUE
        (organization_reference, operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_v2_report_distribution_history
    ON pis_v2.report_distribution (organization_reference, report_id, requested_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_v2_report_print_history
    ON pis_v2.report_print_record (organization_reference, report_id, printed_at DESC, id);
