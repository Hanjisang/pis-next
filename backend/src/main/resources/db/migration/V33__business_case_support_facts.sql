CREATE TABLE IF NOT EXISTS pis_v2.case_favorite (
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    user_reference VARCHAR(256) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (case_id, user_reference, organization_reference)
);

CREATE TABLE IF NOT EXISTS pis_v2.case_follow_up (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    follow_up_date DATE NOT NULL,
    plan VARCHAR(4000) NOT NULL,
    content VARCHAR(10000),
    result VARCHAR(10000),
    operator_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.case_consultation (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    consultation_at TIMESTAMPTZ NOT NULL,
    initiator_ref VARCHAR(128) NOT NULL,
    participant_refs VARCHAR(4000) NOT NULL,
    reason VARCHAR(4000) NOT NULL,
    discussion VARCHAR(10000),
    conclusion VARCHAR(10000),
    note VARCHAR(4000),
    attachment_reference VARCHAR(1024),
    recorded_by_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_v2_follow_up_case ON pis_v2.case_follow_up (case_id, follow_up_date);
CREATE INDEX IF NOT EXISTS ix_v2_consultation_case ON pis_v2.case_consultation (case_id, consultation_at);
