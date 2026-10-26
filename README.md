# SkyGrid

SkyGrid is a decision-support prototype for assessing proposed drone mission areas against demonstration restricted zones and reported hazards. It is not an aviation authorisation, flight-safety certification, or source of authoritative airspace clearance. A low score does not mean a flight is safe or legally authorised.

## Project status

The Mission Service supports mission CRUD, validated GeoJSON Polygon and LineString route storage in PostGIS, multiple route associations per mission area, full-route containment checks, and polygon restricted-zone intersection checks. The separate Hazard Service provides on-demand hourly weather assessments for saved mission polygons and routes using Open-Meteo. The React/Vite frontend provides a map-first workspace for planning, saving, exporting, and reviewing area-associated routes. Weather and containment are decision support only, never aviation clearance or a safety determination. Risk scoring, hazard reporting, Kafka, authentication, and deployment remain unimplemented. The zone-check API cannot establish whether its restricted-zone dataset is complete or populated; the frontend reports that limitation and never describes a mission as safe or cleared. See [docs/HANDOFF.md](docs/HANDOFF.md).

## Approved development workflow

Mission areas can now have multiple explicitly associated saved routes. The backend evaluates the complete route LineString against the area with PostGIS; contained geometry is not flight clearance. The additive migration and manual rollout are documented in `services/mission-service/README.md`.

The Mission Service also supports multiple explicitly associated LineString routes per mission area and a backend PostGIS containment check for the complete route. The additive database prerequisite and boundary-inclusive check semantics are documented in `API_CONTRACT.md` and `services/mission-service/README.md`. This geometry review is not flight clearance.

- Use VS Code and Codex. Develop the React frontend locally with Vite.
- Use Java 25 and Spring Boot 3.5.16 for the Mission Service. Its Maven tests and package build are verified locally.
- Do not install/configure local PostgreSQL, PostGIS, Kafka, Docker, or Compose. Do not change Windows system settings for this project.
- The existing local profile connects to Neon PostgreSQL 18.6 with PostGIS enabled and spatial queries verified. Kafka and deployment providers remain subject to the documented provider decision and verification requirements.
- The Mission Service includes an opt-in, bounded-batch, repeat-safe snapshot importer and viewport-based airspace API for the supplied FAA national-security GeoJSON, Netherlands ED-269 polygons, and retained India AIP ENR 5.1/5.2 source documents. Import execution against Neon remains unverified after an intermittent connection reset; coverage remains partial and unverified. See `docs/AIRSPACE_DATA_IMPORT.md`.
- Use AWS S3 for private evidence attachments. Use workload identity/role on the service host where available; never put AWS credentials in the browser or source.
- Introduce Docker only during deployment preparation. Provisional host research recommends one OCI Always Free A1 VM for both separate Spring services and Vercel Hobby for the frontend. This is not approved or provisioned; verify region, Java 25/ARM64 compatibility, connectivity, limits, and pricing first.

## Planned stack and architecture

- Java 25, Spring Boot 3.5.16 candidate, Maven Wrapper, Spring Web, Data JPA, Bean Validation, Spring Kafka, Spring Security, Actuator, Springdoc OpenAPI, Flyway, JUnit 5 and Mockito.
- Managed PostgreSQL/PostGIS with service-owned `mission` and `hazard` schemas; managed Kafka topic `hazard-events`.
- AWS S3 private evidence objects and short-lived presigned uploads (optional after core workflow).
- React, TypeScript, Vite, React Router, Leaflet and React Leaflet.
- Exactly two business services: Mission Service and Hazard Service. Mission owns its risk engine and event-fed hazard projection; Hazard owns reported hazards and attachment metadata.

All restrictions and hazards are demonstration data. No official aviation data, live telemetry, paid map API, or AI service is required or implied. Risk output is not flight authorization or a safety guarantee.

## Planned layout

```text
services/mission-service/  Mission API, risk engine, event consumer
services/hazard-service/   Weather forecast API; future hazard-report/event producer
frontend/                  React/Vite application
infra/                     Cloud environment variable reference
docs/                      Design and handoff documentation
```

## Cloud configuration

`infra/.env.example` lists variable names only and contains no provider identifiers or credentials. Obtain database endpoint/name/user, TLS requirements, Kafka bootstrap/security settings, and service/frontend origins from selected providers. S3 region/bucket apply only if attachments are later approved. Put secrets in the hosting provider's secret manager/runtime configuration, not in Git or Vite variables. See [ARCHITECTURE.md](ARCHITECTURE.md) and [CODING_GUIDELINES.md](CODING_GUIDELINES.md).

## Provisional zero-cost provider research

Research checked 2026-10-25 conditionally recommends Neon Free (PG17 deployment candidate), Aiven Kafka Free, a single OCI Always Free Ampere A1 VM for the two separate backend services, Vercel Hobby for the static frontend, and deferring S3 evidence. The existing development database is Neon PostgreSQL 18.6 with PostGIS verified; this does not approve the separate deployment stack. Estimated recurring base for the researched candidate is $0 / ₹0 while all free quotas and personal-use terms are met. This is not guaranteed free or continuously available: OCI may reclaim idle compute, Aiven may power Kafka off when idle and has no SLA, and cross-cloud paths use public TLS. Neon Free has no IP Allow/private networking, so its database endpoint is publicly reachable and relies on TLS and credential controls; owner must explicitly accept this for synthetic demo data before provisioning. OCI signup usually requires payment-card verification. Provider/region selection and provisioning require owner approval. See [docs/CLOUD_PROVIDER_COMPARISON.md](docs/CLOUD_PROVIDER_COMPARISON.md) for limits, alternatives, fallback estimates, sources and blockers.

## Frontend setup

The map workspace uses React, TypeScript, Vite, Leaflet and React Leaflet. Node.js 22+ and npm are expected. From `frontend/`:

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

Leave `VITE_API_BASE_URL` and `VITE_HAZARD_API_BASE_URL` empty for local development. Vite proxies Mission `/api` requests to port 8081 and `/hazard-api/api` requests to Hazard Service port 8082. For deployment, set the two variables to the corresponding public service origins; configure each service's CORS for the exact frontend origin. Set `VITE_MAP_TILE_URL` only to a tile service whose usage policy and traffic allowance fit the demo; the default OpenStreetMap endpoint is subject to its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/), including attribution, caching, no bulk prefetch, and best-effort availability. If you replace that URL, set matching `VITE_MAP_TILE_ATTRIBUTION`. The alternate Carto light layer is also public and subject to [CARTO Basemaps terms](https://carto.com/legal/basemap-terms/). These URLs are public configuration, not credentials; neither provider is represented as unlimited or guaranteed free for production traffic.

Run frontend checks from `frontend/` with `npm test`, `npm run lint`, and `npm run build`. The browser never receives database, Kafka, or cloud credentials. Local environment files and generated assets are ignored by `frontend/.gitignore`.

## Application status

The Mission Service and frontend include backward-compatible Polygon missions plus separately stored LineString waypoint routes with route editing and export. The Hazard Service weather endpoint is stateless and fetches forecasts on demand; it does not persist weather, perform risk scoring, or report aviation weather. The route planner does not estimate duration, battery, altitude, speed, obstacle avoidance, or clearance. The restricted-zone API cannot distinguish an empty zone dataset from a completed negative check, so the frontend explicitly labels negative results as “No intersection in returned dataset” and says dataset completeness is unknown. Hazard reporting, risk assessments/history, Kafka, and deployment URLs do not exist yet.
