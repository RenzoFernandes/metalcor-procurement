import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

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
  const [user, setUser] = useState<CurrentUser | null>(null)
  const value = useMemo(() => ({ user, setUser }), [user])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useCurrentUser(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useCurrentUser must be used inside <SessionProvider>')
  return value
}