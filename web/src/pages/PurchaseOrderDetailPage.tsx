import { Navigate, useParams } from 'react-router-dom'
import { getPurchaseOrder } from '../api/client'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { formatDate, formatMoney, formatNumber } from '../components/format'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'

export function PurchaseOrderDetailPage() {
  const { t, lang } = useTranslation()
  const { user } = useCurrentUser()
  const { id: idParam } = useParams()
  const id = Number(idParam)

  const { data: order, error, loading } = useApi(
    () => (Number.isInteger(id) && id > 0 ? getPurchaseOrder(id) : Promise.reject(new Error('invalid id'))),
    id,
  )

  if (!user) return <Navigate to="/" replace />
  if (loading) return <LoadingNotice />
  if (error !== null || !order) {
    return (
      <section>
        <h1>{t('order.title')}</h1>
        <ErrorNotice error={error} />
      </section>
    )
  }

  return (
    <section>
      <div className="page-head">
        <h1>
          {t('order.title')} {order.documentNumber}
        </h1>
        <span className={`status status--${order.status}`}>{t(`status.${order.status}`)}</span>
      </div>

      <div className="card">
        <dl className="facts">
          <div><dt>{t('order.supplier')}</dt><dd>{order.supplier.code} – {order.supplier.name}</dd></div>
          <div><dt>{t('fields.plantId')}</dt><dd>{order.plant.code} – {order.plant.name}</dd></div>
          <div><dt>{t('order.buyer')}</dt><dd>{order.buyer.name}</dd></div>
          <div><dt>{t('order.requisition')}</dt><dd>{order.requisitionNumber ?? '—'}</dd></div>
          <div><dt>{t('order.orderDate')}</dt><dd>{formatDate(order.orderDate, lang)}</dd></div>
          <div><dt>{t('order.expectedDelivery')}</dt><dd>{formatDate(order.expectedDeliveryDate, lang)}</dd></div>
          <div>
            <dt>{t('order.paymentTerms')}</dt>
            <dd>{order.paymentTermsDays === null ? '—' : t('order.days', { count: order.paymentTermsDays })}</dd>
          </div>
        </dl>
      </div>

      <div className="card">
        <h2 className="card__title">{t('requisition.new.items')}</h2>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t('fields.materialId')}</th>
                <th className="num">{t('fields.quantity')}</th>
                <th className="num">{t('order.unitPrice')}</th>
                <th className="num">{t('requisition.detail.lineTotal')}</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.material.code} – {item.material.description}</td>
                  <td className="num">{formatNumber(item.quantity, lang)}</td>
                  <td className="num">{formatMoney(item.unitPrice, lang)}</td>
                  <td className="num">{formatMoney(item.lineTotal, lang)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={3} scope="row" className="num">{t('requisition.total')}</th>
                <td className="num"><strong>{formatMoney(order.total, lang)}</strong></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </section>
  )
}