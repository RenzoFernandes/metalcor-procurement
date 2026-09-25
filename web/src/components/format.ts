import type { Language } from '../i18n/I18nContext'

const LOCALES: Record<Language, string> = { pt: 'pt-BR', en: 'en-US' }

export function formatMoney(value: number, lang: Language): string {
  return new Intl.NumberFormat(LOCALES[lang], { style: 'currency', currency: 'BRL' }).format(value)
}

export function formatNumber(value: number, lang: Language): string {
  return new Intl.NumberFormat(LOCALES[lang], { maximumFractionDigits: 3 }).format(value)
}

/** ISO date (yyyy-mm-dd) shown without timezone shifts. */
export function formatDate(iso: string | null, lang: Language): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Intl.DateTimeFormat(LOCALES[lang]).format(new Date(y, m - 1, d))
}

export function formatDateTime(iso: string | null, lang: Language): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(LOCALES[lang], { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
}