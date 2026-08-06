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
import com.hanjisang.pis.technical.application.TechnicalOrderApplicationService;
import com.hanjisang.pis.technical.web.TechnicalOrderController;

@ExtendWith(MockitoExtension.class)
class TechnicalOrderWebTest {

    @Mock private TechnicalOrderApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TechnicalOrderController(service))
                .setControllerAdvice(new P15ExceptionHandler()).build();
    }

    @Test
    void technicalOrderListAndCreateRoutesAreExposed() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(service.orders()).thenReturn(List.of());
        mvc.perform(get("/api/p18/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void technicalOrderCreateRouteDelegatesToApplicationService() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.createOrder(any())).thenReturn(new TechnicalOrderApplicationService.OrderResult(orderId, "P18-ORDER-1",
                UUID.randomUUID(), "DRAFT", "ROUTINE", 1, false, List.of()));

        mvc.perform(post("/api/p18/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId":"%s",
                                  "orderKindCode":"TECHNICAL_ORDER",
                                  "priorityCode":"ROUTINE",
                                  "reasonText":"synthetic test order",
                                  "projects":[],
                                  "idempotencyKey":"web-test-create"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"orderId\":\"%s\"}".formatted(orderId)));
    }
}
