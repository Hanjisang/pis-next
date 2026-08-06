package com.hanjisang.pis;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
class FlywayConfiguration {

    @Bean(initMethod = "migrate")
    Flyway pisFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("pis")
                .defaultSchema("pis")
                .baselineOnMigrate(true)
                .load();
    }
}
