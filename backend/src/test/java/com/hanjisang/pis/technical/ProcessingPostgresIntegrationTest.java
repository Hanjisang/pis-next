package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProcessingPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis")
            .withUsername("pis")
            .withPassword("synthetic-p17");

    @Test
    void postgresMigrationsCreateP17TraceTablesAndConstraints() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        assertThat(jdbc.queryForObject("SELECT foundation_version FROM pis.foundation_schema_metadata WHERE schema_code = 'PIS_NEXT'",
                String.class)).isEqualTo("P18");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'pis' AND table_name LIKE 'p17_%'",
                Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.p17_processing_program WHERE program_code = 'P17-SYNTHETIC-REFERENCE'",
                Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pis.p17_processing_program
                (id, program_code, display_name, environment_code, lifecycle_state_code, created_at, created_by_ref)
                VALUES (?, 'P17-SYNTHETIC-REFERENCE', 'duplicate', 'SYNTHETIC', 'ACTIVE', CURRENT_TIMESTAMP, 'test')
                """, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }
}
