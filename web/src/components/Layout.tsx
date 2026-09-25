import { Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser } from '../session/SessionContext'
import { LanguageSwitcher } from './LanguageSwitcher'

export function Layout() {
  const { t } = useTranslation()
  const { user, setUser } = useCurrentUser()
  const navigate = useNavigate()

  function switchUser() {
    setUser(null)
    navigate('/')
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header__brand">
          <span className="app-header__title">{t('app.title')}</span>
          <span className="app-header__subtitle">{t('app.subtitle')}</span>
        </div>

        <div className="app-header__actions">
          {user && (
            <>
              <div className="app-header__user" aria-label={t('header.currentUser')}>
                <span className="app-header__user-name">{user.name}</span>
                <span className="badge">{t(`roles.${user.role}`)}</span>
              </div>
              <button type="button" className="button button--secondary" onClick={switchUser}>
                {t('header.switchUser')}
              </button>
            </>
          )}
          <LanguageSwitcher />
        </div>
      </header>

      <div className="app-body">
        {user && (
          <nav className="app-nav" aria-label={t('nav.label')}>
            <p className="app-nav__empty">{t('nav.empty')}</p>
          </nav>
        )}
        <main className="app-main">
          <Outlet />
        </main>
      </div>

      <footer className="app-footer">{t('app.fictionalNotice')}</footer>
    </div>
  )
}