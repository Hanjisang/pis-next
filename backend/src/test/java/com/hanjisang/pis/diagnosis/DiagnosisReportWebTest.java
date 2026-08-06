package com.hanjisang.pis.diagnosis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CommandResult;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.TaskRow;
import com.hanjisang.pis.diagnosis.web.DiagnosisReportController;
import com.hanjisang.pis.security.P15ExceptionHandler;

@ExtendWith(MockitoExtension.class)
class DiagnosisReportWebTest {

    @Mock private DiagnosisReportApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.standaloneSetup(new DiagnosisReportController(service)).setControllerAdvice(new P15ExceptionHandler()).build(); }

    @Test
    void diagnosisQueueAndTaskCreateRoutesAreExposed() throws Exception {
        when(service.taskQueue(null)).thenReturn(List.of());
        UUID taskId = UUID.randomUUID();
        when(service.createTask(any())).thenReturn(new CommandResult(taskId, "P19-DIAGNOSIS-1", "P19-DIAGNOSIS-TASK", "P19-DIAGNOSIS-TASK-PLANNED", 0, 0, null, false, "op", "COMPLETED", "SAFE_RETRY"));
        mvc.perform(get("/api/p19/diagnosis-queue")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/p19/diagnosis-tasks").contentType(MediaType.APPLICATION_JSON).content("{\"caseId\":\"" + UUID.randomUUID() + "\",\"idempotencyKey\":\"p19-web-create\"}"))
                .andExpect(status().isCreated()).andExpect(content().string(org.hamcrest.Matchers.containsString("P19-DIAGNOSIS-1")));
    }
}
