-- S04: connect the existing authenticated user and DoctorIdentity to hospital organization
-- configuration and establish external identity-provider mapping. This is not a new user system.

ALTER TABLE pis_v2.auth_user
    ADD COLUMN hospital_profile_id UUID REFERENCES pis_v2.hospital_profile(id),
    ADD COLUMN campus_id UUID REFERENCES pis_v2.hospital_campus(id),
    ADD COLUMN department_id UUID REFERENCES pis_v2.hospital_department(id);

ALTER TABLE pis_v2.doctor_identity
    ADD COLUMN department_id UUID REFERENCES pis_v2.hospital_department(id);

UPDATE pis_v2.auth_user u
   SET hospital_profile_id = hp.id
  FROM pis_v2.hospital_profile hp
 WHERE hp.profile_code = u.hospital_scope;

UPDATE pis_v2.auth_user u
   SET campus_id = hc.id
  FROM pis_v2.hospital_campus hc
 WHERE hc.hospital_profile_id = u.hospital_profile_id
   AND hc.campus_code = 'MAIN';

UPDATE pis_v2.auth_user u
   SET department_id = hd.id
  FROM pis_v2.hospital_department hd
 WHERE hd.hospital_profile_id = u.hospital_profile_id
   AND hd.department_code = u.department_scope;

UPDATE pis_v2.doctor_identity di
   SET department_id = u.department_id
  FROM pis_v2.auth_user u
 WHERE u.id = di.user_id;

CREATE TABLE pis_v2.identity_provider_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    provider_code VARCHAR(128) NOT NULL,
    provider_type_code VARCHAR(32) NOT NULL,
    adapter_code VARCHAR(128) NOT NULL,
    authority_reference VARCHAR(1024),
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_identity_provider UNIQUE (hospital_profile_id, provider_code),
    CONSTRAINT ck_v2_identity_provider_type CHECK (provider_type_code IN
        ('LDAP', 'ACTIVE_DIRECTORY', 'SSO', 'OAUTH', 'MOCK')),
    CONSTRAINT ck_v2_identity_provider_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.external_identity_link (
    id UUID PRIMARY KEY,
    identity_provider_id UUID NOT NULL REFERENCES pis_v2.identity_provider_configuration(id),
    external_subject VARCHAR(512) NOT NULL,
    user_id UUID NOT NULL REFERENCES pis_v2.auth_user(id),
    enabled BOOLEAN NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    linked_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_external_identity_subject UNIQUE (identity_provider_id, external_subject),
    CONSTRAINT uq_v2_external_identity_user UNIQUE (identity_provider_id, user_id)
);

CREATE TABLE pis_v2.external_authentication_event (
    id UUID PRIMARY KEY,
    hospital_profile_code VARCHAR(128) NOT NULL,
    provider_code VARCHAR(128) NOT NULL,
    external_subject_digest VARCHAR(128),
    result_code VARCHAR(32) NOT NULL,
    error_code VARCHAR(128),
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_v2_external_auth_result CHECK (result_code IN
        ('AUTHENTICATED', 'REJECTED', 'UNMAPPED', 'DISABLED'))
);

INSERT INTO pis_v2.identity_provider_configuration
    (id, hospital_profile_id, provider_code, provider_type_code, adapter_code, authority_reference,
     enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-IDP:' || hp.profile_code || ':' || seed.provider_code)::uuid,
       hp.id, seed.provider_code, seed.provider_type_code, seed.adapter_code, seed.authority_reference,
       seed.enabled, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
CROSS JOIN (VALUES
    ('MOCK-LOCAL', 'MOCK', 'MOCK_IDENTITY', 'mock://identity', TRUE),
    ('LDAP-RESERVED', 'LDAP', 'LDAP_IDENTITY', NULL, FALSE),
    ('AD-RESERVED', 'ACTIVE_DIRECTORY', 'AD_IDENTITY', NULL, FALSE),
    ('SSO-RESERVED', 'SSO', 'SSO_IDENTITY', NULL, FALSE),
    ('OAUTH-RESERVED', 'OAUTH', 'OAUTH_IDENTITY', NULL, FALSE)
) AS seed(provider_code, provider_type_code, adapter_code, authority_reference, enabled)
ON CONFLICT (hospital_profile_id, provider_code) DO NOTHING;

CREATE INDEX ix_v2_auth_user_organization
    ON pis_v2.auth_user (hospital_profile_id, campus_id, department_id, enabled);
CREATE INDEX ix_v2_doctor_organization
    ON pis_v2.doctor_identity (department_id, enabled);
CREATE INDEX ix_v2_external_identity_user
    ON pis_v2.external_identity_link (user_id, enabled);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'S04-IDENTITY-INTEGRATION', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'S04-IDENTITY-INTEGRATION', recorded_at = CURRENT_TIMESTAMP;
