package com.hanjisang.pis.v2.workbench;

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
class WorkbenchQueueCountConsistencyTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void everyQueueCountComesFromItsReturnedList() throws Exception {
        JsonNode body = new ObjectMapper().readTree(mockMvc.perform(get("/api/v2/my-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode queue : body.path("capabilityQueues")) {
            assertThat(queue.path("count").asInt()).isEqualTo(queue.path("items").size());
            if ("PUBLIC_POOL".equals(queue.path("key").asText())) {
                queue.path("items").forEach(item ->
                        assertThat(item.path("task").asText()).isEqualTo("待接诊"));
            }
        }
    }
}
