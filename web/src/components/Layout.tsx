import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from '../i18n/I18nContext'
import { CHANGE_EVENT, loadRecentRequisitions, type RecentRequisition } from '../session/recentRequisitions'
import { useCurrentUser } from '../session/SessionContext'
import { DocumentLookup } from './DocumentLookup'
import { LanguageSwitcher } from './LanguageSwitcher'

const navClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'app-nav__link is-active' : 'app-nav__link')

function RecentRequisitions({ userId }: { userId: number }) {
  const { t } = useTranslation()
  const [items, setItems] = useState<RecentRequisition[]>(() => loadRecentRequisitions(userId))

  useEffect(() => {
    const refresh = () => setItems(loadRecentRequisitions(userId))
    refresh()
    window.addEventListener(CHANGE_EVENT, refresh)
    return () => window.removeEventListener(CHANGE_EVENT, refresh)
  }, [userId])

  return (
    <div className="app-nav__group">
      <h2 className="app-nav__heading">{t('nav.recent')}</h2>
      {items.length === 0 ? (
        <p className="app-nav__empty">{t('nav.recentEmpty')}</p>
      ) : (
        <ul className="app-nav__list">
          {items.map((r) => (
            <li key={r.id}>
              <NavLink to={`/requisitions/${r.id}`} className={navClass}>
                {r.documentNumber}
              </NavLink>
            </li>
          ))}
        </ul>
      )}
      <p className="app-nav__note">{t('nav.recentNote')}</p>
    </div>
  )
}

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
            <NavLink to="/home" className={navClass}>
              {t('nav.home')}
            </NavLink>
            {user.role === 'requester' && (
              <>
                <NavLink to="/requisitions/new" className={navClass}>
                  {t('nav.newRequisition')}
                </NavLink>
                <RecentRequisitions userId={user.id} />
              </>
            )}
            <DocumentLookup />
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
