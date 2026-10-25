# Data model

The existing development database is Neon PostgreSQL 18.6. PostGIS is enabled and `ST_SRID`/`ST_Intersects` have been verified against it. The Mission Service owns its tables; no local PostgreSQL or cross-service foreign keys are used. Other tables below remain planned unless marked implemented.

## Mission schema — Mission Service owns

| Table | Planned fields/purpose | Keys and constraints |
|---|---|---|
| `missions` | **Implemented:** `id uuid`, `name varchar(120)`, `description varchar(1000) null`, `created_at timestamptz`, `geometry geometry(Polygon,4326) null`, `route geometry(LineString,4326) null` | PK; polygon and route are separate optional representations and requests cannot supply both. Existing polygon meaning and rows remain compatible. Routes preserve waypoint coordinate order and are validated by the API. |
| `restricted_zones` | **Implemented:** `id uuid`, `name varchar(120)`, `geometry geometry(Polygon,4326)`, `demonstration boolean` | PK; PostGIS polygon; GiST index `idx_restricted_zones_geometry_gist`. No zone rows are seeded automatically. |
| `risk_assessments` | `id uuid`, `mission_id uuid`, `score smallint`, `level varchar`, `assessed_at timestamptz`, `trigger varchar`, `source_event_id uuid null`, `rule_version varchar` | PK, FK mission; score 0–100; constrained level; `(mission_id, assessed_at desc)` index; append-only. |
| `risk_factors` | `id uuid`, `assessment_id uuid`, `code`, `label`, `description`, `contribution`, `evidence jsonb` | PK, FK assessment; defined contribution policy; assessment index. |
| `processed_events` | `event_id uuid`, `schema_version integer`, `processed_at timestamptz`, `outcome varchar` | Unique event identity; successful effects and marker commit atomically. |
| `hazard_projection` | `hazard_id uuid`, `type`, `severity`, `location geometry(Point,4326)`, `description`, `reported_at timestamptz`, `source_event_id uuid` | Mission-owned event projection; no Hazard FK; GiST point index; defined upsert/dedupe. |
| `restricted_zones` | `id uuid`, `name`, `zone_type`, `geometry geometry(Polygon,4326)`, `demo boolean`, `source_label` | Mission-owned fixtures; GiST; valid geometry; explicit demo label. |

## Hazard schema — Hazard Service owns

The implemented weather-assessment endpoint is stateless: it retrieves forecast values on demand and returns them in the HTTP response. No weather request, provider response, warning, or forecast cache is persisted. The tables below are still planned for future hazard-reporting work.

| Table | Planned fields/purpose | Keys and constraints |
|---|---|---|
| `hazards` | `id uuid`, `type`, `severity`, `location geometry(Point,4326)`, `description`, `status`, `reported_at`, `updated_at timestamptz` | PK; bounds and enum constraints; GiST; demo source indication. |
| `attachments` | `id uuid`, `hazard_id uuid`, `object_key`, `content_type`, `size_bytes bigint`, `created_at timestamptz` | PK, FK hazard; unique object key; allowed type and positive bounded size; metadata only. |
| `outbox_events` (optional) | `id uuid`, `aggregate_id uuid`, `event_type`, `schema_version integer`, `payload jsonb`, `created_at`, `published_at timestamptz`, `attempts integer` | PK; pending index; atomically inserted with hazard, bounded retries. |

## Spatial/migration rules

API uses two-dimensional WGS84 EPSG:4326 and longitude-latitude order. The Mission API checks polygon bounds, ring closure, topology, validity, and a maximum of 10,000 coordinate positions; LineString routes require at least two valid ordered points, no identical consecutive points, and the same bounds. Local/development Hibernate `update` adds nullable mission geometry/route columns and creates the restricted-zone table without dropping or recreating existing tables; the startup initializer creates the GiST zone index idempotently. Production remains Hibernate `validate`: apply `services/mission-service/db/production-spatial-schema.sql` manually after PostGIS is enabled and before deployment. The script is additive and does not run `CREATE EXTENSION`.
