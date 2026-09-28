import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/DashboardPage'
import { HomePage } from './pages/HomePage'
import { InvoiceDetailPage } from './pages/InvoiceDetailPage'
import { LoginPage } from './pages/LoginPage'
import { PaymentDetailPage } from './pages/PaymentDetailPage'
import { PurchaseOrderDetailPage } from './pages/PurchaseOrderDetailPage'
import { RequisitionDetailPage } from './pages/RequisitionDetailPage'
import { RequisitionNewPage } from './pages/RequisitionNewPage'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<LoginPage />} />
        <Route path="/home" element={<HomePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/requisitions/new" element={<RequisitionNewPage />} />
        <Route path="/requisitions/:id" element={<RequisitionDetailPage />} />
        <Route path="/purchase-orders/:id" element={<PurchaseOrderDetailPage />} />
        <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
        <Route path="/payments/:id" element={<PaymentDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
