export type FinanceChargeType = 'APPLICATION' | 'PROGRAMME' | 'MODULE' | 'ACCOMMODATION' | 'DINING' | 'GRADUATION' | 'OTHER'
export type FinanceFeeCatalogueStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED'
export type FinanceFeeRuleStatus = 'DRAFT' | 'PENDING_RATE' | 'APPROVED' | 'RETIRED'
export type FinanceFeeRatingStatus = 'RATED' | 'UNRATED'

export interface ApplicationPaymentOptions {
  proofOfPaymentUploadAvailable: boolean
  onlinePayment: {
    available: boolean
    availabilityMessage: string
  }
}

export interface ApplicationHostedCheckout {
  attemptId: string
  embeddedCheckoutUrl: string
  returnMessageOrigin: string
  formParameters: Record<string, string>
  expiresAt: string
}
export type FinanceFeeScopeDimension = 'GLOBAL' | 'INSTITUTION' | 'ACADEMIC_UNIT' | 'ACADEMIC_PERIOD' | 'PROGRAMME_PERIOD' | 'APPLICATION_TYPE' | 'PROGRAMME_LEVEL' | 'PROGRAMME_TYPE' | 'APPLICANT_CATEGORY' | 'PROGRAMME' | 'MODULE' | 'ACCOMMODATION_TYPE' | 'DINING_PLAN' | 'GRADUATION'

export type FinanceFeeContext = 'ACADEMIC' | 'APPLICATION' | 'ACCOMMODATION'
export type FinanceFeeStructureScopeType = 'INSTITUTION' | 'ACADEMIC_UNIT' | 'PROGRAMME' | 'PROGRAMME_LEVEL' | 'PROGRAMME_TYPE' | 'GLOBAL'
export type FinanceFeeStructureStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED'
export type FinanceFeeStructureDiscountType = 'PERCENTAGE' | 'AMOUNT'

export interface FinanceFeeStructureLineSummary {
  feeRuleId: string
  lineNumber: number
  feeCatalogueId: string
  feeCode: string
  feeName: string
  description: string
  chargeType: FinanceChargeType
  receivableAccountCode: string
  revenueAccountCode: string
  taxCode?: string | null
  transactionAmount: number
  transactionCurrencyCode: string
  baseAmount?: number | null
  ratingStatus: FinanceFeeRatingStatus
  status: FinanceFeeRuleStatus
}

export interface FinanceFeeStructureAttachmentSummary {
  id: string
  programmeId: string
  programmeCode: string
  programmeName: string
  academicPeriodId: string
  academicPeriodCode: string
  academicPeriodName: string
  programmePeriodNumber: number
  discountType?: FinanceFeeStructureDiscountType | null
  discountValue?: number | null
  discountReason?: string | null
  discountAmount: number
  discountedTotal: number
}

export interface FinanceFeeStructureSummary {
  id: string
  code: string
  name: string
  description?: string | null
  feeContext: FinanceFeeContext
  scopeType: FinanceFeeStructureScopeType
  scopeReferenceId?: string | null
  scopeReferenceCode?: string | null
  scopeReferenceName?: string | null
  programmeLevelId?: string | null
  programmeLevelCode: string
  programmeLevelName: string
  academicPeriodId?: string | null
  academicPeriodCode?: string | null
  academicPeriodName?: string | null
  programmePeriodNumber?: number | null
  applicantCategoryCode?: string | null
  transactionCurrencyCode: string
  effectiveFrom: string
  effectiveUntil?: string | null
  status: FinanceFeeStructureStatus
  preparedByUserId: string
  activatedByUserId?: string | null
  activatedAt?: string | null
  version: number
  lines: FinanceFeeStructureLineSummary[]
  attachments: FinanceFeeStructureAttachmentSummary[]
  selectedAttachment?: FinanceFeeStructureAttachmentSummary | null
}

export interface FinanceFeeStructureRegister {
  structures: FinanceFeeStructureSummary[]
}

export interface FinanceFeeRuleScopeSummary {
  id: string
  scopeDimension: FinanceFeeScopeDimension
  referenceId?: string | null
  referenceCode?: string | null
  referenceName?: string | null
}

export interface FinanceFeeRuleSummary {
  id: string
  ruleVersion: number
  transactionCurrencyCode: string
  transactionAmount: number
  baseCurrencyCode: 'USD'
  exchangeRateId?: string | null
  baseAmount?: number | null
  ratingStatus: FinanceFeeRatingStatus
  effectiveFrom: string
  effectiveUntil?: string | null
  scopeSignature?: string | null
  status: FinanceFeeRuleStatus
  preparedByUserId: string
  approvedByUserId?: string | null
  approvedAt?: string | null
  version: number
  scopes: FinanceFeeRuleScopeSummary[]
}

