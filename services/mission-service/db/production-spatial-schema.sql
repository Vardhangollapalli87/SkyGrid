-- Run manually against the Mission Service schema before production startup.
-- PostGIS must already be installed in this database; this script does not enable extensions.
-- These statements are additive and preserve existing missions and identifiers.

ALTER TABLE missions
    ADD COLUMN IF NOT EXISTS geometry geometry(Polygon, 4326);

CREATE TABLE IF NOT EXISTS restricted_zones (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    geometry geometry(Polygon, 4326) NOT NULL,
    demonstration BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_restricted_zones_geometry_gist
    ON restricted_zones USING GIST (geometry);
