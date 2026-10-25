-- Run manually against the Mission Service schema before production startup.
-- PostGIS must already be installed in this database; this script does not enable extensions.
-- These statements are additive and preserve existing missions and identifiers.

ALTER TABLE missions
    ADD COLUMN IF NOT EXISTS geometry geometry(Polygon, 4326);

ALTER TABLE missions
    ADD COLUMN IF NOT EXISTS route geometry(LineString, 4326);

CREATE TABLE IF NOT EXISTS restricted_zones (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    geometry geometry(Polygon, 4326) NOT NULL,
    demonstration BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_restricted_zones_geometry_gist
    ON restricted_zones USING GIST (geometry);

CREATE TABLE IF NOT EXISTS airspace_features (
    id UUID PRIMARY KEY,
    source_id TEXT NOT NULL,
    source_name TEXT NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    dataset_name TEXT NOT NULL,
    name TEXT,
    category VARCHAR(32) NOT NULL,
    classification TEXT,
    geometry geometry(Geometry, 4326),
    source_properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_text TEXT,
    geometry_status VARCHAR(40) NOT NULL,
    source_effective_at TIMESTAMPTZ,
    imported_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_attempt_id UUID,
    source_url TEXT,
    UNIQUE (source_name, dataset_name, source_id)
);
CREATE INDEX IF NOT EXISTS idx_airspace_features_geometry_gist
    ON airspace_features USING GIST (geometry);
CREATE INDEX IF NOT EXISTS idx_airspace_features_category_country
    ON airspace_features (category, country_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_airspace_source_identity
    ON airspace_features (source_name, dataset_name, source_id);
ALTER TABLE airspace_features ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE airspace_features ADD COLUMN IF NOT EXISTS last_seen_attempt_id UUID;
ALTER TABLE airspace_features ALTER COLUMN geometry_status TYPE VARCHAR(40);
UPDATE airspace_features SET imported_at = COALESCE(last_attempt_at, CURRENT_TIMESTAMP) WHERE imported_at IS NULL;
ALTER TABLE airspace_features ALTER COLUMN imported_at SET NOT NULL;
ALTER TABLE airspace_features ALTER COLUMN last_attempt_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS airspace_dataset_imports (
    country_code VARCHAR(3) NOT NULL,
    source_name TEXT NOT NULL,
    dataset_name TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    input_count INTEGER NOT NULL,
    spatial_count INTEGER NOT NULL,
    unconverted_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    duplicate_count INTEGER NOT NULL,
    imported_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_id UUID,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    non_spatial_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    source_url TEXT,
    source_effective_at TIMESTAMPTZ,
    coverage_verified BOOLEAN NOT NULL DEFAULT FALSE,
    limitations TEXT NOT NULL,
    PRIMARY KEY (country_code, source_name, dataset_name)
);

ALTER TABLE airspace_dataset_imports ALTER COLUMN imported_at DROP NOT NULL;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS attempt_id UUID;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS inserted_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS updated_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS skipped_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS non_spatial_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS failed_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE airspace_dataset_imports ADD COLUMN IF NOT EXISTS source_url TEXT;

CREATE TABLE IF NOT EXISTS airspace_import_batches (
    attempt_id UUID NOT NULL,
    batch_number BIGINT NOT NULL,
    inserted_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    spatial_count INTEGER NOT NULL,
    non_spatial_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    source_fingerprint TEXT NOT NULL,
    record_count INTEGER NOT NULL,
    PRIMARY KEY (attempt_id, batch_number)
);

ALTER TABLE airspace_import_batches ADD COLUMN IF NOT EXISTS source_fingerprint TEXT;
ALTER TABLE airspace_import_batches ADD COLUMN IF NOT EXISTS record_count INTEGER;
