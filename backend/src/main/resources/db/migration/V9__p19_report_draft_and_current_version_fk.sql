CREATE TABLE IF NOT EXISTS pis.p19_report_draft (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE REFERENCES pis.p19_report(id),
    owner_actor_ref VARCHAR(128) NOT NULL,
    clinical_information TEXT,
    specimen_information TEXT,
    gross_description TEXT,
    microscopic_description TEXT,
    diagnosis_conclusion TEXT NOT NULL,
    supplementary_note TEXT,
    technical_result_reference_summary TEXT,
    concurrency_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);

ALTER TABLE pis.p19_report
    ADD CONSTRAINT fk_p19_report_current_version
    FOREIGN KEY (current_effective_version_id) REFERENCES pis.p19_report_content_version(id);
