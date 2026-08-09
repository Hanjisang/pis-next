package com.hanjisang.pis.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthIdentityRepository implements AuthenticatedUserDirectory {

    private final JdbcTemplate jdbc;

    public AuthIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) return Optional.empty();
        Optional<AuthRow> row = findAuthRow("username = ?", username.trim());
        if (row.isEmpty() || !row.get().enabled() || !PasswordHash.matches(password, row.get().passwordDigest())) {
            return Optional.empty();
        }
        return Optional.of(toUser(row.get()));
    }

    public Optional<AuthenticatedUser> find(UUID userId) {
        return findAuthRow("id = ?", userId).map(this::toUser);
    }

    public List<DoctorIdentity> findEnabledDoctors(String hospitalScope) {
        return jdbc.query("""
                SELECT d.id, d.user_id, d.doctor_code, d.display_name, d.title, d.department,
                       d.department_id, d.enabled
                FROM pis_v2.doctor_identity d
                JOIN pis_v2.auth_user u ON u.id = d.user_id
                WHERE d.enabled = TRUE AND u.enabled = TRUE AND u.hospital_scope = ?
                ORDER BY d.display_name, d.doctor_code
                """, (rs, rowNum) -> doctor(rs), hospitalScope);
    }

    public void seedSyntheticAccounts(String password) {
        for (AccountSpec account : syntheticAccounts()) {
            UUID userId = findAuthRow("username = ?", account.username()).map(AuthRow::id)
                    .orElseGet(UUID::randomUUID);
            Instant now = Instant.now();
            OrganizationIds organization = findOrganization("LOCAL_HOSPITAL", account.department());
            jdbc.update("""
                    INSERT INTO pis_v2.auth_user
                        (id, username, display_name, password_digest, role_code, hospital_scope,
                         department_scope, task_scope, enabled, created_at, updated_at,
                         hospital_profile_id, campus_id, department_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)
                    ON CONFLICT (username) DO UPDATE SET
                        display_name = EXCLUDED.display_name,
                        password_digest = EXCLUDED.password_digest,
                        role_code = EXCLUDED.role_code,
                        hospital_scope = EXCLUDED.hospital_scope,
                        department_scope = EXCLUDED.department_scope,
                        task_scope = EXCLUDED.task_scope,
                        hospital_profile_id = EXCLUDED.hospital_profile_id,
                        campus_id = EXCLUDED.campus_id,
                        department_id = EXCLUDED.department_id,
                        enabled = TRUE,
                        updated_at = EXCLUDED.updated_at
                    """, userId, account.username(), account.displayName(), PasswordHash.create(password),
                    account.roleCode(), "LOCAL_HOSPITAL", account.department(), account.taskScope(),
                    Timestamp.from(now), Timestamp.from(now), organization.hospitalProfileId(),
                    organization.campusId(), organization.departmentId());
            jdbc.update("DELETE FROM pis_v2.auth_user_permission WHERE user_id = ?", userId);
            for (String permission : account.permissions()) {
                jdbc.update("INSERT INTO pis_v2.auth_user_permission (user_id, permission_code) VALUES (?, ?)",
                        userId, permission);
            }
            if (account.doctorCode() != null) {
                UUID doctorId = jdbc.query("SELECT id FROM pis_v2.doctor_identity WHERE user_id = ?", rs ->
                        rs.next() ? rs.getObject(1, UUID.class) : null, userId);
                if (doctorId == null) doctorId = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO pis_v2.doctor_identity
                            (id, user_id, doctor_code, display_name, title, department, department_id,
                             enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE SET
                            doctor_code = EXCLUDED.doctor_code,
                            display_name = EXCLUDED.display_name,
                            title = EXCLUDED.title,
                            department = EXCLUDED.department,
                            department_id = EXCLUDED.department_id,
                            enabled = TRUE,
                            updated_at = EXCLUDED.updated_at
                        """, doctorId, userId, account.doctorCode(), account.displayName(), account.title(),
                        account.department(), organization.departmentId(), Timestamp.from(now), Timestamp.from(now));
            }
        }
    }

    private Optional<AuthRow> findAuthRow(String predicate, Object... arguments) {
        String sql = "SELECT u.id, u.username, u.display_name, u.password_digest, u.role_code, u.hospital_scope, "
                + "u.department_scope, u.task_scope, u.enabled, u.hospital_profile_id, u.campus_id, "
                + "u.department_id, hp.profile_code, hc.campus_code, hd.department_code, hd.department_name "
                + "FROM pis_v2.auth_user u "
                + "LEFT JOIN pis_v2.hospital_profile hp ON hp.id = u.hospital_profile_id "
                + "LEFT JOIN pis_v2.hospital_campus hc ON hc.id = u.campus_id "
                + "LEFT JOIN pis_v2.hospital_department hd ON hd.id = u.department_id WHERE u." + predicate;
        return jdbc.query(sql, rs -> rs.next() ? Optional.of(authRow(rs)) : Optional.empty(), arguments);
    }

    private AuthenticatedUser toUser(AuthRow row) {
        Set<String> permissions = new LinkedHashSet<>(jdbc.query(
                "SELECT permission_code FROM pis_v2.auth_user_permission WHERE user_id = ? ORDER BY permission_code",
                (rs, rowNum) -> rs.getString(1), row.id()));
        DoctorIdentity doctor = jdbc.query("""
                SELECT id, user_id, doctor_code, display_name, title, department, department_id, enabled
                FROM pis_v2.doctor_identity WHERE user_id = ? AND enabled = TRUE
                """, rs -> rs.next() ? doctor(rs) : null, row.id());
        return new AuthenticatedUser(row.id(), row.username(), row.displayName(), row.roleCode(), row.hospitalScope(),
                row.departmentScope(), row.taskScope(), Set.copyOf(permissions), doctor,
                new OrganizationContext(row.hospitalProfileId(), row.profileCode(), row.campusId(), row.campusCode(),
                        row.departmentId(), row.organizationDepartmentCode(), row.departmentName()));
    }

    private static AuthRow authRow(ResultSet rs) throws SQLException {
        return new AuthRow(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_digest"), rs.getString("role_code"), rs.getString("hospital_scope"),
                rs.getString("department_scope"), rs.getString("task_scope"), rs.getBoolean("enabled"),
                rs.getObject("hospital_profile_id", UUID.class), rs.getObject("campus_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getString("profile_code"),
                rs.getString("campus_code"), rs.getString("department_code"), rs.getString("department_name"));
    }

    private static DoctorIdentity doctor(ResultSet rs) throws SQLException {
        return new DoctorIdentity(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("doctor_code"), rs.getString("display_name"), rs.getString("title"),
                rs.getString("department"), rs.getObject("department_id", UUID.class), rs.getBoolean("enabled"));
    }

    private OrganizationIds findOrganization(String profileCode, String departmentCode) {
        return jdbc.query("""
                SELECT hp.id AS hospital_profile_id, hc.id AS campus_id, hd.id AS department_id
                FROM pis_v2.hospital_profile hp
                LEFT JOIN pis_v2.hospital_campus hc
                  ON hc.hospital_profile_id = hp.id AND hc.campus_code = 'MAIN'
                LEFT JOIN pis_v2.hospital_department hd
                  ON hd.hospital_profile_id = hp.id AND hd.department_code = ?
                WHERE hp.profile_code = ?
                """, rs -> rs.next() ? new OrganizationIds(rs.getObject("hospital_profile_id", UUID.class),
                rs.getObject("campus_id", UUID.class), rs.getObject("department_id", UUID.class))
                : new OrganizationIds(null, null, null), departmentCode, profileCode);
    }

    private static List<AccountSpec> syntheticAccounts() {
        String query = "P14-PERM-048,P14-PERM-055";
        String diagnosis = "P14-PERM-034,P14-PERM-015," + query;
        String audit = diagnosis + ",P14-PERM-035,P14-PERM-036";
        return List.of(
                new AccountSpec("doctor-a", "Doctor A", "DOCTOR", "DOC-A", "主治医师", "PATHOLOGY",
                        "P19-DIAGNOSIS-REPORT", permissions(diagnosis)),
                new AccountSpec("doctor-b", "Doctor B", "DOCTOR", "DOC-B", "主治医师", "PATHOLOGY",
                        "P19-DIAGNOSIS-REPORT", permissions(diagnosis)),
                new AccountSpec("doctor-c", "Doctor C", "DOCTOR", "DOC-C", "审核医师", "PATHOLOGY",
                        "P19-DIAGNOSIS-REPORT", permissions(audit)),
                new AccountSpec("registrar", "Registrar", "REGISTRAR", null, null, "REGISTRATION",
                        "P15-REGISTRATION-RECEIVING", permissions("P14-PERM-004,P14-PERM-008,P14-PERM-010," + query)),
                new AccountSpec("technician", "Technician", "TECHNICIAN", null, null, "PATHOLOGY",
                        "P16-GROSSING-BLOCK-LABELING,P17-TECHNICAL-PROCESSING-EMBEDDING,P18-TECHNICAL-ORDER",
                        permissions("P14-PERM-008,P14-PERM-013,P14-PERM-014,P14-PERM-015,P14-PERM-016,P14-PERM-017,P14-PERM-049," + query)),
                new AccountSpec("admin", "Admin", "ADMIN", null, null, "ADMINISTRATION",
                        "P15-REGISTRATION-RECEIVING,P16-GROSSING-BLOCK-LABELING,P17-TECHNICAL-PROCESSING-EMBEDDING,P18-TECHNICAL-ORDER,P19-DIAGNOSIS-REPORT",
                        permissions("P14-PERM-001,P14-PERM-002,P14-PERM-003,P14-PERM-004,P14-PERM-008,P14-PERM-009,P14-PERM-010,P14-PERM-011,P14-PERM-013,P14-PERM-014,P14-PERM-015,P14-PERM-016,P14-PERM-017,P14-PERM-029,P14-PERM-034,P14-PERM-035,P14-PERM-036,P14-PERM-042,P14-PERM-044,P14-PERM-046,P14-PERM-047,P14-PERM-048,P14-PERM-049,P14-PERM-050,P14-PERM-055,P14-PERM-057,P14-PERM-058")));
    }

    private static Set<String> permissions(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private record AuthRow(UUID id, String username, String displayName, String passwordDigest, String roleCode,
            String hospitalScope, String departmentScope, String taskScope, boolean enabled,
            UUID hospitalProfileId, UUID campusId, UUID departmentId, String profileCode, String campusCode,
            String organizationDepartmentCode, String departmentName) { }

    private record OrganizationIds(UUID hospitalProfileId, UUID campusId, UUID departmentId) { }

    private record AccountSpec(String username, String displayName, String roleCode, String doctorCode, String title,
            String department, String taskScope, Set<String> permissions) { }
}
