package com.hanjisang.pis.v2.digital.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.digital.domain.DigitalSlide;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalSlideRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2DigitalSlideApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2DigitalSlideRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2DigitalSlideApplicationService(JdbcV2DigitalSlideRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2MaterialRepository materialRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public DigitalSlideResult create(CreateDigitalSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Case pathologyCase = activeCase(command.caseId(), actor);
        require(command.viewerReference(), "阅片器引用不能为空");
        require(command.sourcePlatform(), "数字切片来源平台不能为空");
        requireMode(command.bindingModeCode());
        validateBindings(pathologyCase.id(), command.blockId(), command.slideId(), actor);
        Instant now = Instant.now();
        DigitalSlide digitalSlide = new DigitalSlide(UUID.randomUUID(), pathologyCase.id(), command.blockId(),
                command.slideId(), command.bindingModeCode(), DigitalSlide.ACTIVE, command.viewerReference(),
                command.sourcePlatform(), now, actor.actorId(), now, actor.actorId());
        repository.insert(digitalSlide);
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-CREATE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlide.id(), "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片元数据已登记");
        outbox.append("V2-I06-DIGITAL-SLIDE-CREATED", digitalSlide.id(), "V2-DIGITAL-SLIDE", 0,
                UUID.randomUUID().toString(), digitalSlide.viewerReference(), actor.actorId());
        return result(digitalSlide);
    }

    @Transactional
    public DigitalSlideResult rebind(UUID digitalSlideId, RebindCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        DigitalSlide existing = find(digitalSlideId, actor);
        if (DigitalSlide.UNBOUND.equals(existing.statusCode())) {
            throw reject("V2-DIGITAL-SLIDE-CLOSED", "已解除绑定的数字切片不能继续改绑");
        }
        validateBindings(existing.caseId(), command.blockId(), command.slideId(), actor);
        if (!repository.updateBinding(existing.id(), command.blockId(), command.slideId(), DigitalSlide.ACTIVE,
                Instant.now(), actor.actorId(), actor.hospitalScope())) {
            throw reject("V2-DIGITAL-SLIDE-CONFLICT", "数字切片绑定已被其他请求改变");
        }
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-REBIND", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlideId, "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片已改绑");
        return result(repository.find(digitalSlideId, actor.hospitalScope()).orElseThrow());
    }

    @Transactional
    public DigitalSlideResult unbind(UUID digitalSlideId) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        DigitalSlide existing = find(digitalSlideId, actor);
        if (!repository.updateBinding(existing.id(), null, null, DigitalSlide.UNBOUND, Instant.now(), actor.actorId(),
                actor.hospitalScope())) {
            throw reject("V2-DIGITAL-SLIDE-CONFLICT", "数字切片绑定已被其他请求改变");
        }
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-UNBIND", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlideId, "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片已解除绑定");
        return result(repository.find(digitalSlideId, actor.hospitalScope()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<DigitalSlideResult> byCase(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        activeCase(caseId, actor);
        return repository.findByCase(caseId, actor.hospitalScope()).stream().map(this::result).toList();
    }

    @Transactional(readOnly = true)
    public DigitalSlideResult get(UUID digitalSlideId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return result(find(digitalSlideId, actor));
    }

    private DigitalSlide find(UUID id, ActorContext actor) {
        return repository.find(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIGITAL-SLIDE-NOT-FOUND", "数字切片不存在"));
    }

    private Case activeCase(UUID id, ActorContext actor) {
        if (id == null) throw reject("V2-INVALID-REQUEST", "病例不能为空");
        Case pathologyCase = registrationRepository.findCase(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能登记数字切片");
        }
        return pathologyCase;
    }

    private void validateBindings(UUID caseId, UUID blockId, UUID slideId, ActorContext actor) {
        if (blockId != null && materialRepository.findBlock(blockId, actor.hospitalScope())
                .filter(block -> block.caseId().equals(caseId) && !block.isDeleted()).isEmpty()) {
            throw reject("V2-DIGITAL-SLIDE-BINDING-INVALID", "蜡块不属于当前病例");
        }
        if (slideId != null && materialRepository.findSlide(slideId, actor.hospitalScope())
                .filter(slide -> slide.caseId().equals(caseId) && !slide.isDeleted()).isEmpty()) {
            throw reject("V2-DIGITAL-SLIDE-BINDING-INVALID", "切片不属于当前病例");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message);
    }

    private static void requireMode(String mode) {
        if (!"AUTOMATIC".equals(mode) && !"MANUAL".equals(mode)) {
            throw reject("V2-INVALID-REQUEST", "数字切片绑定方式不受支持");
        }
    }

    private DigitalSlideResult result(DigitalSlide item) {
        return new DigitalSlideResult(item.id(), item.caseId(), item.blockId(), item.slideId(), item.bindingModeCode(),
                item.statusCode(), item.viewerReference(), item.sourcePlatform(), item.updatedAt());
    }

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }

    public record CreateDigitalSlideCommand(UUID caseId, UUID blockId, UUID slideId, String bindingModeCode,
            String viewerReference, String sourcePlatform) { }
    public record RebindCommand(UUID blockId, UUID slideId) { }
    public record DigitalSlideResult(UUID digitalSlideId, UUID caseId, UUID blockId, UUID slideId,
            String bindingModeCode, String statusCode, String viewerReference, String sourcePlatform,
            Instant updatedAt) { }
}
