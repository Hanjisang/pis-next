package com.hanjisang.pis.v2.custody.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.custody.infrastructure.JdbcV2CustodyRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;

@Service
public class V2CustodyApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2CustodyRepository repository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2CustodyApplicationService(JdbcV2CustodyRepository repository, JdbcV2MaterialRepository materialRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public LocationResult createLocation(CreateLocationCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        require(command.locationCode(), "归档位置编码不能为空");
        require(command.locationName(), "归档位置名称不能为空");
        require(command.locationKindCode(), "归档位置类型不能为空");
        UUID id = UUID.randomUUID();
        repository.insertLocation(id, command.parentId(), command.locationCode(), command.locationName(),
                command.locationKindCode(), actor.hospitalScope(), actor.actorId(), Instant.now());
        return new LocationResult(id, command.parentId(), command.locationCode(), command.locationName(),
                command.locationKindCode());
    }

    @Transactional
    public CustodyBatchResult archive(ArchiveCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        require(command.locationId(), "归档位置不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        if (!repository.locationActive(command.locationId(), actor.hospitalScope())) {
            throw reject("V2-ARCHIVE-LOCATION-NOT-FOUND", "归档位置不存在或已停用");
        }
        String operation = "PIS-V2-I06-MATERIAL-ARCHIVE";
        String digest = digest(command.locationId(), command.blockIds(), command.slideIds(), command.reason());
        CustodyBatchResult replay = replay(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        for (UUID blockId : safe(command.blockIds())) {
            findBlock(blockId, actor);
            ensureNotDestroyedBlock(blockId);
            repository.archiveBlock(blockId, command.locationId(), "ARCHIVED", command.reason(), actor.actorId(), now);
        }
        for (UUID slideId : safe(command.slideIds())) {
            findSlide(slideId, actor);
            ensureNotDestroyedSlide(slideId);
            repository.archiveSlide(slideId, command.locationId(), "ARCHIVED", command.reason(), actor.actorId(), now);
        }
        reserve(operation, command.idempotencyKey(), digest, batchId, actor, now);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", batchId, "V2-ARCHIVE-BATCH",
                UUID.randomUUID().toString(), "材料已批量归档");
        return new CustodyBatchResult(batchId, safe(command.blockIds()).size(), safe(command.slideIds()).size(), false);
    }

    @Transactional
    public LoanResult borrow(BorrowCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        require(command.borrowerReference(), "借阅人不能为空");
        require(command.purpose(), "借阅用途不能为空");
        if (safe(command.blockIds()).isEmpty() && safe(command.slideIds()).isEmpty()) {
            throw reject("V2-INVALID-REQUEST", "借阅材料不能为空");
        }
        Instant now = Instant.now();
        Instant expectedReturnAt = command.expectedReturnAt() == null
                ? now.plus(Duration.ofDays(7)) : command.expectedReturnAt();
        if (!expectedReturnAt.isAfter(now)) throw reject("V2-LOAN-EXPECTED-RETURN-INVALID", "预计归还日期必须晚于当前时间");
        UUID loanId = UUID.randomUUID();
        repository.insertLoan(loanId, command.borrowerReference(), blankToNull(command.borrowerDepartment()),
                command.purpose(), expectedReturnAt, actor.hospitalScope(), actor.actorId(), now);
        for (UUID blockId : safe(command.blockIds())) {
            findBlock(blockId, actor); ensureNotDestroyedBlock(blockId); repository.insertLoanBlockItem(loanId, blockId);
        }
        for (UUID slideId : safe(command.slideIds())) {
            findSlide(slideId, actor); ensureNotDestroyedSlide(slideId); repository.insertLoanSlideItem(loanId, slideId);
        }
        audit.append("PIS-V2-I06-MATERIAL-BORROW", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", loanId,
                "V2-LOAN", UUID.randomUUID().toString(), "材料已借出");
        return new LoanResult(loanId, safe(command.blockIds()).size(), safe(command.slideIds()).size(), "BORROWED");
    }

    @Transactional
    public LoanResult returnLoan(UUID loanId) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        if (!repository.returnLoan(loanId, actor.actorId(), Instant.now(), actor.hospitalScope())) {
            throw reject("V2-LOAN-CONFLICT", "借阅记录不存在或已归还");
        }
        audit.append("PIS-V2-I06-MATERIAL-RETURN", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", loanId,
                "V2-LOAN", UUID.randomUUID().toString(), "材料已归还");
        return new LoanResult(loanId, 0, 0, "RETURNED");
    }

    @Transactional
    public CustodyBatchResult destroy(DestroyCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        require(command.reason(), "销毁原因不能为空");
        require(command.batchReference(), "销毁批次不能为空");
        if (safe(command.blockIds()).isEmpty() && safe(command.slideIds()).isEmpty()) {
            throw reject("V2-INVALID-REQUEST", "销毁材料不能为空");
        }
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        for (UUID blockId : safe(command.blockIds())) {
            findBlock(blockId, actor); ensureNotDestroyedBlock(blockId);
            repository.destroyBlock(blockId, command.reason(), command.batchReference(), actor.actorId(), now);
        }
        for (UUID slideId : safe(command.slideIds())) {
            findSlide(slideId, actor); ensureNotDestroyedSlide(slideId);
            repository.destroySlide(slideId, command.reason(), command.batchReference(), actor.actorId(), now);
        }
        audit.append("PIS-V2-I06-MATERIAL-DESTRUCTION", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", batchId,
                "V2-DESTRUCTION-BATCH", UUID.randomUUID().toString(), command.batchReference());
        return new CustodyBatchResult(batchId, safe(command.blockIds()).size(), safe(command.slideIds()).size(), false);
    }

    @Transactional(readOnly = true)
    public List<LoanView> loans(String statusCode, String borrowerReference, String borrowerDepartment,
            String query) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        Instant now = Instant.now();
        return repository.findLoans(actor.hospitalScope()).stream()
                .map(row -> loanView(row, now))
                .filter(item -> statusCode == null || statusCode.isBlank() || statusCode.equals(item.statusCode()))
                .filter(item -> contains(item.borrowerReference(), borrowerReference)
                        && contains(item.borrowerDepartment(), borrowerDepartment)
                        && (contains(item.borrowerReference(), query) || contains(item.purpose(), query)
                                || item.items().stream().anyMatch(material -> contains(material.materialCode(), query)
                                        || contains(material.pathologyNo(), query))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustodyMaterialView> caseMaterials(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.findCaseMaterials(caseId, actor.hospitalScope()).stream()
                .map(row -> new CustodyMaterialView(row.materialKind(), row.materialId(), row.materialCode(),
                        row.locationId(), row.locationCode(), row.locationName(), row.loanId(),
                        row.borrowerReference(), row.destroyedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationResult> locations() {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return repository.findLocations(actor.hospitalScope()).stream()
                .map(row -> new LocationResult(row.locationId(), row.parentId(), row.locationCode(),
                        row.locationName(), row.locationKindCode()))
                .toList();
    }

    private CustodyBatchResult replay(String operation, String key, String digest, ActorContext actor) {
        var existing = repository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        if (!existing.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "材料保管命令摘要冲突");
        return new CustodyBatchResult(existing.resultEntityId(), 0, 0, true);
    }

    private static LoanView loanView(JdbcV2CustodyRepository.LoanRow row, Instant now) {
        String status = row.returnedAt() != null ? "RETURNED"
                : row.expectedReturnAt() != null && now.isAfter(row.expectedReturnAt()) ? "OVERDUE"
                        : row.expectedReturnAt() != null && !now.plus(Duration.ofDays(1)).isBefore(row.expectedReturnAt())
                                ? "DUE_SOON" : "BORROWED";
        return new LoanView(row.loanId(), row.borrowerReference(), row.borrowerDepartment(), row.purpose(),
                row.borrowedAt(), row.expectedReturnAt(), row.returnedAt(), row.returnedByRef(), status,
                row.items().stream().map(item -> new LoanMaterialView(item.materialKind(), item.materialId(),
                        item.materialCode(), item.caseId(), item.pathologyNo(), item.returnedAt())).toList());
    }

    private static boolean contains(String value, String query) {
        return query == null || query.isBlank() || (value != null && value.toLowerCase().contains(query.toLowerCase()));
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void reserve(String operation, String key, String digest, UUID resultId, ActorContext actor, Instant now) {
        if (!repository.insertIdempotency(operation, key, digest, resultId, actor.actorId(), now)) {
            throw reject("V2-IDEMPOTENCY-RETRY", "材料保管命令正在处理，请重试");
        }
    }

    private void findBlock(UUID id, ActorContext actor) {
        if (id == null || materialRepository.findBlock(id, actor.hospitalScope()).filter(block -> !block.isDeleted()).isEmpty()) {
            throw reject("V2-MATERIAL-NOT-FOUND", "蜡块不存在或已失效");
        }
    }

    private void findSlide(UUID id, ActorContext actor) {
        if (id == null || materialRepository.findSlide(id, actor.hospitalScope()).filter(slide -> !slide.isDeleted()).isEmpty()) {
            throw reject("V2-MATERIAL-NOT-FOUND", "切片不存在或已失效");
        }
    }

    private void ensureNotDestroyedBlock(UUID id) { if (repository.isDestroyedBlock(id)) throw reject("V2-MATERIAL-DESTROYED", "蜡块已销毁"); }
    private void ensureNotDestroyedSlide(UUID id) { if (repository.isDestroyedSlide(id)) throw reject("V2-MATERIAL-DESTROYED", "切片已销毁"); }
    private static List<UUID> safe(List<UUID> values) { return values == null ? List.of() : values; }
    private static void require(Object value, String message) { if (value == null || value.toString().isBlank()) throw reject("V2-INVALID-REQUEST", message); }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static String digest(Object... values) {
        try {
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString()).reduce((a, b) -> a + "|" + b).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }

    public record CreateLocationCommand(UUID parentId, String locationCode, String locationName, String locationKindCode) { }
    public record ArchiveCommand(List<UUID> blockIds, List<UUID> slideIds, UUID locationId, String reason, String idempotencyKey) { }
    public record BorrowCommand(List<UUID> blockIds, List<UUID> slideIds, String borrowerReference, String purpose,
            String borrowerDepartment, Instant expectedReturnAt) {
        public BorrowCommand(List<UUID> blockIds, List<UUID> slideIds, String borrowerReference, String purpose) {
            this(blockIds, slideIds, borrowerReference, purpose, null, null);
        }
    }
    public record DestroyCommand(List<UUID> blockIds, List<UUID> slideIds, String reason, String batchReference) { }
    public record LocationResult(UUID locationId, UUID parentId, String locationCode, String locationName, String locationKindCode) { }
    public record CustodyBatchResult(UUID batchId, int blockCount, int slideCount, boolean duplicate) { }
    public record LoanResult(UUID loanId, int blockCount, int slideCount, String statusCode) { }
    public record LoanView(UUID loanId, String borrowerReference, String borrowerDepartment, String purpose,
            Instant borrowedAt, Instant expectedReturnAt, Instant returnedAt, String returnedByRef,
            String statusCode, List<LoanMaterialView> items) { }
    public record LoanMaterialView(String materialKind, UUID materialId, String materialCode, UUID caseId,
            String pathologyNo, Instant returnedAt) { }
    public record CustodyMaterialView(String materialKind, UUID materialId, String materialCode, UUID locationId,
            String locationCode, String locationName, UUID loanId, String borrowerReference, Instant destroyedAt) { }
}
