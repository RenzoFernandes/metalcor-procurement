import { ApiError } from './client'

type Translate = (key: string, params?: Record<string, string | number>) => string

export interface FriendlyError {
  /** Short, translated headline. Never raw JSON. */
  message: string
  /** Extra lines: per-field validation messages, or the API's own detail for business-rule errors. */
  details: string[]
}

// Validation messages produced by the API (Bean Validation), mapped to translation keys.
const VALIDATION_MESSAGES: Record<string, string> = {
  'must not be null': 'errors.validation.required',
  'must not be blank': 'errors.validation.required',
  'must be positive': 'errors.validation.positive',
  'must contain at least one item': 'errors.validation.minItems',
  'must contain at most 8 items': 'errors.validation.maxItems',
  'must be approve or reject': 'errors.validation.decision',
}

const KNOWN_FIELDS = new Set([
  'plantId',
  'costCenterId',
  'neededBy',
  'notes',
  'items',
  'materialId',
  'quantity',
  'unitOfMeasureId',
  'estimatedUnitPrice',
  'decision',
  'comment',
])

function fieldLabel(field: string, t: Translate): string {
  const line = /^items\[(\d+)\]\.(\w+)$/.exec(field)
  if (line) {
    return t('errors.line', { n: Number(line[1]) + 1 }) + ' – ' + fieldLabel(line[2], t)
  }
  return KNOWN_FIELDS.has(field) ? t(`fields.${field}`) : field
}

export function describeError(error: unknown, t: Translate): FriendlyError {
  if (!(error instanceof ApiError)) {
    return { message: t('errors.unexpected'), details: [] }
  }

  if (error.status === 0) return { message: t('errors.network'), details: [] }

  if (error.status === 400 && error.fieldErrors.length > 0) {
    const details = error.fieldErrors.map((fe) => {
      const key = VALIDATION_MESSAGES[fe.message]
      return `${fieldLabel(fe.field, t)}: ${key ? t(key) : fe.message}`
    })
    return { message: t('errors.validation.headline'), details }
  }

  const detail = error.detail ? [error.detail] : []
  switch (error.status) {
    case 400:
      return { message: t('errors.badRequest'), details: detail }
    case 401:
      return { message: t('errors.unauthorized'), details: [] }
    case 403:
      return { message: t('errors.forbidden'), details: detail }
    case 404:
      return { message: t('errors.notFound'), details: [] }
    case 409:
      return { message: t('errors.conflict'), details: detail }
    default:
      return { message: t('errors.server'), details: [] }
  }
}