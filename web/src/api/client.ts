import type {
  ApprovalRequest,
  CopilotQueryResponse,
  CreateReceiptResponse,
  CreateRequisitionRequest,
  DashboardKpis,
  DashboardResult,
  DecisionRequest,
  ExceptionSummary,
  GoodsReceipt,
  Invoice,
  InvoiceException,
  InvoiceExceptionsQuery,
  InvoiceRequest,
  IssueOrderResponse,
  LatePaymentsByMonth,
  PageResponse,
  PayInvoiceResponse,
  Payment,
  PaymentRequest,
  PurchaseOrder,
  ReceiptRequest,
  Requisition,
  SpendByMonthCategory,
  StaleInvoice,
  SupplierScorecard,
} from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1').replace(/\/+$/, '')

/** Error raised for any non-2xx response. Carries the ProblemDetail (RFC 7807) fields. */
export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly title: string | null
  readonly detail: string | null
  /** Per-field messages of a 400 validation failure (the "errors" property of the ProblemDetail). */
  readonly fieldErrors: FieldError[]

  constructor(status: number, title: string | null, detail: string | null, fieldErrors: FieldError[] = []) {
    super(detail ?? title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
  }
}

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((entry) =>
    entry && typeof entry.field === 'string' && typeof entry.message === 'string'
      ? [{ field: entry.field, message: entry.message }]
      : [],
  )
}

/**
 * The API requires X-User-Id on every call, reads included. SessionProvider keeps this in sync with
 * the user chosen in "Entrar como", so GETs need no userId argument.
 */
let sessionUserId: number | null = null

export function setApiUserId(userId: number | null): void {
  sessionUserId = userId
}

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  userId?: number
  query?: Record<string, string | number | undefined>
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query } = options
  const userId = options.userId ?? sessionUserId ?? undefined

  const url = new URL(BASE_URL + path)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) url.searchParams.set(key, String(value))
    }
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (userId !== undefined) headers['X-User-Id'] = String(userId)

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, null, null)
  }

  if (!response.ok) {
    let title: string | null = null
    let detail: string | null = null
    let fieldErrors: FieldError[] = []
    try {
      const problem = await response.json()
      title = typeof problem.title === 'string' ? problem.title : null
      detail = typeof problem.detail === 'string' ? problem.detail : null
      fieldErrors = parseFieldErrors(problem.errors)
    } catch {
      // Body is not a ProblemDetail: keep only the status.
    }
    throw new ApiError(response.status, title, detail, fieldErrors)
  }

  return (await response.json()) as T
}

// Reports

export const getSupplierScorecard = () => request<SupplierScorecard[]>('/suppliers/scorecard')

export const getInvoiceExceptions = (query: InvoiceExceptionsQuery = {}) =>
  request<PageResponse<InvoiceException>>('/invoices/exceptions', { query: { ...query } })

// Requisitions

export const createRequisition = (userId: number, body: CreateRequisitionRequest) =>
  request<Requisition>('/requisitions', { method: 'POST', body, userId })

export const submitRequisition = (userId: number, id: number) =>
  request<Requisition>(`/requisitions/${id}/submit`, { method: 'POST', userId })

export const decideRequisition = (userId: number, id: number, body: DecisionRequest) =>
  request<Requisition>(`/requisitions/${id}/decide`, { method: 'POST', body, userId })

export const issueOrder = (userId: number, id: number) =>
  request<IssueOrderResponse>(`/requisitions/${id}/issue-order`, { method: 'POST', userId })

export const getRequisition = (id: number) => request<Requisition>(`/requisitions/${id}`)

// Purchase orders

export const getPurchaseOrder = (id: number) => request<PurchaseOrder>(`/purchase-orders/${id}`)

export const createReceipt = (userId: number, orderId: number, body: ReceiptRequest) =>
  request<CreateReceiptResponse>(`/purchase-orders/${orderId}/receipts`, { method: 'POST', body, userId })

export const createInvoice = (userId: number, orderId: number, body: InvoiceRequest) =>
  request<Invoice>(`/purchase-orders/${orderId}/invoices`, { method: 'POST', body, userId })

// Goods receipts, invoices and payments

export const getGoodsReceipt = (id: number) => request<GoodsReceipt>(`/goods-receipts/${id}`)

export const getInvoice = (id: number) => request<Invoice>(`/invoices/${id}`)

export const approveInvoice = (userId: number, id: number, body: ApprovalRequest = {}) =>
  request<Invoice>(`/invoices/${id}/approve`, { method: 'POST', body, userId })

export const payInvoice = (userId: number, id: number, body: PaymentRequest) =>
  request<PayInvoiceResponse>(`/invoices/${id}/pay`, { method: 'POST', body, userId })

export const getPayment = (id: number) => request<Payment>(`/payments/${id}`)

// Dashboard (manager panel)

export const getDashboardKpis = () => request<DashboardKpis>('/dashboard/kpis')

export const getDashboardSpendByMonth = () =>
  request<DashboardResult<SpendByMonthCategory[]>>('/dashboard/spend-by-month')

export const getDashboardExceptionSummary = () =>
  request<DashboardResult<ExceptionSummary[]>>('/dashboard/exception-summary')

export const getDashboardLatePayments = () =>
  request<DashboardResult<LatePaymentsByMonth[]>>('/dashboard/late-payments')

export const getDashboardStaleInvoices = () =>
  request<DashboardResult<StaleInvoice[]>>('/dashboard/stale-invoices')

export const getDashboardSupplierScorecard = () =>
  request<DashboardResult<SupplierScorecard[]>>('/dashboard/supplier-scorecard')

// Copilot

export const askCopilot = (userId: number, pergunta: string) =>
  request<CopilotQueryResponse>('/copilot/query', { method: 'POST', body: { pergunta }, userId })

function fileNameFromContentDisposition(headerValue: string | null): string | null {
  if (!headerValue) return null
  const match = /filename="?([^";]+)"?/.exec(headerValue)
  return match ? match[1] : null
}

export async function downloadDashboardExport(userId: number): Promise<{ blob: Blob; fileName: string }> {
  const url = new URL(BASE_URL + '/dashboard/export.xlsx')

  let response: Response
  try {
    response = await fetch(url, { headers: { 'X-User-Id': String(userId) } })
  } catch {
    throw new ApiError(0, null, null)
  }

  if (!response.ok) {
    let title: string | null = null
    let detail: string | null = null
    try {
      const problem = await response.json()
      title = typeof problem.title === 'string' ? problem.title : null
      detail = typeof problem.detail === 'string' ? problem.detail : null
    } catch {
      // Body is not a ProblemDetail: keep only the status.
    }
    throw new ApiError(response.status, title, detail)
  }

  const blob = await response.blob()
  const fileName = fileNameFromContentDisposition(response.headers.get('Content-Disposition')) ?? 'metalcor-painel.xlsx'
  return { blob, fileName }
}
