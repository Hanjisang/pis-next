package com.hanjisang.pis.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CommandResult;
import com.hanjisang.pis.security.P15BusinessException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pis.runtime-environment=test",
        "pis.actor-id=p19-test-actor",
        "pis.actor-task-scope=P19-DIAGNOSIS-REPORT",
        "pis.actor-permissions=P14-PERM-034,P14-PERM-035,P14-PERM-036,P14-PERM-055,P14-PERM-057,P14-PERM-058"
})
@Sql("/p19-test-schema.sql")
class DiagnosisReportApplicationServiceTest {

    @Autowired
    private DiagnosisReportApplicationService service;

    @Test
    void completesDiagnosisReviewAndSigningWithImmutableVersion() {
        UUID caseId = UUID.randomUUID();
        CommandResult task = service.createTask(new DiagnosisReportApplicationService.CreateTaskCommand(caseId, "HISTOLOGY", "INITIAL", "ROUTINE", "PATHOLOGY", "p19-create-1"));
        CommandResult takeover = service.takeover(task.objectId(), new DiagnosisReportApplicationService.VersionCommand(0, "p19-takeover-1", "接管"));
        service.saveDraft(task.objectId(), new DiagnosisReportApplicationService.SaveDraftCommand(null, "镜下描述", "诊断结论", null, "{}", null, takeover.concurrencyVersion(), "p19-draft-1"));
        CommandResult diagnosis = service.submitInitial(task.objectId(), new DiagnosisReportApplicationService.SubmitDiagnosisCommand(takeover.concurrencyVersion(), "p19-submit-1"));
        CommandResult report = service.createReport(task.objectId(), new DiagnosisReportApplicationService.CreateReportCommand(diagnosis.relatedObjectId(), "HISTOPATHOLOGY", "p19-report-1"));
        CommandResult content = service.generateContent(report.objectId(), new DiagnosisReportApplicationService.GenerateContentCommand("patient-snapshot", "encounter-snapshot", "CASE-SYNTHETIC-1", "specimen-material-snapshot", "clinical", "specimen", "gross", "micro", "diagnosis", null, "technical-reference", "P19-TEMPLATE-1", diagnosis.relatedObjectId(), "p19-content-1"));
        service.submitReview(content.relatedObjectId(), new DiagnosisReportApplicationService.ReviewCommand("p19-reviewer", "独立复核", "p19-review-submit-1"));
        service.approveReview(content.relatedObjectId(), new DiagnosisReportApplicationService.ReviewDecisionCommand("p19-reviewer", "APPROVED", "通过", "p19-review-approve-1"));
        CommandResult signed = service.sign(content.relatedObjectId(), new DiagnosisReportApplicationService.SignCommand("p19-reviewer", content.concurrencyVersion(), "p19-sign-1"));
        assertThat(signed.stateCode()).isEqualTo("SIGNED");
        assertThat(service.content(content.relatedObjectId()).orElseThrow().stateCode()).isEqualTo("SIGNED");
        assertThatThrownBy(() -> service.sign(content.relatedObjectId(), new DiagnosisReportApplicationService.SignCommand("p19-reviewer", content.concurrencyVersion(), "p19-sign-2")))
                .isInstanceOf(P15BusinessException.class);
    }
}
