# Render and Vercel deployment preparation

Status checked 2026-10-26: deployment files and backend build instructions are prepared below. No Render/Vercel resources have been created and no service has been deployed. This document does not authorize deployment or a database change.

## Render topology

Use three Render services in the same Render workspace and region:

| Suggested service name | Render type | Root directory | Dockerfile path | Docker context | Public? | Health check |
|---|---|---|---|---|---|---|
| `skygrid-api-gateway` | Web Service, Docker runtime | `services/api-gateway` | `./Dockerfile` | `.` | Yes, the only public backend | HTTP `GET /actuator/health` |
| `skygrid-mission-service` | Private Service, Docker runtime | `services/mission-service` | `./Dockerfile` | `.` | No | Render TCP port check; private services do not support HTTP health-check paths |
| `skygrid-hazard-service` | Private Service, Docker runtime | `services/hazard-service` | `./Dockerfile` | `.` | No | Render TCP port check; private services do not support HTTP health-check paths |

The names above are suggested dashboard labels, not existing resource IDs or hostnames. In the Dashboard, select Docker as the runtime, use the listed root directory and Dockerfile path, and leave the start command at the Dockerfile `ENTRYPOINT`. The images compile with Maven and Java 25, then run on the Java 25 JRE image. Each service reads `PORT` and binds to `0.0.0.0`; set `PORT=10000` for all three services. The Gateway web service also defaults to Render's web port.

For each private backend, copy its internal service URL from Render's **Connect → Internal** panel into Gateway environment settings. Set `MISSION_SERVICE_URL` and `HAZARD_SERVICE_URL` to those values. Do not put provider-assigned internal hostnames in source or Dockerfiles. Keep all three services in the same region. Gateway routes preserve the existing API paths and strip the browser `Origin` header before proxying, so only Gateway adds CORS headers.

### Required runtime variables

**Public Gateway:**

- `PORT=10000` (Render web service may provide its port automatically).
- `MISSION_SERVICE_URL` and `HAZARD_SERVICE_URL`: the corresponding Render internal URLs, including `http://` and port as shown by Render.
- `CORS_ALLOWED_ORIGINS`: the exact Vercel origin, for example `https://<owner-selected-project>.vercel.app`; do not use `*`.

**Private Mission Service:**

- `PORT=10000`.
- `SPRING_PROFILES_ACTIVE=production`.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.
- `DATABASE_URL`: the protected Neon PostgreSQL URI. Do not put it in source, Docker build arguments, or logs.
- No backend CORS origin is required for gateway traffic because the Gateway removes `Origin` before forwarding. Keep direct public access disabled.

**Private Hazard Service:**

- `PORT=10000`.
- No database, Kafka, or provider credential is required for the current weather endpoint.
- `OPEN_METEO_BASE_URL`, `WEATHER_CONNECT_TIMEOUT`, `WEATHER_READ_TIMEOUT`, `WEATHER_MAX_FORECAST_HOURS`, `WEATHER_MAX_ROUTE_LENGTH_KM`, `WEATHER_MAX_ROUTE_SAMPLES`, `WEATHER_SAMPLE_SPACING_KM`, `WEATHER_WIND_SPEED_THRESHOLD_MS`, `WEATHER_WIND_GUST_THRESHOLD_MS`, and `WEATHER_PRECIPITATION_THRESHOLD_MM_PRECEDING_HOUR` are optional overrides with defaults in `application.properties`.
- No backend CORS origin is needed while it remains private behind the Gateway.

Spring exposes only `/actuator/health` and hides health details. Render supports HTTP path checks for Web Services. For Private Services, Render only performs a TCP check, so their application health path cannot be configured in the dashboard; confirm each backend via its API through the Gateway after deployment.

## Render plans and blockers

