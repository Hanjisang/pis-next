package com.hanjisang.pis.v2.material.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.AssociateSpecimenCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CompleteGrossingCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CompleteSlideCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CompleteSlidesCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CorrectGrossingCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateBlockCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateBlockItem;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateBlocksCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateGrossingCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateDirectSlideCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.PrintCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.PrintBlocksCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.ReopenGrossingCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.SlideCompletion;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.SoftDeleteCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.UpdateBlockCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.UpdateGrossingCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.UpdateGrossingSpecimenCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.VerifyBlockCommand;

@RestController
@RequestMapping("/api/v2")
public class V2MaterialProductionController {

    private final V2MaterialProductionApplicationService service;

    public V2MaterialProductionController(V2MaterialProductionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}/grossings")
    public V2MaterialProductionApplicationService.GrossingResult createGrossing(@PathVariable UUID caseId,
            @RequestBody CreateGrossingRequest request) {
        return service.createGrossing(new CreateGrossingCommand(caseId, request.sourceType(),
                request.sourceReferenceId(), request.grossDescription(), request.grossingInstruction(),
                request.grossingDoctorId(), request.recorderId(), request.idempotencyKey()));
    }

    @PutMapping("/grossings/{grossingId}")
    public V2MaterialProductionApplicationService.GrossingResult updateGrossing(@PathVariable UUID grossingId,
            @RequestBody UpdateGrossingRequest request) {
        return service.updateGrossing(grossingId, new UpdateGrossingCommand(request.grossDescription(),
                request.grossingInstruction(), request.grossingDoctorId(), request.recorderId(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossings/{grossingId}/specimens")
    public V2MaterialProductionApplicationService.GrossingResult associateSpecimen(@PathVariable UUID grossingId,
            @RequestBody AssociateSpecimenRequest request) {
        return service.associateSpecimen(grossingId,
                new AssociateSpecimenCommand(request.specimenId(), request.materialDescription(),
                        request.idempotencyKey()));
    }

    @PutMapping("/grossings/{grossingId}/specimens/{specimenId}")
    public V2MaterialProductionApplicationService.GrossingResult updateGrossingSpecimen(
            @PathVariable UUID grossingId, @PathVariable UUID specimenId,
            @RequestBody UpdateGrossingSpecimenRequest request) {
        return service.updateGrossingSpecimen(grossingId, new UpdateGrossingSpecimenCommand(specimenId,
                request.materialDescription(), request.expectedVersion(), request.reason()));
    }

    @PostMapping("/grossings/{grossingId}/correct")
    public V2MaterialProductionApplicationService.GrossingResult correctGrossing(@PathVariable UUID grossingId,
            @RequestBody CorrectGrossingRequest request) {
        return service.correctCompletedGrossing(grossingId, new CorrectGrossingCommand(request.grossDescription(),
                request.grossingInstruction(), request.grossingDoctorId(), request.recorderId(), request.reason(),
                request.expectedVersion()));
    }

    @PostMapping("/grossings/{grossingId}/blocks")
    public V2MaterialProductionApplicationService.BlockResult createBlock(@PathVariable UUID grossingId,
            @RequestBody CreateBlockRequest request) {
        return service.createBlock(grossingId,
                new CreateBlockCommand(request.specimenId(), request.blockCode(), request.blockType(),
                        request.samplingDescription(), request.note(), request.idempotencyKey(),
                        Boolean.TRUE.equals(request.externalSource()),
                        request.externalSourceReference()));
    }

    @PostMapping("/grossings/{grossingId}/blocks/batch")
    public V2MaterialProductionApplicationService.BlockBatchResult createBlocks(@PathVariable UUID grossingId,
            @RequestBody CreateBlocksRequest request) {
        List<CreateBlockItem> blocks = request.blocks() == null ? List.of() : request.blocks().stream()
                .map(item -> new CreateBlockItem(item.specimenId(), item.blockCode(), item.blockType(),
                        item.samplingDescription(), item.note())).toList();
        return service.createBlocks(grossingId, new CreateBlocksCommand(blocks, request.idempotencyKey()));
    }

    @PutMapping("/blocks/{blockId}")
    public V2MaterialProductionApplicationService.BlockResult updateBlock(@PathVariable UUID blockId,
            @RequestBody UpdateBlockRequest request) {
        return service.updateBlock(blockId, new UpdateBlockCommand(request.blockCode(), request.blockType(),
                request.samplingDescription(), request.note(), request.reason(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @PostMapping("/blocks/{blockId}/verify")
    public V2MaterialProductionApplicationService.BlockVerificationResult verifyBlock(@PathVariable UUID blockId,
            @RequestBody VerifyBlockRequest request) {
        return service.verifyBlock(blockId, new VerifyBlockCommand(request.verifiedCode(),
                request.verifiedSpecimenId(), request.verifiedQuantity(), request.reason()));
    }

    @PostMapping("/cases/{caseId}/specimens/{specimenId}/slides")
    public V2MaterialProductionApplicationService.SlideResult createDirectCytologySlide(
            @PathVariable UUID caseId, @PathVariable UUID specimenId, @RequestBody CreateDirectSlideRequest request) {
        return service.createDirectCytologySlide(caseId, specimenId,
                new CreateDirectSlideCommand(request.slideCode(), request.slideType(), request.idempotencyKey()));
    }

    @PostMapping("/cases/{caseId}/external-blocks/{blockId}/slides")
    public V2MaterialProductionApplicationService.SlideResult createDirectExternalSlide(
            @PathVariable UUID caseId, @PathVariable UUID blockId, @RequestBody CreateDirectSlideRequest request) {
        return service.createDirectExternalSlide(caseId, blockId,
                new CreateDirectSlideCommand(request.slideCode(), request.slideType(), request.idempotencyKey()));
    }

    @PostMapping("/blocks/{blockId}/soft-delete")
    public V2MaterialProductionApplicationService.BlockResult softDeleteBlock(@PathVariable UUID blockId,
            @RequestBody SoftDeleteRequest request) {
        return service.softDeleteBlock(blockId, new SoftDeleteCommand(request.expectedVersion(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/grossings/{grossingId}/complete")
    public V2MaterialProductionApplicationService.GrossingCompletionResult completeGrossing(
            @PathVariable UUID grossingId, @RequestBody ExpectedVersionRequest request) {
        return service.completeGrossing(grossingId,
                new CompleteGrossingCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/grossings/{grossingId}/reopen")
    public V2MaterialProductionApplicationService.GrossingResult reopenGrossing(@PathVariable UUID grossingId,
            @RequestBody ReopenGrossingRequest request) {
        return service.reopenGrossing(grossingId, new ReopenGrossingCommand(request.expectedVersion(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/slides/{slideId}/complete")
    public V2MaterialProductionApplicationService.SlideResult completeSlide(@PathVariable UUID slideId,
            @RequestBody ExpectedVersionRequest request) {
        return service.completeSlide(slideId,
                new CompleteSlideCommand(request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/slides/complete-batch")
    public V2MaterialProductionApplicationService.SlideBatchResult completeSlides(
            @RequestBody CompleteSlidesRequest request) {
        List<SlideCompletion> slides = request.slides() == null ? List.of()
                : request.slides().stream().map(item -> new SlideCompletion(item.slideId(), item.expectedVersion()))
                        .toList();
        return service.completeSlides(new CompleteSlidesCommand(slides, request.idempotencyKey()));
    }

    @PostMapping("/blocks/{blockId}/print")
    public V2MaterialProductionApplicationService.PrintResult printBlock(@PathVariable UUID blockId,
            @RequestBody PrintRequest request) {
        return service.printBlock(blockId, new PrintCommand(request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/blocks/print-batch")
    public V2MaterialProductionApplicationService.PrintBatchResult printBlocks(
            @RequestBody PrintBlocksRequest request) {
        return service.printBlocks(new PrintBlocksCommand(request.blockIds(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/slides/{slideId}/print")
    public V2MaterialProductionApplicationService.PrintResult printSlide(@PathVariable UUID slideId,
            @RequestBody PrintRequest request) {
        return service.printSlide(slideId, new PrintCommand(request.reason(), request.idempotencyKey()));
    }

    @GetMapping("/cases/{caseId}/materials")
    public V2MaterialProductionApplicationService.MaterialTreeResult materialTree(@PathVariable UUID caseId) {
        return service.materialTree(caseId);
    }

    @GetMapping("/cases/{caseId}/grossing-workspace")
    public V2MaterialProductionApplicationService.GrossingWorkspaceResult grossingWorkspace(
            @PathVariable UUID caseId, @RequestParam(defaultValue = "INITIAL") String sourceType,
            @RequestParam(required = false) UUID sourceReferenceId) {
        return service.grossingWorkspace(caseId, sourceType, sourceReferenceId);
    }

    public record CreateGrossingRequest(String sourceType, UUID sourceReferenceId, String grossDescription,
            String grossingInstruction, String grossingDoctorId, String recorderId, String idempotencyKey) { }

    public record UpdateGrossingRequest(String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId, long expectedVersion, String idempotencyKey) { }

    public record AssociateSpecimenRequest(UUID specimenId, String materialDescription, String idempotencyKey) { }

    public record UpdateGrossingSpecimenRequest(String materialDescription, long expectedVersion, String reason) { }

    public record CorrectGrossingRequest(String grossDescription, String grossingInstruction,
            String grossingDoctorId, String recorderId, String reason, long expectedVersion) { }

    public record CreateBlockRequest(UUID specimenId, String blockCode, String blockType, String samplingDescription,
            String note, String idempotencyKey, Boolean externalSource, String externalSourceReference) { }

    public record CreateBlockItemRequest(UUID specimenId, String blockCode, String blockType,
            String samplingDescription, String note) { }

    public record CreateBlocksRequest(List<CreateBlockItemRequest> blocks, String idempotencyKey) { }

    public record CreateDirectSlideRequest(String slideCode, String slideType, String idempotencyKey) { }

    public record UpdateBlockRequest(String blockCode, String blockType, String samplingDescription, String note,
            String reason, long expectedVersion, String idempotencyKey) { }

    public record VerifyBlockRequest(String verifiedCode, UUID verifiedSpecimenId, int verifiedQuantity,
            String reason) { }

    public record SoftDeleteRequest(long expectedVersion, String reason, String idempotencyKey) { }

    public record ExpectedVersionRequest(long expectedVersion, String idempotencyKey) { }

    public record ReopenGrossingRequest(long expectedVersion, String reason, String idempotencyKey) { }

    public record SlideCompletionRequest(UUID slideId, long expectedVersion) { }

    public record CompleteSlidesRequest(List<SlideCompletionRequest> slides, String idempotencyKey) { }

    public record PrintRequest(String reason, String idempotencyKey) { }

    public record PrintBlocksRequest(List<UUID> blockIds, String reason, String idempotencyKey) { }
}
