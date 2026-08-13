package com.hanjisang.pis.v2.production;

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
class RoutineProductionPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_fc03a_concurrency")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateFreshDatabase() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration").load().migrate();
    }

    @Test
    void postgresProtectsRequiredGenerationSlideCodeAndOptimisticCompletion() throws Exception {
        Fixture fixture = seedFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> attempts = List.of(
                    () -> insertSameRequiredSlide(fixture, ready, start),
                    () -> insertSameRequiredSlide(fixture, ready, start));
            var futures = attempts.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }

        JdbcTemplate jdbc = jdbc();
        UUID slideId = jdbc.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", UUID.class,
                fixture.blockId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE block_id = ?", Integer.class,
                fixture.blockId())).isEqualTo(1);

        CountDownLatch completionReady = new CountDownLatch(2);
        CountDownLatch completionStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                    executor.submit(() -> completeSameSlide(slideId, completionReady, completionStart, "tech-a")),
                    executor.submit(() -> completeSameSlide(slideId, completionReady, completionStart, "tech-b")));
            completionReady.await();
            completionStart.countDown();
            int changed = futures.get(0).get() + futures.get(1).get();
            assertThat(changed).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT concurrency_version FROM pis_v2.slide WHERE id = ?", Long.class,
                slideId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM pis_v2.slide WHERE id = ?",
                Boolean.class, slideId)).isTrue();
    }

    private boolean insertSameRequiredSlide(Fixture fixture, CountDownLatch ready, CountDownLatch start) {
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
                    VALUES (?, ?, ?, ?, 'A1-HE', 'HE', 'INITIAL', ?, 'ROUTINE-HE', 1, TRUE, 0,
                            'LOCAL_HOSPITAL', ?, 'concurrency-test', ?, 'concurrency-test')
                    """)) {
                OffsetDateTime now = OffsetDateTime.now();
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, fixture.caseId());
                statement.setObject(3, fixture.blockId());
                statement.setObject(4, fixture.specimenId());
                statement.setObject(5, fixture.grossingId());
                statement.setObject(6, now);
                statement.setObject(7, now);
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

    private int completeSameSlide(UUID slideId, CountDownLatch ready, CountDownLatch start, String operator)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE pis_v2.slide SET completed_at = ?, completed_by_ref = ?, concurrency_version = 1,
                        updated_at = ?, updated_by_ref = ?
                    WHERE id = ? AND concurrency_version = 0 AND completed_at IS NULL
                    """)) {
                OffsetDateTime now = OffsetDateTime.now();
                statement.setObject(1, now);
                statement.setString(2, operator);
                statement.setObject(3, now);
                statement.setString(4, operator);
                statement.setObject(5, slideId);
                int changed = statement.executeUpdate();
                connection.commit();
                return changed;
            }
        }
    }

    private Fixture seedFixture() {
        JdbcTemplate jdbc = jdbc();
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID grossingId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH', ?, 'SYNTH-HISTOLOGY', ?, 'ACTIVE', TRUE, 0,
                        'LOCAL_HOSPITAL', ?, 'concurrency-test')
                """, caseId, "P-FC03A-" + caseId, "APP-FC03A-" + caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_name, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, 'A', '合成部位', 'TISSUE', 'APPLICATION', ?, '合成部位', 'SURGERY', ?, 0,
                        'LOCAL_HOSPITAL', ?, 'concurrency-test', ?, 'concurrency-test')
                """, specimenId, caseId, "SP-" + specimenId, "APP-" + caseId, "LBL-" + specimenId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.grossing
                    (id, case_id, grossing_no, source_type, gross_description, grossing_doctor_id, recorder_id,
                     started_at, completed_at, completed_by_ref, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, 'INITIAL', '合成取材', 'doctor', 'recorder', ?, ?, 'doctor', 1,
                        'LOCAL_HOSPITAL', ?, 'concurrency-test', ?, 'concurrency-test')
                """, grossingId, caseId, "G-" + grossingId, now, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.block
                    (id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'A1', 'ROUTINE', FALSE, 0, 'LOCAL_HOSPITAL',
                        ?, 'concurrency-test', ?, 'concurrency-test')
                """, blockId, caseId, grossingId, specimenId, now, now);
        return new Fixture(caseId, specimenId, grossingId, blockId);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private record Fixture(UUID caseId, UUID specimenId, UUID grossingId, UUID blockId) { }
}
