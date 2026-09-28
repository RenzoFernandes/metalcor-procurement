import { Bar, BarChart, CartesianGrid, LabelList, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SupplierScorecard } from '../../api/types'
import { formatMoney, formatPercent } from '../../components/format'
import { useTranslation } from '../../i18n/I18nContext'
import { ChartCard } from './ChartCard'
import { AXIS_TEXT, CATEGORY_COLORS, GRIDLINE } from './colors'

// Display only: strips common legal-entity suffixes so the axis shows a short trade name.
// supplierName itself is untouched everywhere else (tooltips, data, exports).
const LEGAL_SUFFIX = /\s+(ltda\.?|s\/?a\.?|eireli|me|epp)\.?$/i

function shortSupplierName(name: string): string {
  return name.replace(LEGAL_SUFFIX, '').trim()
}

export function SupplierScorecardChart({ rows, sql }: { rows: SupplierScorecard[]; sql: string }) {
  const { t, lang } = useTranslation()

  // Ascending by spend: recharts' vertical category axis lists the first row at the bottom,
  // so the biggest spender ends up on top.
  const data = [...rows]
    .sort((a, b) => a.totalSpend - b.totalSpend)
    .map((s) => ({
      supplier: `${s.supplierCode} – ${shortSupplierName(s.supplierName)}`,
      onTimePct: s.onTimeDeliveryPct ?? 0,
      onTimePctRaw: s.onTimeDeliveryPct,
      exceptionRatePct: s.exceptionRatePct,
      totalSpend: s.totalSpend,
    }))

  const yAxisWidth = Math.min(240, Math.max(120, Math.max(...data.map((d) => d.supplier.length), 0) * 6.5))

  return (
    <ChartCard
      title={t('dashboard.charts.supplierScorecard.title')}
      description={t('dashboard.charts.supplierScorecard.description')}
      sql={sql}
    >
      <ResponsiveContainer width="100%" height={Math.max(240, data.length * 46)}>
        <BarChart data={data} layout="vertical" barGap={4} margin={{ top: 8, right: 32, left: 8, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} horizontal={false} />
          <XAxis
            type="number"
            domain={[0, 100]}
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={{ stroke: GRIDLINE }}
            tickLine={false}
            tickFormatter={(v: number) => `${v}%`}
          />
          <YAxis
            type="category"
            dataKey="supplier"
            tick={{ fill: AXIS_TEXT, fontSize: 13 }}
            axisLine={false}
            tickLine={false}
            width={yAxisWidth}
          />
          <Tooltip
            content={({ active, label, payload }) => {
              if (!active || !payload || payload.length === 0) return null
              const row = data.find((d) => d.supplier === label)
              return (
                <div className="chart-tooltip">
                  <p className="chart-tooltip__title">{label}</p>
                  <p className="chart-tooltip__row">
                    {t('dashboard.axis.onTimePct')}: {formatPercent(row?.onTimePctRaw ?? null, lang)}
                  </p>
                  <p className="chart-tooltip__row">
                    {t('dashboard.axis.exceptionRatePct')}: {formatPercent(row?.exceptionRatePct ?? null, lang)}
                  </p>
                  {row && <p className="muted muted--small">{formatMoney(row.totalSpend, lang)}</p>}
                </div>
              )
            }}
          />
          <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
          <Bar dataKey="onTimePct" name={t('dashboard.axis.onTimePct')} fill={CATEGORY_COLORS[2]} radius={[0, 4, 4, 0]} barSize={12}>
            <LabelList
              dataKey="onTimePct"
              position="right"
              formatter={(v: number) => `${Math.round(v)}%`}
              style={{ fill: AXIS_TEXT, fontSize: 11 }}
            />
          </Bar>
          <Bar
            dataKey="exceptionRatePct"
            name={t('dashboard.axis.exceptionRatePct')}
            fill={CATEGORY_COLORS[1]}
            radius={[0, 4, 4, 0]}
            barSize={12}
          >
            <LabelList
              dataKey="exceptionRatePct"
              position="right"
              formatter={(v: number) => `${Math.round(v)}%`}
              style={{ fill: AXIS_TEXT, fontSize: 11 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}