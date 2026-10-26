# Airspace snapshot ingestion

SkyGrid stores source-preserving airspace records in Mission Service/PostGIS. Imports use fixed repository-relative snapshots and make no network requests. They are opt-in, parsed locally, and written in batches of at most 100 records, each in its own database transaction. Each attempt has a stable UUID and batch markers, so a lost response after a commit can be replayed without double-counting; a later full rerun safely upserts by `(source_name, dataset_name, source_id)`. A dataset is hidden from viewport and mission restriction queries while it is `IN_PROGRESS` or `FAILED`. Only after all batches commit does a final transaction remove stale source IDs and mark the dataset `SUCCESS` (or `PARTIAL` when individual rows had permanent data errors). A failed attempt retains its committed batch rows for replay, but they do not participate in queries until a complete attempt succeeds. Transient SQL connection/transaction errors are retried at most three times per batch; SQL data errors are isolated by splitting the batch until the offending row can be recorded as rejected. Geometry conversion/validity is checked by PostGIS (`ST_GeomFromGeoJSON`, SRID 4326, `ST_IsValid`). No production extension is created. Apply `services/mission-service/db/production-spatial-schema.sql` after PostGIS is available and before production startup.

The reliability change adds `last_seen_attempt_id` and a unique source-identity index to `airspace_features`, import attempt/count columns to `airspace_dataset_imports`, and `airspace_import_batches` for commit-safe progress accounting. The SQL script is additive and backfills the feature timestamp before enforcing its non-null constraint. Local/development startup applies equivalent `IF NOT EXISTS` schema updates. Production must apply the reviewed SQL script manually before deployment.

## Snapshot audit (25 October 2026)

