package com.hanjisang.pis.v2.cytology;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CytologyProductionPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_fc03b_cytology_concurrency")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateFreshDatabase() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration").load().migrate();
    }

    @Test
    void postgresAllowsOnlyOneRequiredDirectSlideForAConcurrentSpecimenGeneration() throws Exception {
        Fixture fixture = seedFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> attempts = List.of(
                    () -> insertRequiredSlide(fixture, ready, start, "tech-a"),
                    () -> insertRequiredSlide(fixture, ready, start, "tech-b"));
            var futures = attempts.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }

        JdbcTemplate jdbc = jdbc();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE specimen_id = ?", Integer.class,
                fixture.specimenId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT block_id IS NULL FROM pis_v2.slide WHERE specimen_id = ?", Boolean.class,
                fixture.specimenId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT source_context_type FROM pis_v2.slide WHERE specimen_id = ?", String.class,
                fixture.specimenId())).isEqualTo("CYTOLOGY");
    }

    private boolean insertRequiredSlide(Fixture fixture, CountDownLatch ready, CountDownLatch start, String operator) {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pis_v2.slide
                        (id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                         source_context_id, rule_code, occurrence_no, required, concurrency_version,
                         organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                    VALUES (?, ?, NULL, ?, ?, 'CYTOLOGY', 'CYTOLOGY', ?, 'CYTOLOGY-DIRECT', 1, TRUE, 0,
                            'LOCAL_HOSPITAL', ?, ?, ?, ?)
                    """)) {
                OffsetDateTime now = OffsetDateTime.now();
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, fixture.caseId());
                statement.setObject(3, fixture.specimenId());
                statement.setString(4, "CY-1-" + operator);
                statement.setObject(5, fixture.specimenId());
                statement.setObject(6, now);
                statement.setString(7, operator);
                statement.setObject(8, now);
                statement.setString(9, operator);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (Exception conflict) {
                connection.rollback();
                return false;
            }
        } catch (Exception failure) {
            return false;
        }
    }

    private Fixture seedFixture() {
        JdbcTemplate jdbc = jdbc();
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'CYTOLOGY_NON_GYN'", UUID.class);
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH', ?, 'SYNTH-CYTOLOGY', ?, 'ACTIVE', TRUE, 0,
                        'LOCAL_HOSPITAL', ?, 'concurrency-test')
                """, caseId, "CY-FC03B-" + caseId, "APP-FC03B-" + caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_name, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, '1', 'Synthetic fluid', 'FLUID', 'APPLICATION', ?, 'Synthetic site',
                        'COLLECTED', ?, 0, 'LOCAL_HOSPITAL', ?, 'concurrency-test', ?, 'concurrency-test')
                """, specimenId, caseId, "SP-" + specimenId, "APP-FC03B-" + caseId, "LBL-" + specimenId,
                now, now);
        return new Fixture(caseId, specimenId);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private record Fixture(UUID caseId, UUID specimenId) { }
}
