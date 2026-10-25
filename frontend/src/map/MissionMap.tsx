import { useEffect, useMemo, useRef, useState } from 'react'
import { CircleMarker, MapContainer, Marker, Polygon, ScaleControl, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import L, { type LeafletMouseEvent } from 'leaflet'
import { Crosshair, Layers2, LocateFixed, Minus, Plus, RotateCcw } from 'lucide-react'
import type { Mission } from '../api/client'
import type { Position } from '../domain/geometry'

const tiles = {
  'OpenStreetMap': import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  'Carto light': 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
}
const osmAttribution = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
const vertexIcon = L.divIcon({ className: 'vertex-marker', html: '<span></span>', iconSize: [16, 16], iconAnchor: [8, 8] })
type Props = { mission?: Mission; drawing: boolean; points: Position[]; onAdd: (point: Position) => void; onMove: (index: number, point: Position) => void; onRemove: (index: number) => void; tileFailed: () => void; tileLoaded: () => void }

export default function MissionMap({ mission, drawing, points, onAdd, onMove, onRemove, tileFailed, tileLoaded }: Props) {
  const [tileSet, setTileSet] = useState<keyof typeof tiles>('OpenStreetMap')
  const [tileState, setTileState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [coordinate, setCoordinate] = useState<Position>([0, 20])
  const geoRings = mission?.geometry?.coordinates?.map(ring => ring.map(([lng, lat]) => [lat, lng] as [number, number]))
  const geo = geoRings?.[0]
  const draft = points.map(([lng, lat]) => [lat, lng] as [number, number])
  const mapCenter = useMemo((): [number, number] => geo?.[0] || draft[0] || [12.9716, 77.5946], [mission?.id, points.length])
  const setLayer = (layer: keyof typeof tiles) => { setTileSet(layer); setTileState('loading') }

  return <div className="map-wrap">
    <MapContainer center={mapCenter} zoom={geo || draft.length ? 12 : 2} zoomControl={false} doubleClickZoom={!drawing} className="leaflet-map">
      <MapInteraction drawing={drawing} onAdd={onAdd} setCoordinate={setCoordinate} />
      <MapFocus missionGeometry={geo} missionId={mission?.id} />
      <TileLayer key={tileSet} url={tiles[tileSet]} attribution={tileSet === 'OpenStreetMap' ? osmAttribution : '&copy; <a href="https://carto.com/attributions">CARTO</a>'} eventHandlers={{ loading: () => { setTileState('loading'); tileLoaded() }, load: () => { setTileState('ready'); tileLoaded() }, tileerror: () => { setTileState('error'); tileFailed() } }} />
      <ScaleControl position="bottomleft" />
      <MapButtons missionGeometry={geo} />
      {geoRings && <Polygon positions={geoRings} pathOptions={{ color: '#46c8c1', fillColor: '#46c8c1', fillOpacity: 0.18, weight: 2.5 }}><Tooltip sticky>{mission?.name || 'Selected mission area'}</Tooltip></Polygon>}
      {!mission && draft.length > 2 && <Polygon positions={draft} pathOptions={{ color: '#58c9bd', fillColor: '#55cec2', fillOpacity: 0.14, weight: 2, dashArray: drawing ? '6 5' : undefined }} />}
      {!mission && draft.map((position, index) => <Marker key={`vertex-${index}`} position={position} icon={vertexIcon} draggable bubblingMouseEvents={false} eventHandlers={{ dragend: (event) => { const p = (event.target as L.Marker).getLatLng(); onMove(index, [p.lng, p.lat]) }, click: () => onRemove(index) }}><Tooltip>Vertex {index + 1} · drag to edit · click to remove</Tooltip></Marker>)}
      {!drawing && geo?.map((position, index) => <CircleMarker key={`point-${index}`} center={position} radius={3} pathOptions={{ color: '#10231f', fillColor: '#f4fffc', fillOpacity: 1, weight: 1 }} />)}
      {drawing && draft.length === 0 && <CenterHint />}
    </MapContainer>
    <div className="map-topline"><span className="map-live"><i /> {tileState === 'error' ? 'Map tiles unavailable' : tileState === 'loading' ? 'Loading map' : 'Map ready'}</span><div className="map-layer"><Layers2 size={14} /><select aria-label="Map layer" value={tileSet} onChange={e => setLayer(e.target.value as keyof typeof tiles)}>{Object.keys(tiles).map(name => <option key={name}>{name}</option>)}</select></div></div>
    {tileState === 'loading' && <div className="tile-state">Loading map imagery…</div>}
    {tileState === 'error' && <div className="tile-state tile-error">Map tiles could not load. Check the connection or choose another layer.</div>}
    <div className="map-coordinate"><Crosshair size={12} /> {Math.abs(coordinate[1]).toFixed(5)}° {coordinate[1] < 0 ? 'S' : 'N'}&nbsp; {Math.abs(coordinate[0]).toFixed(5)}° {coordinate[0] < 0 ? 'W' : 'E'}</div>
    {drawing && <div className="draw-tip"><span className="draw-key">CLICK MAP</span> to add vertices <span className="tip-divider" /> Drag a point to adjust</div>}
  </div>
}

function MapInteraction({ drawing, onAdd, setCoordinate }: { drawing: boolean; onAdd: (p: Position) => void; setCoordinate: (p: Position) => void }) {
  useMapEvents({ click(e: LeafletMouseEvent) { if (drawing) onAdd([e.latlng.lng, e.latlng.lat]) }, mousemove(e) { setCoordinate([e.latlng.lng, e.latlng.lat]) } })
  return null
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
