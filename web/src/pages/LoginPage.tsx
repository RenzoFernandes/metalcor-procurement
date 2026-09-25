import { useNavigate } from 'react-router-dom'
import { useTranslation } from '../i18n/I18nContext'
import { useCurrentUser, type CurrentUser } from '../session/SessionContext'
import { ROLE_ORDER, SEED_USERS } from '../session/seedUsers'

// Temporary limitation: the users come from a static list (see seedUsers.ts),
// because the API has no users endpoint yet.
export function LoginPage() {
  const { t } = useTranslation()
  const { setUser } = useCurrentUser()
  const navigate = useNavigate()

  function signIn(user: CurrentUser) {
    setUser(user)
    navigate('/home')
  }

  return (
    <section className="login">
      <h1>{t('login.title')}</h1>
      <p className="muted">{t('login.intro')}</p>

      <div className="login__groups">
        {ROLE_ORDER.map((role) => (
          <section key={role} className="card">
            <h2 className="card__title">{t(`roles.${role}`)}</h2>
            <ul className="user-list">
              {SEED_USERS.filter((u) => u.role === role).map((u) => (
                <li key={u.id}>
                  <button type="button" className="user-list__button" onClick={() => signIn(u)}>
                    <span>{u.name}</span>
                    <span className="user-list__id">#{u.id}</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      <p className="muted muted--small">{t('login.limitation')}</p>
    </section>
  )
}