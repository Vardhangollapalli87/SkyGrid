# UI design specification (implemented baseline)

The Mission API accepts GeoJSON mission polygons and returns stored restricted-zone intersection results. The current frontend implements the mission workspace, map-based polygon planning, CRUD display, and restricted-zone check. It displays backend results without calculating intersections. The current API cannot distinguish an empty zone dataset from a complete negative query; the UI calls this out. SkyGrid is a prototype decision-support tool, not an aviation authority UI. Label restrictions/hazards as demonstration data and never imply clearance or a safety guarantee.

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

## Interaction/accessibility

- Do not rely on color alone. Maintain keyboard focus and visible states; label controls and provide alternatives for map drawing where feasible.
- Show pending/failed propagation accurately; do not imply consumer completion before updated assessment is returned.
- Poll at a modest documented interval, show last refresh, allow manual refresh.
- On small screens use full-width map and collapsible details; keep primary action reachable.
- Render UTC timestamp in readable local form while exposing exact timestamp in details.
- Never frame score as clearance, safety determination or prediction.
