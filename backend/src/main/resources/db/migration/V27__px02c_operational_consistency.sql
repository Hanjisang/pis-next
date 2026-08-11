-- PX02C adds projection metadata for readable history and custody due dates.
ALTER TABLE pis.audit_event
    ADD COLUMN IF NOT EXISTS category_code VARCHAR(32);

ALTER TABLE pis.audit_event
    ADD COLUMN IF NOT EXISTS changes_json TEXT;

ALTER TABLE pis_v2.loan
    ADD COLUMN IF NOT EXISTS borrower_department VARCHAR(256);

ALTER TABLE pis_v2.loan
    ADD COLUMN IF NOT EXISTS expected_return_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS ix_v2_loan_due
    ON pis_v2.loan (organization_reference, expected_return_at, returned_at);
