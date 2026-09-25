import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from '../i18n/I18nContext'

/*
 * Limitation: the API has no endpoint to search by document_number (e.g. PR-2026-000973),
 * so textual numbers cannot be resolved here. Only numeric ids work, either typed in the
 * main field (the user then picks requisition or order) or in the two dedicated ID fields.
 * Searching by document_number would need a new API endpoint (out of scope for now).
 */

const ONLY_DIGITS = /^\d+$/

function IdForm({ label, basePath }: { label: string; basePath: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [value, setValue] = useState('')
  const [invalid, setInvalid] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    const id = value.trim()
    if (!ONLY_DIGITS.test(id)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setValue('')
    navigate(`${basePath}/${Number(id)}`)
  }

  return (
    <form className="lookup__form" onSubmit={submit}>
      <label className="field">
        {label}
        <input
          inputMode="numeric"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          aria-invalid={invalid}
        />
      </label>
      {invalid && <p className="field__error">{t('lookup.invalidId')}</p>}
      <button type="submit" className="button button--outline">
        {t('lookup.go')}
      </button>
    </form>
  )
}

export function DocumentLookup() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [value, setValue] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [numericId, setNumericId] = useState<number | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    const text = value.trim()
    setNumericId(null)
    if (text === '') {
      setMessage(null)
      return
    }
    if (ONLY_DIGITS.test(text)) {
      setMessage(null)
      setNumericId(Number(text))
      return
    }
    setMessage(t('lookup.textNotSupported'))
  }

  function open(basePath: string) {
    if (numericId === null) return
    navigate(`${basePath}/${numericId}`)
    setValue('')
    setNumericId(null)
  }

  return (
    <div className="app-nav__group lookup">
      <h2 className="app-nav__heading">{t('lookup.title')}</h2>

      <form className="lookup__form" onSubmit={submit}>
        <label className="field">
          {t('lookup.documentLabel')}
          <input
            value={value}
            placeholder="PR-2026-000973"
            onChange={(e) => setValue(e.target.value)}
          />
        </label>
        <button type="submit" className="button button--outline">
          {t('lookup.go')}
        </button>
      </form>

      {message && <p className="app-nav__note">{message}</p>}

      {numericId !== null && (
        <div className="lookup__choice">
          <p className="app-nav__note">{t('lookup.whichType', { id: numericId })}</p>
          <button type="button" className="button button--ghost" onClick={() => open('/requisitions')}>
            {t('lookup.requisition')}
          </button>
          <button type="button" className="button button--ghost" onClick={() => open('/purchase-orders')}>
            {t('lookup.order')}
          </button>
        </div>
      )}

      <IdForm label={t('lookup.requisitionById')} basePath="/requisitions" />
      <IdForm label={t('lookup.orderById')} basePath="/purchase-orders" />

      <p className="app-nav__note">{t('lookup.note')}</p>
    </div>
  )
}
