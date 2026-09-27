import { useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { getPurchaseOrder } from '../api/client'
import type { GoodsReceipt, PurchaseOrder } from '../api/types'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { formatDate, formatMoney, formatNumber } from '../components/format'
import { OrderInvoiceForm } from '../components/OrderInvoiceForm'
import { OrderReceiptForm } from '../components/OrderReceiptForm'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser, type CurrentUser } from '../session/SessionContext'

const RECEIVABLE = ['issued', 'partially_received']
const INVOICEABLE = ['received', 'partially_received']

export function PurchaseOrderDetailPage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const { id: idParam } = useParams()
  const id = Number(idParam)

  const { data: order, error, loading, setData } = useApi(
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

  return <OrderView key={order.id} order={order} user={user} onChange={setData} />
}

interface ViewProps {
  order: PurchaseOrder
  user: CurrentUser
  onChange: (order: PurchaseOrder) => void
}

function OrderView({ order, user, onChange }: ViewProps) {
  const { t, lang } = useTranslation()
  const [receipt, setReceipt] = useState<GoodsReceipt | null>(null)

  return (
    <section>
      <div className="page-head">
        <h1>
          {t('order.title')} {order.documentNumber}
        </h1>
        <span className={`status status--${order.status}`}>{t(`status.${order.status}`)}</span>
      </div>

      {receipt && (
        <div className="notice notice--success" role="status">
          <p className="notice__title">{t('receipt.created', { number: receipt.documentNumber })}</p>
          {receipt.deliveryNoteNumber && (
            <p>{t('fields.deliveryNoteNumber')}: {receipt.deliveryNoteNumber}</p>
          )}
          <ul className="notice__list">
            {receipt.items.map((item) => (
              <li key={item.material.id}>
                {item.material.code} – {item.material.description}: {formatNumber(item.quantityReceived, lang)}
              </li>
            ))}
          </ul>
        </div>
      )}

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

      {RECEIVABLE.includes(order.status) && (
        <OrderReceiptForm
          order={order}
          user={user}
          onReceived={(result) => {
            setReceipt(result.goodsReceipt)
            onChange({ ...order, status: result.purchaseOrderStatus })
          }}
        />
      )}

      {INVOICEABLE.includes(order.status) && <OrderInvoiceForm order={order} user={user} />}
    </section>
  )
}