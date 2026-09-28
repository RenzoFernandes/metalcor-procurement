import { useState } from 'react'
import { downloadDashboardExport } from '../../api/client'
import { describeError } from '../../api/errors'
import { useTranslation } from '../../i18n/I18nContext'
import { useCurrentUser } from '../../session/SessionContext'

export function ExportButton() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function handleClick() {
    if (!user || loading) return
    setLoading(true)
    setError(null)
    try {
      const { blob, fileName } = await downloadDashboardExport(user.id)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="dashboard-export">
      <button type="button" className="button button--outline" onClick={handleClick} disabled={loading}>
        {loading ? t('dashboard.export.loading') : t('dashboard.export.button')}
      </button>
      {error !== null && (
        <div className="notice notice--error" role="alert">
          <p className="notice__title">{describeError(error, t).message}</p>
        </div>
      )}
    </div>
  )
}
