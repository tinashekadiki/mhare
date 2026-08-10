export default defineNuxtConfig({
  extends: ['../../packages/portal-shell'],
  compatibilityDate: '2026-08-06',
  devtools: { enabled: false },
  app: {
    head: {
      title: 'eMhare UI Workbench',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' }
      ]
    }
  }
})
