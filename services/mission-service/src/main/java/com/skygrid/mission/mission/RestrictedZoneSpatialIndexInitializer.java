package com.skygrid.mission.mission;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "development"})
class RestrictedZoneSpatialIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    RestrictedZoneSpatialIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                create index if not exists idx_restricted_zones_geometry_gist
                on restricted_zones using gist (geometry)
                """);
    }
}
