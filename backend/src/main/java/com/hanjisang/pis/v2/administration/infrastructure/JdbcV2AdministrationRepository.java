package com.hanjisang.pis.v2.administration.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2AdministrationRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2AdministrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UserRow> users() {
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name, u.role_code, u.hospital_scope, u.department_scope,
                       u.task_scope, u.enabled, d.id AS doctor_id, d.doctor_code, d.title AS doctor_title,
                       d.department AS doctor_department, d.enabled AS doctor_enabled
                FROM pis_v2.auth_user u
                LEFT JOIN pis_v2.doctor_identity d ON d.user_id = u.id
                ORDER BY u.username
                """, (rs, rowNum) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("display_name"), rs.getString("role_code"), rs.getString("hospital_scope"),
                rs.getString("department_scope"), rs.getString("task_scope"), rs.getBoolean("enabled"),
                rs.getObject("doctor_id", UUID.class), rs.getString("doctor_code"), rs.getString("doctor_title"),
                rs.getString("doctor_department"), rs.getObject("doctor_enabled", Boolean.class),
                permissions(rs.getObject("id", UUID.class))));
    }

    private List<String> permissions(UUID userId) {
        return jdbc.query("SELECT permission_code FROM pis_v2.auth_user_permission WHERE user_id = ? ORDER BY permission_code",
                (rs, rowNum) -> rs.getString(1), userId);
    }

    public List<String> roleCodes() {
        return jdbc.query("SELECT DISTINCT role_code FROM pis_v2.auth_user ORDER BY role_code",
                (rs, rowNum) -> rs.getString(1));
    }

    public List<OrganizationRow> organizations() {
        return jdbc.query("""
                SELECT hp.profile_code, hc.campus_code, hd.department_code, hd.department_name
                FROM pis_v2.hospital_profile hp
                LEFT JOIN pis_v2.hospital_campus hc ON hc.hospital_profile_id = hp.id
                LEFT JOIN pis_v2.hospital_department hd ON hd.hospital_profile_id = hp.id
                    AND (hd.campus_id = hc.id OR hc.id IS NULL)
                WHERE hp.enabled = TRUE ORDER BY hp.profile_code, hc.campus_code, hd.department_code
                """, (rs, rowNum) -> new OrganizationRow(rs.getString("profile_code"), rs.getString("campus_code"),
                rs.getString("department_code"), rs.getString("department_name")));
    }

    public boolean updateUser(UUID id, UserUpdate update, Instant now) {
        int changed = jdbc.update("""
                UPDATE pis_v2.auth_user SET display_name = ?, role_code = ?, hospital_scope = ?,
                       department_scope = ?, task_scope = ?, enabled = ?, updated_at = ? WHERE id = ?
                """, update.displayName(), update.roleCode(), update.hospitalScope(), update.departmentScope(),
                update.taskScope(), update.enabled(), Timestamp.from(now), id);
        if (changed != 1) return false;
        jdbc.update("DELETE FROM pis_v2.auth_user_permission WHERE user_id = ?", id);
        for (String permission : update.permissions()) {
            jdbc.update("INSERT INTO pis_v2.auth_user_permission (user_id, permission_code) VALUES (?, ?)", id, permission);
        }
        if (update.doctorCode() != null && !update.doctorCode().isBlank()) {
            UUID doctorId = jdbc.query("SELECT id FROM pis_v2.doctor_identity WHERE user_id = ?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, id);
            if (doctorId == null) doctorId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO pis_v2.doctor_identity
                        (id, user_id, doctor_code, display_name, title, department, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET doctor_code = EXCLUDED.doctor_code,
                        display_name = EXCLUDED.display_name, title = EXCLUDED.title,
                        department = EXCLUDED.department, enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                    """, doctorId, id, update.doctorCode().trim(), update.displayName(), update.doctorTitle(),
                    update.doctorDepartment(), update.doctorEnabled(), Timestamp.from(now), Timestamp.from(now));
        }
        return true;
    }

    public record UserRow(UUID id, String username, String displayName, String roleCode, String hospitalScope,
            String departmentScope, String taskScope, boolean enabled, UUID doctorId, String doctorCode,
            String doctorTitle, String doctorDepartment, Boolean doctorEnabled, List<String> permissions) { }
    public record OrganizationRow(String hospitalProfileCode, String campusCode, String departmentCode,
            String departmentName) { }
    public record UserUpdate(String displayName, String roleCode, String hospitalScope, String departmentScope,
            String taskScope, boolean enabled, String doctorCode, String doctorTitle, String doctorDepartment,
            boolean doctorEnabled, List<String> permissions) { }
}
