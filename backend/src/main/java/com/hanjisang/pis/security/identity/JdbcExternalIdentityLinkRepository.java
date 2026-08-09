package com.hanjisang.pis.security.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExternalIdentityLinkRepository implements ExternalIdentityLinkStore {

    private final JdbcTemplate jdbc;

    public JdbcExternalIdentityLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findUserId(String hospitalProfileCode, String providerCode, String externalSubject) {
        return jdbc.query("""
                SELECT link.user_id
                FROM pis_v2.external_identity_link link
                JOIN pis_v2.identity_provider_configuration provider ON provider.id = link.identity_provider_id
                JOIN pis_v2.hospital_profile hp ON hp.id = provider.hospital_profile_id
                WHERE hp.profile_code = ? AND provider.provider_code = ? AND provider.enabled = TRUE
                  AND link.external_subject = ? AND link.enabled = TRUE
                """, rs -> rs.next() ? Optional.of(rs.getObject("user_id", UUID.class)) : Optional.empty(),
                hospitalProfileCode, providerCode, externalSubject);
    }

    @Override
    public void link(String hospitalProfileCode, String providerCode, String externalSubject, UUID userId,
            String linkedByRef, Instant linkedAt) {
        UUID providerId = jdbc.query("""
                SELECT provider.id
                FROM pis_v2.identity_provider_configuration provider
                JOIN pis_v2.hospital_profile hp ON hp.id = provider.hospital_profile_id
                WHERE hp.profile_code = ? AND provider.provider_code = ? AND provider.enabled = TRUE
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, hospitalProfileCode, providerCode);
        if (providerId == null) throw new IllegalArgumentException("外部身份提供方不存在或未启用");
        jdbc.update("""
                INSERT INTO pis_v2.external_identity_link
                    (id, identity_provider_id, external_subject, user_id, enabled, linked_at,
                     linked_by_ref, updated_at)
                VALUES (?, ?, ?, ?, TRUE, ?, ?, ?)
                ON CONFLICT (identity_provider_id, external_subject) DO UPDATE SET
                    user_id = EXCLUDED.user_id, enabled = TRUE, linked_by_ref = EXCLUDED.linked_by_ref,
                    updated_at = EXCLUDED.updated_at
                """, UUID.randomUUID(), providerId, externalSubject, userId, Timestamp.from(linkedAt), linkedByRef,
                Timestamp.from(linkedAt));
    }
}
