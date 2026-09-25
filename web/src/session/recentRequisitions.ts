/**
 * TEMPORARY LIMITATION: the API has no requisition listing endpoint, so "recent requisitions"
 * only lists what was created in this browser session (sessionStorage, per user id). It is not
 * a source of truth and disappears when the tab is closed.
 */

export interface RecentRequisition {
  id: number
  documentNumber: string
}

const MAX_ITEMS = 10
const keyFor = (userId: number) => `metalcor.recentRequisitions.${userId}`

export function loadRecentRequisitions(userId: number): RecentRequisition[] {
  try {
    const raw = sessionStorage.getItem(keyFor(userId))
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (e): e is RecentRequisition => e && typeof e.id === 'number' && typeof e.documentNumber === 'string',
    )
  } catch {
    return []
  }
}

export function rememberRequisition(userId: number, entry: RecentRequisition): void {
  try {
    const next = [entry, ...loadRecentRequisitions(userId).filter((e) => e.id !== entry.id)].slice(0, MAX_ITEMS)
    sessionStorage.setItem(keyFor(userId), JSON.stringify(next))
    window.dispatchEvent(new Event(CHANGE_EVENT))
  } catch {
    // Storage unavailable: the list is only a convenience.
  }
}

export const CHANGE_EVENT = 'metalcor:recent-requisitions-changed'