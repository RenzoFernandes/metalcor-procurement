import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import {
  getDashboardExceptionSummary,
  getDashboardKpis,
  getDashboardLatePayments,
  getDashboardSpendByMonth,
  getDashboardStaleInvoices,
  getDashboardSupplierScorecard,
} from '../api/client'
import { useApi } from '../api/useApi'
import { ErrorNotice, LoadingNotice } from '../components/ErrorNotice'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'
import { ExceptionSummaryChart } from './dashboard/ExceptionSummaryChart'
import { ExportButton } from './dashboard/ExportButton'
import { KpiCards } from './dashboard/KpiCards'
import { LatePaymentsPanel } from './dashboard/LatePaymentsPanel'
import { SpendByMonthChart } from './dashboard/SpendByMonthChart'
import { StaleInvoicesTable } from './dashboard/StaleInvoicesTable'
import { SupplierScorecardChart } from './dashboard/SupplierScorecardChart'

function Section({ loading, error, children }: { loading: boolean; error: unknown; children: ReactNode }) {
  if (loading) return <LoadingNotice />
  if (error !== null) return <ErrorNotice error={error} />
  return <>{children}</>
}

export function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()

  const kpis = useApi(getDashboardKpis, 'kpis')
  const spendByMonth = useApi(getDashboardSpendByMonth, 'spend-by-month')
  const exceptionSummary = useApi(getDashboardExceptionSummary, 'exception-summary')
  const latePayments = useApi(getDashboardLatePayments, 'late-payments')
  const staleInvoices = useApi(getDashboardStaleInvoices, 'stale-invoices')
  const supplierScorecard = useApi(getDashboardSupplierScorecard, 'supplier-scorecard')

  if (!user) return <Navigate to="/" replace />

  return (
    <section>
      <h1>{t('dashboard.title')}</h1>
      <p className="muted">{t('dashboard.intro')}</p>

      <ExportButton />

      <Section loading={kpis.loading} error={kpis.error}>
        {kpis.data && <KpiCards kpis={kpis.data} />}
      </Section>

      <div className="dashboard-grid">
        <Section loading={spendByMonth.loading} error={spendByMonth.error}>
          {spendByMonth.data && <SpendByMonthChart rows={spendByMonth.data.data} sql={spendByMonth.data.sql} />}
        </Section>

        <Section loading={exceptionSummary.loading} error={exceptionSummary.error}>
          {exceptionSummary.data && <ExceptionSummaryChart rows={exceptionSummary.data.data} sql={exceptionSummary.data.sql} />}
        </Section>

        <div className="dashboard-grid__full">
          <Section loading={latePayments.loading} error={latePayments.error}>
            {latePayments.data && <LatePaymentsPanel rows={latePayments.data.data} sql={latePayments.data.sql} />}
          </Section>
        </div>

        <div className="dashboard-grid__full">
          <Section loading={supplierScorecard.loading} error={supplierScorecard.error}>
            {supplierScorecard.data && (
              <SupplierScorecardChart rows={supplierScorecard.data.data} sql={supplierScorecard.data.sql} />
            )}
          </Section>
        </div>
      </div>

      <Section loading={staleInvoices.loading} error={staleInvoices.error}>
        {staleInvoices.data && <StaleInvoicesTable rows={staleInvoices.data.data} sql={staleInvoices.data.sql} />}
      </Section>
    </section>
  )
}