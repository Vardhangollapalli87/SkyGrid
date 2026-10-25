import { useCallback, useEffect, useMemo, useReducer, useRef, useState, type FormEvent } from 'react'
import { Activity, AlertTriangle, ArrowDownToLine, ArrowLeft, ArrowUp, ArrowDown, Check, ChevronDown, ChevronRight, CircleHelp, Clock3, Compass, FilePlus2, Hexagon, Menu, Plane, RefreshCw, Search, ShieldAlert, Route as RouteIcon, Undo2, X } from 'lucide-react'
import { ApiError, missionApi, type AirspaceDataset, type Mission, type MissionPage, type ZoneCheck } from '../api/client'
import { toGeoJson, validatePolygon, type Position } from '../domain/geometry'
import { missionGeoJson } from '../domain/geoJsonExport'
import { measurePolygon } from '../domain/missionMetrics'
import { estimatedGroundTrackMeters, toRouteGeometry, validateRoute } from '../domain/route'
import { missionRouteGeoJson } from '../domain/routeGeoJson'
import { weatherApi, WeatherApiError, weatherAssessmentIsStale, weatherPanelReducer, type WeatherPanelState } from '../api/weather'
import MissionMap from '../map/MissionMap'

type ServiceState = 'connecting' | 'online' | 'offline' | 'error'
export default function App() {
  const [missions, setMissions] = useState<Mission[]>([])
  const [pageInfo, setPageInfo] = useState<MissionPage>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  const [pageIndex, setPageIndex] = useState(0)
  const [selected, setSelected] = useState<Mission | undefined>(undefined)
  const [service, setService] = useState<ServiceState>('connecting')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | undefined>(undefined)
  const [slow, setSlow] = useState(false)
  const [search, setSearch] = useState('')
  const [drawing, setDrawing] = useState(false)
  const [routeEditing, setRouteEditing] = useState(false)
  const [points, setPoints] = useState<Position[]>([])
  const [routePoints, setRoutePoints] = useState<Position[]>([])
  const [selectedRouteWaypoint, setSelectedRouteWaypoint] = useState<number>()
  const routeHistory = useRef<Position[][]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [check, setCheck] = useState<ZoneCheck | undefined>(undefined)
  const [checkError, setCheckError] = useState('')
  const [checkAt, setCheckAt] = useState<string>()
  const [detailState, setDetailState] = useState<'ready' | 'loading' | 'unavailable'>('ready')
  const [exportError, setExportError] = useState('')
  const routeErrors = useMemo(() => validateRoute(routePoints), [routePoints])
  const [coverage, setCoverage] = useState<AirspaceDataset[]>([])
  const [coverageState, setCoverageState] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  const [weatherState, dispatchWeather] = useReducer(weatherPanelReducer, { status: 'idle' })
  const weatherController = useRef<AbortController | null>(null)
  const [panelOpen, setPanelOpen] = useState(true)
  const [mobileNav, setMobileNav] = useState(false)
  const [tileError, setTileError] = useState(false)
  const [notice, setNotice] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const selectionRequest = useRef(0)
  const filtered = useMemo(() => missions.filter(m => m.name.toLowerCase().includes(search.toLowerCase())), [missions, search])
  const geometryErrors = useMemo(() => validatePolygon(points), [points])
  const refresh = useCallback(async (quiet = false) => {
    abortRef.current?.abort()
    const controller = new AbortController(); abortRef.current = controller
    if (!quiet) { setLoading(true); setError(undefined); setService('connecting') }
    setSlow(false)
    const slowTimer = window.setTimeout(() => setSlow(true), 4500)
    try {
      const page = await missionApi.list(pageIndex, 20, controller.signal)
      if (controller.signal.aborted) return
      setMissions(page.content); setPageInfo(page); setService('online'); setError(undefined)
    } catch (cause) {
      if (controller.signal.aborted) return
      const apiError = cause instanceof ApiError ? cause : new ApiError('The Mission Service could not be reached.', 'network')
      setError(apiError); setService(apiError.kind === 'network' || apiError.kind === 'timeout' ? 'offline' : 'error')
    } finally { window.clearTimeout(slowTimer); if (!controller.signal.aborted) setLoading(false) }
  }, [pageIndex])
  useEffect(() => { void refresh(); return () => abortRef.current?.abort() }, [refresh])
  useEffect(() => { if (!notice) return; const timer = window.setTimeout(() => setNotice(''), 4200); return () => window.clearTimeout(timer) }, [notice])
  useEffect(() => {
    if (!drawing && !routeEditing) return
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') { if (routeEditing) cancelRouteEditing(); else setDrawing(false) } }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [drawing, routeEditing])

  const selectMission = async (mission: Mission) => {
    weatherController.current?.abort(); dispatchWeather({ type: 'reset' })
    const requestId = ++selectionRequest.current
    setSelected(mission); setDrawing(false); setRouteEditing(false); setRoutePoints(mission.route?.coordinates ?? []); routeHistory.current = []; setSelectedRouteWaypoint(undefined); setCheck(undefined); setCheckError(''); setCheckAt(undefined); setChecking(false); setFormError(''); setExportError(''); setDetailState('loading'); setCoverageState('loading')
    try { const latest = await missionApi.get(mission.id); if (selectionRequest.current === requestId) { setSelected(latest); setRoutePoints(latest.route?.coordinates ?? []); setDetailState('ready') } }
    catch { if (selectionRequest.current === requestId) setDetailState('unavailable') }
    try { const datasets = await missionApi.airspaceDatasets(); if (selectionRequest.current === requestId) { setCoverage(datasets); setCoverageState('ready') } }
    catch { if (selectionRequest.current === requestId) setCoverageState('unavailable') }
  }
  const startDraft = () => { weatherController.current?.abort(); dispatchWeather({ type: 'reset' }); selectionRequest.current++; setSelected(undefined); setRouteEditing(false); setRoutePoints([]); setCheck(undefined); setCheckAt(undefined); setDrawing(true); setPoints([]); setName(''); setDescription(''); setFormError(''); setPanelOpen(true) }
  const startRouteDraft = () => { weatherController.current?.abort(); dispatchWeather({ type: 'reset' }); selectionRequest.current++; setSelected(undefined); setDrawing(false); setRouteEditing(true); setPoints([]); setRoutePoints([]); routeHistory.current = []; setSelectedRouteWaypoint(undefined); setName(''); setDescription(''); setFormError(''); setPanelOpen(true) }
  const editSavedRoute = () => { if (!selected?.route) return; setRoutePoints(selected.route.coordinates.map(([lng, lat]) => [lng, lat])); routeHistory.current = []; setSelectedRouteWaypoint(undefined); setName(selected.name); setDescription(selected.description ?? ''); setFormError(''); setRouteEditing(true) }
  const changeRoute = (next: Position[]) => { routeHistory.current = [...routeHistory.current.slice(-49), routePoints.map(p => [p[0], p[1]])]; setRoutePoints(next); setSelectedRouteWaypoint(undefined); setFormError('') }
  const undoRoute = () => { const previous = routeHistory.current.pop(); if (previous) { setRoutePoints(previous); setSelectedRouteWaypoint(undefined); setFormError('') } }
  const addPoint = (point: Position) => { if (points.length >= 500) { setFormError('This planner supports up to 500 vertices per mission area.'); return } setPoints(current => [...current, point]); setFormError('') }
  const movePoint = (index: number, point: Position) => { setPoints(current => current.map((p, i) => i === index ? point : p)); setFormError('') }
  const removePoint = (index: number) => { setPoints(current => current.filter((_, i) => i !== index)); setFormError('') }
  const clearDraft = () => { if ((points.length || name || description) && !window.confirm('Clear the current mission polygon and form?')) return; setPoints([]); setName(''); setDescription(''); setFormError('') }
  const closeDrawing = () => { if (points.length < 3) { setFormError('Add at least three vertices before finishing the polygon.'); return } setDrawing(false); setFormError('') }
  const cancelDraft = () => { if ((points.length || routeEditing && routePoints.length || name || description) && !window.confirm('Discard the current mission edits?')) return false; setDrawing(false); setRouteEditing(false); setPoints([]); setRoutePoints(selected?.route?.coordinates ?? []); setName(''); setDescription(''); setFormError(''); routeHistory.current = []; return true }
  const cancelRouteEditing = () => { setRouteEditing(false); setRoutePoints(selected?.route?.coordinates ?? []); setName(selected?.name ?? ''); setDescription(selected?.description ?? ''); setFormError(''); routeHistory.current = [] }
  const goToList = () => { if ((drawing || routeEditing) && !cancelDraft()) return; selectionRequest.current++; setSelected(undefined); setDrawing(false); setRouteEditing(false); setChecking(false); setMobileNav(false) }
  const saveMission = async (event: FormEvent) => {
    event.preventDefault()
    const cleanName = name.trim()
    if (!cleanName) { setFormError('Enter a mission name.'); return }
    if (cleanName.length > 120) { setFormError('Mission name must be 120 characters or fewer.'); return }
    if (description.length > 1000) { setFormError('Description must be 1,000 characters or fewer.'); return }
    if (routeEditing ? routeErrors.length : geometryErrors.length) { setFormError((routeEditing ? routeErrors : geometryErrors).join(' ')); return }
    const requestId = selectionRequest.current
    setSaving(true); setFormError('')
    try {
      const input = routeEditing
        ? { name: cleanName, description: description.trim() || undefined, route: toRouteGeometry(routePoints) }
        : { name: cleanName, description: description.trim() || undefined, geometry: toGeoJson(points) }
      const created = selected && routeEditing ? await missionApi.update(selected.id, input) : await missionApi.create(input)
      if (selectionRequest.current !== requestId) { void refresh(true); return }
      weatherController.current?.abort(); dispatchWeather({ type: 'reset' })
      setMissions(current => selected && routeEditing ? current.map(item => item.id === created.id ? created : item) : pageIndex === 0 ? [created, ...current].slice(0, 20) : [created]); setSelected(created); setDrawing(false); setNotice('Mission saved to the Mission Service.')
      setRouteEditing(false); setRoutePoints(created.route?.coordinates ?? []); routeHistory.current = []
      setPageIndex(0)
      if (pageIndex === 0) void refresh(true)
    } catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Mission could not be saved. Your draft is still here.') }
    finally { setSaving(false) }
  }
  const runCheck = async () => {
    if (!selected) return
    const missionId = selected.id; const requestId = selectionRequest.current
    setChecking(true); setCheck(undefined); setCheckAt(undefined); setCheckError('')
    try { const result = await missionApi.check(missionId); if (selectionRequest.current === requestId) { setCheck(result); setCheckAt(new Date().toISOString()) } }
    catch (cause) { if (selectionRequest.current === requestId) { const e = cause as ApiError; setCheckError(e.status === 400 ? 'This saved mission has no geometry to check.' : e.message || 'The check failed. Try again.') } }
    finally { if (selectionRequest.current === requestId) setChecking(false) }
  }
  const runWeatherAssessment = async () => {
    if (!selected) return
    const requestId = selectionRequest.current
    const geometry = selected.geometry ?? selected.route
    if (!geometry) { dispatchWeather({ type: 'error', error: 'This mission has no Polygon or route geometry to assess.', validation: true }); return }
    weatherController.current?.abort()
    const controller = new AbortController(); weatherController.current = controller
    dispatchWeather({ type: 'loading' })
    try {
      const result = await weatherApi.assess({ assessmentType: selected.route ? 'ROUTE' : 'POLYGON', geometry }, controller.signal)
      if (!controller.signal.aborted && selectionRequest.current === requestId) dispatchWeather({ type: 'result', assessment: result })
    } catch (cause) {
      if (controller.signal.aborted || selectionRequest.current !== requestId) return
      const error = cause instanceof WeatherApiError ? cause : new WeatherApiError('Weather assessment failed.', 'provider')
      dispatchWeather({ type: 'error', error: error.message, validation: error.kind === 'validation' })
    }
  }
  const exportMission = () => {
    if (!selected) return
    try {
      const feature = missionGeoJson(selected)
      const blob = new Blob([JSON.stringify(feature, null, 2)], { type: 'application/geo+json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a'); link.href = url; link.download = `${safeFileName(selected.name)}.geojson`; link.click()
      URL.revokeObjectURL(url); setExportError(''); setNotice('GeoJSON exported.')
    } catch (cause) { setExportError(cause instanceof Error ? cause.message : 'GeoJSON export is unavailable.') }
  }
  const exportRoute = () => {
    if (!selected) return
    try { downloadGeoJson(missionRouteGeoJson(selected), `${safeFileName(selected.name)}-route.geojson`); setExportError(''); setNotice('Route GeoJSON exported.') }
    catch (cause) { setExportError(cause instanceof Error ? cause.message : 'Route export is unavailable.') }
  }
  const removeSavedRoute = async () => {
    if (!selected?.route || !window.confirm('Delete this saved route? This cannot be undone.')) return
    const current = selected; const requestId = selectionRequest.current; setSaving(true); setFormError('')
    try {
      const updated = await missionApi.update(current.id, { name: current.name, description: current.description ?? undefined })
      if (selectionRequest.current !== requestId) return
      setSelected(updated); weatherController.current?.abort(); dispatchWeather({ type: 'reset' }); setRoutePoints([]); setRouteEditing(false); setMissions(list => list.map(item => item.id === updated.id ? updated : item)); setNotice('Saved route deleted.')
    } catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Route could not be deleted.') }
    finally { setSaving(false) }
  }
  const reorderWaypoint = (index: number, delta: number) => { const target = index + delta; if (target < 0 || target >= routePoints.length) return; const next = [...routePoints]; [next[index], next[target]] = [next[target], next[index]]; changeRoute(next); setSelectedRouteWaypoint(target) }
  const addRouteWaypoint = (point: Position) => { if (routePoints.length >= 500) { setFormError('This planner supports up to 500 waypoints per route.'); return } changeRoute([...routePoints, point]); setSelectedRouteWaypoint(routePoints.length) }
  const moveRouteWaypoint = (index: number, point: Position) => changeRoute(routePoints.map((current, i) => i === index ? point : current))

  return <div className="app-shell">
    <header className="topbar">
      <button className="mobile-menu icon-btn" aria-label="Open navigation" onClick={() => setMobileNav(!mobileNav)}><Menu size={19} /></button>
      <a className="brand" href="#workspace" aria-label="SkyGrid workspace"><span className="brand-mark"><Compass size={19} strokeWidth={1.8} /></span><span>skygrid<span className="brand-period">.</span></span></a>
      <div className="top-divider" />
      <div className="workspace-select"><span className="workspace-kicker">WORKSPACE</span><span>Mission operations <ChevronDown size={13} /></span></div>
      <div className="top-spacer" />
      <div className={`service-pill ${service}`}><span className="status-dot" />{service === 'online' ? 'Mission service online' : service === 'connecting' ? 'Connecting to service' : service === 'offline' ? 'Service unavailable' : 'Service error'}</div>
      <button className="help-button" title="About SkyGrid" onClick={() => setNotice('SkyGrid provides geospatial decision support using configured demonstration data. It is not flight clearance.')}><CircleHelp size={17} /><span>Help</span></button>
      <div className="avatar" aria-label="Workspace user">SG</div>
    </header>
    <div className="workspace-layout">
      <nav className={`rail ${mobileNav ? 'rail-open' : ''}`} aria-label="Primary navigation">
        <button className="rail-item active" title="Mission workspace" aria-label="Mission workspace" onClick={goToList}><Compass size={19} /><span>Workspace</span></button>
        <button className="rail-item" title="Missions" aria-label="Missions" onClick={goToList}><Plane size={19} /><span>Missions</span></button>
        <div className="rail-bottom"><span className="rail-version">DEMO<br />0.1</span></div>
      </nav>
      {mobileNav && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}
      <main className="main-workspace" id="workspace">
        <section className="map-stage" aria-label="Mission map">
          <MissionMap mission={selected} drawing={drawing} routeEditing={routeEditing} points={points} routePoints={routePoints} selectedRouteWaypoint={selectedRouteWaypoint} onAdd={routeEditing ? addRouteWaypoint : addPoint} onMove={movePoint} onRemove={removePoint} onSelectRouteWaypoint={setSelectedRouteWaypoint} onMoveRouteWaypoint={moveRouteWaypoint} tileFailed={() => setTileError(true)} tileLoaded={() => setTileError(false)} />
          <div className="map-heading"><div className="eyebrow">OPERATIONS / MAP VIEW</div><h1>{routeEditing ? 'Plan a flight route' : drawing ? 'Plan a mission area' : selected ? 'Mission overview' : 'Mission workspace'}</h1><p>{routeEditing ? 'Add ordered waypoints along the intended ground track.' : drawing ? 'Define the intended operating area on the map.' : selected ? 'Review the saved mission footprint and available spatial checks.' : 'Plan and inspect mission areas and routes against available geospatial data.'}</p></div>
          <div className="map-float-actions"><button className="button button-quiet" onClick={() => setPanelOpen(!panelOpen)}><ChevronRight className={panelOpen ? 'rotate-180' : ''} size={16} />{panelOpen ? 'Collapse panel' : 'Open panel'}</button></div>
          {(drawing || routeEditing) && <div className="drawing-banner"><span className="drawing-pulse" /><div><strong>{routeEditing ? 'Editing ordered route' : 'Drawing mission boundary'}</strong><span>{routeEditing ? `${routePoints.length} waypoints` : `${points.length} ${points.length === 1 ? 'vertex' : 'vertices'} placed`}</span></div>{routeEditing && <button onClick={undoRoute} disabled={!routeHistory.current.length} className="undo-button" aria-label="Undo last waypoint edit"><Undo2 size={14} /> Undo</button>}{drawing && <button onClick={() => setPoints(current => current.slice(0, -1))} disabled={!points.length} className="undo-button">Undo last</button>}<button onClick={cancelDraft} aria-label="Cancel editing" title="Cancel editing"><X size={16} /></button></div>}
        </section>
        <aside className={`context-panel ${panelOpen ? '' : 'panel-collapsed'}`} aria-label="Mission planning panel">
          <div className="panel-grip" aria-hidden="true" />
          {!panelOpen ? <button className="collapsed-open" onClick={() => setPanelOpen(true)} aria-label="Open mission panel"><ChevronRight size={18} /></button> : <>
            <div className="panel-head"><div><div className="eyebrow">MISSION CONTROL</div><h2>{routeEditing ? selected ? 'Edit flight route' : 'New flight route' : drawing ? 'New mission area' : selected ? 'Mission details' : 'Missions'}</h2></div><button className="icon-btn panel-refresh" onClick={() => void refresh()} title="Refresh missions" aria-label="Refresh missions"><RefreshCw size={16} /></button></div>
            {drawing || routeEditing ? <form className="plan-form" onSubmit={saveMission}>
              {routeEditing ? <>
                <div className="section-label"><RouteIcon size={15} /> ORDERED FLIGHT ROUTE <span className="label-required">2 WAYPOINTS MIN</span></div>
                <p className="route-instructions">Click the map to append waypoints. Drag a numbered marker to move it. Use the waypoint list to select, reorder, or remove points.</p>
                <div className={`geometry-status ${routeErrors.length ? '' : 'complete'}`}><span className={`geometry-indicator ${routeErrors.length ? '' : 'complete'}`} />{routeErrors.length ? routeErrors[0] : `${routePoints.length} ordered waypoints · valid LineString`}</div>
                <div className="route-summary">Estimated ground-track distance <strong>{routePoints.length >= 2 && !routeErrors.length ? formatDistance(estimatedGroundTrackMeters(routePoints)) : '—'}</strong><small>Geodesic estimate only; not guaranteed flight distance.</small></div>
                <ol className="waypoint-list" aria-label="Ordered route waypoints">{routePoints.map(([longitude, latitude], index) => <li key={`${index}-${longitude}-${latitude}`} className={selectedRouteWaypoint === index ? 'waypoint-active' : ''}><button type="button" className="waypoint-select" onClick={() => setSelectedRouteWaypoint(index)} aria-label={`Select waypoint ${index + 1}`}><span>{index + 1}</span><span>{latitude.toFixed(5)}°, {longitude.toFixed(5)}°</span></button><button type="button" className="waypoint-order" disabled={index === 0} onClick={() => reorderWaypoint(index, -1)} aria-label={`Move waypoint ${index + 1} earlier`}><ArrowUp size={14} /></button><button type="button" className="waypoint-order" disabled={index === routePoints.length - 1} onClick={() => reorderWaypoint(index, 1)} aria-label={`Move waypoint ${index + 1} later`}><ArrowDown size={14} /></button><button type="button" className="waypoint-delete" onClick={() => changeRoute(routePoints.filter((_, i) => i !== index))} aria-label={`Delete waypoint ${index + 1}`}><X size={14} /></button></li>)}</ol>
              </> : <><div className="section-label"><Hexagon size={15} /> MISSION AREA <span className="label-required">REQUIRED</span></div><div className="geometry-status"><span className={`geometry-indicator ${geometryErrors.length ? '' : 'complete'}`} />{points.length < 3 ? `${points.length} of 3 minimum vertices` : geometryErrors.length ? 'Review polygon geometry' : 'Polygon is valid'}</div>{points.length >= 3 && geometryErrors.length > 0 && <div className="inline-error" role="alert"><AlertTriangle size={15} />{geometryErrors.join(' ')}</div>}</>}
              <div className="field"><label htmlFor="mission-name">Mission name <span>*</span></label><input autoFocus id="mission-name" value={name} maxLength={120} onChange={e => setName(e.target.value)} placeholder="e.g. Riverside infrastructure survey" /><small>{name.length}/120 characters</small></div>
              <div className="field"><label htmlFor="mission-description">Description <span className="optional">OPTIONAL</span></label><textarea id="mission-description" value={description} maxLength={1000} onChange={e => setDescription(e.target.value)} placeholder="Purpose or operational notes" rows={3} /><small>{description.length}/1,000 characters</small></div>
              {!routeEditing && <div className="field-actions"><button type="button" className="button button-outline" onClick={clearDraft} disabled={!points.length && !name && !description}>Clear draft</button>{drawing ? <button type="button" className="button button-outline" disabled={points.length < 3} onClick={closeDrawing}>Finish boundary</button> : <button type="button" className="button button-outline" onClick={() => setDrawing(true)}>Edit boundary</button>}</div>}
                {routeEditing && <div className="field-actions">{selected?.route ? <button type="button" className="button button-danger" onClick={removeSavedRoute} disabled={saving}>Delete saved route</button> : <button type="button" className="button button-outline" onClick={() => { if (!routePoints.length || window.confirm('Clear all waypoints from this unsaved route?')) changeRoute([]) }} disabled={!routePoints.length}>Clear route</button>}<button type="button" className="button button-outline" onClick={cancelDraft}>Cancel edits</button></div>}
              <div className="validation-note"><ShieldAlert size={16} /><span>{routeEditing ? 'Route order is preserved when saved. No speed, altitude, obstacle avoidance, or clearance is calculated.' : 'Boundary geometry is validated before saving. This workspace does not provide flight clearance.'}</span></div>
              {formError && <div className="inline-error" role="alert"><AlertTriangle size={15} />{formError}</div>}
              <button className="button button-primary save-button" type="submit" disabled={saving || (routeEditing ? routeErrors.length > 0 : geometryErrors.length > 0)}><FilePlus2 size={16} />{saving ? 'Saving mission…' : selected && routeEditing ? 'Save route changes' : 'Save mission'}</button>
            </form> : selected ? <MissionDetails mission={selected} check={check} checkAt={checkAt} checking={checking} checkError={checkError} detailState={detailState} exportError={exportError} actionError={formError} coverage={coverage} coverageState={coverageState} saving={saving} weatherState={weatherState} onWeather={runWeatherAssessment} onEditRoute={editSavedRoute} onExportRoute={exportRoute} onRemoveRoute={removeSavedRoute} onExport={exportMission} onCheck={runCheck} onBack={() => { weatherController.current?.abort(); selectionRequest.current++; setSelected(undefined); setCheck(undefined); setChecking(false); dispatchWeather({ type: 'reset' }) }} /> : <>
              <button className="button button-primary create-button" onClick={startDraft}><FilePlus2 size={16} /> Plan a mission</button>
              <button className="button button-outline create-button route-create" onClick={startRouteDraft}><RouteIcon size={16} /> Plan a flight route</button>
              <div className="list-toolbar"><div className="section-label">SAVED MISSIONS <span className="count-pill">{pageInfo.totalElements}</span></div><button className="icon-btn" title="Refresh missions" aria-label="Refresh missions" onClick={() => void refresh()}><RefreshCw size={15} /></button></div>
              <label className="search-box"><Search size={15} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Filter this page" aria-label="Filter current mission page" /></label>
              <MissionList loading={loading} slow={slow} error={error} missions={filtered} search={search} page={pageInfo.page} totalPages={pageInfo.totalPages} onPage={setPageIndex} onSelect={selectMission} onRetry={() => void refresh()} />
              <div className="dataset-footnote"><ShieldAlert size={15} /><p><strong>Decision support only</strong><br />Restricted-zone data availability and completeness are not established. Never use this check as flight clearance.</p></div>
            </>}
          </>}
        </aside>
        <div className="statusbar"><span><Activity size={13} /> {service === 'online' ? 'Mission API connected' : service === 'connecting' ? 'Connecting to Mission API' : 'Mission API unreachable'}</span><span className="status-separator" /><span><Clock3 size={13} /> {pageInfo.totalElements} saved {pageInfo.totalElements === 1 ? 'mission' : 'missions'}</span><span className="status-right">WGS 84 · EPSG:4326</span></div>
      </main>
    </div>
    {tileError && <span className="sr-only" role="status">Map tile requests are failing.</span>}
    {notice && <div className="toast" role="status"><Check size={16} />{notice}<button aria-label="Dismiss" onClick={() => setNotice('')}><X size={15} /></button></div>}
  </div>
}

function MissionList({ loading, slow, error, missions, search, page, totalPages, onPage, onSelect, onRetry }: { loading: boolean; slow: boolean; error?: ApiError; missions: Mission[]; search: string; page: number; totalPages: number; onPage: (page: number) => void; onSelect: (m: Mission) => void; onRetry: () => void }) {
  if (loading) return <div className="mission-list"><div className="load-label"><span className="spinner" />{slow ? 'Service may be waking up' : 'Retrieving saved missions'}</div>{slow && <p className="sleep-note">Free hosted services can take a moment to start. Please keep this workspace open.</p>}{[0, 1, 2].map(i => <div key={i} className="mission-skeleton"><i /><span><b /><b /></span></div>)}</div>
  if (error) return <div className="empty-state error-state"><div className="empty-icon"><Activity size={20} /></div><h3>{error.kind === 'timeout' ? 'Service is taking longer' : 'Missions unavailable'}</h3><p>{error.message}</p>{(error.kind === 'network' || error.kind === 'timeout') && <p>The Mission Service may be starting after an idle period.</p>}<button className="button button-outline" onClick={onRetry}><RefreshCw size={14} /> Retry connection</button></div>
  if (!missions.length) return <div className="empty-state"><div className="empty-icon"><Plane size={20} /></div><h3>{search ? 'No matching missions' : 'No missions yet'}</h3><p>{search ? 'Try a different search term.' : 'Your saved mission areas will appear here. Start by drawing an operating boundary.'}</p>{!search && <span className="empty-action-hint">Start with <strong>Plan a mission</strong> above.</span>}</div>
  return <><div className="mission-list" role="list">{missions.map(mission => <button key={mission.id} role="listitem" className="mission-row" onClick={() => onSelect(mission)}><span className="mission-glyph">{mission.route ? <RouteIcon size={15} /> : <Plane size={15} />}</span><span className="mission-row-copy"><strong>{mission.name}</strong><small>{mission.route ? 'Flight route' : mission.geometry ? 'Area defined' : 'No boundary'} <i /> {new Date(mission.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</small></span><ChevronRight size={15} /></button>)}</div>{totalPages > 1 && <div className="pagination"><button className="button button-outline" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {page + 1} of {totalPages}</span><button className="button button-outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button></div>}</>
}

function MissionDetails({ mission, check, checkAt, checking, checkError, detailState, exportError, actionError, coverage, coverageState, saving, weatherState, onWeather, onEditRoute, onExportRoute, onRemoveRoute, onExport, onCheck, onBack }: { mission: Mission; check?: ZoneCheck; checkAt?: string; checking: boolean; checkError: string; detailState: 'ready' | 'loading' | 'unavailable'; exportError: string; actionError: string; coverage: AirspaceDataset[]; coverageState: 'loading' | 'ready' | 'unavailable'; saving: boolean; weatherState: WeatherPanelState; onWeather: () => void; onEditRoute: () => void; onExportRoute: () => void; onRemoveRoute: () => void; onExport: () => void; onCheck: () => void; onBack: () => void }) {
  const metrics = mission.geometry ? (() => { try { return { value: measurePolygon(mission.geometry), error: '' } } catch { return { value: undefined, error: 'Saved polygon is invalid; measurements are unavailable.' } } })() : undefined
  return <div className="detail-content"><button className="back-link" onClick={onBack}><ArrowLeft size={14} /> All missions</button>{actionError && <div className="inline-error" role="alert"><AlertTriangle size={14} />{actionError}</div>}<div className="detail-title"><span className="detail-glyph"><Plane size={18} /></span><div><h3>{mission.name}</h3><span>Saved mission</span></div></div>
    <div className="detail-section"><div className="section-label">MISSION OPERATIONS</div><dl className="details-dl"><div><dt>State</dt><dd><span className="operation-state"><i /> Saved</span></dd></div><div><dt>Mission ID</dt><dd className="mission-id" title={mission.id}>{mission.id}</dd></div><div><dt>Created</dt><dd title={mission.createdAt}>{new Date(mission.createdAt).toLocaleString()}</dd></div><div><dt>Geometry</dt><dd>{mission.geometry ? metrics?.value ? 'Valid Polygon · WGS 84' : metrics?.error || 'Invalid Polygon' : mission.route ? 'Valid LineString route · WGS 84' : 'Not provided'}</dd></div>{metrics?.value && <><div><dt>Area</dt><dd>{formatArea(metrics.value.areaM2)}</dd></div><div><dt>Perimeter</dt><dd>{formatDistance(metrics.value.perimeterM)}</dd></div></>}{mission.route && <><div><dt>Waypoints</dt><dd>{mission.route.coordinates.length}</dd></div><div><dt>Ground track</dt><dd>{formatDistance(estimatedGroundTrackMeters(mission.route.coordinates))} estimated</dd></div></>}</dl>{mission.description && <p className="mission-description">{mission.description}</p>}{mission.geometry && <button className="button button-outline export-button" disabled={detailState !== 'ready' || !metrics?.value} onClick={onExport}><ArrowDownToLine size={15} /> Export polygon GeoJSON</button>}{mission.route && <div className="route-detail-actions"><button className="button button-outline export-button" disabled={detailState !== 'ready'} onClick={onExportRoute}><ArrowDownToLine size={15} /> Export route GeoJSON</button><button className="button button-outline" disabled={saving || detailState !== 'ready'} onClick={onEditRoute}><RouteIcon size={15} /> Edit route</button><button className="button button-danger" disabled={saving} onClick={onRemoveRoute}>Delete saved route</button></div>}{exportError && <div className="inline-error" role="alert"><AlertTriangle size={14} />{exportError}</div>}</div>
    <div className="detail-section analysis-section"><div className="section-label">AIRSPACE CHECK</div><p className="analysis-intro">Query stored, validated restriction polygons for intersections with this mission area.</p>
      {detailState === 'loading' && <div className="analysis-result neutral" role="status"><span className="spinner small" /><span><strong>Loading saved mission</strong><small>Refreshing mission details from the service.</small></span></div>}{detailState === 'unavailable' && <div className="inline-error" role="status"><AlertTriangle size={14} />Saved details could not be refreshed. Showing the last mission-list copy.</div>}
      {!mission.geometry ? <div className="analysis-result neutral"><AlertTriangle size={17} /><span><strong>{mission.route ? 'Polygon area required' : 'Geometry required'}</strong><small>{mission.route ? 'This check currently accepts Polygon mission areas; route corridor checks are not implemented.' : 'This mission has no saved polygon boundary.'}</small></span></div> : <>{check && <div className={`analysis-result ${check.intersects ? 'detected' : 'clear'}`}><span className="result-mark">{check.intersects ? <AlertTriangle size={17} /> : <Check size={17} />}</span><span><strong>{check.intersects ? `Intersection detected · ${check.zones.length} ${check.zones.length === 1 ? 'zone' : 'zones'}` : 'No intersection in returned dataset'}</strong><small>{check.intersects ? check.zones.map(z => z.name).join(', ') : 'No matching feature was returned. Dataset completeness and population are not established.'}{checkAt ? ` Checked ${new Date(checkAt).toLocaleString()}.` : ''}</small></span></div>}{!check && !checking && !checkError && <div className="analysis-result neutral"><Clock3 size={16} /><span><strong>Not checked</strong><small>Run a spatial check to query currently available stored restriction polygons.</small></span></div>}<button className="button button-outline analysis-button" disabled={checking || detailState !== 'ready'} onClick={onCheck}>{checking ? <><span className="spinner small" /> Checking stored zones…</> : <><ShieldAlert size={15} /> {check ? 'Run check again' : 'Run spatial check'}</>}</button></>}
      {checkError && <div className="inline-error analysis-error" role="alert"><AlertTriangle size={14} />{checkError}<button onClick={onCheck}>Retry</button></div>}
      {(check || mission.geometry) && <p className="analysis-disclaimer">{check?.dataNotice || 'Restricted-zone data completeness is unknown. This result is decision support, not flight clearance or a safety determination.'}</p>}
      {check?.zones.length ? <ul className="zone-list">{check.zones.map(zone => <li key={zone.id}><span className="zone-dot" /><span>{zone.name}</span>{zone.demonstration && <small>DEMO</small>}</li>)}</ul> : null}
    </div>
    <WeatherAssessmentPanel state={weatherState} route={Boolean(mission.route)} hasGeometry={Boolean(mission.route || mission.geometry)} onAssess={onWeather} disabled={detailState !== 'ready'} />
    {mission.route && <div className="detail-section"><div className="section-label">ORDERED WAYPOINTS</div><ol className="saved-waypoints">{mission.route.coordinates.map(([longitude, latitude], index) => <li key={`${index}-${longitude}-${latitude}`}><span>{index + 1}</span><code>Lat {latitude.toFixed(5)}° · Lon {longitude.toFixed(5)}°</code></li>)}</ol></div>}
    <div className="detail-section"><div className="section-label">DATASET COVERAGE</div>{coverageState === 'loading' ? <p className="coverage-state" role="status">Loading source inventory…</p> : coverageState === 'unavailable' ? <p className="coverage-state">Source inventory is unavailable. Coverage cannot be assessed.</p> : coverage.length ? <ul className="coverage-list">{coverage.map(dataset => <li key={`${dataset.country}-${dataset.source}-${dataset.dataset}`}><strong>{dataset.country} · {dataset.dataset}</strong><span>{dataset.status} · {dataset.spatialFeatures} spatial · {dataset.nonSpatialCount} non-spatial · {dataset.rejectedCount} rejected</span><small>{dataset.limitations || 'Source completeness is unverified.'}</small></li>)}</ul> : <p className="coverage-state">No imported dataset inventory is available. An empty check does not indicate clear airspace.</p>}</div>
    <div className="notice-card"><ShieldAlert size={16} /><p><strong>Not flight clearance</strong><br />SkyGrid does not provide aviation authorization or authoritative airspace data.</p></div>
  </div>
}

function WeatherAssessmentPanel({ state, route, hasGeometry, onAssess, disabled }: { state: WeatherPanelState; route: boolean; hasGeometry: boolean; onAssess: () => void; disabled: boolean }) {
  const assessment = 'assessment' in state ? state.assessment : undefined
  const stale = weatherAssessmentIsStale(state)
  return <div className="detail-section weather-panel"><div className="section-label">WEATHER FORECAST</div>
    <p className="analysis-intro">{!hasGeometry ? 'This mission has no saved geometry to assess.' : route ? 'Forecast samples are evaluated along the complete saved route.' : 'A representative interior point is used for this Polygon mission.'}</p>
    <button className="button button-outline analysis-button" onClick={onAssess} disabled={disabled || !hasGeometry || state.status === 'loading'}>
      {state.status === 'loading' ? <><span className="spinner small" /> Retrieving forecast…</> : <><RefreshCw size={15} /> {assessment ? 'Refresh weather assessment' : 'Assess weather conditions'}</>}
    </button>
    {state.status === 'idle' && <p className="analysis-disclaimer">Forecast retrieval is on demand. No weather values are shown until the provider returns an assessment.</p>}
    {state.status === 'loading' && <div className="weather-state" role="status">{stale ? 'Refreshing forecast. The previous assessment shown below is stale.' : 'Loading forecast data from Open-Meteo…'}</div>}
    {(state.status === 'unavailable' || state.status === 'validation-error') && <div className="inline-error" role="alert">{state.status === 'validation-error' ? 'Assessment request invalid: ' : 'Weather assessment unavailable: '}{state.error}{stale && <span> Previous data below is stale.</span>}</div>}
    {assessment && <>
      <div className={`weather-meta ${stale ? 'weather-stale' : ''}`}><strong>{stale ? 'STALE ASSESSMENT' : assessment.status === 'PARTIAL' ? 'PARTIAL FORECAST DATA' : 'FORECAST AVAILABLE'}</strong><span>{assessment.provider} · retrieved {new Date(assessment.retrievedAt).toLocaleString()}</span><span>Forecast horizon {assessment.forecastHours} h · times {assessment.timezone}</span><small>{assessment.strategy}</small></div>
      {assessment.status === 'PARTIAL' && <p className="weather-state">Some provider values or forecast times are missing. Missing values are marked unavailable and are not treated as zero.</p>}
      {!assessment.warnings.length && assessment.status === 'COMPLETE' && <div className="analysis-result clear"><Check size={16} /><span><strong>No configured weather thresholds were exceeded</strong><small>This reports only the returned forecast and general planning thresholds.</small></span></div>}
      {!assessment.warnings.length && assessment.status === 'PARTIAL' && <div className="analysis-result neutral"><AlertTriangle size={16} /><span><strong>No warnings in available values</strong><small>Incomplete data prevents a complete threshold comparison.</small></span></div>}
      <div className="weather-variation"><strong>Forecast range across time{route ? ' and route samples' : ''}</strong>{assessment.variation.map(value => <div key={value.field}><span>{weatherFieldLabel(value.field)}</span><span>{formatRange(value.minimum, value.maximum, value.unit)}</span>{route && <small>Sample-mean range: {formatRange(value.sampleMinimum, value.sampleMaximum, value.unit)}</small>}</div>)}</div>
      {route && <ol className="weather-samples" aria-label="Weather forecast by route sample">{assessment.samples.map(sample => <li key={sample.sampleIndex}><strong>Sample {sample.sampleIndex}</strong><span>Lat {sample.coordinate.latitude.toFixed(4)}° · Lon {sample.coordinate.longitude.toFixed(4)}°</span><small>{sample.forecast.length} hourly forecast times</small>{assessment.warnings.filter(w => w.sampleIndex === sample.sampleIndex).map((warning, index) => <em key={`${warning.code}-${warning.validAt}-${index}`}>{warning.field} {warning.measuredValue} {warning.unit} · {new Date(warning.validAt).toLocaleString()}</em>)}</li>)}</ol>}
      {!!assessment.warnings.length && <div className="weather-warnings"><strong>{assessment.warnings.length} threshold warning{assessment.warnings.length === 1 ? '' : 's'}</strong>{assessment.warnings.map((warning, index) => <div key={`${warning.code}-${warning.validAt}-${warning.sampleIndex}-${index}`}><b>{warning.code.replaceAll('_', ' ')}</b><span>{warning.measuredValue} {warning.unit} ≥ {warning.threshold} {warning.unit} · {new Date(warning.validAt).toLocaleString()}</span>{route && <small>Route sample {warning.sampleIndex} · {warning.coordinate.latitude.toFixed(4)}°, {warning.coordinate.longitude.toFixed(4)}°</small>}<small>{warning.explanation}</small></div>)}</div>}
      <ul className="weather-limitations">{assessment.limitations.map((limitation, index) => <li key={index}>{limitation}</li>)}</ul>
      <p className="analysis-disclaimer">Thresholds: wind {assessment.thresholds.windSpeedMs} m/s, gust {assessment.thresholds.windGustMs} m/s, preceding-hour precipitation {assessment.thresholds.precipitationMmPrecedingHour} mm. General planning indicators only; not aircraft-specific limits, safety advice, or flight clearance.</p>
    </>}
  </div>
}

function safeFileName(name: string) { return name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'mission' }
function downloadGeoJson(value: unknown, filename: string) { const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/geo+json' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 0) }
function formatArea(areaM2: number) { return areaM2 >= 1_000_000 ? `${(areaM2 / 1_000_000).toLocaleString(undefined, { maximumFractionDigits: 2 })} km²` : `${Math.round(areaM2).toLocaleString()} m²` }
function formatDistance(distanceM: number) { return distanceM >= 1000 ? `${(distanceM / 1000).toLocaleString(undefined, { maximumFractionDigits: 2 })} km` : `${Math.round(distanceM).toLocaleString()} m` }
function weatherFieldLabel(field: string) { return ({ temperature: 'Temperature', windSpeed: 'Wind speed', windGust: 'Wind gust', precipitation: 'Precipitation (preceding hour)' } as Record<string, string>)[field] ?? field }
function formatRange(minimum: number | null, maximum: number | null, unit: string) { return minimum === null || maximum === null ? 'Unavailable' : `${minimum}–${maximum} ${unit}` }