Render Free web services can send private-network requests but cannot receive private-network traffic. Render Private Services have no free plan. Therefore Mission and Hazard must use paid Private Service plans for the requested “Gateway only public” topology; making them Free web services would expose public backend URLs and violate that requirement. The public Gateway may use a Free web plan and call paid private services over the internal network, but it sleeps after 15 minutes without inbound traffic, takes about a minute to wake, and shares a 750 instance-hour workspace allowance. The Free web plan has 512 MB RAM and 0.1 CPU. Render lists its smallest paid compute plan at $7/month per service (512 MB RAM, 0.5 CPU); check current region, tax, bandwidth, and account pricing before choosing. Mission/Hazard memory sufficiency has not been load-tested. [Render private network](https://render.com/docs/private-network), [Render free plan](https://render.com/docs/free), [Render compute plans](https://render.com/docs/compute-plans), [Render pricing](https://render.com/pricing).

Spring Cloud `2025.0.x` is compatible with Spring Boot `3.5.x`, including the project's Boot generation, but the latest open-source `2025.0.3` release train ended open-source support on 2026-06-30. The requested train is used here; its support status is a production risk that requires owner acceptance or a separately approved version strategy. [Spring Cloud compatibility table](https://spring.io/projects/spring-cloud), [2025.0.3 release notice](https://spring.io/blog/2026/06/11/spring-cloud-2025-0-3-aka-northfields-has-been-released).

## Vercel frontend

- Root Directory: `frontend`.
- Framework preset: Vite.
- Build Command: `npm run build`.
- Output Directory: `dist`.
- Install Command: `npm ci` (the committed npm lockfile is present).
- Production environment: `VITE_API_BASE_URL` set to the public Gateway HTTPS origin only.
- Keep `VITE_API_BASE_URL` empty for local development. Vite retains its local `/api` and `/hazard-api` proxies; weather requests use the shared API base when it is configured and `/hazard-api` when it is blank.

Vite variables are public build-time values. Never put database URLs, provider credentials, or other secrets in any `VITE_*` variable. [Vercel Vite guide](https://vercel.com/docs/frameworks/frontend/vite), [Vercel build settings](https://vercel.com/docs/builds/configure-a-build).

## Build and start commands

The Dockerfiles build and package each service with Maven and Java 25. To build/test outside Docker from each service directory, use:

```powershell
mvn --batch-mode -s ../mission-service/target/maven-settings.xml test package
```

For Mission Service, the equivalent settings path from its own directory is `target/maven-settings.xml`. The resulting executable JARs can be started with `java -jar target/<artifact>.jar`; Render should use the Dockerfile `ENTRYPOINT`. No compose file or Render Blueprint is included.

## Deployment order and smoke checks

1. Owner selects same-region Render plans and approves any paid private-service spend. Confirm Render account/workspace access and Vercel account eligibility.
2. Configure the Vercel project origin and the Gateway's exact `CORS_ALLOWED_ORIGINS` value.
3. Create/deploy Mission and Hazard as private services with the variables above. Confirm Mission's production schema validation succeeds; do not run migrations automatically.
4. Copy both private service URLs from Render's Internal panel to the Gateway's `MISSION_SERVICE_URL` and `HAZARD_SERVICE_URL`; deploy the public Gateway.
5. Set Vercel's `VITE_API_BASE_URL` to the Gateway's public HTTPS origin; build and deploy the frontend.
6. Smoke-check Gateway `GET /actuator/health`, then `GET /api/v1/missions`, `GET /api/v1/airspace/datasets`, and the existing weather assessment flow through `/api/v1/hazards/weather-assessment`. Check an allowed and disallowed CORS origin, `X-Correlation-ID`, and the Gateway's safe upstream error response. Use existing saved test data for weather checks; do not create or delete mission/airspace records as part of a health check.

The Neon database is not inspected or modified by this deployment preparation. The owner-deferred V2 constraint and partial index remain absent; no V2 SQL should be applied without separate approval. Also verify the complete production schema and live Mission startup before deployment: current project evidence does not prove every mapped table/column is compatible. No deployment URL, provider service name, internal hostname, Vercel origin, or successful live health result exists yet.
