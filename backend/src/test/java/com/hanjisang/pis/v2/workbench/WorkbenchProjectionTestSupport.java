package com.hanjisang.pis.v2.workbench;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
abstract class WorkbenchProjectionTestSupport {

    @Autowired private WebApplicationContext context;
    protected MockMvc mockMvc;

    @BeforeEach
    void setUpWorkbenchProjection() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected JsonNode workbench() throws Exception {
        return new ObjectMapper().readTree(mockMvc.perform(get("/api/v2/my-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected JsonNode queue(JsonNode body, String key) {
        for (JsonNode queue : body.path("capabilityQueues")) {
            if (key.equals(queue.path("key").asText())) return queue;
        }
        throw new AssertionError("Missing workbench queue " + key);
    }
}
