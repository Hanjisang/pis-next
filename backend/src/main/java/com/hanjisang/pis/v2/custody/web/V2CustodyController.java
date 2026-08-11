package com.hanjisang.pis.v2.custody.web;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.custody.application.V2CustodyApplicationService;
import com.hanjisang.pis.v2.custody.application.V2CustodyApplicationService.ArchiveCommand;
import com.hanjisang.pis.v2.custody.application.V2CustodyApplicationService.BorrowCommand;
import com.hanjisang.pis.v2.custody.application.V2CustodyApplicationService.CreateLocationCommand;
import com.hanjisang.pis.v2.custody.application.V2CustodyApplicationService.DestroyCommand;

@RestController
@RequestMapping("/api/v2/custody")
public class V2CustodyController {

    private final V2CustodyApplicationService service;
    public V2CustodyController(V2CustodyApplicationService service) { this.service = service; }

    @PostMapping("/locations")
    public V2CustodyApplicationService.LocationResult createLocation(@RequestBody CreateLocationRequest request) {
        return service.createLocation(new CreateLocationCommand(request.parentId(), request.locationCode(),
                request.locationName(), request.locationKindCode()));
    }

    @GetMapping("/locations")
    public List<V2CustodyApplicationService.LocationResult> locations() {
        return service.locations();
    }

    @PostMapping("/archive")
    public V2CustodyApplicationService.CustodyBatchResult archive(@RequestBody ArchiveRequest request) {
        return service.archive(new ArchiveCommand(request.blockIds(), request.slideIds(), request.locationId(),
                request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/loans")
    public V2CustodyApplicationService.LoanResult borrow(@RequestBody BorrowRequest request) {
        return service.borrow(new BorrowCommand(request.blockIds(), request.slideIds(), request.borrowerReference(),
                request.purpose(), request.borrowerDepartment(), request.expectedReturnAt()));
    }

    @GetMapping("/loans")
    public List<V2CustodyApplicationService.LoanView> loans(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String borrower,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String department,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query) {
        return service.loans(status, borrower, department, query);
    }

    @PostMapping("/loans/{loanId}/return")
    public V2CustodyApplicationService.LoanResult returnLoan(@PathVariable UUID loanId) {
        return service.returnLoan(loanId);
    }

    @PostMapping("/destruction")
    public V2CustodyApplicationService.CustodyBatchResult destroy(@RequestBody DestroyRequest request) {
        return service.destroy(new DestroyCommand(request.blockIds(), request.slideIds(), request.reason(),
                request.batchReference()));
    }

    @GetMapping("/cases/{caseId}/materials")
    public List<V2CustodyApplicationService.CustodyMaterialView> caseMaterials(@PathVariable UUID caseId) {
        return service.caseMaterials(caseId);
    }

    public record CreateLocationRequest(UUID parentId, String locationCode, String locationName, String locationKindCode) { }
    public record ArchiveRequest(List<UUID> blockIds, List<UUID> slideIds, UUID locationId, String reason,
            String idempotencyKey) { }
    public record BorrowRequest(List<UUID> blockIds, List<UUID> slideIds, String borrowerReference, String purpose,
            String borrowerDepartment, Instant expectedReturnAt) { }
    public record DestroyRequest(List<UUID> blockIds, List<UUID> slideIds, String reason, String batchReference) { }
}
