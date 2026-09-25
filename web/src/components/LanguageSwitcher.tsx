import { LANGUAGES, useTranslation } from '../i18n/I18nContext'

export function LanguageSwitcher() {
  const { lang, setLang, t } = useTranslation()

  return (
    <div className="lang-switcher" role="group" aria-label={t('header.language')}>
      {LANGUAGES.map((code, index) => (
        <span key={code} className="lang-switcher__item">
          {index > 0 && <span className="lang-switcher__sep" aria-hidden="true">|</span>}
          <button
            type="button"
            className="lang-switcher__button"
            aria-pressed={lang === code}
            onClick={() => setLang(code)}
          >
            {code.toUpperCase()}
          </button>
        </span>
      ))}
    </div>
  )
}