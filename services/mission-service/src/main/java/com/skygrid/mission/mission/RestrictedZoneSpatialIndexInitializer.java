package com.skygrid.mission.mission;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "development"})
@Order(Ordered.HIGHEST_PRECEDENCE)
class RestrictedZoneSpatialIndexInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    RestrictedZoneSpatialIndexInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        jdbc.execute("create index if not exists idx_restricted_zones_geometry_gist on restricted_zones using gist (geometry)");
        jdbc.execute("create index if not exists idx_airspace_features_geometry_gist on airspace_features using gist (geometry)");
        jdbc.execute("create unique index if not exists uq_airspace_source_identity on airspace_features(source_name,dataset_name,source_id)");
        jdbc.execute("alter table missions add column if not exists route geometry(LineString,4326)");
        jdbc.execute("alter table airspace_features add column if not exists last_attempt_at timestamptz not null default current_timestamp");
        jdbc.execute("alter table airspace_features add column if not exists last_seen_attempt_id uuid");
        jdbc.execute("alter table airspace_features alter column geometry_status type varchar(40)");
        jdbc.execute("""
                create table if not exists airspace_dataset_imports (
                  country_code varchar(3) not null, source_name text not null, dataset_name text not null,
                  category varchar(32) not null, status varchar(16) not null, input_count integer not null,
                  spatial_count integer not null, unconverted_count integer not null, rejected_count integer not null,
                  duplicate_count integer not null, inserted_count integer not null default 0,
                  updated_count integer not null default 0, skipped_count integer not null default 0,
                  non_spatial_count integer not null default 0, failed_count integer not null default 0,
                  imported_at timestamptz, last_attempt_at timestamptz not null, attempt_id uuid,
                  source_url text, source_effective_at timestamptz,
                  coverage_verified boolean not null default false, limitations text not null,
                  primary key(country_code,source_name,dataset_name))
                """);
        jdbc.execute("alter table airspace_dataset_imports alter column imported_at drop not null");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists attempt_id uuid");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists inserted_count integer not null default 0");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists updated_count integer not null default 0");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists skipped_count integer not null default 0");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists non_spatial_count integer not null default 0");
        jdbc.execute("alter table airspace_dataset_imports add column if not exists failed_count integer not null default 0");
        jdbc.execute("""
                create table if not exists airspace_import_batches (
                  attempt_id uuid not null, batch_number bigint not null,
                  inserted_count integer not null, updated_count integer not null, skipped_count integer not null,
                  spatial_count integer not null, non_spatial_count integer not null,
                  rejected_count integer not null, failed_count integer not null,
                  source_fingerprint text not null, record_count integer not null,
                  primary key(attempt_id,batch_number))
                """);
        jdbc.execute("alter table airspace_import_batches add column if not exists source_fingerprint text");
        jdbc.execute("alter table airspace_import_batches add column if not exists record_count integer");
    }
}
