import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { ExceptionSummary } from '../../api/types'
import { formatMoney, formatNumber } from '../../components/format'
import { useTranslation } from '../../i18n/I18nContext'
import { ChartCard } from './ChartCard'
import { AXIS_TEXT, CATEGORY_COLORS, GRIDLINE } from './colors'

const EXCEPTION_TYPES = ['price_variance', 'quantity_variance', 'invoice_before_receipt']

interface Row {
  exceptionType: string
  typeLabel: string
  open: number
  released: number
  openAmount: number
  releasedAmount: number
}

export function ExceptionSummaryChart({ rows, sql }: { rows: ExceptionSummary[]; sql: string }) {
  const { t, lang } = useTranslation()

  const data: Row[] = EXCEPTION_TYPES.map((type) => {
    const open = rows.find((r) => r.exceptionType === type && r.resolution === 'open')
    const released = rows.find((r) => r.exceptionType === type && r.resolution === 'released')
    return {
      exceptionType: type,
      typeLabel: t(`dashboard.exceptionType.${type}`),
      open: open?.invoiceCount ?? 0,
      released: released?.invoiceCount ?? 0,
      openAmount: open?.grossAmount ?? 0,
      releasedAmount: released?.grossAmount ?? 0,
    }
  })

  return (
    <ChartCard
      title={t('dashboard.charts.exceptionSummary.title')}
      description={t('dashboard.charts.exceptionSummary.description')}
      sql={sql}
    >
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} vertical={false} />
          <XAxis dataKey="typeLabel" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={{ stroke: GRIDLINE }} tickLine={false} />
          <YAxis tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} allowDecimals={false} width={40} />
          <Tooltip
            content={({ active, label, payload }) => {
              if (!active || !payload || payload.length === 0) return null
              const row = data.find((d) => d.typeLabel === label)
              return (
                <div className="chart-tooltip">
                  <p className="chart-tooltip__title">{label}</p>
                  {payload.map((p) => (
                    <p key={String(p.dataKey)} className="chart-tooltip__row">
                      <span className="chart-tooltip__swatch" style={{ background: p.color }} />
                      {p.name}: {formatNumber(Number(p.value), lang)} {t('dashboard.tooltip.invoices')}
                      {row && (
                        <span className="muted muted--small">
                          {' '}
                          ({formatMoney(p.dataKey === 'open' ? row.openAmount : row.releasedAmount, lang)})
                        </span>
                      )}
                    </p>
                  ))}
                </div>
              )
            }}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {/* Blue x aqua, not red x green: colorblind users must not rely on a red/green cue here. */}
          <Bar dataKey="open" name={t('dashboard.resolution.open')} fill={CATEGORY_COLORS[0]} radius={[4, 4, 0, 0]} />
          <Bar dataKey="released" name={t('dashboard.resolution.released')} fill={CATEGORY_COLORS[2]} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}