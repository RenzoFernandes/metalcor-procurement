import type { DashboardKpis } from '../../api/types'
import { formatMoney, formatPercent } from '../../components/format'
import { SqlBlock } from '../../components/SqlBlock'
import type { Language } from '../../i18n/I18nContext'
import { useTranslation } from '../../i18n/I18nContext'

export function KpiCards({ kpis }: { kpis: DashboardKpis }) {
  const { t, lang } = useTranslation()

  const cards: { label: string; value: string; sql: string }[] = [
    {
      label: t('dashboard.kpis.totalSpend'),
      value: formatMoney(kpis.totalSpend.data, lang),
      sql: kpis.totalSpend.sql,
    },
    {
      label: t('dashboard.kpis.orders'),
      value: formatCount(kpis.orders.data, lang),
      sql: kpis.orders.sql,
    },
    {
      label: t('dashboard.kpis.openExceptionInvoices'),
      value: formatCount(kpis.openExceptionInvoices.data, lang),
      sql: kpis.openExceptionInvoices.sql,
    },
    {
      label: t('dashboard.kpis.blockedAmount'),
      value: formatMoney(kpis.blockedAmount.data, lang),
      sql: kpis.blockedAmount.sql,
    },
    {
      label: t('dashboard.kpis.onTimeDelivery'),
      value: formatPercent(kpis.overallOnTimeDeliveryPct.data, lang),
      sql: kpis.overallOnTimeDeliveryPct.sql,
    },
  ]

  return (
    <div className="kpi-grid">
      {cards.map((card) => (
        <div className="card kpi-card" key={card.label}>
          <p className="kpi-card__label">{card.label}</p>
          <p className="kpi-card__value">{card.value}</p>
          <SqlBlock sql={card.sql} />
        </div>
      ))}
    </div>
  )
}

function formatCount(value: number, lang: Language): string {
  return new Intl.NumberFormat(lang === 'pt' ? 'pt-BR' : 'en-US').format(value)
}