import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

interface TechnicalModeValue {
  enabled: boolean
  toggle: () => void
}

const TechnicalModeContext = createContext<TechnicalModeValue | null>(null)

/** Off by default, kept in memory only (resets on reload). */
export function TechnicalModeProvider({ children }: { children: ReactNode }) {
  const [enabled, setEnabled] = useState(false)
  const value = useMemo(() => ({ enabled, toggle: () => setEnabled((v) => !v) }), [enabled])
  return <TechnicalModeContext.Provider value={value}>{children}</TechnicalModeContext.Provider>
}

export function useTechnicalMode(): TechnicalModeValue {
  const value = useContext(TechnicalModeContext)
  if (!value) throw new Error('useTechnicalMode must be used inside <TechnicalModeProvider>')
  return value
}