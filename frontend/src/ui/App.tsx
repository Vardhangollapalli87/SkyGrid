import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Activity, AlertTriangle, ArrowLeft, Check, ChevronDown, ChevronRight, CircleHelp, Clock3, Compass, FilePlus2, Hexagon, Menu, Plane, RefreshCw, Search, ShieldAlert, X } from 'lucide-react'
import { ApiError, missionApi, type Mission, type MissionPage, type ZoneCheck } from '../api/client'
import { toGeoJson, validatePolygon, type Position } from '../domain/geometry'
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
  const [points, setPoints] = useState<Position[]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [checking, setChecking] = useState(false)
  const [check, setCheck] = useState<ZoneCheck | undefined>(undefined)
  const [checkError, setCheckError] = useState('')
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
    if (!drawing) return
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') setDrawing(false) }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [drawing])

  const selectMission = async (mission: Mission) => {
    const requestId = ++selectionRequest.current
    setSelected(mission); setDrawing(false); setCheck(undefined); setCheckError(''); setFormError('')
    try { const latest = await missionApi.get(mission.id); if (selectionRequest.current === requestId) setSelected(latest) } catch { /* selected list item remains usable if detail fetch fails */ }
  }
  const startDraft = () => { setSelected(undefined); setCheck(undefined); setDrawing(true); setPoints([]); setName(''); setDescription(''); setFormError(''); setSaved(false); setPanelOpen(true) }
  const addPoint = (point: Position) => { if (points.length >= 500) { setFormError('This planner supports up to 500 vertices per mission area.'); return } setPoints(current => [...current, point]); setFormError('') }
  const movePoint = (index: number, point: Position) => { setPoints(current => current.map((p, i) => i === index ? point : p)); setFormError('') }
  const removePoint = (index: number) => { setPoints(current => current.filter((_, i) => i !== index)); setFormError('') }
  const clearDraft = () => { if ((points.length || name || description) && !window.confirm('Clear the current mission polygon and form?')) return; setPoints([]); setName(''); setDescription(''); setFormError('') }
  const closeDrawing = () => { if (points.length < 3) { setFormError('Add at least three vertices before finishing the polygon.'); return } setDrawing(false); setFormError('') }
  const cancelDraft = () => { if ((points.length || name || description) && !window.confirm('Discard this mission draft?')) return false; setDrawing(false); setPoints([]); setName(''); setDescription(''); setFormError(''); return true }
  const goToList = () => { if (drawing && !cancelDraft()) return; setSelected(undefined); setDrawing(false); setMobileNav(false) }
  const saveMission = async (event: FormEvent) => {
    event.preventDefault()
    const cleanName = name.trim()
    if (!cleanName) { setFormError('Enter a mission name.'); return }
    if (cleanName.length > 120) { setFormError('Mission name must be 120 characters or fewer.'); return }
    if (description.length > 1000) { setFormError('Description must be 1,000 characters or fewer.'); return }
    if (geometryErrors.length) { setFormError(geometryErrors.join(' ')); return }
    setSaving(true); setFormError('')
    try {
      const created = await missionApi.create({ name: cleanName, description: description.trim() || undefined, geometry: toGeoJson(points) })
      setMissions(current => pageIndex === 0 ? [created, ...current].slice(0, 20) : [created]); setSelected(created); setDrawing(false); setSaved(true); setNotice('Mission saved to the Mission Service.')
      setPageIndex(0)
      if (pageIndex === 0) void refresh(true)
    } catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Mission could not be saved. Your draft is still here.') }
    finally { setSaving(false) }
  }
  const runCheck = async () => {
    if (!selected) return
    setChecking(true); setCheck(undefined); setCheckError('')
    try { setCheck(await missionApi.check(selected.id)) }
    catch (cause) { const e = cause as ApiError; setCheckError(e.status === 400 ? 'This saved mission has no geometry to check.' : e.message || 'The check failed. Try again.') }
    finally { setChecking(false) }
  }

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
          <MissionMap mission={selected} drawing={drawing} points={points} onAdd={addPoint} onMove={movePoint} onRemove={removePoint} tileFailed={() => setTileError(true)} tileLoaded={() => setTileError(false)} />
          <div className="map-heading"><div className="eyebrow">OPERATIONS / MAP VIEW</div><h1>{drawing ? 'Plan a mission' : selected ? 'Mission overview' : 'Mission workspace'}</h1><p>{drawing ? 'Define the intended operating area on the map.' : selected ? 'Review the saved mission footprint and run a spatial check.' : 'Plan and inspect mission areas against available geospatial data.'}</p></div>
          <div className="map-float-actions"><button className="button button-quiet" onClick={() => setPanelOpen(!panelOpen)}><ChevronRight className={panelOpen ? 'rotate-180' : ''} size={16} />{panelOpen ? 'Collapse panel' : 'Open panel'}</button></div>
          {drawing && <div className="drawing-banner"><span className="drawing-pulse" /><div><strong>Drawing mission boundary</strong><span>{points.length} {points.length === 1 ? 'vertex' : 'vertices'} placed</span></div><button onClick={() => setPoints(current => current.slice(0, -1))} disabled={!points.length} className="undo-button">Undo last</button><button onClick={cancelDraft} aria-label="Cancel drawing" title="Cancel drawing"><X size={16} /></button></div>}
        </section>
        <aside className={`context-panel ${panelOpen ? '' : 'panel-collapsed'}`} aria-label="Mission planning panel">
          <div className="panel-grip" aria-hidden="true" />
          {!panelOpen ? <button className="collapsed-open" onClick={() => setPanelOpen(true)} aria-label="Open mission panel"><ChevronRight size={18} /></button> : <>
            <div className="panel-head"><div><div className="eyebrow">MISSION CONTROL</div><h2>{drawing ? 'New mission' : selected ? 'Mission details' : 'Missions'}</h2></div><button className="icon-btn panel-refresh" onClick={() => void refresh()} title="Refresh missions" aria-label="Refresh missions"><RefreshCw size={16} /></button></div>
            {drawing ? <form className="plan-form" onSubmit={saveMission}>
              <div className="section-label"><Hexagon size={15} /> MISSION AREA <span className="label-required">REQUIRED</span></div>
              <div className="geometry-status"><span className={`geometry-indicator ${geometryErrors.length ? '' : 'complete'}`} />{points.length < 3 ? `${points.length} of 3 minimum vertices` : geometryErrors.length ? 'Review polygon geometry' : 'Polygon is valid'}</div>
              {points.length >= 3 && geometryErrors.length > 0 && <div className="inline-error" role="alert"><AlertTriangle size={15} />{geometryErrors.join(' ')}</div>}
              <div className="field"><label htmlFor="mission-name">Mission name <span>*</span></label><input autoFocus id="mission-name" value={name} maxLength={120} onChange={e => setName(e.target.value)} placeholder="e.g. Riverside infrastructure survey" /><small>{name.length}/120 characters</small></div>
              <div className="field"><label htmlFor="mission-description">Description <span className="optional">OPTIONAL</span></label><textarea id="mission-description" value={description} maxLength={1000} onChange={e => setDescription(e.target.value)} placeholder="Purpose or operational notes" rows={3} /><small>{description.length}/1,000 characters</small></div>
              <div className="field-actions"><button type="button" className="button button-outline" onClick={clearDraft} disabled={!points.length && !name && !description}>Clear draft</button>{drawing ? <button type="button" className="button button-outline" disabled={points.length < 3} onClick={closeDrawing}>Finish boundary</button> : <button type="button" className="button button-outline" onClick={() => setDrawing(true)}>Edit boundary</button>}</div>
              <div className="validation-note"><ShieldAlert size={16} /><span>Boundary geometry is validated before saving. This workspace does not provide flight clearance.</span></div>
              {formError && <div className="inline-error" role="alert"><AlertTriangle size={15} />{formError}</div>}
              <button className="button button-primary save-button" type="submit" disabled={saving || geometryErrors.length > 0}><FilePlus2 size={16} />{saving ? 'Saving mission…' : saved ? 'Save another mission' : 'Save mission'}</button>
            </form> : selected ? <MissionDetails mission={selected} check={check} checking={checking} checkError={checkError} onCheck={runCheck} onBack={() => { setSelected(undefined); setCheck(undefined) }} /> : <>
              <button className="button button-primary create-button" onClick={startDraft}><FilePlus2 size={16} /> Plan a mission</button>
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
  return <><div className="mission-list" role="list">{missions.map(mission => <button key={mission.id} role="listitem" className="mission-row" onClick={() => onSelect(mission)}><span className="mission-glyph"><Plane size={15} /></span><span className="mission-row-copy"><strong>{mission.name}</strong><small>{mission.geometry ? 'Area defined' : 'No boundary'} <i /> {new Date(mission.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</small></span><ChevronRight size={15} /></button>)}</div>{totalPages > 1 && <div className="pagination"><button className="button button-outline" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {page + 1} of {totalPages}</span><button className="button button-outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button></div>}</>
}

function MissionDetails({ mission, check, checking, checkError, onCheck, onBack }: { mission: Mission; check?: ZoneCheck; checking: boolean; checkError: string; onCheck: () => void; onBack: () => void }) {
  return <div className="detail-content"><button className="back-link" onClick={onBack}><ArrowLeft size={14} /> All missions</button><div className="detail-title"><span className="detail-glyph"><Plane size={18} /></span><div><h3>{mission.name}</h3><span>Saved mission</span></div></div>
    <div className="detail-section"><div className="section-label">MISSION SUMMARY</div><dl className="details-dl"><div><dt>Created</dt><dd title={mission.createdAt}>{new Date(mission.createdAt).toLocaleString()}</dd></div><div><dt>Boundary</dt><dd>{mission.geometry ? 'Polygon · WGS 84' : 'Not provided'}</dd></div></dl>{mission.description && <p className="mission-description">{mission.description}</p>}</div>
    <div className="detail-section analysis-section"><div className="section-label">RESTRICTED-ZONE CHECK</div><p className="analysis-intro">Query stored restricted-zone polygons for intersections with this mission area.</p>
      {!mission.geometry ? <div className="analysis-result neutral"><AlertTriangle size={17} /><span><strong>Geometry required</strong><small>This mission has no saved polygon boundary.</small></span></div> : <>{check && <div className={`analysis-result ${check.intersects ? 'detected' : 'clear'}`}><span className="result-mark">{check.intersects ? <AlertTriangle size={17} /> : <Check size={17} />}</span><span><strong>{check.intersects ? `Intersection detected · ${check.zones.length} ${check.zones.length === 1 ? 'zone' : 'zones'}` : 'No intersection in returned dataset'}</strong><small>{check.intersects ? check.zones.map(z => z.name).join(', ') : 'The API cannot confirm whether the restricted-zone dataset is complete or populated.'}</small></span></div>}<button className="button button-outline analysis-button" disabled={checking} onClick={onCheck}>{checking ? <><span className="spinner small" /> Checking stored zones…</> : <><ShieldAlert size={15} /> {check ? 'Run check again' : 'Run spatial check'}</>}</button></>}
      {checkError && <div className="inline-error analysis-error" role="alert"><AlertTriangle size={14} />{checkError}<button onClick={onCheck}>Retry</button></div>}
      {(check || mission.geometry) && <p className="analysis-disclaimer">{check?.dataNotice || 'Restricted-zone data completeness is unknown. This result is decision support, not flight clearance or a safety determination.'}</p>}
      {check?.zones.length ? <ul className="zone-list">{check.zones.map(zone => <li key={zone.id}><span className="zone-dot" /><span>{zone.name}</span>{zone.demonstration && <small>DEMO</small>}</li>)}</ul> : null}
    </div>
    <div className="notice-card"><ShieldAlert size={16} /><p><strong>Not flight clearance</strong><br />SkyGrid does not provide aviation authorization or authoritative airspace data.</p></div>
  </div>
}
