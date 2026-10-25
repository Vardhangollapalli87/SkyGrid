# Repository instructions

## Scope and workflow

Read `docs/HANDOFF.md`, `ARCHITECTURE.md`, `API_CONTRACT.md`, and `docs/UI_DESIGN.md` before implementation. Implement only the phase and scope the owner has explicitly authorized. Mission Service CRUD, the PostGIS geospatial foundation, and the mission workspace frontend are authorized and implemented; future business features still require authorization.

Use VS Code and Codex. Java 25 is approved for local Spring Boot development, subject to build/runtime verification. Develop React with Vite locally. Do not install/configure local PostgreSQL, PostGIS, Kafka, Docker, or Docker Compose; do not change Windows system settings. Docker is introduced only during deployment preparation.

## Product and architecture boundaries

- SkyGrid is decision support using demonstration data, not official aviation clearance, safety certification, or a safety guarantee. LOW risk does not establish safety or legality.
- Keep exactly two deployable business services: Mission Service and Hazard Service. The risk engine remains in Mission Service.
- Mission owns missions, immutable assessment snapshots, risk factors, processed events, restrictions, and its event-fed hazard projection. Hazard owns hazard reports and attachment metadata. No cross-service table access or foreign keys.
- The existing development database is Neon PostgreSQL 18.6 with PostGIS verified. Production PostgreSQL/Kafka provider selection remains provisional until capabilities, connectivity, pricing, and hosting compatibility are reviewed.
- Kafka topic `hazard-events`; versioned JSON with stable `eventId`, `schemaVersion`, and hazard UUID key. Validate events, bound retries, route failures explicitly, and deduplicate effects. Never claim exactly-once business processing without evidence.
- Database persistence and Kafka publication are not automatically atomic. Prefer a transactional outbox if feasible; otherwise document and recover the failure window.
- Use GeoJSON longitude/latitude order, UUID identifiers, UTC instants, validated geometry, and deterministic server-side scoring.

## Security and cloud configuration

- Never commit secrets, populated `.env` files, access keys, or tokens. `infra/.env.example` lists variable names only. Keep actual secrets in host-managed secret storage.
- Never expose AWS credentials to the frontend. Prefer host workload identity/role; keep S3 private and use short-lived, scoped presigned uploads with MIME/size constraints.
- Frontend `VITE_*` variables are public build-time values; never put secrets there.
- Require TLS for managed database/Kafka connections where supported; use least-privilege service-specific identities and network allowlists/private networking when available. The provisional Neon Free candidate has a public-reachable DB endpoint and lacks IP Allow/private networking; use TLS identity validation and strong credentials, synthetic data only, and require owner approval of that exposure before provisioning. Do not expose data services publicly without access controls.
- Restrict production CORS to the deployed frontend origin. Validate requests and parameterize SQL. Keep errors free from secrets/internal traces and Actuator exposure minimal.
- Verify provider PostGIS support, Kafka protocol/auth compatibility, pricing, egress, TLS/network access, Vercel origin, and container-host compatibility before selecting/provisioning resources.

## Quality and documentation

- Keep API types/calls separate from frontend presentation. Include loading, empty, success, and error states. Never calculate risk in the frontend.
- Do not add AI, Kubernetes, Redis, another service, API gateway, or unrelated modules.
- Keep features marked planned until implemented and verified. Update architecture/API/data/design docs, `CHANGELOG.md`, and `docs/HANDOFF.md` at each completed phase.
- Report only checks actually executed and observed. Do not provision paid resources, install software, alter system settings, or initialize Git as part of documentation work.
