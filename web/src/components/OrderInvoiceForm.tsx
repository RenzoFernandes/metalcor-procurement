import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createInvoice } from '../api/client'
import type { Invoice, InvoiceItemRequest, PurchaseOrder } from '../api/types'
import { useTranslation } from '../i18n/I18nContext'
import type { CurrentUser } from '../session/SessionContext'
import { ErrorNotice } from './ErrorNotice'
import { formatNumber, todayIso } from './format'

interface Props {
  order: PurchaseOrder
  user: CurrentUser
}

interface LineDraft {
  quantity: string
  unitPrice: string
}

export function OrderInvoiceForm({ order, user }: Props) {
  const { t, lang } = useTranslation()
  const [supplierInvoiceNumber, setSupplierInvoiceNumber] = useState('')
  const [invoiceDate, setInvoiceDate] = useState(todayIso())
  const [dueDate, setDueDate] = useState('')
  const [lines, setLines] = useState<Record<number, LineDraft>>(() =>
    Object.fromEntries(order.items.map((item) => [item.id, { quantity: String(item.quantity), unitPrice: String(item.unitPrice) }])),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [localErrors, setLocalErrors] = useState<string[]>([])
  const [created, setCreated] = useState<Invoice | null>(null)

  const isFinance = user.role === 'finance'

  function setLine(itemId: number, patch: Partial<LineDraft>) {
    setLines((prev) => ({ ...prev, [itemId]: { ...prev[itemId], ...patch } }))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setError(null)
    setCreated(null)

    const problems: string[] = []
    if (supplierInvoiceNumber.trim() === '') {
      problems.push(`${t('fields.supplierInvoiceNumber')}: ${t('errors.validation.required')}`)
    }
    if (invoiceDate === '') {
      problems.push(`${t('fields.invoiceDate')}: ${t('errors.validation.required')}`)
    }
    if (invoiceDate !== '' && dueDate !== '' && dueDate < invoiceDate) {
      problems.push(`${t('fields.dueDate')}: ${t('invoice.form.dueBeforeInvoice')}`)
    }

    const items: InvoiceItemRequest[] = []
    for (const item of order.items) {
      const line = lines[item.id]
      const qtyRaw = line.quantity.trim()
      if (qtyRaw === '') continue
      const quantity = Number(qtyRaw)
      const unitPrice = Number(line.unitPrice)
      if (!Number.isFinite(quantity) || quantity < 0 || line.unitPrice.trim() === '' || !Number.isFinite(unitPrice)) {
        problems.push(`${item.material.code}: ${t('invoice.form.invalidLine')}`)
        continue
      }
      if (quantity === 0) continue
      if (unitPrice <= 0) {
        problems.push(`${item.material.code}: ${t('invoice.form.invalidLine')}`)
        continue
      }
      items.push({ purchaseOrderItemId: item.id, quantityInvoiced: quantity, unitPrice })
    }
    if (items.length === 0 && problems.length === 0) problems.push(t('invoice.form.noItems'))

    setLocalErrors(problems)
    if (problems.length > 0) return

    setBusy(true)
    try {
      const invoice = await createInvoice(user.id, order.id, {
        supplierInvoiceNumber: supplierInvoiceNumber.trim(),
        invoiceDate,
        dueDate: dueDate || null,
        items,
      })
      setCreated(invoice)
      setSupplierInvoiceNumber('')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card form" onSubmit={onSubmit}>
      <h2 className="card__title">{t('invoice.form.title')}</h2>
      <p className="muted muted--small">{t('invoice.form.help')}</p>
      {!isFinance && <p className="notice notice--info">{t('invoice.form.financeOnly')}</p>}

      <div className="form__grid">
        <label className="field">
          <span>{t('fields.supplierInvoiceNumber')} *</span>
          <input value={supplierInvoiceNumber} onChange={(e) => setSupplierInvoiceNumber(e.target.value)} />
        </label>
        <label className="field">
          <span>{t('fields.invoiceDate')} *</span>
          <input type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} />
        </label>
        <label className="field">
          <span>{t('fields.dueDate')} ({t('common.optional')})</span>
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </label>
      </div>
      <p className="muted muted--small">{t('invoice.form.dueHelp')}</p>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">{t('fields.materialId')}</th>
              <th scope="col" className="num">{t('receipt.form.ordered')}</th>
              <th scope="col" className="num">{t('invoice.form.quantityInvoiced')}</th>
              <th scope="col" className="num">{t('invoice.form.unitPriceInvoiced')}</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.id}>
                <td>{item.material.code} – {item.material.description}</td>
                <td className="num">{formatNumber(item.quantity, lang)}</td>
                <td className="num">
                  <input
                    type="number"
                    min="0"
                    step="0.001"
                    inputMode="decimal"
                    aria-label={`${t('invoice.form.quantityInvoiced')}: ${item.material.description}`}
                    value={lines[item.id].quantity}
                    onChange={(e) => setLine(item.id, { quantity: e.target.value })}
                  />
                </td>
                <td className="num">
                  <input
                    type="number"
                    min="0"
                    step="0.0001"
                    inputMode="decimal"
                    aria-label={`${t('invoice.form.unitPriceInvoiced')}: ${item.material.description}`}
                    value={lines[item.id].unitPrice}
                    onChange={(e) => setLine(item.id, { unitPrice: e.target.value })}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {localErrors.length > 0 && (
        <div className="notice notice--error" role="alert">
          <p className="notice__title">{t('errors.validation.headline')}</p>
          <ul className="notice__list">
            {localErrors.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      )}
      {error !== null && <ErrorNotice error={error} />}

      {created && (
        <div className="notice notice--success" role="status">
          {t('invoice.form.created', { number: created.documentNumber })}{' '}
          <Link to={`/invoices/${created.id}`}>{t('invoice.form.open')}</Link>
        </div>
      )}

      <div>
        <button type="submit" className="button button--primary" disabled={busy}>
          {busy ? t('common.saving') : t('invoice.form.submit')}
        </button>
      </div>
    </form>
  )
}