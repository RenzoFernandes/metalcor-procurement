import { Link, Navigate } from 'react-router-dom'
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

      <h2>{t('home.tasksTitle')}</h2>
      <p className="muted">{t('home.tasksIntro')}</p>

      <div className="task-list">
        <div className="card">
          <h3 className="card__title">{t('home.task1Title')}</h3>
          <p>{t('home.task1Text')}</p>
          <Link to="/requisitions/new" className="button button--outline">
            {t('home.task1Link')}
          </Link>
        </div>

        <div className="card">
          <h3 className="card__title">{t('home.task2Title')}</h3>
          <p>{t('home.task2Text')}</p>
        </div>

        <div className="card">
          <h3 className="card__title">{t('home.task3Title')}</h3>
          <p>{t('home.task3Text')}</p>
          <Link to="/dashboard" className="button button--outline">
            {t('home.task3Link')}
          </Link>
        </div>
      </div>
    </section>
  )
}