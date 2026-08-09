package com.hanjisang.pis.presentation.configuration;

import org.springframework.stereotype.Service;

@Service
public class HospitalProfileApplicationService {

    private final JdbcHospitalProfileRepository repository;

    public HospitalProfileApplicationService(JdbcHospitalProfileRepository repository) {
        this.repository = repository;
    }

    public HospitalProfileSnapshot requireProfile(String profileCode) {
        if (profileCode == null || profileCode.isBlank()) {
            throw new IllegalArgumentException("医院 Profile 编码不能为空");
        }
        return repository.findByCode(profileCode.trim())
                .filter(HospitalProfileSnapshot::enabled)
                .orElseThrow(() -> new IllegalArgumentException("医院 Profile 不存在或未启用：" + profileCode));
    }

    public RegistrationConfiguration registrationConfiguration(String profileCode,
            String canonicalBusinessTypeCode) {
        HospitalProfileSnapshot profile = requireProfile(profileCode);
        HospitalProfileSnapshot.BusinessTypeConfiguration business = profile.businessTypes().stream()
                .filter(item -> item.canonicalBusinessTypeCode().equals(canonicalBusinessTypeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("医院未配置业务类型：" + canonicalBusinessTypeCode));
        HospitalProfileSnapshot.PathologyNumberConfiguration numberRule = profile.pathologyNumberRules().stream()
                .filter(item -> item.canonicalBusinessTypeCode().equals(canonicalBusinessTypeCode))
                .filter(item -> "CASE".equals(item.numberKindCode()))
                .findFirst().orElse(null);
        HospitalProfileSnapshot.WorkflowConfiguration workflow = profile.workflows().stream()
                .filter(item -> item.canonicalBusinessTypeCode().equals(canonicalBusinessTypeCode))
                .findFirst().orElse(null);
        return new RegistrationConfiguration(profile.profileCode(), canonicalBusinessTypeCode,
                business.coreBusinessTypeCode(), business.enabled(), numberRule == null ? null : numberRule.prefix(),
                workflow != null && workflow.requireReview(), workflow != null && workflow.requireAudit(),
                workflow != null && workflow.allowDirectSlide());
    }

    public record RegistrationConfiguration(String profileCode, String canonicalBusinessTypeCode,
            String coreBusinessTypeCode, boolean enabled, String caseNumberPrefix, boolean requireReview,
            boolean requireAudit, boolean allowDirectSlide) { }
}
