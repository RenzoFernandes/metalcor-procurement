import { useState, type FormEvent } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { approveInvoice, getInvoice, payInvoice } from '../api/client'
import type { Invoice, PaymentMethod } from '../api/types'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../components/format'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser, type CurrentUser } from '../session/SessionContext'

const PAYMENT_METHODS: PaymentMethod[] = ['bank_transfer', 'boleto', 'pix']

export function InvoiceDetailPage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const { id: idParam } = useParams()
  const id = Number(idParam)

  const { data, error, loading, setData } = useApi(
    () => (Number.isInteger(id) && id > 0 ? getInvoice(id) : Promise.reject(new Error('invalid id'))),
    id,
  )

  if (!user) return <Navigate to="/" replace />
  if (loading) return <LoadingNotice />
  if (error !== null || !data) {
    return (
      <section>
        <h1>{t('invoice.title')}</h1>
        <ErrorNotice error={error} />
      </section>
    )
  }

  return <InvoiceView key={data.id} invoice={data} user={user} onChange={setData} />
}

interface ViewProps {
  invoice: Invoice
  user: CurrentUser
  onChange: (invoice: Invoice) => void
}

function InvoiceView({ invoice, user, onChange }: ViewProps) {
  const { t, lang } = useTranslation()
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const [notes, setNotes] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('bank_transfer')
  const [reference, setReference] = useState('')

  const isFinance = user.role === 'finance'
  const canApprove = invoice.status === 'blocked'
  const canPay = invoice.status === 'matched' || invoice.status === 'approved'

  async function run<T>(action: () => Promise<T>, onSuccess: (result: T) => void) {
    if (busy) return
    setBusy(true)
    setActionError(null)
    setNotice(null)
    try {
      onSuccess(await action())
    } catch (err) {
      setActionError(err)
    } finally {
      setBusy(false)
    }
  }

  function onApprove(event: FormEvent) {
    event.preventDefault()
    void run(
      () => approveInvoice(user.id, invoice.id, { notes: notes.trim() || null }),
      (updated) => {
        onChange(updated)
        setNotes('')
        setNotice(t('invoice.detail.approvedNotice'))
      },
    )
  }

  function onPay(event: FormEvent) {
    event.preventDefault()
    void run(
      () => payInvoice(user.id, invoice.id, { paymentMethod: method, reference: reference.trim() || null }),
      (result) => {
        const { payment } = result
        onChange({
          ...invoice,
          status: result.invoiceStatus,
          payment: {
            id: payment.id,
            documentNumber: payment.documentNumber,
            amount: payment.amount,
            scheduledFor: payment.scheduledFor,
            paymentMethod: payment.paymentMethod,
            paidAt: payment.paidAt,
          },
        })
        setReference('')
        setNotice(t('invoice.detail.paidNotice', { number: payment.documentNumber }))
      },
    )
  }

  return (
    <section>
      <div className="page-head">
        <h1>
          {t('invoice.title')} {invoice.documentNumber}
        </h1>
        <span className={`status status--${invoice.status}`}>{t(`status.${invoice.status}`)}</span>
      </div>

      {notice && (
        <div className="notice notice--success" role="status">
          {notice}
        </div>
      )}
      {actionError !== null && <ErrorNotice error={actionError} />}

      {invoice.status === 'blocked' && (
        <div className="notice notice--error" role="status">
          <p className="notice__title">{t('invoice.detail.blocked')}</p>
          {invoice.blockReason && <p>{invoice.blockReason}</p>}
        </div>
      )}

      <div className="card">
        <dl className="facts">
          <div><dt>{t('fields.supplierInvoiceNumber')}</dt><dd>{invoice.supplierInvoiceNumber}</dd></div>
          <div><dt>{t('order.supplier')}</dt><dd>{invoice.supplier.code} – {invoice.supplier.name}</dd></div>
          <div><dt>{t('invoice.detail.order')}</dt><dd>{invoice.purchaseOrderNumber}</dd></div>
          <div><dt>{t('fields.invoiceDate')}</dt><dd>{formatDate(invoice.invoiceDate, lang)}</dd></div>
          <div><dt>{t('fields.dueDate')}</dt><dd>{formatDate(invoice.dueDate, lang)}</dd></div>
          <div><dt>{t('invoice.detail.postingDate')}</dt><dd>{formatDate(invoice.postingDate, lang)}</dd></div>
          <div><dt>{t('invoice.detail.grossAmount')}</dt><dd><strong>{formatMoney(invoice.grossAmount, lang)}</strong></dd></div>
          {invoice.approvedBy && (
            <div>
              <dt>{t('requisition.detail.approvedBy')}</dt>
              <dd>{invoice.approvedBy.name} ({formatDateTime(invoice.approvedAt, lang)})</dd>
            </div>
          )}
          {invoice.payment && (
            <div>
              <dt>{t('invoice.detail.payment')}</dt>
              <dd>
                <Link to={`/payments/${invoice.payment.id}`}>{invoice.payment.documentNumber}</Link>
                {' – '}
                {formatMoney(invoice.payment.amount, lang)}
              </dd>
            </div>
          )}
        </dl>
        <p className="muted muted--small">{t('invoice.detail.linkLimitation')}</p>
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
                <th>{t('invoice.detail.exceptions')}</th>
              </tr>
            </thead>
            <tbody>
              {invoice.items.map((item, index) => {
                const hasException = item.priceException || item.quantityException
                return (
                  <tr key={index} className={hasException ? 'row--exception' : undefined}>
                    <td>{item.material.code} – {item.material.description}</td>
                    <td className={`num${item.quantityException ? ' cell--exception' : ''}`}>
                      {formatNumber(item.quantityInvoiced, lang)}
                    </td>
                    <td className={`num${item.priceException ? ' cell--exception' : ''}`}>
                      {formatMoney(item.unitPrice, lang)}
                    </td>
                    <td className="num">{formatMoney(item.lineTotal, lang)}</td>
                    <td>
                      {item.priceException && <span className="flag flag--exception">⚠ {t('invoice.detail.priceException')}</span>}
                      {item.quantityException && <span className="flag flag--exception">⚠ {t('invoice.detail.quantityException')}</span>}
                      {!hasException && <span className="flag flag--ok">✓ {t('invoice.detail.noException')}</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={3} scope="row" className="num">{t('requisition.total')}</th>
                <td className="num"><strong>{formatMoney(invoice.grossAmount, lang)}</strong></td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {invoice.status === 'paid' && (
        <div className="notice notice--info" role="status">
          {t('invoice.detail.alreadyPaid')}
        </div>
      )}

      {canApprove && (
        <form className="card form" onSubmit={onApprove}>
          <h2 className="card__title">{t('invoice.detail.approveTitle')}</h2>
          <p className="muted muted--small">{t('invoice.detail.approveHelp')}</p>
          {!isFinance && <p className="notice notice--info">{t('invoice.detail.financeOnly')}</p>}
          <label className="field field--wide">
            <span>{t('fields.notes')} ({t('common.optional')})</span>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
          </label>
          <div>
            <button type="submit" className="button button--primary" disabled={busy}>
              {busy ? t('common.saving') : t('invoice.detail.approveButton')}
            </button>
          </div>
        </form>
      )}

      {canPay && (
        <form className="card form" onSubmit={onPay}>
          <h2 className="card__title">{t('invoice.detail.payTitle')}</h2>
          <p className="muted muted--small">{t('invoice.detail.payHelp')}</p>
          {!isFinance && <p className="notice notice--info">{t('invoice.detail.financeOnly')}</p>}
          <div className="form__grid">
            <label className="field">
              <span>{t('fields.paymentMethod')}</span>
              <select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m} value={m}>
                    {t(`paymentMethod.${m}`)}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>{t('fields.reference')} ({t('common.optional')})</span>
              <input value={reference} maxLength={60} onChange={(e) => setReference(e.target.value)} />
            </label>
          </div>
          <div>
            <button type="submit" className="button button--primary" disabled={busy}>
              {busy ? t('common.saving') : t('invoice.detail.payButton')}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
