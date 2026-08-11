package com.hanjisang.pis.v2.capability;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves the shared BusinessType capability projection from V2 facts. */
@Service
public class BusinessTypeCapabilityService {

    private final JdbcTemplate jdbc;

    public BusinessTypeCapabilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BusinessTypeCapability forBusinessType(String businessTypeCode) {
        return jdbc.query("""
                SELECT business_type_code, modality_code
                FROM pis_v2.business_type
                WHERE business_type_code = ?
                """, rs -> rs.next()
                        ? BusinessTypeCapability.from(rs.getString("business_type_code"),
                                rs.getString("modality_code"))
                        : BusinessTypeCapability.from(businessTypeCode, null), businessTypeCode);
    }

    public BusinessTypeCapability forCase(UUID caseId, String organizationReference) {
        return jdbc.query("""
                SELECT bt.business_type_code, bt.modality_code
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next()
                        ? BusinessTypeCapability.from(rs.getString("business_type_code"),
                                rs.getString("modality_code"))
                        : BusinessTypeCapability.from("UNKNOWN", null), caseId, organizationReference);
    }
}
