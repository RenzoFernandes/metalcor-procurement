import { Navigate, useParams } from 'react-router-dom'
import { getPayment } from '../api/client'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { formatDate, formatDateTime, formatMoney } from '../components/format'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'

export function PaymentDetailPage() {
  const { t, lang } = useTranslation()
  const { user } = useCurrentUser()
  const { id: idParam } = useParams()
  const id = Number(idParam)

  const { data: payment, error, loading } = useApi(
    () => (Number.isInteger(id) && id > 0 ? getPayment(id) : Promise.reject(new Error('invalid id'))),
    id,
  )

  if (!user) return <Navigate to="/" replace />
  if (loading) return <LoadingNotice />
  if (error !== null || !payment) {
    return (
      <section>
        <h1>{t('payment.title')}</h1>
        <ErrorNotice error={error} />
      </section>
    )
  }

  return (
    <section>
      <div className="page-head">
        <h1>
          {t('payment.title')} {payment.documentNumber}
        </h1>
        <span className={`status status--${payment.status}`}>{t(`status.${payment.status}`)}</span>
      </div>

      <div className="card">
        <dl className="facts">
          <div><dt>{t('payment.invoice')}</dt><dd>{payment.invoiceNumber}</dd></div>
          <div><dt>{t('payment.amount')}</dt><dd><strong>{formatMoney(payment.amount, lang)}</strong></dd></div>
          <div><dt>{t('payment.scheduledFor')}</dt><dd>{formatDate(payment.scheduledFor, lang)}</dd></div>
          <div><dt>{t('fields.paymentMethod')}</dt><dd>{t(`paymentMethod.${payment.paymentMethod}`)}</dd></div>
          <div><dt>{t('payment.paidAt')}</dt><dd>{formatDateTime(payment.paidAt, lang)}</dd></div>
          <div><dt>{t('payment.createdBy')}</dt><dd>{payment.createdBy.name}</dd></div>
          <div><dt>{t('fields.reference')}</dt><dd>{payment.reference ?? '—'}</dd></div>
        </dl>
        <p className="muted muted--small">{t('payment.linkLimitation')}</p>
      </div>
    </section>
  )
}
