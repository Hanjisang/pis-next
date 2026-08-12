package com.hanjisang.pis.integration.patient;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class SimulatorPatientInfoProvider implements PatientInfoProviderPort {

    @Override
    public Optional<PatientInfo> lookup(PatientLookup query) {
        String reference = first(query.outpatientNo(), query.inpatientNo(), query.visitId(), query.patientId());
        if (reference == null || "UNKNOWN".equalsIgnoreCase(reference)) return Optional.empty();
        if ("TIMEOUT".equalsIgnoreCase(reference)) {
            throw new PatientLookupFailure("HIS_PATIENT_LOOKUP_TIMEOUT", "患者信息服务暂时无响应，请重试或人工补录");
        }
        boolean inpatient = query.inpatientNo() != null && !query.inpatientNo().isBlank();
        return Optional.of(new PatientInfo("SYNTH-PATIENT-" + reference, inpatient ? "李某" : "张某", "MALE",
                LocalDate.of(1981, 6, 15), null, null, "SYNTH-ID-" + reference,
                reference, inpatient ? "INPATIENT" : "OUTPATIENT", "CARD-" + reference,
                "138****0000", inpatient ? "住院病区" : "门诊外科", inpatient ? "一病区" : null,
                inpatient ? "08" : null, "合成临床诊断", "合成病史资料"));
    }

    @Override
    public String adapterCode() { return "SIMULATOR"; }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

}
