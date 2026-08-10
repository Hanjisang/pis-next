package com.hanjisang.pis.v2.administration.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.administration.infrastructure.JdbcV2AdministrationRepository;
import com.hanjisang.pis.v2.administration.infrastructure.JdbcV2AdministrationRepository.UserRow;

@Service
public class V2AdministrationApplicationService {

    private static final String ADMIN_PERMISSION = "P14-PERM-001";
    private static final Set<String> BUSINESS = Set.of("P14-PERM-001", "P14-PERM-004", "P14-PERM-008",
            "P14-PERM-013", "P14-PERM-014", "P14-PERM-015", "P14-PERM-017", "P14-PERM-034",
            "P14-PERM-048", "P14-PERM-049", "P14-PERM-055");
    private static final Set<String> ACTION = Set.of("P14-PERM-002", "P14-PERM-003", "P14-PERM-009",
            "P14-PERM-010", "P14-PERM-011", "P14-PERM-016", "P14-PERM-029", "P14-PERM-035",
            "P14-PERM-036", "P14-PERM-042", "P14-PERM-044", "P14-PERM-046", "P14-PERM-047",
            "P14-PERM-050", "P14-PERM-057", "P14-PERM-058");
    private final JdbcV2AdministrationRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2AdministrationApplicationService(JdbcV2AdministrationRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdministrationSnapshot snapshot() {
        authorization.require(ADMIN_PERMISSION);
        List<UserAdmin> users = repository.users().stream().map(this::user).toList();
        List<PermissionOption> permissions = allPermissions(users);
        return new AdministrationSnapshot(users, repository.roleCodes(), permissions, repository.organizations());
    }

    @Transactional
    public AdministrationSnapshot updateUser(UUID id, UpdateUser command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new P15BusinessException("V2-ADMIN-INVALID", "显示名称不能为空", 400);
        }
        List<String> permissions = java.util.stream.Stream.concat(
                command.businessPermissions() == null ? java.util.stream.Stream.empty() : command.businessPermissions().stream(),
                command.actionPermissions() == null ? java.util.stream.Stream.empty() : command.actionPermissions().stream())
                .distinct().toList();
        boolean changed = repository.updateUser(id, new JdbcV2AdministrationRepository.UserUpdate(
                command.displayName().trim(), command.roleCode(), command.hospitalScope(), command.departmentScope(),
                command.taskScope(), command.enabled(), command.doctorCode(), command.doctorTitle(),
                command.doctorDepartment(), command.doctorEnabled(), permissions), Instant.now());
        if (!changed) throw new P15BusinessException("V2-ADMIN-NOT-FOUND", "用户不存在", 404);
        audit.append("PIS-V2-PX02-ADMIN-USER-UPDATE", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-AUTH-USER", UUID.randomUUID().toString(), "permissions and scope updated");
        return snapshot();
    }

    private UserAdmin user(UserRow row) {
        List<String> business = row.permissions().stream().filter(BUSINESS::contains).toList();
        List<String> action = row.permissions().stream().filter(ACTION::contains).toList();
        return new UserAdmin(row.id(), row.username(), row.displayName(), row.roleCode(), row.hospitalScope(),
                row.departmentScope(), row.taskScope(), row.enabled(), row.doctorId(), row.doctorCode(),
                row.doctorTitle(), row.doctorDepartment(), row.doctorEnabled() == null || row.doctorEnabled(),
                business, action);
    }

    private static List<PermissionOption> allPermissions(List<UserAdmin> users) {
        Set<String> codes = new java.util.TreeSet<>();
        users.forEach(user -> { codes.addAll(user.businessPermissions()); codes.addAll(user.actionPermissions()); });
        return codes.stream().map(code -> new PermissionOption(code, label(code),
                BUSINESS.contains(code) ? "BUSINESS" : "ACTION")).toList();
    }

    private static String label(String code) {
        return switch (code) {
            case "P14-PERM-001" -> "系统管理";
            case "P14-PERM-004" -> "登记病例";
            case "P14-PERM-008" -> "标本接收/冰冻";
            case "P14-PERM-013" -> "取材";
            case "P14-PERM-014" -> "材料与制片";
            case "P14-PERM-015" -> "开立技术医嘱";
            case "P14-PERM-017" -> "执行技术医嘱";
            case "P14-PERM-034" -> "诊断责任";
            case "P14-PERM-035" -> "审核";
            case "P14-PERM-036" -> "报告签发";
            case "P14-PERM-048" -> "查询";
            case "P14-PERM-055" -> "报告查看";
            case "P14-PERM-042" -> "模板配置";
            default -> code;
        };
    }

    public record AdministrationSnapshot(List<UserAdmin> users, List<String> roles,
            List<PermissionOption> permissions, List<JdbcV2AdministrationRepository.OrganizationRow> organizations) { }
    public record PermissionOption(String code, String label, String dimension) { }
    public record UserAdmin(UUID id, String username, String displayName, String roleCode, String hospitalScope,
            String departmentScope, String taskScope, boolean enabled, UUID doctorId, String doctorCode,
            String doctorTitle, String doctorDepartment, boolean doctorEnabled, List<String> businessPermissions,
            List<String> actionPermissions) { }
    public record UpdateUser(String displayName, String roleCode, String hospitalScope, String departmentScope,
            String taskScope, boolean enabled, String doctorCode, String doctorTitle, String doctorDepartment,
            boolean doctorEnabled, List<String> businessPermissions, List<String> actionPermissions,
            List<String> dataPermissions) { }
}
