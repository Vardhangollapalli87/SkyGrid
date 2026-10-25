package com.skygrid.mission.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseUrlConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(DatabaseUrlConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=local",
                    "spring.datasource.url=jdbc:postgresql://db.example.invalid:5432/skygrid?sslmode=require",
                    "spring.datasource.username=synthetic_user",
                    "spring.datasource.password=synthetic_password");

    @Test
    void localProfileUsesSpringBootDatasourcePropertiesWithoutNeonUriParser() {
        contextRunner.run(context -> {
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertEquals("jdbc:postgresql://db.example.invalid:5432/skygrid?sslmode=require",
                    dataSource.getJdbcUrl());
            assertEquals("synthetic_user", dataSource.getUsername());
            assertEquals("synthetic_password", dataSource.getPassword());
        });
    }
}
