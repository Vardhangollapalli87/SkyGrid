# UI design specification (implemented baseline)

The Mission API accepts GeoJSON mission polygons and LineString routes and returns stored restricted-zone intersection results for polygons. The current frontend implements mission workspace, polygon planning, ordered waypoint route editing, CRUD display, restricted-zone check, and an on-demand weather assessment panel backed by Hazard Service. Selected missions show saved state, geometry validity, spherical geodesic area/perimeter estimates, API-backed dataset coverage limitations, and check result/time. Polygon and route exports are GeoJSON. Route distance is only an estimated geodesic ground track. Weather UI shows provider/retrieval/horizon, route samples, aggregate ranges, configured general thresholds, warning timestamps, partial data and limitations; it distinguishes missing values from zero and states that no-warning output is not a safety determination. It displays backend results without calculating intersections or weather thresholds. The current airspace API cannot distinguish an empty zone dataset from a complete negative query; the UI calls this out. SkyGrid is a prototype decision-support tool, not an aviation authority UI. Label restrictions/hazards as demonstration data and never imply clearance or a safety guarantee.

## Visual system

- Calm operational workspace: map-forward, clear hierarchy, restrained color, visible provenance.
- Initial tokens: cool off-white `#F4F7FA`, white surface, ink `#14283F`, secondary text `#526579`, border `#D9E2EC`, primary action `#1769AA`, focus `#78B8E8`.
- Risk colors: low teal `#16836B`, medium amber `#B97812`, high red `#B33A3A`; always pair with text/icon.
- System sans typography, 24–32 px page headings, readable 16 px body, tabular score/time numerals.
- 4 px spacing base with 8/12/16/24/32 rhythm; 8 px card radius; subtle borders.

Tokens are an initial direction, not existing code.

## Screens

1. Dashboard: mission count, risk distribution, recent hazard reports, demo notice, quick create action; useful empty state.
2. Mission planning: Leaflet map distinguishing restricted polygons, hazard points and editable mission area; name/type panel and validation/submit progress.
3. Mission detail: current score/level and time, factors, intersection warning, map and assessment timeline; explain event-driven changes.
4. Hazard report: type/severity, map point or coordinates, description, validation/confirmation; evidence only after S3 milestone.
5. Mission history: timestamped scores, changed factors, loading and empty states.

6. Weather assessment (implemented in the selected-mission panel): assessment is on demand. Show idle, loading, complete, partial, validation error, and provider unavailable states; preserve prior values only when clearly marked stale. Show per-route sample locations and forecast counts, field ranges, threshold warnings, provider attribution, retrieval time, and policy limitations. Do not display fabricated values, treat missing data as zero, infer aircraft readiness, or frame a no-warning forecast as clearance.

## Interaction/accessibility

- Do not rely on color alone. Maintain keyboard focus and visible states; label controls and provide alternatives for map drawing where feasible.
- Show pending/failed propagation accurately; do not imply consumer completion before updated assessment is returned.
- Poll at a modest documented interval, show last refresh, allow manual refresh.
- On small screens use full-width map and collapsible details; keep primary action reachable.
- Render UTC timestamp in readable local form while exposing exact timestamp in details.
- Never frame score as clearance, safety determination or prediction.

## Coordinate handling and workspace coverage

- Leaflet coordinates enter the application through named `lat`/`lng` fields; the boundary conversion wraps a world-copy longitude into WGS84 and returns numeric GeoJSON `[longitude, latitude]`. Backend validation remains strict and rejects out-of-range values.
- Display waypoint and cursor positions with explicit `Lat` and `Lon` labels. Keep route waypoint order and Polygon boundaries separate.
- Saved single-ring Polygon missions can reopen in the existing boundary editor and update through the Mission API. Polygons with interior rings remain viewable but are read-only in that editor so holes are never silently discarded.
- Show only compact source-coverage and viewport status on the map. Place inventory diagnostics and restriction/guidance layer toggles in the collapsible **Airspace Data & Coverage** section in the mission panel, with readable wrapping and scrollable details. Keep partial/in-progress and unverified-coverage warnings visible; do not infer clearance from an empty result.
- A LineString is an intended ground track, not an executable flight plan. Do not imply altitude, duration, aircraft limits, obstacle avoidance, legal authorization, or clearance.
