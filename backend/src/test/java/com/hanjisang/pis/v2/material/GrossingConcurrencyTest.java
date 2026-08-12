package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class GrossingConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_grossing_concurrency")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("pis.require-auth", () -> "false");
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void concurrentInitialGrossingCreatesExactlyOneFact() throws Exception {
        String caseId = createCase();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> mockMvc.perform(post("/api/v2/cases/{id}/grossings", caseId)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"sourceType":"INITIAL","grossDescription":"并发首次取材",
                             "grossingDoctorId":"SYNTH-DOCTOR","recorderId":"SYNTH-RECORDER",
                             "idempotencyKey":"%s"}
                            """.formatted(UUID.randomUUID()))).andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(create);
            Future<Integer> second = executor.submit(create);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing WHERE case_id = ? AND source_type = 'INITIAL'",
                    Integer.class, UUID.fromString(caseId))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBlockCodeCreationReturnsOneSuccessAndOneBusinessConflict() throws Exception {
        String caseId = createCase();
        String specimenId = new ObjectMapper().readTree(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"caseId":"%s","specimenCode":"CON-S1","specimenName":"并发测试组织",
                         "specimenKindCode":"TISSUE","sourceKindCode":"LOCAL","sourceReference":"SYNTH-CON",
                         "labelCode":"LBL-CON","idempotencyKey":"%s"}
                        """.formatted(caseId, UUID.randomUUID()))).andReturn().getResponse().getContentAsString())
                .path("specimenId").asText();
        String grossingId = new ObjectMapper().readTree(mockMvc.perform(post("/api/v2/cases/{id}/grossings", caseId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"INITIAL","grossDescription":"并发材块取材",
                         "grossingDoctorId":"SYNTH-DOCTOR","recorderId":"SYNTH-RECORDER",
                         "idempotencyKey":"%s"}
                        """.formatted(UUID.randomUUID()))).andReturn().getResponse().getContentAsString())
                .path("grossingId").asText();
        mockMvc.perform(post("/api/v2/grossings/{id}/specimens", grossingId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"specimenId":"%s","materialDescription":"并发材块来源","idempotencyKey":"%s"}
                        """.formatted(specimenId, UUID.randomUUID()))).andReturn();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> create = () -> mockMvc.perform(post("/api/v2/grossings/{id}/blocks", grossingId)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"specimenId":"%s","blockCode":"CON-A3","blockType":"ROUTINE",
                             "samplingDescription":"并发测试","idempotencyKey":"%s"}
                            """.formatted(specimenId, UUID.randomUUID()))).andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(create);
            Future<Integer> second = executor.submit(create);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE case_id = ? AND block_code = 'CON-A3'",
                    Integer.class, UUID.fromString(caseId))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void grossingWorkspaceReadsThePostgreSqlMaterialTree() throws Exception {
        String caseId = createCase();
        mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"caseId":"%s","specimenCode":"PG-S1","specimenName":"PostgreSQL 取材标本",
                         "specimenKindCode":"TISSUE","sourceKindCode":"LOCAL","sourceReference":"SYNTH-PG",
                         "labelCode":"LBL-PG","idempotencyKey":"%s"}
                        """.formatted(caseId, UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v2/cases/{id}/grossing-workspace", caseId)
                        .queryParam("sourceType", "INITIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specimens[0].specimenCode").value("PG-S1"));
    }

    private String createCase() throws Exception {
        String suffix = UUID.randomUUID().toString();
        return new ObjectMapper().readTree(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"GROSS-CON-%s",
                         "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-CON",
                         "visitReference":"SYNTH-VISIT","idempotencyKey":"case-%s"}
                        """.formatted(suffix, suffix))).andReturn().getResponse().getContentAsString())
                .path("caseId").asText();
    }
}
