import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { decideRequisition, getRequisition, issueOrder, submitRequisition } from '../api/client'
import type { Decision, PurchaseOrder, Requisition } from '../api/types'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../components/format'
import { useTranslation } from '../i18n/I18nContext'
import { rememberRequisition } from '../session/recentRequisitions'
import { useCurrentUser, type CurrentUser } from '../session/SessionContext'

const DECIDER_ROLES = ['buyer', 'approver', 'manager']

export function RequisitionDetailPage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const { id: idParam } = useParams()
  const location = useLocation()
  const id = Number(idParam)

  const { data, error, loading, setData } = useApi(
    () => (Number.isInteger(id) && id > 0 ? getRequisition(id) : Promise.reject(new Error('invalid id'))),
    id,
  )

  if (!user) return <Navigate to="/" replace />
  if (loading) return <LoadingNotice />
  if (error !== null || !data) {
    return (
      <section>
        <h1>{t('requisition.detail.title')}</h1>
        <ErrorNotice error={error} />
      </section>
    )
  }

  const justCreated = (location.state as { created?: boolean } | null)?.created === true
  return <RequisitionView key={data.id} requisition={data} user={user} justCreated={justCreated} onChange={setData} />
}

interface ViewProps {
  requisition: Requisition
  user: CurrentUser
  justCreated: boolean
  onChange: (requisition: Requisition) => void
}

