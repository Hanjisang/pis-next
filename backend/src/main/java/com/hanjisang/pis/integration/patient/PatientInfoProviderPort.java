package com.hanjisang.pis.integration.patient;

import java.time.LocalDate;
import java.util.Optional;

public interface PatientInfoProviderPort {

    Optional<PatientInfo> lookup(PatientLookup query);

    String adapterCode();

    record PatientLookup(String patientId, String visitId, String outpatientNo, String inpatientNo) {
        public boolean empty() {
            return blank(patientId) && blank(visitId) && blank(outpatientNo) && blank(inpatientNo);
        }

        private static boolean blank(String value) { return value == null || value.isBlank(); }
    }

    record PatientInfo(String patientReference, String patientName, String patientSexCode, LocalDate birthDate,
            Integer ageValue, String ageUnitCode, String identityNo, String visitReference, String visitTypeCode,
            String visitCardNo, String contactPhone, String departmentReference, String wardReference,
            String bedReference, String clinicalDiagnosis, String medicalHistory) { }

    final class PatientLookupFailure extends RuntimeException {
        private final String errorCode;

        public PatientLookupFailure(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() { return errorCode; }
    }
}
