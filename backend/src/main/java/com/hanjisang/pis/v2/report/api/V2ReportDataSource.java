package com.hanjisang.pis.v2.report.api;

import java.util.Map;
import java.util.UUID;

/** Stable extension point for hospital-specific report data without changing the V2 domain. */
public interface V2ReportDataSource {
    String code();
    Map<String, Object> load(UUID caseId);
}
