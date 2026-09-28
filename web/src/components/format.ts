import type { Language } from '../i18n/I18nContext'

const LOCALES: Record<Language, string> = { pt: 'pt-BR', en: 'en-US' }

export function formatMoney(value: number, lang: Language): string {
  return new Intl.NumberFormat(LOCALES[lang], { style: 'currency', currency: 'BRL' }).format(value)
}

export function formatNumber(value: number, lang: Language): string {
  return new Intl.NumberFormat(LOCALES[lang], { maximumFractionDigits: 3 }).format(value)
}

/** Money with a compact suffix (e.g. "R$ 1,2 mi" / "$1.2M"), for chart axes. */
export function formatCompactMoney(value: number, lang: Language): string {
  return new Intl.NumberFormat(LOCALES[lang], {
    style: 'currency',
    currency: 'BRL',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}

/** A 0-100 number as a percentage with one decimal (e.g. "94,3%"). */
export function formatPercent(value: number | null, lang: Language): string {
  if (value === null) return '—'
  return `${new Intl.NumberFormat(LOCALES[lang], { maximumFractionDigits: 1 }).format(value)}%`
}

/** ISO month (yyyy-mm-dd, first day of month) as a short label (e.g. "jan/26"). */
export function formatMonth(iso: string, lang: Language): string {
  const [y, m] = iso.slice(0, 10).split('-').map(Number)
  const label = new Intl.DateTimeFormat(LOCALES[lang], { month: 'short', year: '2-digit' }).format(new Date(y, m - 1, 1))
  return label.replace('.', '')
}

/** ISO date (yyyy-mm-dd) shown without timezone shifts. */
export function formatDate(iso: string | null, lang: Language): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Intl.DateTimeFormat(LOCALES[lang]).format(new Date(y, m - 1, d))
}

/** Today as yyyy-mm-dd in the browser's local time (value for <input type="date">). */
export function todayIso(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

export function formatDateTime(iso: string | null, lang: Language): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(LOCALES[lang], { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
}