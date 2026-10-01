import { useState, type FormEvent } from 'react'
import { createReceipt } from '../api/client'
import type { CreateReceiptResponse, PurchaseOrder, ReceiptItemRequest } from '../api/types'
import { useTranslation } from '../i18n/I18nContext'
import type { CurrentUser } from '../session/SessionContext'
import { ErrorNotice } from './ErrorNotice'
import { formatNumber } from './format'

interface Props {
  order: PurchaseOrder
  user: CurrentUser
  onReceived: (result: CreateReceiptResponse) => void
}

/*
 * The order response does not say how much was already received, so "still to receive" is
 * assumed to be the full ordered quantity (first receipt). The API rejects over-receipts.
 */
export function OrderReceiptForm({ order, user, onReceived }: Props) {
  const { t, lang } = useTranslation()
  const [quantities, setQuantities] = useState<Record<number, string>>(() =>
    Object.fromEntries(order.items.map((item) => [item.id, String(item.quantity)])),
  )
  const [deliveryNote, setDeliveryNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [localError, setLocalError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setError(null)

    const items: ReceiptItemRequest[] = []
    for (const item of order.items) {
      const raw = (quantities[item.id] ?? '').trim()
      if (raw === '') continue
      const quantity = Number(raw)
      if (!Number.isFinite(quantity) || quantity < 0) {
        setLocalError(t('receipt.form.invalidQuantity'))
        return
      }
      if (quantity > 0) items.push({ purchaseOrderItemId: item.id, quantityReceived: quantity })
    }
    if (items.length === 0) {
      setLocalError(t('receipt.form.noItems'))
      return
    }
    setLocalError(null)

    setBusy(true)
    try {
      const result = await createReceipt(user.id, order.id, {
        deliveryNoteNumber: deliveryNote.trim() || null,
        items,
      })
      onReceived(result)
      setDeliveryNote('')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card form" onSubmit={onSubmit}>
      <h2 className="card__title">{t('receipt.form.title')}</h2>
      <p className="muted muted--small">{t('receipt.form.help')}</p>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">{t('fields.materialId')}</th>
              <th scope="col" className="num">{t('receipt.form.ordered')}</th>
              <th scope="col" className="num">{t('receipt.form.remaining')}</th>
              <th scope="col" className="num">{t('receipt.form.toReceive')}</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.id}>
                <td>{item.material.code} – {item.material.description}</td>
                <td className="num">{formatNumber(item.quantity, lang)}</td>
                <td className="num">{formatNumber(item.quantity, lang)}</td>
                <td className="num">
                  <input
                    type="number"
                    min="0"
                    step="0.001"
                    inputMode="decimal"
                    aria-label={`${t('receipt.form.toReceive')}: ${item.material.description}`}
                    value={quantities[item.id] ?? ''}
                    onChange={(e) => setQuantities((prev) => ({ ...prev, [item.id]: e.target.value }))}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <label className="field field--wide">
        <span>{t('fields.deliveryNoteNumber')} ({t('common.optional')})</span>
        <input value={deliveryNote} onChange={(e) => setDeliveryNote(e.target.value)} />
      </label>

      {localError && (
        <p className="field__error" role="alert">
          {localError}
        </p>
      )}
      {error !== null && <ErrorNotice error={error} />}

      <div>
        <button type="submit" className="button button--primary" disabled={busy}>
          {busy ? t('common.saving') : t('receipt.form.submit')}
        </button>
      </div>
    </form>
  )
}