import { Navigate } from 'react-router-dom'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'

export function HomePage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()

  if (!user) return <Navigate to="/" replace />

  return (
    <section>
      <h1>{t('home.welcome', { name: user.name })}</h1>
      <p className="muted">{t('home.roleLine', { role: t(`roles.${user.role}`) })}</p>

      <div className="card">
        <h2 className="card__title">{t('home.scriptTitle')}</h2>
        <p>{t('home.scriptPlaceholder')}</p>
      </div>
    </section>
  )
}