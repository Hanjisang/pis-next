-- FROZEN-011: warning/overdue projections become actionable without changing the round fact.
CREATE TABLE pis_v2.frozen_tat_alert_action (
    id UUID PRIMARY KEY,
    frozen_round_id UUID NOT NULL REFERENCES pis_v2.frozen_round(id),
    organization_reference VARCHAR(128) NOT NULL,
    tat_status_code VARCHAR(16) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    note VARCHAR(1000),
    acted_at TIMESTAMPTZ NOT NULL,
    acted_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_frozen_tat_alert_action UNIQUE
        (frozen_round_id, organization_reference, tat_status_code, action_code),
    CONSTRAINT ck_v2_frozen_tat_alert_status CHECK (tat_status_code IN ('WARNING', 'OVERDUE')),
    CONSTRAINT ck_v2_frozen_tat_alert_action CHECK (action_code IN ('ACKNOWLEDGED'))
);

CREATE INDEX idx_v2_frozen_tat_alert_round
    ON pis_v2.frozen_tat_alert_action (organization_reference, frozen_round_id, acted_at DESC);
