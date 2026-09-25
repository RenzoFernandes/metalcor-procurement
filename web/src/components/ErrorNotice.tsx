import { describeError } from '../api/errors'
import { useTranslation } from '../i18n/I18nContext'

export function ErrorNotice({ error }: { error: unknown }) {
  const { t } = useTranslation()
  const { message, details } = describeError(error, t)
  return (
    <div className="notice notice--error" role="alert">
      <p className="notice__title">{message}</p>
      {details.length > 0 && (
        <ul className="notice__list">
          {details.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function LoadingNotice() {
  const { t } = useTranslation()
  return (
    <p className="muted" role="status">
      {t('common.loading')}
    </p>
  )
}