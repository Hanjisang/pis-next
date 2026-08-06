package com.hanjisang.pis.technical;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hanjisang.pis.technical.application.GrossingApplicationService;
import com.hanjisang.pis.technical.web.GrossingController;
import com.hanjisang.pis.security.P15ExceptionHandler;

@ExtendWith(MockitoExtension.class)
class GrossingWebTest {

    @Mock private GrossingApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GrossingController(service))
                .setControllerAdvice(new P15ExceptionHandler()).build();
    }

    @Test
    void independentWebAdapterCoversQueueStartSampleBlockLabelPrintReprintAndCompleteShapes() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();
        var batch = new GrossingApplicationService.BatchResult(batchId, "DEV-GROSS-WEB", "P16-TASK-TAKEN-OVER",
                "P16-GROSSING-IN-PROGRESS", "actor", "actor", 2, false);
        var block = new GrossingApplicationService.BlockResult(blockId, "DEV-BLOCK-WEB", "DEV-BOX-WEB",
                "P08-SM-004-ST-01", 0, false);
        var label = new GrossingApplicationService.LabelResult(labelId, blockId, 1, "P16-LABEL-GENERATED",
                "case_no=DEV-CASE", "P16|DEV-BLOCK-WEB", false);
        org.mockito.Mockito.when(service.transition(eq(batchId), any(), eq("P16-GROSSING-IN-PROGRESS"), eq("P14-PERM-013")))
                .thenReturn(batch);
        org.mockito.Mockito.when(service.createBlock(eq(batchId), any())).thenReturn(block);
        org.mockito.Mockito.when(service.generateLabel(eq(blockId), any())).thenReturn(label);
        org.mockito.Mockito.when(service.submitPrint(eq(labelId), any(), eq(false)))
                .thenReturn(new GrossingApplicationService.PrintResult(UUID.randomUUID(), UUID.randomUUID(), labelId,
                        "P16-PRINT-SUBMITTED", "REFERENCE_SUBMITTED", true));
        org.mockito.Mockito.when(service.submitPrint(eq(labelId), any(), eq(true)))
                .thenReturn(new GrossingApplicationService.PrintResult(UUID.randomUUID(), UUID.randomUUID(), labelId,
                        "P16-PRINT-SUBMITTED", "REFERENCE_SUBMITTED", true));
        org.mockito.Mockito.when(service.complete(eq(batchId), any())).thenReturn(batch);
        org.mockito.Mockito.when(service.addSample(eq(batchId), any()))
                .thenReturn(new GrossingApplicationService.SampleResult(sampleId, "DEV-SAMPLE-WEB", "P16-SAMPLE-UNASSIGNED", false));

        mvc.perform(post("/api/p16/grossing-batches/{id}/start", batchId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/grossing-batches/{id}/samples", batchId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"specimenId\":\"" + UUID.randomUUID() + "\",\"sourceSite\":\"site\",\"description\":\"fragment\",\"quantity\":1,\"unit\":\"PIECE\",\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/grossing-batches/{id}/blocks", batchId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"specimenId\":\"" + UUID.randomUUID() + "\",\"expectedVersion\":3}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/blocks/{id}/labels", blockId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"label-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/labels/{id}/print", labelId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"print-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/labels/{id}/reprint", labelId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"print-2\",\"reason\":\"synthetic damaged label\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/p16/grossing-batches/{id}/complete", batchId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":4}"))
                .andExpect(status().isOk());
    }
}
