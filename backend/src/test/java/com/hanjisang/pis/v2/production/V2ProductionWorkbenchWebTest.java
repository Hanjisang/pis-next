package com.hanjisang.pis.v2.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class V2ProductionWorkbenchWebTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void productionWorkbenchReturnsBusinessSourceQueuesWithoutPhysicalPhaseQueues() throws Exception {
        JsonNode result = objectMapper.readTree(mockMvc.perform(get("/api/v2/production-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode queues = result.get("queues");
        assertThat(queues).isNotNull();
        assertThat(queues.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "routineProduction", "cytologyProduction", "frozenProduction", "technicalOrders",
                "incompleteSlides", "exceptions");
        assertThat(queues.get("routineProduction").get("label").asText()).isEqualTo("常规制片");
        assertThat(queues.get("cytologyProduction").get("label").asText()).isEqualTo("细胞制片");
        assertThat(queues.get("frozenProduction").get("label").asText()).isEqualTo("冰冻制片");
        assertThat(queues.get("technicalOrders").get("label").asText()).isEqualTo("技术医嘱");
        assertThat(queues.get("incompleteSlides").get("label").asText()).isEqualTo("待完成玻片");
        assertThat(queues.get("exceptions").get("label").asText()).isEqualTo("异常 / 返工");
        assertThat(result.toString()).doesNotContain("DEHYDRATION", "EMBEDDING", "CUTTING", "STAINING",
                "COVERSLIPPING");
    }
}
