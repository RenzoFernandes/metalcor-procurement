import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SpendByMonthCategory } from '../../api/types'
import { formatCompactMoney, formatMoney, formatMonth, formatNumber } from '../../components/format'
import { useTranslation } from '../../i18n/I18nContext'
import { ChartCard } from './ChartCard'
import { AXIS_TEXT, CATEGORY_COLORS, GRIDLINE } from './colors'

const CATEGORY_ORDER = ['ACO', 'ROL', 'FER', 'TIN', 'EMB', 'MAN']

interface Row {
  orderMonth: string
  monthLabel: string
  [categoryCode: string]: string | number
}

export function SpendByMonthChart({ rows, sql }: { rows: SpendByMonthCategory[]; sql: string }) {
  const { t, lang } = useTranslation()

  const categoryNames = new Map<string, string>()
  for (const r of rows) categoryNames.set(r.categoryCode, r.categoryName)
  const detailByKey = new Map<string, SpendByMonthCategory>()
  for (const r of rows) detailByKey.set(`${r.orderMonth}|${r.categoryCode}`, r)

  const months = [...new Set(rows.map((r) => r.orderMonth))].sort()
  const data: Row[] = months.map((month) => {
    const row: Row = { orderMonth: month, monthLabel: formatMonth(month, lang) }
    for (const code of CATEGORY_ORDER) {
      const found = rows.find((r) => r.orderMonth === month && r.categoryCode === code)
      row[code] = found?.totalValue ?? 0
    }
    return row
  })

  const categoriesPresent = CATEGORY_ORDER.filter((code) => categoryNames.has(code))

  return (
    <ChartCard title={t('dashboard.charts.spendByMonth.title')} description={t('dashboard.charts.spendByMonth.description')} sql={sql}>
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={GRIDLINE} vertical={false} />
          <XAxis dataKey="monthLabel" tick={{ fill: AXIS_TEXT, fontSize: 12 }} axisLine={{ stroke: GRIDLINE }} tickLine={false} />
          <YAxis
            tick={{ fill: AXIS_TEXT, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v: number) => formatCompactMoney(v, lang)}
            width={64}
          />
          <Tooltip
            content={({ active, label, payload }) => {
              if (!active || !payload || payload.length === 0) return null
              const monthRow = data.find((d) => d.monthLabel === label)
              return (
                <div className="chart-tooltip">
                  <p className="chart-tooltip__title">{label}</p>
                  {payload
                    .filter((p) => Number(p.value) > 0)
                    .map((p) => {
                      const code = String(p.dataKey)
                      const detail = monthRow ? detailByKey.get(`${monthRow.orderMonth}|${code}`) : undefined
                      return (
                        <p key={code} className="chart-tooltip__row">
                          <span className="chart-tooltip__swatch" style={{ background: p.color }} />
                          {categoryNames.get(code) ?? code}: {formatMoney(Number(p.value), lang)}
                          {detail && (
                            <span className="muted muted--small">
                              {' '}
                              ({formatNumber(detail.orders, lang)} {t('dashboard.tooltip.orders')},{' '}
                              {formatNumber(detail.suppliers, lang)} {t('dashboard.tooltip.suppliers')})
                            </span>
                          )}
                        </p>
                      )
                    })}
                </div>
              )
            }}
          />
          <Legend formatter={(value: string) => categoryNames.get(value) ?? value} wrapperStyle={{ fontSize: 12 }} />
          {categoriesPresent.map((code, i) => (
            <Bar key={code} dataKey={code} stackId="spend" fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]} radius={0} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}