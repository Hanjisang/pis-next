package com.hanjisang.pis.v2.frozen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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

/** PostgreSQL constraints used by the Frozen round and Frozen End commands. */
@Testcontainers
class FrozenPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_fc03c_frozen_concurrency")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @BeforeAll
    static void migrateFreshDatabase() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration").load().migrate();
    }

    @Test
    void concurrentRoundCreationCannotDuplicateRoundNumber() throws Exception {
        UUID frozenCaseId = seedFrozenCase();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                    executor.submit(() -> insertRound(frozenCaseId, ready, start, "round-a")),
                    executor.submit(() -> insertRound(frozenCaseId, ready, start, "round-b")));
            ready.await();
            start.countDown();
            int successes = futures.get(0).get() + futures.get(1).get();
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM pis_v2.frozen_round WHERE case_id = ? AND round_no = 1",
                Integer.class, frozenCaseId)).isEqualTo(1);
    }

    @Test
    void concurrentFrozenEndRelationsAllowOnlyOneRoutineCasePerFrozenCase() throws Exception {
        UUID frozenCaseId = seedFrozenCase();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                    executor.submit(() -> insertRoutineCase(frozenCaseId, ready, start, "routine-a")),
                    executor.submit(() -> insertRoutineCase(frozenCaseId, ready, start, "routine-b")));
            ready.await();
            start.countDown();
            int successes = futures.get(0).get() + futures.get(1).get();
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_case WHERE frozen_source_case_id = ?",
                Integer.class, frozenCaseId)).isEqualTo(1);
    }

    private int insertRound(UUID caseId, CountDownLatch ready, CountDownLatch start, String actor) {
        OffsetDateTime now = OffsetDateTime.now();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pis_v2.frozen_round
                        (id, case_id, round_no, status_code, arrival_time, registered_at, concurrency_version,
                         organization_reference, created_at, created_by_ref)
                    VALUES (?, ?, 1, 'OPEN', ?, ?, 0, 'LOCAL_HOSPITAL', ?, ?)
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, caseId);
                statement.setObject(3, now);
                statement.setObject(4, now);
                statement.setObject(5, now);
                statement.setString(6, actor);
                statement.executeUpdate();
                connection.commit();
                return 1;
            } catch (Exception conflict) {
                connection.rollback();
                return 0;
            }
        } catch (Exception failure) {
            return 0;
        }
    }

    private int insertRoutineCase(UUID frozenCaseId, CountDownLatch ready, CountDownLatch start, String suffix)
            throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID routineId = UUID.randomUUID();
        UUID histologyType = jdbc().queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pis_v2.pathology_case
                            (id, case_no, source_system_code, external_application_id, application_item_code,
                             business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                             frozen_source_case_id, organization_reference, created_at, created_by_ref)
                        VALUES (?, ?, 'V2-FROZEN-END', ?, 'SYNTH-HISTOLOGY', ?, 'ACTIVE', TRUE, 0, ?,
                                'LOCAL_HOSPITAL', ?, ?)
                        """)) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            statement.setObject(1, routineId);
            statement.setString(2, "P-FC03C-" + suffix + "-" + routineId);
            statement.setString(3, "FROZEN:" + frozenCaseId + ":" + suffix);
            statement.setObject(4, histologyType);
            statement.setObject(5, frozenCaseId);
            statement.setObject(6, now);
            statement.setString(7, suffix);
            statement.executeUpdate();
            connection.commit();
            return 1;
        } catch (Exception conflict) {
            return 0;
        }
    }

    private UUID seedFrozenCase() {
        JdbcTemplate jdbc = jdbc();
        UUID caseId = UUID.randomUUID();
        UUID frozenType = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'FROZEN'", UUID.class);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH', ?, 'SYNTH-FROZEN', ?, 'ACTIVE', TRUE, 0, 'LOCAL_HOSPITAL', ?, 'test')
                """, caseId, "F-FC03C-" + caseId, "APP-FC03C-" + caseId, frozenType, now);
        return caseId;
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
