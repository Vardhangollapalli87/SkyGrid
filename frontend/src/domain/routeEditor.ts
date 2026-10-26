import type { Position } from './geometry'

export type RouteEditorState = { points: Position[]; history: Position[][] }
export type RouteEditorAction =
  | { type: 'load'; points: Position[] }
  | { type: 'replace'; points: Position[] }
  | { type: 'undo' }

const clone = (points: Position[]): Position[] => points.map(([longitude, latitude]) => [longitude, latitude])

export function createRouteEditorState(points: Position[] = []): RouteEditorState {
  return { points: clone(points), history: [] }
}

export function routeEditorReducer(state: RouteEditorState, action: RouteEditorAction): RouteEditorState {
  if (action.type === 'load') return createRouteEditorState(action.points)
  if (action.type === 'undo') {
    const previous = state.history.at(-1)
    return previous ? { points: clone(previous), history: state.history.slice(0, -1) } : state
  }
  return {
    points: clone(action.points),
    history: [...state.history.slice(-49), clone(state.points)],
  }
}