function RequisitionView({ requisition, user, justCreated, onChange }: ViewProps) {
  const { t, lang } = useTranslation()
  const [notice, setNotice] = useState<string | null>(justCreated ? t('requisition.detail.created') : null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [orders, setOrders] = useState<PurchaseOrder[] | null>(null)

  const [decision, setDecision] = useState<Decision>('approve')
  const [comment, setComment] = useState('')
  const [commentMissing, setCommentMissing] = useState(false)

  const isOwner = requisition.requestedBy.id === user.id
  const canSubmit = requisition.status === 'draft' && isOwner
  const canDecide = requisition.status === 'pending_approval' && DECIDER_ROLES.includes(user.role)
  const canIssue = requisition.status === 'approved' && user.role === 'buyer'

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

  function onSubmitForApproval() {
    void run(
      () => submitRequisition(user.id, requisition.id),
      (updated) => {
        onChange(updated)
        setNotice(t('requisition.detail.submitted'))
      },
    )
  }

  function onDecide(event: FormEvent) {
    event.preventDefault()
    if (decision === 'reject' && comment.trim() === '') {
      setCommentMissing(true)
      return
    }
    setCommentMissing(false)
    void run(
      () => decideRequisition(user.id, requisition.id, { decision, comment: comment.trim() || null }),
      (updated) => {
        onChange(updated)
        setNotice(t(decision === 'approve' ? 'requisition.detail.approvedNotice' : 'requisition.detail.rejectedNotice'))
      },
    )
  }

  function onIssueOrder() {
    void run(
      () => issueOrder(user.id, requisition.id),
      (result) => {
        setOrders(result.orders)
        onChange({ ...requisition, status: result.requisitionStatus })
        setNotice(t('requisition.detail.issued', { count: result.orders.length }))
      },
    )
  }

  // Keep the sidebar list in sync for requisitions opened by link too (own ones only).
  useEffect(() => {
    if (isOwner) rememberRequisition(user.id, { id: requisition.id, documentNumber: requisition.documentNumber })
  }, [isOwner, user.id, requisition.id, requisition.documentNumber])

  return (
    <section>
      <div className="page-head">
        <h1>
          {t('requisition.detail.title')} {requisition.documentNumber}
        </h1>
        <span className={`status status--${requisition.status}`}>{t(`status.${requisition.status}`)}</span>
      </div>

      {notice && (
        <div className="notice notice--success" role="status">
          {notice}
        </div>
      )}
      {actionError !== null && <ErrorNotice error={actionError} />}

      <div className="card">
        <dl className="facts">
          <div><dt>{t('fields.plantId')}</dt><dd>{requisition.plant.code} – {requisition.plant.name}</dd></div>
          <div><dt>{t('fields.costCenterId')}</dt><dd>{requisition.costCenter.code} – {requisition.costCenter.name}</dd></div>
          <div><dt>{t('requisition.detail.requestedBy')}</dt><dd>{requisition.requestedBy.name}</dd></div>
          <div><dt>{t('fields.neededBy')}</dt><dd>{formatDate(requisition.neededBy, lang)}</dd></div>
          {requisition.notes && <div className="facts__wide"><dt>{t('fields.notes')}</dt><dd>{requisition.notes}</dd></div>}
          {requisition.approvedBy && (
            <div>
              <dt>{t('requisition.detail.approvedBy')}</dt>
              <dd>
                {requisition.approvedBy.name} ({formatDateTime(requisition.approvedAt, lang)})
              </dd>
            </div>
          )}
          {requisition.rejectionReason && (
            <div className="facts__wide">
              <dt>{t('requisition.detail.rejectionReason')}</dt>
              <dd>{requisition.rejectionReason}</dd>
            </div>
          )}
        </dl>
      </div>

      <div className="card">
        <h2 className="card__title">{t('requisition.new.items')}</h2>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('fields.materialId')}</th>
                <th className="num">{t('fields.quantity')}</th>
                <th>{t('fields.unitOfMeasureId')}</th>
                <th className="num">{t('fields.estimatedUnitPrice')}</th>
                <th className="num">{t('requisition.detail.lineTotal')}</th>
              </tr>
            </thead>
            <tbody>
              {requisition.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.lineNumber}</td>
                  <td>{item.material.code} – {item.material.description}</td>
                  <td className="num">{formatNumber(item.quantity, lang)}</td>
                  <td>{item.unitOfMeasure.code}</td>
                  <td className="num">{formatMoney(item.estimatedUnitPrice, lang)}</td>
                  <td className="num">{formatMoney(item.estimatedTotal, lang)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th colSpan={5} scope="row" className="num">{t('requisition.total')}</th>
                <td className="num"><strong>{formatMoney(requisition.total, lang)}</strong></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {canSubmit && (
        <div className="card">
          <p>{t('requisition.detail.submitHelp')}</p>
          <button type="button" className="button button--primary" onClick={onSubmitForApproval} disabled={busy}>
            {busy ? t('common.saving') : t('requisition.detail.submitButton')}
          </button>
        </div>
      )}

      {canDecide && (
        <form className="card form" onSubmit={onDecide}>
          <h2 className="card__title">{t('requisition.detail.decisionTitle')}</h2>
          <p className="muted muted--small">{t('requisition.detail.decisionHelp')}</p>

          <div className="radio-row" role="radiogroup" aria-label={t('requisition.detail.decisionTitle')}>
            <label>
              <input type="radio" name="decision" checked={decision === 'approve'} onChange={() => setDecision('approve')} />{' '}
              {t('requisition.detail.approve')}
            </label>
            <label>
              <input type="radio" name="decision" checked={decision === 'reject'} onChange={() => setDecision('reject')} />{' '}
              {t('requisition.detail.reject')}
            </label>
          </div>

          <label className="field field--wide">
            <span>
              {t('fields.comment')}
              {decision === 'reject' ? ' *' : ` (${t('common.optional')})`}
            </span>
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2} />
            {commentMissing && <span className="field__error">{t('requisition.detail.commentRequired')}</span>}
          </label>

          <div>
            <button type="submit" className="button button--primary" disabled={busy}>
              {busy ? t('common.saving') : t('requisition.detail.decide')}
            </button>
          </div>
        </form>
      )}

      {canIssue && (
        <div className="card">
          <p>{t('requisition.detail.issueHelp')}</p>
          <button type="button" className="button button--primary" onClick={onIssueOrder} disabled={busy}>
            {busy ? t('common.saving') : t('requisition.detail.issueButton')}
          </button>
        </div>
      )}

      {orders && (
        <div className="card">
          <h2 className="card__title">{t('requisition.detail.ordersTitle')}</h2>
          <ul className="link-list">
            {orders.map((order) => (
              <li key={order.id}>
                <Link to={`/purchase-orders/${order.id}`}>{order.documentNumber}</Link>
                {' – '}
                {order.supplier.name} ({formatMoney(order.total, lang)})
              </li>
            ))}
          </ul>
          <p className="muted muted--small">{t('requisition.detail.ordersLimitation')}</p>
        </div>
      )}
    </section>
  )
}