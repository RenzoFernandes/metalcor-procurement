import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import pt from './pt.json'
import en from './en.json'

export type Language = 'pt' | 'en'

export const LANGUAGES: Language[] = ['pt', 'en']

const dictionaries: Record<Language, typeof pt> = { pt, en }

type Params = Record<string, string | number>

interface I18nValue {
  lang: Language
  setLang: (lang: Language) => void
  t: (key: string, params?: Params) => string
}

const I18nContext = createContext<I18nValue | null>(null)

function lookup(dictionary: unknown, key: string): string | undefined {
  let node: unknown = dictionary
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === null) return undefined
    node = (node as Record<string, unknown>)[part]
  }
  return typeof node === 'string' ? node : undefined
}

/** Language is kept in memory only (resets on reload). Default is Portuguese. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Language>('pt')

  const t = useCallback(
    (key: string, params?: Params) => {
      const text = lookup(dictionaries[lang], key) ?? lookup(dictionaries.pt, key) ?? key
      if (!params) return text
      return text.replace(/\{(\w+)\}/g, (match, name: string) =>
        name in params ? String(params[name]) : match,
      )
    },
    [lang],
  )

  const value = useMemo(() => ({ lang, setLang, t }), [lang, t])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useTranslation(): I18nValue {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useTranslation must be used inside <I18nProvider>')
  return value
}