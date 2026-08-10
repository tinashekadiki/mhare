<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const columns = [
  { key: 'reference', label: 'Reference', sortable: true, frozen: true, width: '12rem' },
  { key: 'applicant', label: 'Applicant', sortable: true },
  { key: 'programme', label: 'Programme' },
  { key: 'status', label: 'Status', filterable: true },
  { key: 'fee', label: 'Fee', align: 'right' as const, total: true },
  { key: 'score', label: 'Score', align: 'right' as const, editable: true }
]

const allRows = [
  { id: 'APP-001', reference: 'APP-001', applicant: 'Tinashe Kadiki', programme: 'BSc Computer Science', status: 'Submitted', fee: 25, score: 82 },
  { id: 'APP-002', reference: 'APP-002', applicant: 'Rudo Moyo', programme: 'Bachelor of Commerce', status: 'Paid', fee: 25, score: 76 },
  { id: 'APP-003', reference: 'APP-003', applicant: 'Farai Dube', programme: 'BSc Information Systems', status: 'Draft', fee: 0, score: 0 },
  { id: 'APP-004', reference: 'APP-004', applicant: 'Nyasha Ncube', programme: 'BSc Computer Science', status: 'Review', fee: 25, score: 91 },
  { id: 'APP-005', reference: 'APP-005', applicant: 'Kudzai Chirwa', programme: 'Bachelor of Accounting', status: 'Submitted', fee: 25, score: 71 },
  { id: 'APP-006', reference: 'APP-006', applicant: 'Tariro Biti', programme: 'BSc Statistics', status: 'Paid', fee: 25, score: 79 },
  { id: 'APP-007', reference: 'APP-007', applicant: 'Munyaradzi Zhou', programme: 'BSc Economics', status: 'Review', fee: 25, score: 84 },
  { id: 'APP-008', reference: 'APP-008', applicant: 'Ruvarashe Mlambo', programme: 'Bachelor of Law', status: 'Submitted', fee: 25, score: 74 },
  { id: 'APP-009', reference: 'APP-009', applicant: 'Takudzwa Gono', programme: 'BSc Agriculture', status: 'Draft', fee: 0, score: 0 },
  { id: 'APP-010', reference: 'APP-010', applicant: 'Anesu Dzingai', programme: 'BSc Computer Science', status: 'Paid', fee: 25, score: 88 },
  { id: 'APP-011', reference: 'APP-011', applicant: 'Simbarashe Ndlovu', programme: 'Bachelor of Commerce', status: 'Review', fee: 25, score: 86 },
  { id: 'APP-012', reference: 'APP-012', applicant: 'Vimbai Sithole', programme: 'BSc Information Systems', status: 'Submitted', fee: 25, score: 77 }
]

const tableState = ref({
  page: 1,
  pageSize: 10,
  search: '',
  sort: [],
  filters: [],
  selectedKeys: [],
  visibleColumns: columns.map((column) => column.key)
})

const filteredRows = computed(() => {
  const search = tableState.value.search?.toLowerCase()
  if (!search) {
    return allRows
  }
  return allRows.filter((row) => Object.values(row).join(' ').toLowerCase().includes(search))
})

const paginatedRows = computed(() => {
  const startIndex = (tableState.value.page - 1) * tableState.value.pageSize
  return filteredRows.value.slice(startIndex, startIndex + tableState.value.pageSize)
})

const savedViews = [
  { id: 'paid', label: 'Paid applications', state: { ...tableState.value, search: 'Paid' } },
  { id: 'cs', label: 'Computer Science', state: { ...tableState.value, search: 'Computer Science' } }
]
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader
        title="Data Table"
        description="Server-state-first table with search, sort, selection, actions, column visibility, saved views, export hooks, expansion, totals, and inline edit events."
        icon="i-lucide-columns-3"
      />

      <div class="p-4">
        <EmhareDataTable
          v-model:state="tableState"
          :columns="columns"
          :rows="paginatedRows"
          :total="filteredRows.length"
          row-key="id"
          expandable
          :row-actions="[
            { id: 'view', label: 'View', icon: 'i-lucide-eye' },
            { id: 'review', label: 'Review', icon: 'i-lucide-check', tone: 'primary' }
          ]"
          :bulk-actions="[
            { id: 'assign', label: 'Assign reviewer', icon: 'i-lucide-user' },
            { id: 'export-selected', label: 'Export selected', icon: 'i-lucide-download' }
          ]"
          :saved-views="savedViews"
        >
          <template #status-cell="{ value }">
            <EmhareStatusPill :label="String(value)" :tone="value === 'Paid' || value === 'Review' ? 'success' : value === 'Draft' ? 'warning' : 'primary'" />
          </template>
          <template #fee-cell="{ value }">
            <EmhareMoneyDisplay :amount="Number(value)" />
          </template>
          <template #expanded-row="{ row }">
            <EmhareDescriptionList
              :items="[
                { label: 'Reference', value: String(row.reference ?? '') },
                { label: 'Applicant', value: String(row.applicant ?? '') },
                { label: 'Programme', value: String(row.programme ?? '') },
                { label: 'Workflow', value: 'Admissions review' }
              ]"
            />
          </template>
        </EmhareDataTable>
      </div>
    </template>
  </UDashboardPanel>
</template>
