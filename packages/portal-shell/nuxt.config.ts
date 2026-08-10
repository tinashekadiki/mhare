export default defineNuxtConfig({
  ssr: false,
  modules: ['@nuxt/ui', '@pinia/nuxt'],
  components: [
    { path: './components', pathPrefix: false }
  ],
  ui: {
    fonts: false,
    colorMode: false
  },
  icon: {
    clientBundle: {
      icons: [
        'lucide:book-open',
        'lucide:bookmark',
        'lucide:bell',
        'lucide:building-2',
        'lucide:calendar-clock',
        'lucide:check',
        'lucide:check-circle',
        'lucide:chevron-down',
        'lucide:chevron-right',
        'lucide:circle',
        'lucide:circle-alert',
        'lucide:circle-user-round',
        'lucide:columns-3',
        'lucide:dollar-sign',
        'lucide:download',
        'lucide:ellipsis',
        'lucide:eye',
        'lucide:eye-off',
        'lucide:file-down',
        'lucide:file-check-2',
        'lucide:file-spreadsheet',
        'lucide:file-text',
        'lucide:files',
        'lucide:graduation-cap',
        'lucide:inbox',
        'lucide:layout-dashboard',
        'lucide:list-checks',
        'lucide:log-in',
        'lucide:log-out',
        'lucide:minus',
        'lucide:pencil',
        'lucide:percent',
        'lucide:panel-right-open',
        'lucide:plus',
        'lucide:printer',
        'lucide:receipt-text',
        'lucide:refresh-cw',
        'lucide:save',
        'lucide:school',
        'lucide:search',
        'lucide:send',
        'lucide:shield-check',
        'lucide:triangle-alert',
        'lucide:user',
        'lucide:user-plus',
        'lucide:users',
        'lucide:x',
        'lucide:x-circle',
        'lucide:arrow-up',
        'lucide:arrow-down',
        'lucide:arrow-up-down'
      ]
    },
    serverBundle: {
      collections: ['lucide']
    }
  }
})
