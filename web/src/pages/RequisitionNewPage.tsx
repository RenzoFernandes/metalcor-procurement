import { useRef, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { createRequisition } from '../api/client'
import type { CreateRequisitionRequest } from '../api/types'
import { ErrorNotice } from '../components/ErrorNotice'
import { formatMoney } from '../components/format'
import { COST_CENTERS, MATERIALS, PLANTS, UNITS } from '../data/masterData'
import { useTranslation } from '../i18n/I18nContext'
import { rememberRequisition } from '../session/recentRequisitions'
import { useCurrentUser } from '../session/SessionContext'

const MAX_LINES = 8

interface Line {
  key: number
  materialId: string
  quantity: string
  unitOfMeasureId: string
  price: string
}

const blankLine = (key: number): Line => ({ key, materialId: '', quantity: '', unitOfMeasureId: '', price: '' })

const toNumber = (value: string): number => (value.trim() === '' ? NaN : Number(value))

export function RequisitionNewPage() {
  const { t, lang } = useTranslation()
  const { user } = useCurrentUser()
  const navigate = useNavigate()

  const nextKey = useRef(2)
  const [plantId, setPlantId] = useState('')
  const [costCenterId, setCostCenterId] = useState('')
  const [neededBy, setNeededBy] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<Line[]>([blankLine(1)])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  if (!user) return <Navigate to="/" replace />

  if (user.role !== 'requester') {
    return (
      <section>
        <h1>{t('requisition.new.title')}</h1>
        <div className="notice notice--info">{t('requisition.new.requesterOnly')}</div>
      </section>
    )
  }

  const costCenters = COST_CENTERS.filter((c) => String(c.plantId) === plantId)

  const total = lines.reduce((sum, l) => {
    const q = toNumber(l.quantity)
    const p = toNumber(l.price)
    return Number.isFinite(q) && Number.isFinite(p) ? sum + q * p : sum
  }, 0)

  function updateLine(key: number, patch: Partial<Line>) {
    setLines((current) => current.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  }

  function chooseMaterial(key: number, materialId: string) {
    const material = MATERIALS.find((m) => String(m.id) === materialId)
    // Suggest the material's own unit and standard price; both stay editable.
    updateLine(key, {
      materialId,
      ...(material ? { unitOfMeasureId: String(material.unitOfMeasureId), price: String(material.standardPrice) } : {}),
    })
  }

  function addLine() {
    if (lines.length >= MAX_LINES) return
    setLines((current) => [...current, blankLine(nextKey.current++)])
  }

  function removeLine(key: number) {
    setLines((current) => (current.length > 1 ? current.filter((l) => l.key !== key) : current))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!user || submitting) return

    const body: CreateRequisitionRequest = {
      plantId: toNumber(plantId),
      costCenterId: toNumber(costCenterId),
      neededBy: neededBy || null,
      notes: notes.trim() || null,
      items: lines.map((l) => ({
        materialId: toNumber(l.materialId),
        quantity: toNumber(l.quantity),
        unitOfMeasureId: toNumber(l.unitOfMeasureId),
        estimatedUnitPrice: toNumber(l.price),
      })),
    }
    // NaN would be serialized as null, which the API reports as a missing field.

    setSubmitting(true)
    setError(null)
    try {
      const created = await createRequisition(user.id, body)
      rememberRequisition(user.id, { id: created.id, documentNumber: created.documentNumber })
      navigate(`/requisitions/${created.id}`, { state: { created: true } })
    } catch (err) {
      setError(err)
      setSubmitting(false)
    }
  }

  return (
    <section>
      <h1>{t('requisition.new.title')}</h1>
      <p className="muted">{t('requisition.new.intro')}</p>

      <form className="form" onSubmit={onSubmit}>
        <div className="card form__grid">
          <label className="field">
            <span>{t('fields.plantId')}</span>
            <select
              value={plantId}
              onChange={(e) => {
                setPlantId(e.target.value)
                setCostCenterId('')
              }}
              required
            >
              <option value="">{t('common.select')}</option>
              {PLANTS.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.code} – {p.name}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>{t('fields.costCenterId')}</span>
            <select value={costCenterId} onChange={(e) => setCostCenterId(e.target.value)} required disabled={!plantId}>
              <option value="">{t('common.select')}</option>
              {costCenters.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} – {c.name}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>{t('fields.neededBy')}</span>
            <input type="date" value={neededBy} onChange={(e) => setNeededBy(e.target.value)} required />
          </label>

          <label className="field field--wide">
            <span>{t('fields.notes')}</span>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
          </label>
        </div>

        <div className="card">
          <h2 className="card__title">{t('requisition.new.items')}</h2>

          <div className="lines">
            {lines.map((line, index) => (
              <fieldset key={line.key} className="line">
                <legend>{t('requisition.new.line', { n: index + 1 })}</legend>

                <label className="field field--material">
                  <span>{t('fields.materialId')}</span>
                  <select value={line.materialId} onChange={(e) => chooseMaterial(line.key, e.target.value)} required>
                    <option value="">{t('common.select')}</option>
                    {MATERIALS.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.code} – {m.description}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="field">
                  <span>{t('fields.quantity')}</span>
                  <input
                    type="number"
                    min="0.001"
                    step="0.001"
                    value={line.quantity}
                    onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                    required
                  />
                </label>

                <label className="field">
                  <span>{t('fields.unitOfMeasureId')}</span>
                  <select
                    value={line.unitOfMeasureId}
                    onChange={(e) => updateLine(line.key, { unitOfMeasureId: e.target.value })}
                    required
                  >
                    <option value="">{t('common.select')}</option>
                    {UNITS.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.code} – {u.description}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="field">
                  <span>{t('fields.estimatedUnitPrice')}</span>
                  <input
                    type="number"
                    min="0.0001"
                    step="0.0001"
                    value={line.price}
                    onChange={(e) => updateLine(line.key, { price: e.target.value })}
                    required
                  />
                </label>

                <button
                  type="button"
                  className="button button--ghost"
                  onClick={() => removeLine(line.key)}
                  disabled={lines.length === 1}
                >
                  {t('requisition.new.removeLine')}
                </button>
              </fieldset>
            ))}
          </div>

          <div className="form__footer">
            <button type="button" className="button button--outline" onClick={addLine} disabled={lines.length >= MAX_LINES}>
              {t('requisition.new.addLine')}
            </button>
            <span className="muted muted--small">{t('requisition.new.lineCount', { count: lines.length, max: MAX_LINES })}</span>
            <strong className="form__total">
              {t('requisition.total')}: {formatMoney(total, lang)}
            </strong>
          </div>
        </div>

        <p className="muted muted--small">{t('requisition.new.limitation')}</p>

        {error !== null && <ErrorNotice error={error} />}

        <div>
          <button type="submit" className="button button--primary" disabled={submitting}>
            {submitting ? t('common.saving') : t('requisition.new.submit')}
          </button>
        </div>
      </form>
    </section>
  )
}