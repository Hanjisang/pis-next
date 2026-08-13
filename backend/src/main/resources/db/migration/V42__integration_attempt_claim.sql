-- A persisted sending state makes outbound retry claiming atomic across processes.
ALTER TABLE pis_v2.integration_message_log
    DROP CONSTRAINT IF EXISTS ck_v2_integration_status;

ALTER TABLE pis_v2.integration_message_log
    ADD CONSTRAINT ck_v2_integration_status CHECK (status_code IN
        ('PENDING', 'SENDING', 'SUCCEEDED', 'RETRY_PENDING', 'DEAD_LETTER'));

CREATE INDEX IF NOT EXISTS idx_v2_integration_retry_claim
    ON pis_v2.integration_message_log (status_code, next_retry_at, updated_at);
