import { useTranslation } from '../i18n/I18nContext'
import { useTechnicalMode } from '../technical/TechnicalModeContext'

export function TechnicalModeToggle() {
  const { t } = useTranslation()
  const { enabled, toggle } = useTechnicalMode()

  return (
    <label className="tech-toggle">
      <input type="checkbox" checked={enabled} onChange={toggle} />
      <span>{t('header.technicalMode')}</span>
    </label>
  )
}