import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
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
      <div className="late-payments__grid">
        <div>
          <p className="chart-card__axis-title muted muted--small">{t('dashboard.axis.totalDelayed')}</p>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} vertical={false} />
              <XAxis dataKey="monthLabel" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={{ stroke: GRIDLINE }} tickLine={false} />
              <YAxis tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} allowDecimals={false} width={32} />
              <Tooltip
                formatter={(value, _name, item) => [
                  `${formatNumber(Number(value), lang)} / ${formatNumber(Number(item.payload.payments), lang)}`,
                  t('dashboard.axis.totalDelayed'),
                ]}
              />
              <Bar dataKey="latePayments" fill={CATEGORY_COLORS[0]} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div>
          <p className="chart-card__axis-title muted muted--small">{t('dashboard.axis.avgDaysLate')}</p>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} vertical={false} />
              <XAxis dataKey="monthLabel" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={{ stroke: GRIDLINE }} tickLine={false} />
              <YAxis tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={false} tickLine={false} width={32} />
              <Tooltip formatter={(value) => (value === null ? '—' : formatNumber(Number(value), lang))} />
              <Line
                type="monotone"
                dataKey="avgDaysLate"
                stroke={CATEGORY_COLORS[1]}
                strokeWidth={2}
                dot={{ r: 4 }}
                connectNulls={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </ChartCard>
  )
}