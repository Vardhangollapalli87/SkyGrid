# Project memory

## Identity and scope

SkyGrid is a five-day portfolio MVP: geospatial, event-driven drone mission risk decision support. Operators plan an area; Mission Service evaluates demonstration restrictions and reported hazards. Hazard Service publishes report events; Mission Service reassesses affected missions and appends immutable assessment history. This is not aviation authorization, flight-safety certification, or authoritative clearance. LOW risk does not establish safety or legality.

Core scope: mission polygon creation and deterministic assessment; hazard create/retrieve; Kafka reassessment with idempotency/history; map dashboard and hazard reporting. AWS S3 private evidence is optional. Exclude actual drone control, live telemetry, official datasets, paid map APIs, AI/ML risk prediction, billing, extra services, Redis, Kubernetes, and API gateway.

## Approved development decisions

- Develop in VS Code with Codex. Use Java 25 locally, subject to project build/runtime compatibility verification. Pin Spring Boot 3.5.16: official system requirements list compatibility through Java 25. Do not use the previous 3.4.4 candidate, whose 3.4 line only supports through Java 24.
- Develop the React/TypeScript/Vite frontend locally.
- Do not install/configure local PostgreSQL, PostGIS, Kafka, Docker, or Compose. Do not change Windows system settings. Docker begins only in deployment preparation.
- The existing local development connection is to Neon PostgreSQL 18.6 with PostGIS enabled and verified. Kafka, S3, and production hosting are not provisioned or verified; the zero-cost provider proposal remains provisional for deployment.
- Deploy frontend to Vercel; deploy both Spring services to an appropriate container host (not selected). Validate Java 25 base image/runtime, port/health behavior, network/security configuration, cost and limits.

## Provisional zero-cost research decision (2026-10-25)

- Proposed only for production, pending owner approval: Neon Free PostgreSQL (PG17 candidate) with PostGIS; Aiven Kafka Free; one OCI Always Free Ampere A1 VM to host two separate Spring services; Vercel Hobby static frontend; defer S3 evidence uploads. The existing development DB is a separate Neon PostgreSQL 18.6 project with PostGIS verified. Neon Free does not provide IP Allow/private networking; its endpoint is public-reachable over TLS and relies on strong credentials and least-privilege roles; owner must accept this for synthetic demo data, or choose another plan/provider and revise cost.
- Estimated recurring base: $0 / ₹0/month within published free quotas and Hobby personal non-commercial eligibility. Planning conversion assumed ₹95/USD. No claim that the whole deployment is guaranteed free or continuously available.
- Neon Free research figures remain in `docs/CLOUD_PROVIDER_COMPARISON.md`; the existing development database is PostgreSQL 18.6. Its PostGIS extension and SRID/intersection operations have now been verified directly.
- Aiven Kafka Free: 250 KiB/s in and out, 5 topics with 2 partitions each, up to 3-day retention; TLS, standard Kafka clients, no SLA; idle/first-use poweroff. Exact free-tier authentication and Spring Kafka interoperability require smoke tests.
- OCI A1 Always Free: current quota is 2 OCPU/12 GB, 1,500 OCPU-hours/9,000 GB-hours per month, 10 TB outbound and block volume limits. One host can fit two small JVMs on paper; shared-host blast radius, ARM64 Java25, idle reclamation and region capacity are unresolved. OCI signup usually requires card verification and may place a temporary hold.
- Vercel Hobby allows personal/non-commercial static Vite hosting within limits; re-evaluate if commercial. Render's free 750 shared workspace hours and 15-minute idle sleep cannot keep two services continuously active. Koyeb has only one free web service/org.
- No cross-provider private link assumed. Candidate uses TLS public endpoints; Neon Free lacks IP Allow, and Aiven Free IP filter availability remains to verify. Confirm owner accepts public-reachable DB, region overlap, connectivity and egress fees. Do not add S3 because free allowance is account/time dependent and can incur storage/request/transfer cost.
- Full evidence, current provider source links, fallback monthly costs and explicit owner decision are in `docs/CLOUD_PROVIDER_COMPARISON.md`.

## Architecture decisions

- Exactly two deployable Spring services and service-owned `mission` / `hazard` schemas. No shared tables or cross-service FKs. Mission maintains an event-fed hazard projection for spatial queries.
- Risk engine is internal to Mission. The current mission table uses nullable PostGIS `geometry(Polygon,4326)`; restricted zones have PostGIS polygons and a GiST index. Use additive local Hibernate `update` and the checked-in manual production SQL; do not introduce Flyway in this phase. GeoJSON longitude/latitude; UUID IDs and UTC timestamps.
- Kafka `hazard-events`; stable event ID, schema version, hazard UUID key, validation, bounded retry, explicit failure path, processed-event dedupe. No exactly-once claim. Prefer transactional outbox; otherwise document publish-loss window and recovery.
- Frontend uses Leaflet and does not calculate risk. UI communicates demo provenance and non-authoritative results.
- Auth mechanism remains a pre-implementation decision. Use private S3, scoped short-lived presigned uploads if implemented, and host workload identity rather than static AWS keys.

## Environment and verification state

The map-first React/TypeScript/Vite/Leaflet mission workspace is implemented. It uses the existing Mission API for paginated list/detail/create/spatial checks, validates and edits GeoJSON polygons in the browser, and does not invent mission or restriction data. Frontend TypeScript/build pass; 4 focused geometry/API tests pass. The local Mission Service was not listening during frontend verification, so API/browser end-to-end and visual viewport checks remain pending. See `docs/HANDOFF.md`.

Workspace initially empty and not a Git repository; Git was not initialized. Java/Javac 25, Maven 3.9.12, Node 22.19.0/npm 10.9.3, Git 2.51.0, Docker CLI 29.2.0/Compose 5.0.2 were present. No tools installed and no system settings changed. Local Compose draft was removed per cloud-first decision. Prior Compose syntax validation applied only to the now-removed local draft.

Spring Boot 3.5.16 with Java 25 has compiled and passed the Mission Service tests/package build. The local profile connected to Neon PostgreSQL 18.6; PostGIS extension and spatial predicates are verified. The additive schema update added a nullable mission geometry column, the restricted-zone table, and a GiST index; existing mission rows were readable afterward. Kafka access, S3, Vercel, container-host compatibility, prices, and production hosting remain unverified/unselected.

Cloud provider terms/prices were researched against official sources on 2026-10-25. This geospatial phase did not create accounts, change billing, provision resources, or alter database credentials. The existing Neon development database is in use; production provider selection remains provisional until owner approval.

`infra/.env.example` lists variable names only. Populate secrets only in the selected cloud host's secret management, never in Git or Vite `VITE_*` variables. Follow `AGENTS.md`, `ARCHITECTURE.md`, `API_CONTRACT.md`, `DATA_MODEL.md`, and `CODING_GUIDELINES.md`.
