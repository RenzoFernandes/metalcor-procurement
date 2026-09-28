import { useState } from 'react'
import { useTranslation } from '../i18n/I18nContext'
import { useTechnicalMode } from '../technical/TechnicalModeContext'

/** Collapsible SQL block for the technical mode. Renders nothing while the mode is off. */
export function SqlBlock({ sql }: { sql: string }) {
  const { t } = useTranslation()
  const { enabled } = useTechnicalMode()
  const [copied, setCopied] = useState(false)

  if (!enabled) return null

  async function copy() {
    try {
      await navigator.clipboard.writeText(sql)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard API unavailable: nothing we can do without prompting the user.
    }
  }

  return (
    <details className="sql-block">
      <summary className="sql-block__summary">{t('technical.sql')}</summary>
      <div className="sql-block__body">
        <button type="button" className="button button--ghost sql-block__copy" onClick={copy}>
          {copied ? t('technical.copied') : t('technical.copy')}
        </button>
        <pre className="sql-block__code">
          <code>{sql}</code>
        </pre>
      </div>
    </details>
  )
}