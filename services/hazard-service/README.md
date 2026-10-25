# Hazard Service

The Hazard Service currently implements only a stateless, on-demand weather assessment endpoint for GeoJSON Point, Polygon, and LineString route geometries. It retrieves hourly forecast values from the [Open-Meteo Forecast API](https://open-meteo.com/en/docs). Hazard reports, persistence, Kafka, S3 attachments, and risk scoring are not implemented in this service phase.

## Local development

Use Java 25 and the project's Maven setup. From this directory:

```powershell
mvn --batch-mode -s ../mission-service/target/maven-settings.xml spring-boot:run
```

The service listens on port 8082 by default and exposes `GET /actuator/health`. No database, Kafka broker, provider API key, Docker, or paid resource is required for this endpoint. The forecast provider is called only when a client posts an assessment.

The Vite development server proxies `/hazard-api/*` to this local service and removes the `/hazard-api` prefix. Leave `VITE_HAZARD_API_BASE_URL` empty in `frontend/.env.local` to use that proxy. For a deployed frontend, set it to the public HTTPS Hazard Service origin; this value is public browser configuration, not a secret.

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

Configure `CORS_ALLOWED_ORIGINS` to the exact deployed frontend origin. The service performs synchronous outbound HTTPS calls and uses bounded retries (up to three attempts) for rate limiting, upstream server failures, and network timeouts. Keep the host's outbound DNS/HTTPS access to Open-Meteo available. There are no secrets in the forecast request or required provider credentials. Review current provider terms, attribution requirements, endpoint availability, and usage policy before public deployment.

No public Hazard Service host is currently configured. The provider comparison's backend host is provisional and must not be presented as a deployment. In a container host, set its supplied `PORT`, the weather settings above as needed, and `CORS_ALLOWED_ORIGINS`; provide outbound DNS and HTTPS access to Open-Meteo. The API is stateless and requires no database or Kafka configuration.

## Verification

Tests use mock HTTP responses. They do not make live Open-Meteo requests and do not validate any production host's egress, CORS, or uptime. Run the checks from this directory:

```powershell
mvn --batch-mode -s ../mission-service/target/maven-settings.xml test package
```
