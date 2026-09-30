import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { setApiUserId } from '../api/client'

export type Role = 'requester' | 'buyer' | 'approver' | 'finance' | 'manager'

export interface CurrentUser {
  id: number
  name: string
  role: Role
}

interface SessionValue {
  user: CurrentUser | null
  setUser: (user: CurrentUser | null) => void
}

const SessionContext = createContext<SessionValue | null>(null)

/** The chosen user is kept in memory only (lost on reload, by design for now). */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<CurrentUser | null>(null)
  // Updated before the state, so requests fired by the next render already carry the new X-User-Id.
  const setUser = useCallback((next: CurrentUser | null) => {
    setApiUserId(next?.id ?? null)
    setUserState(next)
  }, [])
  const value = useMemo(() => ({ user, setUser }), [user, setUser])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useCurrentUser(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useCurrentUser must be used inside <SessionProvider>')
  return value
}