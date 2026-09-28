import type { StaleInvoice } from '../../api/types'
import { formatMoney } from '../../components/format'
import { SqlBlock } from '../../components/SqlBlock'
import { useTranslation } from '../../i18n/I18nContext'

export function StaleInvoicesTable({ rows, sql }: { rows: StaleInvoice[]; sql: string }) {
  const { t, lang } = useTranslation()

  return (
    <div className="card chart-card">
      <h2 className="card__title">{t('dashboard.staleInvoices.title')}</h2>
      <p className="chart-card__description muted muted--small">{t('dashboard.staleInvoices.description')}</p>

      {rows.length === 0 ? (
        <p className="muted">{t('dashboard.staleInvoices.empty')}</p>
      ) : (
        <div className="table-wrap table-wrap--stale">
          <table className="table table--stale">
            <colgroup>
              <col style={{ width: '16%' }} />
              <col style={{ width: '16%' }} />
              <col style={{ width: '10%' }} />
              <col style={{ width: '16%' }} />
              <col style={{ width: '42%' }} />
            </colgroup>
            <thead>
              <tr>
                <th>{t('dashboard.staleInvoices.number')}</th>
                <th className="num">{t('dashboard.staleInvoices.amount')}</th>
                <th className="num">{t('dashboard.staleInvoices.age')}</th>
                <th>{t('dashboard.staleInvoices.ageBand')}</th>
                <th>{t('dashboard.staleInvoices.reason')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((inv) => {
                const isStale = inv.ageBand === '90+'
                return (
                  <tr key={inv.invoiceNumber} className={isStale ? 'row--stale' : undefined}>
                    <td>
                      {isStale && (
                        <span className="row--stale__icon" aria-hidden="true" title={t('dashboard.ageBand.90+')}>
                          ⚠
                        </span>
                      )}
                      {inv.invoiceNumber}
                    </td>
                    <td className="num">{formatMoney(inv.amount, lang)}</td>
                    <td className="num">{inv.ageDays}</td>
                    <td>{t(`dashboard.ageBand.${inv.ageBand}`)}</td>
                    <td className="cell--wrap">{inv.blockReason ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <SqlBlock sql={sql} />
    </div>
  )
}