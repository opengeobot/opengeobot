// Function: i18n configuration
// Time: 2026-07-03
// Author: AxeXie
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN/platform.json'
import enUS from './locales/en-US/platform.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export default i18n
