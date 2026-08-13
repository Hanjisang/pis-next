package com.hanjisang.pis.v2.material.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.material.domain.Slide;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialReworkRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialReworkRepository.ReworkRow;

@Service
public class V2MaterialReworkApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private final JdbcV2MaterialReworkRepository reworks;
    private final JdbcV2MaterialRepository materials;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final V2MaterialProductionApplicationService production;

    public V2MaterialReworkApplicationService(JdbcV2MaterialReworkRepository reworks,
            JdbcV2MaterialRepository materials, P15AuthorizationService authorization, JdbcAuditEventRepository audit,
            V2MaterialProductionApplicationService production) {
        this.reworks = reworks;
        this.materials = materials;
        this.authorization = authorization;
        this.audit = audit;
        this.production = production;
    }

    @Transactional
    public ReworkResult request(UUID slideId, RequestCommand command) {
        require(slideId, "玻片不能为空");
        require(command.reworkTypeCode(), "返工类型不能为空");
        require(command.reason(), "返工原因不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Slide slide = materials.findSlide(slideId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SLIDE-NOT-FOUND", "玻片不存在或不在当前数据范围"));
        if (slide.isDeleted()) throw reject("V2-SLIDE-INACTIVE", "失效玻片不能发起返工");
        ReworkRow existing = reworks.findByIdempotency(command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (existing != null) return result(existing, true);
        String reworkType = normalizedType(command.reworkTypeCode());
        ReworkRow row = new ReworkRow(UUID.randomUUID(), slide.caseId(), slide.id(), reworkType,
                command.reason().trim(), "REQUESTED", Instant.now(), actor.actorId(), null, null, null, 0,
                command.idempotencyKey().trim());
        if (!reworks.insert(row, actor.hospitalScope())) {
            return result(reworks.findByIdempotency(command.idempotencyKey(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-REWORK-CONFLICT", "返工请求幂等冲突")), true);
        }
        audit.append("PIS-V2-MATERIAL-REWORK-REQUEST", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                row.id(), "V2-MATERIAL-REWORK", UUID.randomUUID().toString(), row.reason());
        return result(row, false);
    }

    @Transactional
    public ReworkResult complete(UUID reworkId, CompleteCommand command) {
        require(reworkId, "返工记录不能为空");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        ReworkRow row = reworks.findById(reworkId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REWORK-NOT-FOUND", "返工记录不存在"));
        if ("COMPLETED".equals(row.statusCode())) return result(row, true);
        Slide original = materials.findSlide(row.originalSlideId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SLIDE-NOT-FOUND", "原玻片不存在或不在当前数据范围"));
        boolean physicalReplacement = List.of("RECUT", "REMAKE").contains(row.reworkTypeCode());
        Slide replacement = null;
        if (physicalReplacement) {
            require(command.replacementSlideId(), "重新制作必须关联新玻片");
            replacement = materials.findSlide(command.replacementSlideId(), actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-SLIDE-NOT-FOUND", "替代玻片不存在或不在当前数据范围"));
            if (!replacement.caseId().equals(row.caseId()) || replacement.isDeleted()
                    || !java.util.Objects.equals(replacement.blockId(), original.blockId())) {
                throw reject("V2-REWORK-SLIDE-MISMATCH", "替代玻片必须来自原玻片同一病例和材块");
            }
            if (replacement.id().equals(row.originalSlideId())) {
                throw reject("V2-REWORK-SAME-SLIDE", "替代玻片不能是原玻片");
            }
        } else if (command.replacementSlideId() != null) {
            throw reject("V2-REWORK-PHYSICAL-SLIDE-NOT-ALLOWED",
                    "重染或重扫不产生新的物理玻片，请勿关联替代玻片");
        }
        ReworkRow completed = reworks.complete(reworkId, replacement == null ? null : replacement.id(),
                actor.hospitalScope(), actor.actorId(),
                Instant.now(), row.concurrencyVersion())
                .orElseThrow(() -> reject("V2-REWORK-CONFLICT", "返工记录已被其他操作更新"));
        if (!physicalReplacement || replacement.isCompleted()) {
            reworks.resolveOpenProcessExceptions(original.id(), actor.hospitalScope(), actor.actorId(),
                    completed.completedAt(), "返工已完成：" + completed.reworkTypeCode());
        }
        audit.append("PIS-V2-MATERIAL-REWORK-COMPLETE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                reworkId, "V2-MATERIAL-REWORK", UUID.randomUUID().toString(), "返工完成");
        return result(completed, false);
    }

    @Transactional
    public ReworkResult perform(UUID slideId, PerformCommand command) {
        require(slideId, "玻片不能为空");
        require(command, "返工命令不能为空");
        require(command.reason(), "返工原因不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        String type = normalizedType(command.reworkTypeCode());
        ReworkResult requested = request(slideId,
                new RequestCommand(type, command.reason(), command.idempotencyKey() + "/request"));
        if ("COMPLETED".equals(requested.statusCode())) return requested;
        UUID replacementId = null;
        if (List.of("RECUT", "REMAKE").contains(type)) {
            ActorContext actor = authorization.require(MATERIAL_PERMISSION);
            Slide original = materials.findSlide(slideId, actor.hospitalScope())
                    .orElseThrow(() -> reject("V2-SLIDE-NOT-FOUND", "原玻片不存在或不在当前数据范围"));
            if (original.blockId() == null) {
                throw reject("V2-REWORK-SOURCE-INVALID", "重新切片必须来自材块");
            }
            replacementId = production.createExtraSlide(original.blockId(),
                    new V2MaterialProductionApplicationService.CreateExtraSlideCommand(
                            original.slideType(), command.reason(), command.idempotencyKey() + "/slide"))
                    .slideId();
        }
        return complete(requested.reworkId(), new CompleteCommand(replacementId));
    }

    @Transactional(readOnly = true)
    public List<ReworkResult> caseHistory(UUID caseId) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        return reworks.findByCase(caseId, actor.hospitalScope()).stream().map(row -> result(row, false)).toList();
    }

    private static ReworkResult result(ReworkRow row, boolean duplicate) {
        return new ReworkResult(row.id(), row.caseId(), row.originalSlideId(), row.reworkTypeCode(), row.reason(),
                row.statusCode(), row.replacementSlideId(), row.requestedAt(), row.completedAt(),
                row.concurrencyVersion(), duplicate);
    }

    private static void require(Object value, String message) {
        if (value == null || (value instanceof String text && text.isBlank())) throw new IllegalArgumentException(message);
    }

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message, 422); }

    private static String normalizedType(String type) {
        require(type, "返工类型不能为空");
        String normalized = type.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "RE_CUT" -> "RECUT";
            case "RE_STAIN" -> "RESTAIN";
            case "RE_SCAN" -> "RESCAN";
            case "RECUT", "RESTAIN", "RESCAN", "REMAKE" -> normalized;
            default -> throw reject("V2-REWORK-TYPE-INVALID", "返工类型不受支持");
        };
    }

    public record RequestCommand(String reworkTypeCode, String reason, String idempotencyKey) { }
    public record CompleteCommand(UUID replacementSlideId) { }
    public record PerformCommand(String reworkTypeCode, String reason, String idempotencyKey) { }
    public record ReworkResult(UUID reworkId, UUID caseId, UUID originalSlideId, String reworkTypeCode, String reason,
            String statusCode, UUID replacementSlideId, Instant requestedAt, Instant completedAt,
            long concurrencyVersion, boolean duplicate) { }
}