export interface FinanceFeeCatalogueSummary {
  id: string
  code: string
  name: string
  description?: string | null
  chargeType: FinanceChargeType
  receivableAccountCode: string
  revenueAccountCode: string
  taxCode?: string | null
  baseCurrencyCode: 'USD'
  status: FinanceFeeCatalogueStatus
  preparedByUserId: string
  activatedByUserId?: string | null
  activatedAt?: string | null
  version: number
  rules: FinanceFeeRuleSummary[]
}

export interface FinanceFeeCatalogueRegister {
  catalogues: FinanceFeeCatalogueSummary[]
}

export type FinanceStudentDiscountScopeType = 'INSTITUTION' | 'ACADEMIC_UNIT' | 'PROGRAMME'
export type FinanceStudentDiscountTargetType = 'ALL_FEES' | 'FEE_LINE'
export type FinanceStudentDiscountStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED'

export interface FinanceStudentDiscountSummary {
  id: string
  code: string
  name: string
  scopeType: FinanceStudentDiscountScopeType
  academicUnitId?: string | null
  academicUnitCode?: string | null
  academicUnitName?: string | null
  academicUnitDepth: number
  programmeId?: string | null
  programmeCode?: string | null
  programmeName?: string | null
  programmeLevelId: string
  programmeLevelCode: 'UG' | 'PG'
  programmeLevelName: string
  programmeStudyLevel: string
  targetType: FinanceStudentDiscountTargetType
  feeCatalogueId?: string | null
  feeCode?: string | null
  feeName?: string | null
  discountPercentage: number
  authorityReference: string
  effectiveFrom: string
  effectiveUntil?: string | null
  status: FinanceStudentDiscountStatus
  preparedByUserId: string
  activatedByUserId?: string | null
  activatedAt?: string | null
  version: number
}

export interface FinanceStudentDiscountRegister {
  discounts: FinanceStudentDiscountSummary[]
}

export type FinanceBillingEventStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'INVOICED'
export type FinanceBillingPolicyStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED'
export type FinanceBillingLineBasis = 'REGISTRATION' | 'REGISTERED_MODULE'
export type FinanceBillingQuantityBasis = 'FIXED' | 'MODULE_CREDIT'

export interface FinanceBillingPolicySummary {
  id: string
  code: string
  policyVersion: number
  name: string
  sourceEventType: string
  feeCatalogueId: string
  feeCode: string
  feeName: string
  lineBasis: FinanceBillingLineBasis
  quantityBasis: FinanceBillingQuantityBasis
  fixedQuantity?: number | null
  effectiveFrom: string
  effectiveUntil?: string | null
  status: FinanceBillingPolicyStatus
  preparedByUserId: string
  activatedByUserId?: string | null
  activatedAt?: string | null
  version: number
}

export interface FinanceBillingEventSummary {
  id: string
  eventNumber: string
  sourceService: string
  sourceEventType: string
  sourceEventId: string
  sourceAggregateType: string
  sourceAggregateId: string
  sourceLineReference: string
  studentFinanceAccountId: string
  accountNumber: string
  studentId: string
  studentNumber: string
  feeCatalogueId: string
  feeCode: string
  feeName: string
  feeRuleId: string
  feeRuleVersion: number
  description: string
  quantity: number
  transactionCurrencyCode: string
  transactionUnitAmount: number
  grossTransactionAmount: number
  transactionDiscountAmount: number
  transactionAmount: number
  baseCurrencyCode: 'USD'
  exchangeRateId?: string | null
  baseUnitAmount: number
  grossBaseAmount: number
  baseDiscountAmount: number
  baseAmount: number
  discountRuleId?: string | null
  discountRuleCode?: string | null
  discountPercentage?: number | null
  effectiveAt: string
  status: FinanceBillingEventStatus
  preparedByUserId: string
  submittedAt: string
  approvedByUserId?: string | null
  approvedAt?: string | null
  invoicedAt?: string | null
  version: number
  scopes: FinanceFeeRuleScopeSummary[]
}

export interface FinanceInvoiceLineSummary {
  id: string
  lineNumber: number
  billingEventId: string
  billingEventNumber: string
  feeCode: string
  description: string
  quantity: number
  grossTransactionAmount: number
  transactionDiscountAmount: number
  transactionAmount: number
  grossBaseAmount: number
  baseDiscountAmount: number
  baseAmount: number
  discountRuleId?: string | null
  discountRuleCode?: string | null
  discountPercentage?: number | null
  receivableAccountCode: string
  revenueAccountCode: string
}

