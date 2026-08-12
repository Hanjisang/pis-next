package com.hanjisang.pis.v2.diagnosis.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.diagnosis.application.V2CaseSupportApplicationService;
import com.hanjisang.pis.v2.diagnosis.application.V2CaseSupportApplicationService.CompleteFollowUpCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2CaseSupportApplicationService.ConsultationCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2CaseSupportApplicationService.FollowUpCommand;

@RestController
@RequestMapping("/api/v2/case-support")
public class V2CaseSupportController {

    private final V2CaseSupportApplicationService service;

    public V2CaseSupportController(V2CaseSupportApplicationService service) { this.service = service; }

    @PostMapping("/cases/{caseId}/favorite")
    public V2CaseSupportApplicationService.FavoriteResult favorite(@PathVariable UUID caseId) { return service.favorite(caseId); }

    @PostMapping("/cases/{caseId}/unfavorite")
    public V2CaseSupportApplicationService.FavoriteResult unfavorite(@PathVariable UUID caseId) { return service.unfavorite(caseId); }

    @GetMapping("/cases/{caseId}/favorite")
    public V2CaseSupportApplicationService.FavoriteResult favoriteState(@PathVariable UUID caseId) { return service.favoriteState(caseId); }

    @GetMapping("/favorites")
    public List<UUID> favorites() { return service.favorites(); }

    @PostMapping("/cases/{caseId}/follow-ups")
    public V2CaseSupportApplicationService.FollowUpResult createFollowUp(@PathVariable UUID caseId,
            @RequestBody FollowUpRequest request) {
        return service.createFollowUp(caseId, new FollowUpCommand(request.followUpDate(), request.plan()));
    }

    @GetMapping("/cases/{caseId}/follow-ups")
    public List<V2CaseSupportApplicationService.FollowUpResult> followUps(@PathVariable UUID caseId) { return service.followUps(caseId); }

    @PostMapping("/follow-ups/{followUpId}/complete")
    public V2CaseSupportApplicationService.FollowUpResult completeFollowUp(@PathVariable UUID followUpId,
            @RequestBody CompleteFollowUpRequest request) {
        return service.completeFollowUp(followUpId, new CompleteFollowUpCommand(request.content(), request.result()));
    }

    @PostMapping("/cases/{caseId}/consultations")
    public V2CaseSupportApplicationService.ConsultationResult createConsultation(@PathVariable UUID caseId,
            @RequestBody ConsultationRequest request) {
        return service.createConsultation(caseId, new ConsultationCommand(request.consultationAt(), request.initiatorRef(),
                request.participantRefs(), request.reason(), request.discussion(), request.conclusion(), request.note(),
                request.attachmentReference()));
    }

    @GetMapping("/cases/{caseId}/consultations")
    public List<V2CaseSupportApplicationService.ConsultationResult> consultations(@PathVariable UUID caseId) {
        return service.consultations(caseId);
    }

    public record FollowUpRequest(LocalDate followUpDate, String plan) { }
    public record CompleteFollowUpRequest(String content, String result) { }
    public record ConsultationRequest(Instant consultationAt, String initiatorRef, String participantRefs,
            String reason, String discussion, String conclusion, String note, String attachmentReference) { }
}
