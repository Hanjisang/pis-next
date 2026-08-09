-- V2 runtime closure: thin authenticated-user to DoctorIdentity boundary.
-- Synthetic runtime accounts are seeded by the application from a runtime secret;
-- no password material is stored in Flyway history.

CREATE TABLE IF NOT EXISTS pis_v2.auth_user (
    id UUID PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    password_digest VARCHAR(1024) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    hospital_scope VARCHAR(128) NOT NULL,
    department_scope VARCHAR(128),
    task_scope VARCHAR(2000),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_auth_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS pis_v2.auth_user_permission (
    user_id UUID NOT NULL REFERENCES pis_v2.auth_user(id),
    permission_code VARCHAR(128) NOT NULL,
    PRIMARY KEY (user_id, permission_code)
);

CREATE TABLE IF NOT EXISTS pis_v2.doctor_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES pis_v2.auth_user(id),
    doctor_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    title VARCHAR(128),
    department VARCHAR(256),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_doctor_identity_user UNIQUE (user_id),
    CONSTRAINT uq_v2_doctor_identity_code UNIQUE (doctor_code)
);

CREATE INDEX IF NOT EXISTS ix_v2_auth_user_enabled ON pis_v2.auth_user (enabled, username);
CREATE INDEX IF NOT EXISTS ix_v2_auth_permission_code ON pis_v2.auth_user_permission (permission_code, user_id);
CREATE INDEX IF NOT EXISTS ix_v2_doctor_identity_enabled ON pis_v2.doctor_identity (enabled, doctor_code);
