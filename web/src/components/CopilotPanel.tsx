import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, askCopilot } from '../api/client'
import type { CopilotQueryResponse } from '../api/types'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'

interface Exchange {
  id: number
  question: string
  status: 'loading' | 'done' | 'error'
  response?: CopilotQueryResponse
  errorMessage?: string
}

function errorMessage(error: unknown, t: ReturnType<typeof useTranslation>['t']): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return t('errors.network')
    if (error.status === 429) return t('copilot.rateLimited')
    if (error.detail) return error.detail
    if (error.title) return error.title
  }
  return t('errors.unexpected')
}

function cellText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function CopilotResult({ response, t }: { response: CopilotQueryResponse; t: ReturnType<typeof useTranslation>['t'] }) {
  return (
    <>
      <p className="copilot-msg__meta">{t('copilot.rows', { count: response.totalLinhas })}</p>

      {response.linhas.length > 0 && (
        <div className="table-wrap table-wrap--copilot">
          <table className="table">
            <thead>
              <tr>
                {response.colunas.map((col) => (
                  <th key={col} scope="col">{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {response.linhas.map((row, i) => (
                <tr key={i}>
                  {response.colunas.map((col) => (
                    <td key={col}>{cellText(row[col])}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <details className="sql-block">
        <summary className="sql-block__summary">{t('copilot.sql')}</summary>
        <pre className="sql-block__code">
          <code>{response.sql}</code>
        </pre>
      </details>
    </>
  )
}

export function CopilotPanel() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const [open, setOpen] = useState(false)
  const [hasOpenedOnce, setHasOpenedOnce] = useState(false)
  const [text, setText] = useState('')
  const [exchanges, setExchanges] = useState<Exchange[]>([])
  const [sending, setSending] = useState(false)

  const panelRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const fabRef = useRef<HTMLButtonElement>(null)
  const wasOpenRef = useRef(false)
  const hasUser = user !== null

  // React 18 has no `inert` prop: set it on the DOM node so a closed panel is not tabbable.
  useEffect(() => {
    const panel = panelRef.current
    if (!panel) return
    panel.inert = !open
    if (open) {
      inputRef.current?.focus()
      wasOpenRef.current = true
    } else if (wasOpenRef.current) {
      // The FAB is re-mounted when the panel closes; return focus to it.
      wasOpenRef.current = false
      fabRef.current?.focus()
    }
  }, [open, hasUser])

  useEffect(() => {
    if (!open) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  if (!user) return null

  function toggleOpen() {
    setOpen((v) => !v)
    setHasOpenedOnce(true)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const question = text.trim()
    if (!question || sending || !user) return

    const id = Date.now()
    setExchanges((prev) => [...prev, { id, question, status: 'loading' }])
    setText('')
    setSending(true)

    try {
      const response = await askCopilot(user.id, question)
      setExchanges((prev) => prev.map((ex) => (ex.id === id ? { ...ex, status: 'done', response } : ex)))
    } catch (error) {
      setExchanges((prev) =>
        prev.map((ex) => (ex.id === id ? { ...ex, status: 'error', errorMessage: errorMessage(error, t) } : ex)),
      )
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      {!open && (
        <button
          ref={fabRef}
          type="button"
          className="copilot-fab"
          aria-label={t('copilot.fabLabel')}
          aria-expanded={open}
          onClick={toggleOpen}
        >
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false">
            <path
              fill="currentColor"
              d="m12 3 1.912 5.813a2 2 0 0 0 1.275 1.275L21 12l-5.813 1.912a2 2 0 0 0-1.275 1.275L12 21l-1.912-5.813a2 2 0 0 0-1.275-1.275L3 12l5.813-1.912a2 2 0 0 0 1.275-1.275L12 3Z"
            />
          </svg>
          <span className="copilot-fab__label">{t('copilot.fabText')}</span>
          {!hasOpenedOnce && <span className="copilot-fab__pulse" aria-hidden="true" />}
        </button>
      )}

      <div
        ref={panelRef}
        className={open ? 'copilot-panel is-open' : 'copilot-panel'}
        role="dialog"
        aria-modal="true"
        aria-label={t('copilot.title')}
      >
        <div className="copilot-panel__header">
          <h2>{t('copilot.title')}</h2>
          <button type="button" className="button button--ghost" onClick={() => setOpen(false)}>
            {t('copilot.close')}
          </button>
        </div>

        <div className="copilot-panel__body">
          {exchanges.length === 0 && <p className="muted muted--small">{t('copilot.intro')}</p>}

          {exchanges.map((ex) => (
            <div key={ex.id} className="copilot-thread">
              <div className="copilot-msg copilot-msg--user">{ex.question}</div>

              {ex.status === 'loading' && (
                <div className="copilot-msg copilot-msg--bot">
                  <p className="muted" role="status">
                    {t('copilot.sending')}
                  </p>
                </div>
              )}

              {ex.status === 'done' && ex.response && (
                <div className="copilot-msg copilot-msg--bot">
                  <CopilotResult response={ex.response} t={t} />
                </div>
              )}

              {ex.status === 'error' && (
                <div className="copilot-msg copilot-msg--bot">
                  <div className="notice notice--error" role="alert">
                    <p className="notice__title">{ex.errorMessage}</p>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        <form className="copilot-panel__footer" onSubmit={handleSubmit}>
          <input
            ref={inputRef}
            type="text"
            aria-label={t('copilot.questionLabel')}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={t('copilot.placeholder')}
            disabled={sending}
          />
          <button type="submit" className="button button--primary" disabled={sending || !text.trim()}>
            {t('copilot.send')}
          </button>
        </form>
      </div>
    </>
  )
}
