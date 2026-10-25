# Coding guidelines

## General

- Stay within the phase explicitly approved by the owner. Mission CRUD and the PostGIS geospatial foundation are implemented; later product features remain out of scope until approved.
- Use UUID identifiers, UTC `Instant`/`timestamptz`, GeoJSON longitude/latitude.
- Label demo data and never call LOW risk safe or authorized.

## Java

- Java 25 local target; pin Spring Boot 3.5.16 candidate (officially supports Java 25) and Maven Wrapper per service. Verify project build and runtime, including dependencies, before implementation is considered ready.
- Keep API DTOs separate from persistence entities. Use Jakarta Validation and explicit 2D GeoJSON Polygon validation: longitude first, latitude second, WGS84/SRID 4326, closed rings, valid topology, and coordinate bounds.
- JPA for ordinary persistence; parameterized native PostGIS SQL for spatial work. Never concatenate user input into SQL.
- Prefer constructor injection, explicit transaction boundaries and append-only assessment snapshots.
- Use safe structured errors; no stack traces, SQL, tokens or internal addresses.
- Protect writes with agreed demo auth; environment-configured CORS and minimal Actuator.
- Kafka events are versioned/validated; stable event ID, hazard UUID key, bounded retry, failure routing, atomic dedupe/effects.
- Connect only to selected managed PostgreSQL/PostGIS and Kafka over verified TLS/network paths. Never add local database/broker setup.

## React/TypeScript

- Strict TypeScript. Keep API types and requests in dedicated directories and use one typed API client.
- Focused components, controlled forms, accessible labels, validation feedback, and loading/empty/success/error states.
- Risk logic stays backend-side. Do not hardcode API responses in presentation components.
- Keep map usable on narrow screens and identify all demo data/risk limitations.

## Security and checks

- No secrets in source or committed env files. Sample env contains placeholders only.
- Store cloud credentials in the selected host's secret manager. Prefer AWS workload identity/role; no static AWS credentials in frontend. All `VITE_*` variables are public build-time values.
- Verify provider PostGIS availability, Kafka auth/protocol compatibility, connectivity, pricing/egress, and Java 25 container-host support before provisioning.
- S3 private; presigned URLs short-lived/scoped and MIME/size constrained. No AWS browser credentials.
- Add phase-relevant JUnit/Mockito and PostGIS/Kafka integration tests as practical. Report exact commands and real outcomes only.
