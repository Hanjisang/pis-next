package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RegistrationConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_registration_concurrency")
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
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void twoRegistrarsCannotCreateTwoCasesFromTheSameApplicationItem() throws Exception {
        RegistrationTarget target = createAcceptedApplication("APP-CONCURRENT-SAME");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> register = () -> mockMvc.perform(post(
                    "/api/v2/applications/{applicationId}/items/{itemId}/register",
                    target.applicationId(), target.itemId())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(register);
            Future<Integer> second = executor.submit(register);

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pis_v2.pathology_application_case WHERE application_item_id = ?
                    """, Integer.class, UUID.fromString(target.itemId()))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRegistrationsAllocateUniquePathologyNumbers() throws Exception {
        List<RegistrationTarget> targets = new ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            targets.add(createAcceptedApplication("APP-CONCURRENT-" + index));
        }
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (RegistrationTarget target : targets) {
                futures.add(executor.submit(() -> {
                    MvcResult result = mockMvc.perform(post(
                            "/api/v2/applications/{applicationId}/items/{itemId}/register",
                            target.applicationId(), target.itemId())
                            .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
                    assertThat(result.getResponse().getStatus()).isEqualTo(200);
                    return mapper.readTree(result.getResponse().getContentAsString());
                }));
            }
            List<String> pathologyNumbers = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                pathologyNumbers.add(future.get().path("cases").get(0).path("caseNo").asText());
            }
            assertThat(new HashSet<>(pathologyNumbers)).hasSize(targets.size());
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT c.case_no)
                    FROM pis_v2.pathology_case c
                    JOIN pis_v2.pathology_application_case ac ON ac.case_id = c.id
                    JOIN pis_v2.pathology_application a ON a.id = ac.application_id
                    WHERE a.application_no ~ '^APP-CONCURRENT-[0-9]+$'
                    """, Integer.class)).isEqualTo(targets.size());
        } finally {
            executor.shutdownNow();
        }
    }

    private RegistrationTarget createAcceptedApplication(String applicationNo) throws Exception {
        JsonNode created = mapper.readTree(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationNo":"%s","sourceTypeCode":"MANUAL","sourceSystemCode":"PIS-MANUAL",
                         "patientReference":"SYNTH-%s","patientName":"Synthetic Concurrent Patient",
                         "visitReference":"MZ-%s","visitTypeCode":"OUTPATIENT",
                         "applicationDepartment":"SYNTH-DEPARTMENT","applicantReference":"SYNTH-DOCTOR",
                         "items":[{"externalItemCode":"SYNTH-HISTOLOGY","itemName":"routine histology",
                         "specimenKindCode":"TISSUE","specimenDescription":"Synthetic tissue","sequenceNo":1}]}
                        """.formatted(applicationNo, applicationNo, applicationNo))).andReturn()
                .getResponse().getContentAsString());
        String applicationId = created.path("applicationId").asText();
        String itemId = created.path("items").get(0).path("itemId").asText();
        MvcResult verified = mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"BC-%s",
                         "specimenLabelCode":"BC-%s","patientReference":"SYNTH-%s",
                         "actualSpecimenDescription":"Synthetic tissue","outcomeCode":"ACCEPTED",
                         "patientMatch":true,"applicationMatch":true,"quantityMatch":true,
                         "specimenMatch":true,"containerMatch":true,"fixationMatch":true}
                        """.formatted(itemId, applicationNo, applicationNo, applicationNo))).andReturn();
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        return new RegistrationTarget(applicationId, itemId);
    }

    private record RegistrationTarget(String applicationId, String itemId) { }
}
