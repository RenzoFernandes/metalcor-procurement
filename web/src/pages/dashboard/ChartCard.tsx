import type { ReactNode } from 'react'
import { SqlBlock } from '../../components/SqlBlock'

interface ChartCardProps {
  title: string
  description: string
  sql: string | string[]
  children: ReactNode
}

export function ChartCard({ title, description, sql, children }: ChartCardProps) {
  const sqls = Array.isArray(sql) ? sql : [sql]
  return (
    <div className="card chart-card">
      <h2 className="card__title">{title}</h2>
      <p className="chart-card__description muted muted--small">{description}</p>
      <div className="chart-card__body">{children}</div>
      {sqls.map((s, i) => (
        <SqlBlock key={i} sql={s} />
      ))}
    </div>
  )
}