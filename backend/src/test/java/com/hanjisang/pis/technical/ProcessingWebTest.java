package com.hanjisang.pis.technical;

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

import com.hanjisang.pis.security.P15ExceptionHandler;
import com.hanjisang.pis.technical.application.ProcessingApplicationService;
import com.hanjisang.pis.technical.web.ProcessingController;

@ExtendWith(MockitoExtension.class)
class ProcessingWebTest {

    @Mock private ProcessingApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProcessingController(service))
                .setControllerAdvice(new P15ExceptionHandler()).build();
    }

    @Test
    void processingQueueAndTaskCommandsExposeP17Routes() throws Exception {
        UUID blockId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(service.processingQueue()).thenReturn(List.of());
        when(service.createTask(any())).thenReturn(new ProcessingApplicationService.TaskResult(taskId,
                "P17-TASK-TEST", blockId, "P17-PROCESSING-TASK-PLANNED", null, null, 0, false));

        mvc.perform(get("/api/p17/processing-queue"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(post("/api/p17/processing-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tissueBlockId\":\"" + blockId + "\",\"idempotencyKey\":\"p17-web-task\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("P17-TASK-TEST")));
    }
}
