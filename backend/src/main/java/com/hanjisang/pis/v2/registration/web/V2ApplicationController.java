package com.hanjisang.pis.v2.registration.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.ApplicationItemCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.CancelApplicationCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.CreateApplicationCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.DeliveryCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.PrintBarcodeCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.PatientLookupCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.RegisterApplicationCommand;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.UpdateApplicationCommand;

@RestController
@RequestMapping("/api/v2/applications")
public class V2ApplicationController {

    private final V2ApplicationApplicationService service;

    public V2ApplicationController(V2ApplicationApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public V2ApplicationApplicationService.ApplicationResult create(@RequestBody ApplicationRequest request) {
        return service.create(createCommand(request));
    }

    @PostMapping("/validate")
    public V2ApplicationApplicationService.ValidationResult validate(@RequestBody ApplicationRequest request) {
        return service.validate(createCommand(request));
    }

    @PostMapping("/patient-lookup")
    public V2ApplicationApplicationService.PatientLookupResult patientLookup(@RequestBody PatientLookupRequest request) {
        return service.lookupPatient(new PatientLookupCommand(request.patientId(), request.visitId(),
                request.outpatientNo(), request.inpatientNo()));
    }

    private static CreateApplicationCommand createCommand(ApplicationRequest request) {
        return new CreateApplicationCommand(request.applicationNo(), request.sourceTypeCode(),
                request.sourceSystemCode(), request.patientReference(), request.patientName(), request.patientSexCode(),
                request.patientBirthDate(), request.patientInfoSourceCode(), request.patientIdentityNo(),
                request.visitCardNo(), request.contactPhone(), request.ageValue(), request.ageUnitCode(),
                request.visitReference(), request.visitTypeCode(), request.wardReference(), request.bedReference(),
                request.applicationDepartment(), request.applicantReference(), request.appliedAt(),
                request.clinicalDiagnosis(), request.medicalHistory(), request.operationFinding(),
                request.surgeryName(), request.examinationPurpose(), request.specimenDescription(), request.note(),
                items(request.items()));
    }

    @GetMapping("/queue")
    public List<V2ApplicationApplicationService.ApplicationQueueResult> queue() {
        return service.queue();
    }

    @GetMapping("/{applicationId}")
    public V2ApplicationApplicationService.ApplicationResult get(@PathVariable UUID applicationId) {
        return service.get(applicationId);
    }

    @PutMapping("/{applicationId}")
    public V2ApplicationApplicationService.ApplicationResult update(@PathVariable UUID applicationId,
            @RequestBody ApplicationUpdateRequest request) {
        return service.update(applicationId, new UpdateApplicationCommand(request.sourceTypeCode(),
                request.sourceSystemCode(), request.patientReference(), request.patientName(), request.patientSexCode(),
                request.patientBirthDate(), request.patientInfoSourceCode(), request.patientIdentityNo(),
                request.visitCardNo(), request.contactPhone(), request.ageValue(), request.ageUnitCode(),
                request.visitReference(), request.visitTypeCode(), request.wardReference(), request.bedReference(),
                request.applicationDepartment(), request.applicantReference(), request.appliedAt(),
                request.clinicalDiagnosis(), request.medicalHistory(), request.operationFinding(),
                request.surgeryName(), request.examinationPurpose(), request.specimenDescription(), request.note(),
                items(request.items())));
    }

    @PostMapping("/{applicationId}/cancel")
    public V2ApplicationApplicationService.ApplicationResult cancel(@PathVariable UUID applicationId,
            @RequestBody CancelRequest request) {
        return service.cancel(applicationId, new CancelApplicationCommand(request.reason()));
    }

    @PostMapping("/{applicationId}/items/{applicationItemId}/cancel")
    public V2ApplicationApplicationService.ApplicationResult cancelItem(@PathVariable UUID applicationId,
            @PathVariable UUID applicationItemId, @RequestBody CancelRequest request) {
        return service.cancelItem(applicationId, applicationItemId, new CancelApplicationCommand(request.reason()));
    }

    @PostMapping("/{applicationId}/delivery")
    public V2ApplicationApplicationService.DeliveryResult verifyDelivery(@PathVariable UUID applicationId,
            @RequestBody DeliveryRequest request) {
        return service.verifyDelivery(new DeliveryCommand(applicationId, request.applicationItemId(),
                request.incomingSpecimenReference(), request.specimenLabelCode(), request.patientReference(),
                request.actualSpecimenDescription(), request.outcomeCode(), request.reasonCode(), request.reasonText(),
                request.patientMatch(), request.applicationMatch(), request.quantityMatch(), request.specimenMatch(),
                request.containerMatch(), request.fixationMatch()));
    }

    @GetMapping("/barcode-scan")
    public V2ApplicationApplicationService.BarcodeScanResult scanBarcode(@RequestParam String barcode) {
        return service.scanBarcode(barcode);
    }

    @GetMapping("/deliveries")
    public List<V2ApplicationApplicationService.DeliverySearchView> deliveries(
            @RequestParam(required = false) String visitReference,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String externalItemCode) {
        return service.searchDeliveries(visitReference, from, to, externalItemCode);
    }

    @GetMapping(value = "/deliveries/export", produces = "application/vnd.ms-excel")
    public ResponseEntity<String> deliveryExcel(@RequestParam(required = false) String visitReference,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String externalItemCode) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"application-deliveries.xls\"")
                .body(service.deliveryExcel(visitReference, from, to, externalItemCode));
    }

    @PostMapping("/{applicationId}/barcode-print")
    public V2ApplicationApplicationService.PrintResult printBarcodes(@PathVariable UUID applicationId,
            @RequestBody BarcodePrintRequest request) {
        return service.printBarcodes(applicationId, new PrintBarcodeCommand(request.applicationItemId(),
                request.copies() == null ? 1 : request.copies(), request.printerProfileCode()));
    }

    @GetMapping("/{applicationId}/barcode-print-history")
    public List<V2ApplicationApplicationService.BarcodePrintView> barcodePrintHistory(
            @PathVariable UUID applicationId) {
        return service.barcodePrintHistory(applicationId);
    }

    @GetMapping(value = "/{applicationId}/delivery-export", produces = "text/csv")
    public ResponseEntity<String> deliveryExport(@PathVariable UUID applicationId) {
        return csvResponse("application-delivery.csv", service.deliveryExport(applicationId));
    }

    @GetMapping(value = "/{applicationId}/barcode-print-export", produces = "text/csv")
    public ResponseEntity<String> barcodePrintExport(@PathVariable UUID applicationId) {
        return csvResponse("application-barcode-print.csv", service.barcodePrintExport(applicationId));
    }

    @PostMapping("/{applicationId}/register")
    public V2ApplicationApplicationService.RegistrationResult register(@PathVariable UUID applicationId,
            @RequestBody RegisterRequest request) {
        return service.register(applicationId, new RegisterApplicationCommand(request.receiptKindCode(),
                request.printerProfileCode()));
    }

    @PostMapping("/{applicationId}/items/{applicationItemId}/register")
    public V2ApplicationApplicationService.RegistrationResult registerItem(@PathVariable UUID applicationId,
            @PathVariable UUID applicationItemId, @RequestBody RegisterRequest request) {
        return service.registerItem(applicationId, applicationItemId, new RegisterApplicationCommand(
                request.receiptKindCode(), request.printerProfileCode()));
    }

    private static List<ApplicationItemCommand> items(List<ApplicationItemRequest> values) {
        if (values == null) return List.of();
        return values.stream().map(item -> new ApplicationItemCommand(item.itemId(), item.externalItemCode(), item.itemName(),
                item.specimenKindCode(), item.specimenDescription(), item.sequenceNo())).toList();
    }

    private static ResponseEntity<String> csvResponse(String filename, String body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    public record ApplicationRequest(String applicationNo, String sourceTypeCode, String sourceSystemCode,
            String patientReference, String patientName, String patientSexCode, LocalDate patientBirthDate,
            String patientInfoSourceCode, String patientIdentityNo, String visitCardNo, String contactPhone,
            Integer ageValue, String ageUnitCode, String visitReference, String visitTypeCode,
            String wardReference, String bedReference, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding,
            String surgeryName, String examinationPurpose, String specimenDescription, String note,
            List<ApplicationItemRequest> items) { }

    public record ApplicationUpdateRequest(String sourceTypeCode, String sourceSystemCode, String patientReference,
            String patientName, String patientSexCode, LocalDate patientBirthDate, String visitReference,
            String visitTypeCode, String applicationDepartment, String applicantReference, Instant appliedAt,
            String clinicalDiagnosis, String medicalHistory, String operationFinding, String examinationPurpose,
            String specimenDescription, String note, List<ApplicationItemRequest> items,
            String patientInfoSourceCode, String patientIdentityNo, String visitCardNo, String contactPhone,
            Integer ageValue, String ageUnitCode, String wardReference, String bedReference, String surgeryName) { }

    public record ApplicationItemRequest(UUID itemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, int sequenceNo) { }
    public record PatientLookupRequest(String patientId, String visitId, String outpatientNo, String inpatientNo) { }
    public record CancelRequest(String reason) { }
    public record DeliveryRequest(UUID applicationItemId, String incomingSpecimenReference,
            String specimenLabelCode, String patientReference, String actualSpecimenDescription, String outcomeCode,
            String reasonCode, String reasonText, Boolean patientMatch, Boolean applicationMatch,
            Boolean quantityMatch, Boolean specimenMatch, Boolean containerMatch, Boolean fixationMatch) { }
    public record BarcodePrintRequest(List<UUID> applicationItemId, Integer copies, String printerProfileCode) { }
    public record RegisterRequest(String receiptKindCode, String printerProfileCode) { }
}
