# Hazard Service

The Hazard Service currently implements only a stateless, on-demand weather assessment endpoint for GeoJSON Point, Polygon, and LineString route geometries. It retrieves hourly forecast values from the [Open-Meteo Forecast API](https://open-meteo.com/en/docs). Hazard reports, persistence, Kafka, S3 attachments, and risk scoring are not implemented in this service phase.

## Local development

Use Java 25 and the project's Maven setup. From this directory:

```powershell
mvn --batch-mode -s ../mission-service/target/maven-settings.xml spring-boot:run
```

The service listens on port 8082 by default and exposes `GET /actuator/health`. No database, Kafka broker, provider API key, Docker, or paid resource is required for this endpoint. The forecast provider is called only when a client posts an assessment.

The Vite development server proxies `/hazard-api/*` to this local service and removes the `/hazard-api` prefix. Leave `VITE_API_BASE_URL` empty in `frontend/.env.local` to use that proxy. In the Render deployment, the frontend sends weather requests through the public API Gateway; the Hazard Service is a private internal service.

## Weather assessment

`POST /api/v1/hazards/weather-assessment` accepts `assessmentType` (`POINT`, `POLYGON`, or `ROUTE`), matching GeoJSON `geometry`, and optional `forecastHours` (default 24, maximum 72 by default). Coordinates use WGS84 longitude/latitude order. The body limit is 64 KiB. Polygon requests use one representative JTS interior point. Routes are bounded to 64 waypoints, 250 km and at most 12 weather sample points by default, sampled along the full route at approximately 25 km intervals.

Responses contain hourly forecast values in UTC, configured planning thresholds, threshold warnings, ranges, route sample locations, provider/retrieval metadata, and limitations. Temperature is °C, wind/gust are m/s, and precipitation is the sum over the preceding hour in mm. Null values remain explicitly missing. There is no forecast cache or persistence. COMPLETE means the requested provider data was returned for the requested horizon; it is not an assessment of flight safety. Thresholds are general planning indicators and are not aircraft-specific limits. The endpoint does not return current observations, an aviation briefing, risk score, flight duration, or clearance. Open-Meteo's official Forecast API docs were checked 2026-10-25.

## Runtime configuration

All settings are optional and have the documented defaults. They can be overridden through the environment:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8082` | HTTP listener (host supplied in deployment) |
| `OPEN_METEO_BASE_URL` | `https://api.open-meteo.com/v1/forecast` | Forecast endpoint |
| `WEATHER_CONNECT_TIMEOUT` | `3s` | Upstream connect timeout |
| `WEATHER_READ_TIMEOUT` | `6s` | Upstream read timeout |
| `WEATHER_MAX_FORECAST_HOURS` | `72` | Maximum requested horizon |
| `WEATHER_MAX_ROUTE_LENGTH_KM` | `250` | Maximum route length |
| `WEATHER_MAX_ROUTE_SAMPLES` | `12` | Maximum route forecast locations |
| `WEATHER_SAMPLE_SPACING_KM` | `25` | Approximate route sample interval |
| `WEATHER_WIND_SPEED_THRESHOLD_MS` | `12` | General wind planning indicator |
| `WEATHER_WIND_GUST_THRESHOLD_MS` | `17` | General gust planning indicator |
| `WEATHER_PRECIPITATION_THRESHOLD_MM_PRECEDING_HOUR` | `2` | General threshold for precipitation accumulated over the preceding hour |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed frontend origin(s), comma separated |

For a direct browser deployment, configure `CORS_ALLOWED_ORIGINS` to the exact frontend origin. In the requested private-service topology, the API Gateway owns CORS and removes `Origin` before forwarding. The service performs synchronous outbound HTTPS calls and uses bounded retries (up to three attempts) for rate limiting, upstream server failures, and network timeouts. Keep the host's outbound DNS/HTTPS access to Open-Meteo available. There are no secrets in the forecast request or required provider credentials. Review current provider terms, attribution requirements, endpoint availability, and usage policy before public deployment.

Render is the requested deployment target for this preparation, but no Hazard Service resource is configured or deployed. Keep Hazard as a Render Private Service in the same region as the public Gateway; set `PORT=10000` and allow outbound DNS/HTTPS to Open-Meteo. The Gateway owns browser CORS and strips `Origin` before forwarding. Hazard remains stateless and requires no database or Kafka configuration. Render private services have TCP-only dashboard health checks; the application still exposes `GET /actuator/health` for internal verification. See `../../docs/RENDER_DEPLOYMENT.md`.

## Verification

Tests use mock HTTP responses. They do not make live Open-Meteo requests and do not validate any production host's egress, CORS, or uptime. Run the checks from this directory:

```powershell
mvn --batch-mode -s ../mission-service/target/maven-settings.xml test package
```
