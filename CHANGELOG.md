# Changelog

## Phase 0 — Foundation (2026-10-25)

- Established product, architecture, API/data contracts, roadmap, coding guidance, UI direction and handoff.
- Initially drafted local PostgreSQL/PostGIS and Kafka Compose configuration; it was removed in the cloud-first workflow update below.
- No application code, dependencies, or system changes.

## Cloud-first workflow decision (2026-10-25)

- Approved VS Code/Codex, local Java 25 and Vite, managed PostgreSQL with verified PostGIS, managed Kafka, private AWS S3, Vercel frontend, and a compatible service container host.
- Removed local Compose file and replaced local infrastructure setup docs with cloud resource, networking, secret, and environment-variable requirements.
- Changed Spring Boot candidate from 3.4.4 to 3.5.16 after official documentation confirmed Java 25 support. No SkyGrid build or cloud connectivity verification performed.
- No providers/resources selected or provisioned; pricing, service capabilities and hosting compatibility remain to be checked.

## Zero-cost provider research (2026-10-25)

- Compared official current free plans for Neon, Aiven Kafka, OCI, Render, Koyeb, Vercel and AWS S3.
- Added `docs/CLOUD_PROVIDER_COMPARISON.md` with a provisional zero-cost stack, INR/USD estimate, limits, network/security implications, known blockers, fallback costs, URLs and research date.
- Provisional recommendation: Neon Free PG17/PostGIS (version to verify), Aiven Kafka Free, one OCI Always Free A1 host for both services, Vercel Hobby, defer S3. Owner approval is required before provisioning.
- No account, billing details, trial, resource, connectivity check, software installation, system change, Git initialization or feature code.

## Mission Service geospatial foundation (2026-10-25)

- Added optional validated GeoJSON Polygon mission geometry backed by Hibernate Spatial/JTS and PostGIS `geometry(Polygon,4326)`; existing no-geometry requests and rows remain compatible.
- Added restricted-zone polygon persistence, local/development GiST index initialization, and `GET /api/v1/missions/{id}/restricted-zone-check` using native PostGIS `ST_Intersects`.
- Added an additive production SQL script while preserving production Hibernate `validate`; no extension creation, Flyway, or Liquibase was added.
- Excluded the ignored local credential properties file from both original and repackaged Maven JARs while preserving local `spring-boot:run` behavior.
- Verified PostGIS and spatial repository/API operations against the existing Neon PostgreSQL 18.6 database. Opt-in integration fixtures are transaction-scoped and rolled back.
- Verification: `mvn --batch-mode -s target/maven-settings.xml test` passed (37 tests, 0 failures/errors, 6 opt-in integration tests skipped); opt-in Neon/PostGIS integration command passed all 6 tests; `mvn --batch-mode -s target/maven-settings.xml package` passed and produced the executable JAR (same 37/0/6 suite result).
# Frontend mission workspace

- Added a responsive React, TypeScript, Vite and Leaflet map workspace for mission listing, selection, polygon planning/editing, validation, persistence and PostGIS check results.
- Added typed API operations with cancellation, timeout and bounded retry for safe reads; creation is never automatically retried.
- Added UI disclosure that the restricted-zone API cannot confirm dataset completeness. No synthetic mission or zone data is bundled.
- Added frontend geometry/API tests and documented local setup in the README.
