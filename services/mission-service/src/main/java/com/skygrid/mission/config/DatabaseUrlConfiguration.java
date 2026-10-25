package com.skygrid.mission.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class DatabaseUrlConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUrlConfiguration.class);

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        NeonDatabaseUrl parsed;
        try {
            parsed = NeonDatabaseUrl.parse(databaseUrl);
        } catch (NeonDatabaseUrl.InvalidDatabaseUrlException exception) {
            DatabaseUrlDiagnostics.Summary diagnostics = DatabaseUrlDiagnostics.inspect(databaseUrl);
            logger.error("DATABASE_URL parse failed: present={}, length={}, firstCodePoint={}, "
                            + "containsSchemeDelimiter={}, scheme={}, condition={}",
                    diagnostics.present(), diagnostics.length(), diagnostics.firstCodePoint(),
                    diagnostics.containsSchemeDelimiter(), diagnostics.detectedScheme(),
                    exception.failedCondition());
            throw exception;
        }
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(parsed.jdbcUrl())
                .username(parsed.username())
                .password(parsed.password())
                .build();
    }
}