| Snapshot | Parsed source inventory | Spatial handling | Limits |
|---|---|---|---|
| FAA national-security restrictions GeoJSON | 2,287 features; 2,276 Polygon and 11 MultiPolygon; `OBJECTID` distinct for all 2,287. Retained fields include `Proponent`, `Branch`, `Base`, `Facility`, `Airspace`, `Reason`, `Floor`, `Ceiling`, `State`, `FAA_ID`, and source contact data. | Polygon/MultiPolygon are passed through PostGIS validation and stored as `RESTRICTION`. The source classification and full properties are retained. | The supplied object-ID-batch download count is not proof of complete source coverage. Source license/redistribution terms were not established. |
| Netherlands ED-269 JSON | 774 records, all `country=NLD`, 774 distinct `identifier`s; 713 `CONDITIONAL`, 51 `REQ_AUTHORISATION`, 10 `PROHIBITED`; 162 horizontal Polygon projections and 612 Circle projections. | ED-269 polygon and circle horizontal projections use WGS 84 (EPSG:4326), with GeoJSON-style longitude, latitude order as documented by [EUROCONTROL coordinate guidance](https://swim-eurocontrol.atlassian.net/wiki/spaces/UGZ/pages/59113484/Geographical+coordinates+and+GeoJSON). Polygon projections are validated/stored. Circle records are preserved with no geometry rather than approximated. `PROHIBITED` and `REQ_AUTHORISATION` are restriction-category records; conditional/informational records remain separate. | File contains title/version and description but does not identify a publishing organization in those top-level fields. Verify attribution and reuse terms before redistribution. Completeness is unverified. |
| India AAI AIP ENR 5.1 HTML (effective October 2026) | HTML source document validated for expected section heading. | Retained as non-spatial source text; not split into individual records and not included in spatial restriction checks. | Coordinate descriptions, vertical limits, schedule/activation semantics, and complicated boundary descriptions have not yet been extracted or converted. This is not India DigitalSky coverage. |
| India AAI AIP ENR 5.2 HTML (effective October 2026) | HTML source document validated for expected section heading. | Retained as non-spatial source text; classified as aviation information, never as a blanket prohibition. | Exercise/training areas and ADIZ require record-level interpretation. No polygon conversion has been attempted. This is not India DigitalSky coverage. |

The supplied FAA and Netherlands files were parsed locally for these structural counts. The FAA snapshot is specifically labelled national-security restrictions; it is not FAA UAS Facility Map altitude guidance. FAA UASFM values are informational and do not grant flight authorization ([FAA explanation](https://www.faa.gov/uas/commercial_operators/uas_facility_maps/faq)); that separate dataset is not imported here. The specific provider URL, terms, and licensing for the supplied FAA snapshot and Netherlands file were not verified. Import counts are reported only by the import command and dataset API after an actual database import. No source snapshot is included in Git; the root `.gitignore` excludes `data/source-snapshots/`. Preserve source attribution and check each source's applicable terms before publishing or redistributing records.

## Verified FAA recovery (2026-10-26)

The completed FAA National Security Restrictions import accounts for 2,287 parsed records: 2,274 spatial and skipped/already present, 13 rejected, 0 duplicates, and 0 failed. The final dataset status is `PARTIAL`; `coverageVerified` remains false. The FAA national-security-restrictions snapshot is not a complete U.S. airspace dataset and must not be described as complete coverage, proof of safety, or flight clearance.

Rejection reasons for the 13 records are not persisted in the database and are not exposed by `GET /api/v1/airspace/datasets`. Do not infer reasons from the aggregate count. The inventory endpoint maps stored database fields, but its live HTTP response has not been verified.

## Local command

For a future FAA-only import, use the scoped command below. It replays the complete fixed FAA snapshot in a new attempt and does not import or alter Netherlands or India datasets. A new attempt does not resume old batch markers; it safely upserts by source identity, then checks its own committed batch totals before finalizing status.

```powershell
cd services/mission-service
mvn --batch-mode -s target/maven-settings.xml "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--airspace-import-faa-only --spring.main.web-application-type=none" spring-boot:run
```

Before a future import, check for an existing importer process using the PowerShell procedure below. Also confirm the FAA source snapshot is the reviewed 2,287-record input, database connectivity is stable, and the source-identity unique constraint and import-batch schema are present. The process check is a precaution, not proof that no import is active.

```powershell
try {
    $matches = Get-CimInstance -ClassName Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop |
        Where-Object { $_.CommandLine -match '--airspace-import-(faa-only|snapshots)' } |
        Select-Object ProcessId, Name

    if ($matches) { $matches } else { 'No matching Java importer process found.' }
}
catch {
    "Process check unavailable: $($_.Exception.GetType().Name)"
}
```

This prints only matching process IDs and names, never command-line arguments or environment values. A missing match is not conclusive: process visibility can be restricted, and an importer may be launched in another session or under another executable. If the check is unavailable or uncertain, resolve that uncertainty before starting an import.

From the repository root, with the existing local profile and PostGIS connection configured privately:

```powershell
cd services/mission-service
mvn --batch-mode -s target/maven-settings.xml "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--airspace-import-snapshots --spring.main.web-application-type=none" spring-boot:run
```

This imports the supplied source files to the configured database and exits after import. It prints per-batch progress and final parsed/inserted/updated/skipped/spatial/non-spatial/rejected/duplicate/failed counts. Review `/api/v1/airspace/datasets` after starting the service normally. Do not run against production until the SQL migration, access policy, source terms, and dataset review are approved. The command uses only the fixed `data/source-snapshots/` paths and does not accept a URL or expose an upload endpoint.

## Connection diagnostics and pooling

The configured local endpoint uses Neon's pooled-host form (`-pooler`). Neon documents this endpoint as PgBouncer transaction pooling: a backend connection is assigned for a transaction, then returned to the pool. The new importer holds one database transaction per 100-row batch and uses no temporary tables, session variables, or session advisory locks, so its behavior fits transaction pooling. A direct connection is not required by the importer; do not replace the configured URL based only on the earlier reset. See [Neon connection pooling](https://neon.com/docs/connect/connection-pooling) and [Neon connection guidance](https://neon.com/docs/get-started/connect-neon).

For a safe local network check, set `$hostName` to only the hostname from the private local database settings or Neon Console; do not echo or paste the full URL. These commands report success/count only and avoid printing IPs or credentials:

```powershell
$hostName = '<configured database hostname>'
$resolved = [System.Net.Dns]::GetHostAddresses($hostName)
"DNS resolved: $($resolved.Count -gt 0)"
$client = [System.Net.Sockets.TcpClient]::new()
try {
    $connect = $client.ConnectAsync($hostName, 5432)
    if (-not $connect.Wait(10000)) { 'TCP/5432: timed out' }
    elseif ($client.Connected) { 'TCP/5432: connected' }
    else { 'TCP/5432: not connected' }
}
catch { "TCP/5432: failed ($($_.Exception.GetType().Name))" }
finally { $client.Dispose() }
```

If DNS succeeds but the Java import later reports a socket reset, check Neon endpoint status/compute state and the local network/proxy/firewall at the failure time. A later `UnknownHostException` is a DNS resolution failure, not evidence that credentials were rejected. The prior run did not establish whether the reset originated from the client network, DNS path, or database endpoint. The importer now uses bounded short transactions and retries only SQL connection/transaction failures up to three times; data errors are not retried as transient failures.

Historical first-attempt note (25 October 2026): an import attempt established a database session and ran PostGIS geometry validation, but the socket reset during the FAA import and a reconnect reported a DNS lookup failure. That interrupted attempt did not complete. The subsequent FAA recovery and final counts are recorded above; live inventory HTTP behavior remains unverified.

## API and meaning

- `GET /api/v1/airspace/datasets` reports last successful import, latest attempt status/time, counts, source URL/effective date only when recorded, and limitations. This import does not establish source freshness (`freshnessStatus=NOT_EVALUATED`) or completeness (`coverageVerified=false`). Unverified source links are returned as null rather than inferred.
- `GET /api/v1/airspace/features?west=&south=&east=&north=` returns at most 2,000 spatial records intersecting a WGS84 viewport and a `truncated` flag if more were available.
- Mission restricted-zone checks include only records stored in `RESTRICTION` with validated geometry, plus the existing restricted-zone table. FAA national-security records and the Netherlands prohibited/authorization-required classifications are retained as their source labels. Other aviation information and unconverted AIP documents are not treated as restriction polygons.
- `intersects=false` only means no imported compatible record intersected the mission; source coverage is unverified and this is not flight clearance.

Temporary restrictions, NOTAMs, official clearance, worldwide completeness, and DigitalSky drone-zone ingestion are not implemented.
