package com.hanjisang.pis.v2.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
abstract class GrossingClosureTestSupport {

    @Autowired private WebApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    protected MockMvc mockMvc;
    protected final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUpGrossingClosure() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected JsonNode ok(ResultActions action) throws Exception {
        return mapper.readTree(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected JsonNode getOk(String path) throws Exception {
        return ok(mockMvc.perform(get(path)));
    }

    protected JsonNode postOk(String path, String body) throws Exception {
        return ok(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    protected UUID createCase(String label) throws Exception {
        String suffix = label + "-" + UUID.randomUUID();
        return UUID.fromString(postOk("/api/v2/registration/cases", """
                {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                 "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-%s",
                 "visitReference":"SYNTH-VISIT","idempotencyKey":"case-%s"}
                """.formatted(suffix, suffix, suffix)).path("caseId").asText());
    }

    protected UUID createSpecimen(UUID caseId, String code, String creationSource) throws Exception {
        String key = UUID.randomUUID().toString();
        return UUID.fromString(postOk("/api/v2/registration/specimens", """
                {"caseId":"%s","specimenCode":"%s","specimenName":"%s号合成组织",
                 "specimenKindCode":"TISSUE","creationSourceCode":"%s","sourceKindCode":"LOCAL",
                 "sourceReference":"SYNTH-%s","quantityValue":1,"quantityUnitCode":"件",
                 "description":"合成测试标本","labelCode":"LBL-%s",
                 "creationReason":"取材工作区补录","idempotencyKey":"%s"}
                """.formatted(caseId, code, code, creationSource, code, code, key)).path("specimenId").asText());
    }

    protected UUID createGrossing(UUID caseId, String sourceType, UUID sourceReferenceId) throws Exception {
        String source = sourceReferenceId == null ? "" : ",\"sourceReferenceId\":\"" + sourceReferenceId + "\"";
        String key = UUID.randomUUID().toString();
        return UUID.fromString(postOk("/api/v2/cases/" + caseId + "/grossings", """
                {"sourceType":"%s"%s,"grossDescription":"合成大体描述",
                 "grossingInstruction":"按规范取材","grossingDoctorId":"SYNTH-DOCTOR",
                 "recorderId":"SYNTH-RECORDER","idempotencyKey":"%s"}
                """.formatted(sourceType, source, key)).path("grossingId").asText());
    }

    protected void associate(UUID grossingId, UUID specimenId, String description) throws Exception {
        postOk("/api/v2/grossings/" + grossingId + "/specimens", """
                {"specimenId":"%s","materialDescription":"%s","idempotencyKey":"%s"}
                """.formatted(specimenId, description, UUID.randomUUID()));
    }

    protected UUID createBlock(UUID grossingId, UUID specimenId, String code) throws Exception {
        return UUID.fromString(postOk("/api/v2/grossings/" + grossingId + "/blocks", """
                {"specimenId":"%s","blockCode":"%s","blockType":"ROUTINE",
                 "samplingDescription":"代表性组织","note":"合成测试",
                 "idempotencyKey":"%s"}
                """.formatted(specimenId, code, UUID.randomUUID())).path("blockId").asText());
    }

    protected JsonNode complete(UUID grossingId, long version) throws Exception {
        return postOk("/api/v2/grossings/" + grossingId + "/complete", """
                {"expectedVersion":%d,"idempotencyKey":"%s"}
                """.formatted(version, UUID.randomUUID()));
    }
}
