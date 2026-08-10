package com.hanjisang.pis.v2.configuration.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.configuration.infrastructure.JdbcV2ConfigurationRepository;

@Service
public class V2ConfigurationApplicationService {

    private static final String ADMIN_PERMISSION = "P14-PERM-001";
    private final JdbcV2ConfigurationRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2ConfigurationApplicationService(JdbcV2ConfigurationRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public JdbcV2ConfigurationRepository.Snapshot snapshot() {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateBusinessType(UUID id, UpdateBusinessType command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.displayName(), "业务类型名称不能为空");
        update(repository.updateBusinessType(id, command.displayName().trim(), command.enabled(), Instant.now(), actor.actorId()),
                "业务类型不存在");
        audit.append("PIS-V2-PX02-CONFIG-BUSINESS-TYPE", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateMapping(UUID id, UpdateMapping command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.defaultSpecimenKindCode(), "默认标本类型不能为空");
        if (command.sequenceNo() < 0) throw new P15BusinessException("V2-CONFIG-INVALID", "排序号不能为负数", 400);
        update(repository.updateMapping(id, command.defaultSpecimenKindCode().trim(), command.required(),
                command.sequenceNo(), command.active()), "申请项目映射不存在");
        audit.append("PIS-V2-PX02-CONFIG-APPLICATION-MAPPING", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateNumberRule(UUID id, UpdateNumberRule command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.prefix(), "病理号前缀不能为空");
        if (command.paddingWidth() < 1 || command.paddingWidth() > 12) {
            throw new P15BusinessException("V2-CONFIG-INVALID", "补零位数应在1到12之间", 400);
        }
        update(repository.updateNumberRule(id, command.prefix().trim(), command.paddingWidth(), command.active()), "病理号规则不存在");
        audit.append("PIS-V2-PX02-CONFIG-PATHOLOGY-NUMBER", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateTechnicalProject(UUID id, UpdateTechnicalProject command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.projectName(), "技术项目名称不能为空");
        update(repository.updateTechnicalProject(id, command.projectName().trim(), command.enabled(),
                command.requiredBeforeSignOutDefault(), Instant.now(), actor.actorId()), "技术项目不存在");
        audit.append("PIS-V2-PX02-CONFIG-TECHNICAL-PROJECT", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateDiagnosisTemplate(UUID id, UpdateTemplate command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.templateName(), "诊断模板名称不能为空");
        update(repository.updateDiagnosisTemplate(id, command.templateName().trim(), command.enabled(), Instant.now(), actor.actorId()),
                "诊断模板不存在");
        audit.append("PIS-V2-PX02-CONFIG-DIAGNOSIS-TEMPLATE", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2ConfigurationRepository.Snapshot updateReportTemplate(UUID id, UpdateTemplate command) {
        ActorContext actor = authorization.require(ADMIN_PERMISSION);
        requireText(command.templateName(), "报告模板名称不能为空");
        update(repository.updateReportTemplate(id, command.templateName().trim(), command.enabled(), Instant.now(), actor.actorId()),
                "报告模板不存在");
        audit.append("PIS-V2-PX02-CONFIG-REPORT-TEMPLATE", ADMIN_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-CONFIGURATION", UUID.randomUUID().toString(), "configuration updated");
        return repository.snapshot(actor.hospitalScope());
    }

    private static void update(boolean changed, String message) {
        if (!changed) throw new P15BusinessException("V2-CONFIG-NOT-FOUND", message, 404);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new P15BusinessException("V2-CONFIG-INVALID", message, 400);
    }

    public record UpdateBusinessType(String displayName, boolean enabled) { }
    public record UpdateMapping(String defaultSpecimenKindCode, boolean required, int sequenceNo, boolean active) { }
    public record UpdateNumberRule(String prefix, int paddingWidth, boolean active) { }
    public record UpdateTechnicalProject(String projectName, boolean enabled, boolean requiredBeforeSignOutDefault) { }
    public record UpdateTemplate(String templateName, boolean enabled) { }
}
