// Types mirroring the Java API DTOs (api/src/main/java/com/metalcor/procurement).
// Money and quantities arrive as JSON numbers; dates as ISO strings.

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PlantRef { id: number; code: string; name: string }
export interface CostCenterRef { id: number; code: string; name: string }
export interface MaterialRef { id: number; code: string; description: string }
export interface SupplierRef { id: number; code: string; name: string }
export interface UnitOfMeasureRef { id: number; code: string }
export interface UserRef { id: number; name: string; role: string }

// Requisitions

export interface CreateRequisitionItemRequest {
  materialId: number
  quantity: number
  unitOfMeasureId: number
  estimatedUnitPrice: number
  suggestedSupplierId?: number | null
  neededBy?: string | null
}

export interface CreateRequisitionRequest {
  plantId: number
  costCenterId: number
  neededBy?: string | null
  notes?: string | null
  items: CreateRequisitionItemRequest[]
}

export type Decision = 'approve' | 'reject'

export interface DecisionRequest {
  decision: Decision
  comment?: string | null
}

export interface RequisitionItem {
  id: number
  lineNumber: number
  material: MaterialRef
  quantity: number
  unitOfMeasure: UnitOfMeasureRef
  estimatedUnitPrice: number
  estimatedTotal: number
  suggestedSupplier: SupplierRef | null
  neededBy: string | null
}

export interface Requisition {
  id: number
  documentNumber: string
  status: string
  plant: PlantRef
  costCenter: CostCenterRef
  requestedBy: UserRef
  neededBy: string | null
  notes: string | null
  approvedBy: UserRef | null
  approvedAt: string | null
  rejectionReason: string | null
  items: RequisitionItem[]
  total: number
}

// Purchase orders

export interface PurchaseOrderItem {
  id: number
  material: MaterialRef
  quantity: number
  unitPrice: number
  lineTotal: number
}

export interface PurchaseOrder {
  id: number
  documentNumber: string
  supplier: SupplierRef
  requisitionNumber: string | null
  plant: PlantRef
  buyer: UserRef
  orderDate: string
  expectedDeliveryDate: string | null
  paymentTermsDays: number | null
  status: string
  items: PurchaseOrderItem[]
  total: number
}

export interface IssueOrderResponse {
  orders: PurchaseOrder[]
  requisitionStatus: string
}

// Goods receipts

export interface ReceiptItemRequest {
  purchaseOrderItemId: number
  quantityReceived: number
}

export interface ReceiptRequest {
  deliveryNoteNumber?: string | null
  items: ReceiptItemRequest[]
}

export interface GoodsReceiptItem {
  material: MaterialRef
  quantityReceived: number
}

export interface GoodsReceipt {
  id: number
  documentNumber: string
  purchaseOrderNumber: string
  plant: PlantRef
  receivedBy: UserRef
  receiptDate: string
  deliveryNoteNumber: string | null
  status: string
  items: GoodsReceiptItem[]
}

export interface CreateReceiptResponse {
  goodsReceipt: GoodsReceipt
  purchaseOrderStatus: string
}

// Invoices and payments

export interface InvoiceItemRequest {
  purchaseOrderItemId: number
  quantityInvoiced: number
  unitPrice: number
}

export interface InvoiceRequest {
  supplierInvoiceNumber: string
  invoiceDate: string
  dueDate: string
  items: InvoiceItemRequest[]
}

export interface InvoiceItem {
  material: MaterialRef
  quantityInvoiced: number
  unitPrice: number
  lineTotal: number
  priceException: boolean
  quantityException: boolean
}

export interface PaymentRef {
  id: number
  documentNumber: string
  amount: number
  scheduledFor: string
  paymentMethod: string
  paidAt: string | null
}

export interface Invoice {
  id: number
  documentNumber: string
  purchaseOrderNumber: string
  supplier: SupplierRef
  supplierInvoiceNumber: string
  invoiceDate: string
  dueDate: string
  postingDate: string
  grossAmount: number
  status: string
  blockReason: string | null
  approvedBy: UserRef | null
  approvedAt: string | null
  items: InvoiceItem[]
  payment: PaymentRef | null
}

export interface ApprovalRequest {
  notes?: string | null
}

export type PaymentMethod = 'bank_transfer' | 'boleto' | 'pix'

export interface PaymentRequest {
  paymentMethod: PaymentMethod
  reference?: string | null
}

export interface Payment {
  id: number
  documentNumber: string
  invoiceNumber: string
  amount: number
  scheduledFor: string
  paymentMethod: string
  paidAt: string | null
  createdBy: UserRef
  reference: string | null
}

export interface PayInvoiceResponse {
  payment: Payment
  invoiceStatus: string
  purchaseOrderStatus: string
}

// Reports

export interface SupplierScorecard {
  supplierCode: string
  supplierName: string
  orders: number
  totalSpend: number
  onTimeDeliveryPct: number | null
  avgDaysLate: number | null
  invoices: number
  exceptionRatePct: number
  avgDaysToResolve: number | null
  openExceptions: number
}

export type ExceptionResolution = 'open' | 'released'
export type ExceptionType = 'price_variance' | 'quantity_variance' | 'invoice_before_receipt'

export interface InvoiceException {
  invoiceNumber: string
  supplierId: number
  supplierName: string
  poNumber: string
  invoiceDate: string
  postingDate: string
  dueDate: string
  grossAmount: number
  invoiceStatus: string
  maxAbsPriceVariancePct: number | null
  maxQuantityVariancePct: number | null
  priceException: boolean
  quantityException: boolean
  invoiceBeforeReceipt: boolean
  exceptionTypes: string[]
  resolution: string
  approvedBy: string | null
  approvedAt: string | null
  daysToResolve: number | null
  blockReason: string | null
  ageDays: number | null
  stale: boolean
}

export interface InvoiceExceptionsQuery {
  resolution?: ExceptionResolution
  exceptionType?: ExceptionType
  page?: number
  size?: number
}