import { Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { LatePaymentsByMonth } from '../../api/types'
import { formatMonth, formatNumber } from '../../components/format'
import { useTranslation } from '../../i18n/I18nContext'
import { ChartCard } from './ChartCard'
import { AXIS_TEXT, CATEGORY_COLORS, GRIDLINE } from './colors'

export function LatePaymentsPanel({ rows, sql }: { rows: LatePaymentsByMonth[]; sql: string }) {
  const { t, lang } = useTranslation()

  const data = [...rows]
    .sort((a, b) => a.dueMonth.localeCompare(b.dueMonth))
    .map((r) => ({
      monthLabel: formatMonth(r.dueMonth, lang),
      latePayments: r.latePayments,
      payments: r.payments,
      avgDaysLate: r.avgDaysLate,
    }))

  return (
    <ChartCard
      title={t('dashboard.charts.latePayments.title')}
      description={t('dashboard.charts.latePayments.description')}
      sql={sql}
    >
      <ResponsiveContainer width="100%" height={320}>
        <ComposedChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} vertical={false} />
          <XAxis
            dataKey="monthLabel"
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={{ stroke: GRIDLINE }}
            tickLine={false}
          />
          <YAxis
            yAxisId="count"
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            allowDecimals={false}
            width={32}
          />
          <YAxis
            yAxisId="days"
            orientation="right"
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={32}
          />
          <Tooltip
            content={({ active, label, payload }) => {
              if (!active || !payload || payload.length === 0) return null
              const row = data.find((d) => d.monthLabel === label)
              return (
                <div className="chart-tooltip">
                  <p className="chart-tooltip__title">{label}</p>
                  {row && (
                    <p className="chart-tooltip__row">
                      <span className="chart-tooltip__swatch" style={{ background: CATEGORY_COLORS[0] }} />
                      {t('dashboard.axis.totalDelayed')}: {formatNumber(row.latePayments, lang)} / {formatNumber(row.payments, lang)}
                    </p>
                  )}
                  <p className="chart-tooltip__row">
                    <span className="chart-tooltip__swatch" style={{ background: CATEGORY_COLORS[1] }} />
                    {t('dashboard.axis.avgDaysLate')}: {row?.avgDaysLate === null || row?.avgDaysLate === undefined ? '—' : formatNumber(row.avgDaysLate, lang)}
                  </p>
                </div>
              )
            }}
          />
          <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
          <Bar
            yAxisId="count"
            dataKey="latePayments"
            name={t('dashboard.axis.totalDelayed')}
            fill={CATEGORY_COLORS[0]}
            radius={[4, 4, 0, 0]}
            barSize={28}
          />
          <Line
            yAxisId="days"
            type="monotone"
            dataKey="avgDaysLate"
            name={t('dashboard.axis.avgDaysLate')}
            stroke={CATEGORY_COLORS[1]}
            strokeWidth={2}
            dot={{ r: 4 }}
            connectNulls={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
