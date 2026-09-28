import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SupplierScorecard } from '../../api/types'
import { formatMoney, formatPercent } from '../../components/format'
import { useTranslation } from '../../i18n/I18nContext'
import { ChartCard } from './ChartCard'
import { AXIS_TEXT, CATEGORY_COLORS, GRIDLINE } from './colors'

export function SupplierScorecardChart({ rows, sql }: { rows: SupplierScorecard[]; sql: string }) {
  const { t, lang } = useTranslation()

  // Ascending by spend: recharts' vertical category axis lists the first row at the bottom,
  // so the biggest spender ends up on top.
  const data = [...rows]
    .sort((a, b) => a.totalSpend - b.totalSpend)
    .map((s) => ({
      supplier: `${s.supplierCode} – ${s.supplierName}`,
      onTimePct: s.onTimeDeliveryPct ?? 0,
      onTimePctRaw: s.onTimeDeliveryPct,
      exceptionRatePct: s.exceptionRatePct,
      totalSpend: s.totalSpend,
    }))

  return (
    <ChartCard
      title={t('dashboard.charts.supplierScorecard.title')}
      description={t('dashboard.charts.supplierScorecard.description')}
      sql={sql}
    >
      <ResponsiveContainer width="100%" height={Math.max(240, data.length * 34)}>
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
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
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={200}
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
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Bar dataKey="onTimePct" name={t('dashboard.axis.onTimePct')} fill={CATEGORY_COLORS[2]} radius={[0, 4, 4, 0]} />
          <Bar dataKey="exceptionRatePct" name={t('dashboard.axis.exceptionRatePct')} fill={CATEGORY_COLORS[1]} radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}