export interface FinanceInvoiceSummary {
  id: string
  invoiceNumber: string
  studentFinanceAccountId: string
  accountNumber: string
  studentId: string
  studentNumber: string
  transactionCurrencyCode: string
  baseCurrencyCode: 'USD'
  grossTransactionAmount: number
  transactionDiscountAmount: number
  netTransactionAmount: number
  grossBaseAmount: number
  baseDiscountAmount: number
  netBaseAmount: number
  invoiceDate: string
  dueDate: string
  status: 'POSTED'
  postedByUserId: string
  postedAt: string
  version: number
  lines: FinanceInvoiceLineSummary[]
}

export interface FinanceBillingRegister {
  billingPolicies: FinanceBillingPolicySummary[]
  billingEvents: FinanceBillingEventSummary[]
  invoices: FinanceInvoiceSummary[]
}

export type FinancePaymentChannel = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'MOBILE_MONEY' | 'ONLINE' | 'OTHER'
export type FinancePaymentRatingStatus = 'RATED' | 'UNRATED'
export type FinancePaymentReconciliationStatus = 'PENDING' | 'RECONCILED' | 'REJECTED'

export interface FinanceExchangeRateSummary {
  id: string
  sourceCurrencyCode: string
  baseCurrencyCode: 'USD'
  rateToBase: number
  effectiveFrom: string
  effectiveTo?: string | null
  sourceName: string
  sourceReference?: string | null
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  preparedByUserId: string
  approvedByUserId?: string | null
  approvedAt?: string | null
  retiredByUserId?: string | null
  retiredAt?: string | null
  version: number
}

export interface FinanceStudentPaymentSummary {
  id: string
  paymentNumber: string
  studentFinanceAccountId?: string | null
  accountNumber?: string | null
  payerName: string
  providerCode: string
  providerTransactionReference: string
  paymentChannel: FinancePaymentChannel
  transactionCurrencyCode: string
  transactionAmount: number
  baseCurrencyCode: 'USD'
  exchangeRateId?: string | null
  baseAmount?: number | null
  ratingStatus: FinancePaymentRatingStatus
  paidAt: string
  reconciliationStatus: FinancePaymentReconciliationStatus
  capturedByUserId: string
  capturedAt: string
  reconciledByUserId?: string | null
  reconciledAt?: string | null
  inSuspense: boolean
  reversed: boolean
  receiptNumber?: string | null
  version: number
}

export interface FinancePaymentReceiptSummary {
  id: string
  paymentId: string
  paymentNumber: string
  receiptNumber: string
  studentFinanceAccountId: string
  accountNumber: string
  issuedAt: string
}

export interface FinancePaymentAllocationSummary {
  id: string
  allocationNumber: string
  paymentId: string
  paymentNumber: string
  invoiceId: string
  invoiceNumber: string
  transactionCurrencyCode: string
  transactionAmount: number
  paymentBaseAmount: number
  invoiceBaseAmount: number
  realisedExchangeDifference: number
  allocatedByUserId: string
  allocatedAt: string
  reversed: boolean
  reversalNumber?: string | null
  version: number
}

export interface FinanceCreditNoteLineSummary {
  id: string
  lineNumber: number
  invoiceLineId: string
  transactionAmount: number
  baseAmount: number
  reason: string
}

export interface FinanceCreditNoteSummary {
  id: string
  creditNoteNumber: string
  invoiceId: string
  invoiceNumber: string
  transactionCurrencyCode: string
  transactionAmount: number
  baseCurrencyCode: 'USD'
  baseAmount: number
  creditNoteDate: string
  status: 'DRAFT' | 'POSTED'
  preparedByUserId: string
  preparedAt: string
  postedByUserId?: string | null
  postedAt?: string | null
  version: number
  lines: FinanceCreditNoteLineSummary[]
}

export interface FinanceCollectionsRegister {
  exchangeRates: FinanceExchangeRateSummary[]
  payments: FinanceStudentPaymentSummary[]
  receipts: FinancePaymentReceiptSummary[]
  allocations: FinancePaymentAllocationSummary[]
  creditNotes: FinanceCreditNoteSummary[]
}

export interface FinanceStudentAccountSummary {
  id: string
  accountNumber: string
  studentId: string
  studentNumber: string
  primaryEmail: string
  status: string
  baseBalance: number
}

export interface FinanceStatementLine {
  lineType: 'INVOICE' | 'PAYMENT' | 'PAYMENT_REVERSAL' | 'CREDIT_NOTE'
  reference: string
  occurredAt: string
  description: string
  transactionCurrencyCode: string
  transactionDebit: number
  transactionCredit: number
  baseCurrencyCode: 'USD'
  baseDebit: number
  baseCredit: number
  runningBaseBalance: number
}

export interface FinanceStudentAccountStatement {
  account: FinanceStudentAccountSummary
  lines: FinanceStatementLine[]
}
