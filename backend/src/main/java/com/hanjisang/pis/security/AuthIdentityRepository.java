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
public class AuthIdentityRepository {

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

    public void seedSyntheticAccounts(String password) {
        for (AccountSpec account : syntheticAccounts()) {
            UUID userId = findAuthRow("username = ?", account.username()).map(AuthRow::id)
                    .orElseGet(UUID::randomUUID);
            Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO pis_v2.auth_user
                        (id, username, display_name, password_digest, role_code, hospital_scope,
                         department_scope, task_scope, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    ON CONFLICT (username) DO UPDATE SET
                        display_name = EXCLUDED.display_name,
                        password_digest = EXCLUDED.password_digest,
                        role_code = EXCLUDED.role_code,
                        hospital_scope = EXCLUDED.hospital_scope,
                        department_scope = EXCLUDED.department_scope,
                        task_scope = EXCLUDED.task_scope,
                        enabled = TRUE,
                        updated_at = EXCLUDED.updated_at
                    """, userId, account.username(), account.displayName(), PasswordHash.create(password),
                    account.roleCode(), "LOCAL_HOSPITAL", account.department(), account.taskScope(),
                    Timestamp.from(now), Timestamp.from(now));
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
                            (id, user_id, doctor_code, display_name, title, department, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE SET
                            doctor_code = EXCLUDED.doctor_code,
                            display_name = EXCLUDED.display_name,
                            title = EXCLUDED.title,
                            department = EXCLUDED.department,
                            enabled = TRUE,
                            updated_at = EXCLUDED.updated_at
                        """, doctorId, userId, account.doctorCode(), account.displayName(), account.title(),
                        account.department(), Timestamp.from(now), Timestamp.from(now));
            }
        }
    }

    private Optional<AuthRow> findAuthRow(String predicate, Object... arguments) {
        String sql = "SELECT id, username, display_name, password_digest, role_code, hospital_scope, "
                + "department_scope, task_scope, enabled "
                + "FROM pis_v2.auth_user WHERE " + predicate;
        return jdbc.query(sql, rs -> rs.next() ? Optional.of(authRow(rs)) : Optional.empty(), arguments);
    }

    private AuthenticatedUser toUser(AuthRow row) {
        Set<String> permissions = new LinkedHashSet<>(jdbc.query(
                "SELECT permission_code FROM pis_v2.auth_user_permission WHERE user_id = ? ORDER BY permission_code",
                (rs, rowNum) -> rs.getString(1), row.id()));
        DoctorIdentity doctor = jdbc.query("""
                SELECT id, user_id, doctor_code, display_name, title, department, enabled
                FROM pis_v2.doctor_identity WHERE user_id = ? AND enabled = TRUE
                """, rs -> rs.next() ? doctor(rs) : null, row.id());
        return new AuthenticatedUser(row.id(), row.username(), row.displayName(), row.roleCode(), row.hospitalScope(),
                row.departmentScope(), row.taskScope(), Set.copyOf(permissions), doctor);
    }

    private static AuthRow authRow(ResultSet rs) throws SQLException {
        return new AuthRow(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_digest"), rs.getString("role_code"), rs.getString("hospital_scope"),
                rs.getString("department_scope"), rs.getString("task_scope"), rs.getBoolean("enabled"));
    }

    private static DoctorIdentity doctor(ResultSet rs) throws SQLException {
        return new DoctorIdentity(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("doctor_code"), rs.getString("display_name"), rs.getString("title"),
                rs.getString("department"), rs.getBoolean("enabled"));
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
            String hospitalScope, String departmentScope, String taskScope, boolean enabled) { }

    private record AccountSpec(String username, String displayName, String roleCode, String doctorCode, String title,
            String department, String taskScope, Set<String> permissions) { }
}
