import { useEffect, useMemo, useRef, useState } from 'react'
import { CircleMarker, GeoJSON, MapContainer, Marker, Polygon, Polyline, ScaleControl, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import L, { type LeafletMouseEvent } from 'leaflet'
import { Crosshair, Layers2, LocateFixed, Minus, Plus, RotateCcw } from 'lucide-react'
import { missionApi, type AirspaceDataset, type AirspaceFeatureCollection, type AirspaceProperties, type Mission } from '../api/client'
import type { Position } from '../domain/geometry'

const tiles = {
  'OpenStreetMap': import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  'Carto light': 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
}
const osmAttribution = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
const vertexIcon = L.divIcon({ className: 'vertex-marker', html: '<span></span>', iconSize: [16, 16], iconAnchor: [8, 8] })
type Props = { mission?: Mission; drawing: boolean; routeEditing: boolean; points: Position[]; routePoints: Position[]; selectedRouteWaypoint?: number; onAdd: (point: Position) => void; onMove: (index: number, point: Position) => void; onRemove: (index: number) => void; onSelectRouteWaypoint: (index: number) => void; onMoveRouteWaypoint: (index: number, point: Position) => void; tileFailed: () => void; tileLoaded: () => void }

export default function MissionMap({ mission, drawing, routeEditing, points, routePoints, selectedRouteWaypoint, onAdd, onMove, onRemove, onSelectRouteWaypoint, onMoveRouteWaypoint, tileFailed, tileLoaded }: Props) {
  const [tileSet, setTileSet] = useState<keyof typeof tiles>('OpenStreetMap')
  const [tileState, setTileState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [coordinate, setCoordinate] = useState<Position>([0, 20])
  const [airspace, setAirspace] = useState<AirspaceFeatureCollection>()
  const [datasets, setDatasets] = useState<AirspaceDataset[]>([])
  const [airspaceState, setAirspaceState] = useState<'loading' | 'ready' | 'empty' | 'zoom' | 'error'>('loading')
  const [showRestrictions, setShowRestrictions] = useState(true)
  const [showGuidance, setShowGuidance] = useState(true)
  const [featureInfo, setFeatureInfo] = useState<AirspaceProperties>()
  const geoRings = mission?.geometry?.coordinates?.map(ring => ring.map(([lng, lat]) => [lat, lng] as [number, number]))
  const geo = geoRings?.[0]
  const draft = points.map(([lng, lat]) => [lat, lng] as [number, number])
  const routeLine = (routeEditing ? routePoints : mission?.route?.coordinates)?.map(([lng, lat]) => [lat, lng] as [number, number])
  const mapCenter = useMemo((): [number, number] => geo?.[0] || draft[0] || [12.9716, 77.5946], [mission?.id, points.length])
  const setLayer = (layer: keyof typeof tiles) => { setTileSet(layer); setTileState('loading') }

  return <div className="map-wrap">
    <MapContainer center={mapCenter} zoom={geo || draft.length ? 12 : 2} zoomControl={false} doubleClickZoom={!drawing} className="leaflet-map">
      <MapInteraction drawing={drawing || routeEditing} onAdd={onAdd} setCoordinate={setCoordinate} />
      <AirspaceViewport onData={setAirspace} onState={setAirspaceState} />
      <DatasetInventory onData={setDatasets} />
      <MapFocus missionGeometry={routeEditing ? routeLine : geo || routeLine} missionId={mission?.id} />
      <TileLayer key={tileSet} url={tiles[tileSet]} attribution={tileSet === 'OpenStreetMap' ? osmAttribution : '&copy; <a href="https://carto.com/attributions">CARTO</a>'} eventHandlers={{ loading: () => { setTileState('loading'); tileLoaded() }, load: () => { setTileState('ready'); tileLoaded() }, tileerror: () => { setTileState('error'); tileFailed() } }} />
      <ScaleControl position="bottomleft" />
      {airspace?.features.filter(f => (f.properties.category === 'RESTRICTION' ? showRestrictions : showGuidance)).map(f => <GeoJSON key={f.id} data={f as never} style={() => ({ color: f.properties.category === 'RESTRICTION' ? '#b33a3a' : '#b97812', fillColor: f.properties.category === 'RESTRICTION' ? '#b33a3a' : '#b97812', fillOpacity: 0.18, weight: 1.5 })} onEachFeature={(_feature, layer) => layer.on('click', () => setFeatureInfo(f.properties))} />)}
      <MapButtons missionGeometry={geo} />
      {geoRings && <Polygon positions={geoRings} pathOptions={{ color: '#46c8c1', fillColor: '#46c8c1', fillOpacity: 0.18, weight: 2.5 }}><Tooltip sticky>{mission?.name || 'Selected mission area'}</Tooltip></Polygon>}
      {!!routeLine?.length && <Polyline positions={routeLine} pathOptions={{ color: '#147f9d', weight: 4, opacity: .95, dashArray: routeEditing ? '8 5' : undefined }} />}
      {!!routeLine?.length && routeLine.map((position, index) => <Marker key={`route-${mission?.id ?? 'draft'}-${index}`} position={position} icon={waypointIcon(index + 1, selectedRouteWaypoint === index)} draggable={routeEditing} bubblingMouseEvents={false} keyboard title={`Waypoint ${index + 1}`} eventHandlers={{ click: () => onSelectRouteWaypoint(index), dragend: event => { const p = (event.target as L.Marker).getLatLng(); onMoveRouteWaypoint(index, [p.lng, p.lat]) } }}><Tooltip>Waypoint {index + 1}{routeEditing ? ' · drag to move' : ''}</Tooltip></Marker>)}
      {!mission && draft.length > 2 && <Polygon positions={draft} pathOptions={{ color: '#58c9bd', fillColor: '#55cec2', fillOpacity: 0.14, weight: 2, dashArray: drawing ? '6 5' : undefined }} />}
      {!mission && draft.map((position, index) => <Marker key={`vertex-${index}`} position={position} icon={vertexIcon} draggable bubblingMouseEvents={false} eventHandlers={{ dragend: (event) => { const p = (event.target as L.Marker).getLatLng(); onMove(index, [p.lng, p.lat]) }, click: () => onRemove(index) }}><Tooltip>Vertex {index + 1} · drag to edit · click to remove</Tooltip></Marker>)}
      {!drawing && geo?.map((position, index) => <CircleMarker key={`point-${index}`} center={position} radius={3} pathOptions={{ color: '#10231f', fillColor: '#f4fffc', fillOpacity: 1, weight: 1 }} />)}
      {(drawing && draft.length === 0 || routeEditing && routePoints.length === 0) && <CenterHint />}
    </MapContainer>
    <div className="map-topline"><span className="map-live"><i /> {tileState === 'error' ? 'Map tiles unavailable' : tileState === 'loading' ? 'Loading map' : 'Map ready'}</span><div className="map-layer"><Layers2 size={14} /><select aria-label="Map layer" value={tileSet} onChange={e => setLayer(e.target.value as keyof typeof tiles)}>{Object.keys(tiles).map(name => <option key={name}>{name}</option>)}</select></div></div>
    <div className="airspace-control"><label><input type="checkbox" checked={showRestrictions} onChange={e => setShowRestrictions(e.target.checked)} /> Restrictions</label><label><input type="checkbox" checked={showGuidance} onChange={e => setShowGuidance(e.target.checked)} /> Other data</label><span>{airspaceState === 'loading' ? 'Loading airspace…' : airspaceState === 'error' ? 'Airspace unavailable' : !datasets.length ? 'No imported datasets' : airspaceState === 'zoom' ? 'Zoom in to load features' : airspaceState === 'empty' ? 'No features in view' : `${airspace?.features.length ?? 0}${airspace?.truncated ? '+' : ''} features in view`}</span></div>
    {!!datasets.length && <div className="airspace-status">{datasets.map(d => <span key={`${d.country}-${d.dataset}`} title={d.limitations}>{d.country} · {d.status} · {d.insertedCount} inserted · {d.updatedCount} updated · {d.skippedCount} skipped · {d.spatialFeatures} spatial · {d.nonSpatialCount} non-spatial · {d.rejectedCount} rejected · {d.failedCount} failed · {d.status === 'FAILED' || d.status === 'IN_PROGRESS' ? 'attempt ' + (d.lastAttemptAt ? new Date(d.lastAttemptAt).toLocaleString() : 'time unavailable') : 'imported ' + (d.lastImportAt ? new Date(d.lastImportAt).toLocaleString() : 'time unavailable')} · coverage unverified</span>)}</div>}
    {featureInfo && <div className="airspace-info"><button aria-label="Close feature details" onClick={() => setFeatureInfo(undefined)}>×</button><strong>{featureInfo.name || featureInfo.sourceId}</strong><span>{featureInfo.country} · {featureInfo.classification || 'Classification unknown'}</span><span>{featureInfo.source} · {featureInfo.dataset} · ID {featureInfo.sourceId}</span>{featureInfo.effectiveAt && <span>Effective {featureInfo.effectiveAt}</span>}<pre>{JSON.stringify(featureInfo.sourceProperties, null, 2)}</pre></div>}
    {datasets.length > 0 && <div className="airspace-warning">Partial source collection. Coverage is not verified; this is not flight clearance.</div>}
    {airspaceState === 'empty' && datasets.length === 0 && <div className="airspace-warning">Airspace sources have not been imported. An empty map does not indicate clear airspace.</div>}
    {tileState === 'loading' && <div className="tile-state">Loading map imagery…</div>}
    {tileState === 'error' && <div className="tile-state tile-error">Map tiles could not load. Check the connection or choose another layer.</div>}
    <div className="map-coordinate"><Crosshair size={12} /> {Math.abs(coordinate[1]).toFixed(5)}° {coordinate[1] < 0 ? 'S' : 'N'}&nbsp; {Math.abs(coordinate[0]).toFixed(5)}° {coordinate[0] < 0 ? 'W' : 'E'}</div>
    {(drawing || routeEditing) && <div className="draw-tip"><span className="draw-key">CLICK MAP</span> to add {routeEditing ? 'waypoints in order' : 'vertices'} <span className="tip-divider" /> Drag a point to adjust</div>}
  </div>
}

function AirspaceViewport({ onData, onState }: { onData: (data: AirspaceFeatureCollection | undefined) => void; onState: (state: 'loading' | 'ready' | 'empty' | 'zoom' | 'error') => void }) {
  const map = useMap()
  useEffect(() => {
    let controller: AbortController | undefined
    let timer: number | undefined
    const load = () => {
      if (timer) window.clearTimeout(timer)
      timer = window.setTimeout(() => {
        if (map.getZoom() < 4) { controller?.abort(); onData(undefined); onState('zoom'); return }
        controller?.abort(); controller = new AbortController(); onState('loading')
        const b = map.getBounds()
        void missionApi.airspace({ west: Math.max(-180, b.getWest()), south: Math.max(-90, b.getSouth()), east: Math.min(180, b.getEast()), north: Math.min(90, b.getNorth()) }, controller.signal)
          .then(data => { onData(data); onState(data.features.length ? 'ready' : 'empty') })
          .catch(() => { if (!controller?.signal.aborted) { onData(undefined); onState('error') } })
      }, 250)
    }
    map.on('moveend', load); load()
    return () => { map.off('moveend', load); if (timer) window.clearTimeout(timer); controller?.abort() }
  }, [map, onData, onState])
  return null
}
function DatasetInventory({ onData }: { onData: (data: AirspaceDataset[]) => void }) {
  useEffect(() => { const controller = new AbortController(); void missionApi.airspaceDatasets(controller.signal).then(onData).catch(() => onData([])); return () => controller.abort() }, [onData])
  return null
}

function MapInteraction({ drawing, onAdd, setCoordinate }: { drawing: boolean; onAdd: (p: Position) => void; setCoordinate: (p: Position) => void }) {
  useMapEvents({ click(e: LeafletMouseEvent) { if (drawing) onAdd([e.latlng.lng, e.latlng.lat]) }, mousemove(e) { setCoordinate([e.latlng.lng, e.latlng.lat]) } })
  return null
}
function waypointIcon(number: number, selected: boolean) {
  return L.divIcon({ className: `waypoint-marker${selected ? ' waypoint-selected' : ''}`, html: `<span>${number}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] })
}
function MapFocus({ missionGeometry, missionId }: { missionGeometry?: [number, number][]; missionId?: string }) {
  const map = useMap()
  useEffect(() => { if (missionId && missionGeometry?.length) map.fitBounds(missionGeometry, { padding: [54, 54], maxZoom: 15 }) }, [map, missionId])
  return null
}
function MapButtons({ missionGeometry }: { missionGeometry?: [number, number][] }) {
  const map = useMap()
  const controlRef = useRef<HTMLDivElement>(null)
  useEffect(() => { if (controlRef.current) L.DomEvent.disableClickPropagation(controlRef.current) }, [])
  return <div ref={controlRef} className="map-buttons">
    <button title="Zoom in" aria-label="Zoom in" onClick={() => map.zoomIn()}><Plus size={16} /></button><button title="Zoom out" aria-label="Zoom out" onClick={() => map.zoomOut()}><Minus size={16} /></button><button title="Fit selected area" aria-label="Fit selected area" disabled={!missionGeometry?.length} onClick={() => missionGeometry?.length && map.fitBounds(missionGeometry, { padding: [60, 60] })}><LocateFixed size={16} /></button><button title="Default extent" aria-label="Default extent" onClick={() => map.setView([20, 0], 2)}><RotateCcw size={15} /></button>
  </div>
}
function CenterHint() { const map = useMap(); useEffect(() => { map.getContainer().classList.add('drawing-mode'); return () => map.getContainer().classList.remove('drawing-mode') }, [map]); return null }
