<script setup lang="ts">
import type {
  EmhareDataTableAction,
  EmhareDataTableColumn,
  EmhareDataTableExportOption,
  EmhareDataTableSavedView,
  EmhareDataTableState
} from '../../types/emhare-ui'

type TableRow = Record<string, unknown>

const props = withDefaults(defineProps<{
  columns: EmhareDataTableColumn[]
  rows: TableRow[]
  total: number
  state: EmhareDataTableState
  rowKey?: string
  loading?: boolean
  error?: string
  rowActions?: EmhareDataTableAction[]
  bulkActions?: EmhareDataTableAction[]
  savedViews?: EmhareDataTableSavedView[]
  exportOptions?: EmhareDataTableExportOption[]
  expandable?: boolean
}>(), {
  rowKey: 'id',
  loading: false,
  error: undefined,
  rowActions: () => [],
  bulkActions: () => [],
  savedViews: () => [],
  exportOptions: () => [
    { id: 'csv', label: 'CSV', icon: 'i-lucide-file-down' },
    { id: 'excel', label: 'Excel', icon: 'i-lucide-file-spreadsheet' },
    { id: 'pdf', label: 'PDF', icon: 'i-lucide-file-text' },
    { id: 'print', label: 'Print', icon: 'i-lucide-printer' }
  ],
  expandable: false
})

const emit = defineEmits<{
  'update:state': [state: EmhareDataTableState]
  'row-action': [payload: { action: EmhareDataTableAction, row: TableRow }]
  'bulk-action': [payload: { action: EmhareDataTableAction, selectedRows: TableRow[] }]
  'inline-edit': [payload: { row: TableRow, key: string, value: unknown }]
  export: [option: EmhareDataTableExportOption]
  'saved-view-create': [state: EmhareDataTableState]
  'saved-view-apply': [view: EmhareDataTableSavedView]
}>()

const expandedKeys = ref<Array<string | number>>([])
const draftEdits = reactive<Record<string, Record<string, unknown>>>({})

const visibleColumnKeys = computed(() => props.state.visibleColumns?.length
  ? props.state.visibleColumns
  : props.columns.filter((column) => !column.hidden).map((column) => column.key)
)

const visibleColumns = computed(() => props.columns.filter((column) => visibleColumnKeys.value.includes(column.key)))
const selectedKeys = computed(() => props.state.selectedKeys ?? [])
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.state.pageSize)))
const selectedRows = computed(() => props.rows.filter((row) => selectedKeys.value.includes(rowId(row))))
const pageSizeItems = [10, 25, 50, 100].map((value) => ({
  label: `${value} per page`,
  value
}))

const exportItems = computed(() => props.exportOptions.map((option) => ({
  label: option.label,
  icon: option.icon,
  onSelect: () => emit('export', option)
})))

const savedViewItems = computed(() => [
  ...props.savedViews.map((view) => ({
    label: view.label,
    icon: 'i-lucide-bookmark',
    onSelect: () => emit('saved-view-apply', view)
  })),
  { label: 'Save current view', icon: 'i-lucide-save', onSelect: () => emit('saved-view-create', props.state) }
])

const bulkItems = computed(() => props.bulkActions.map((action) => ({
  label: action.label,
  icon: action.icon,
  color: action.tone,
  onSelect: () => emit('bulk-action', { action, selectedRows: selectedRows.value })
})))

function rowId(row: TableRow) {
  return row[props.rowKey] as string | number
}

function nextState(patch: Partial<EmhareDataTableState>) {
  emit('update:state', { ...props.state, ...patch })
}

function setSearch(value: string) {
  nextState({ search: value, page: 1 })
}

function setPage(page: number) {
  nextState({ page })
}

function setPageSize(pageSize: unknown) {
  nextState({ page: 1, pageSize: Number(pageSize) })
}

function toggleSort(column: EmhareDataTableColumn) {
  if (!column.sortable) {
    return
  }
  const current = props.state.sort?.find((sort) => sort.key === column.key)
  const direction = current?.direction === 'asc' ? 'desc' : 'asc'
  nextState({ sort: [{ key: column.key, direction }], page: 1 })
}

function sortIcon(column: EmhareDataTableColumn) {
  const current = props.state.sort?.find((sort) => sort.key === column.key)
  if (!current) {
    return 'i-lucide-arrow-up-down'
  }
  return current.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down'
}

function toggleColumn(column: EmhareDataTableColumn) {
  const keys = new Set(visibleColumnKeys.value)
  if (keys.has(column.key)) {
    keys.delete(column.key)
  } else {
    keys.add(column.key)
  }
  nextState({ visibleColumns: [...keys] })
}

function toggleRow(row: TableRow, selected: boolean | 'indeterminate') {
  const keys = new Set(selectedKeys.value)
  const id = rowId(row)
  if (selected) {
    keys.add(id)
  } else {
    keys.delete(id)
  }
  nextState({ selectedKeys: [...keys] })
}

function toggleAll(selected: boolean | 'indeterminate') {
  const keys = new Set(selectedKeys.value)
  for (const row of props.rows) {
    if (selected) {
      keys.add(rowId(row))
    } else {
      keys.delete(rowId(row))
    }
  }
  nextState({ selectedKeys: [...keys] })
}

function rowSelected(row: TableRow) {
  return selectedKeys.value.includes(rowId(row))
}

function allRowsSelected() {
  return props.rows.length > 0 && props.rows.every((row) => rowSelected(row))
}

function toggleExpanded(row: TableRow) {
  const id = rowId(row)
  expandedKeys.value = expandedKeys.value.includes(id)
    ? expandedKeys.value.filter((key) => key !== id)
    : [...expandedKeys.value, id]
}

function isExpanded(row: TableRow) {
  return expandedKeys.value.includes(rowId(row))
}

function editCell(row: TableRow, column: EmhareDataTableColumn, value: unknown) {
  const id = String(rowId(row))
  draftEdits[id] = { ...(draftEdits[id] ?? {}), [column.key]: value }
  emit('inline-edit', { row, key: column.key, value })
}

function cellValue(row: TableRow, column: EmhareDataTableColumn) {
  return draftEdits[String(rowId(row))]?.[column.key] ?? row[column.key]
}

function columnTotal(column: EmhareDataTableColumn) {
  return props.rows.reduce((sum, row) => {
    const value = Number(row[column.key] ?? 0)
    return Number.isFinite(value) ? sum + value : sum
  }, 0)
}

function rowActionItems(row: TableRow) {
  return props.rowActions.map((action) => ({
    label: action.label,
    icon: action.icon,
    color: action.tone,
    onSelect: () => emit('row-action', { action, row })
  }))
}
</script>

<template>
  <div class="overflow-hidden rounded-md border border-muted bg-default" data-emhare-paginated-table>
    <div class="flex flex-wrap items-center gap-2 border-b border-muted p-3">
      <UInput
        :model-value="state.search"
        icon="i-lucide-search"
        placeholder="Search"
        class="min-w-56 flex-1"
        @update:model-value="setSearch(String($event ?? ''))"
      />

      <UDropdownMenu :items="bulkItems">
        <EmhareGuidedActionButton
          icon="i-lucide-list-checks"
          label="Bulk actions"
          color="neutral"
          variant="outline"
          guidance-title="Select records first"
          :guidance-instructions="selectedKeys.length ? [] : ['Select at least one table row before opening bulk actions.']"
        />
      </UDropdownMenu>

      <UDropdownMenu
        :items="columns.map((column) => ({
          label: column.label,
          icon: visibleColumnKeys.includes(column.key) ? 'i-lucide-check' : 'i-lucide-eye-off',
          onSelect: () => toggleColumn(column)
        }))"
      >
        <UButton icon="i-lucide-columns-3" label="Columns" color="neutral" variant="outline" />
      </UDropdownMenu>

      <UDropdownMenu :items="savedViewItems">
        <UButton icon="i-lucide-bookmark" label="Views" color="neutral" variant="outline" />
      </UDropdownMenu>

      <UDropdownMenu :items="exportItems">
        <UButton icon="i-lucide-download" label="Export" color="neutral" variant="outline" />
      </UDropdownMenu>
    </div>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-circle-alert"
      title="Could not load data"
      :description="error"
      class="m-3"
    />

    <div class="max-w-full overflow-auto">
      <table class="w-full min-w-max text-sm">
        <thead class="sticky top-0 z-10 bg-elevated text-left text-muted">
          <tr>
            <th class="w-10 p-3">
              <UCheckbox
                :model-value="allRowsSelected()"
                aria-label="Select all rows"
                @update:model-value="toggleAll"
              />
            </th>
            <th v-if="expandable" class="w-10 p-3" />
            <th
              v-for="column in visibleColumns"
              :key="column.key"
              class="p-3 font-medium"
              :class="[
                column.align === 'right' ? 'text-right' : column.align === 'center' ? 'text-center' : 'text-left',
                column.frozen ? 'sticky left-0 z-20 bg-elevated' : ''
              ]"
              :style="{ width: column.width }"
            >
              <button
                type="button"
                class="inline-flex items-center gap-1"
                :class="{ 'cursor-pointer': column.sortable }"
                @click="toggleSort(column)"
              >
                <span>{{ column.label }}</span>
                <UIcon v-if="column.sortable" :name="sortIcon(column)" class="size-3.5" />
              </button>
            </th>
            <th v-if="rowActions.length" class="w-12 p-3 text-right">
              Actions
            </th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading">
            <td :colspan="visibleColumns.length + 3" class="p-4">
              <div class="space-y-2">
                <USkeleton v-for="index in 5" :key="index" class="h-8 w-full" />
              </div>
            </td>
          </tr>

          <tr v-else-if="!rows.length">
            <td :colspan="visibleColumns.length + 3" class="p-10 text-center">
              <UIcon name="i-lucide-inbox" class="mx-auto size-8 text-muted" />
              <p class="mt-3 text-sm font-medium text-highlighted">No records found</p>
              <p class="mt-1 text-sm text-muted">Adjust the filters or create a new record.</p>
            </td>
          </tr>

          <template v-for="row in rows" v-else :key="rowId(row)">
            <tr class="border-t border-muted hover:bg-elevated/60">
              <td class="p-3">
                <UCheckbox
                  :model-value="rowSelected(row)"
                  aria-label="Select row"
                  @update:model-value="toggleRow(row, $event)"
                />
              </td>
              <td v-if="expandable" class="p-3">
                <UButton
                  :icon="isExpanded(row) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  :aria-label="isExpanded(row) ? 'Collapse row' : 'Expand row'"
                  @click="toggleExpanded(row)"
                />
              </td>
              <td
                v-for="column in visibleColumns"
                :key="column.key"
                class="p-3"
                :class="[
                  column.align === 'right' ? 'text-right' : column.align === 'center' ? 'text-center' : 'text-left',
                  column.frozen ? 'sticky left-0 bg-default' : ''
                ]"
              >
                <UInput
                  v-if="column.editable"
                  :model-value="cellValue(row, column) as string"
                  size="sm"
                  @update:model-value="editCell(row, column, $event)"
                />
                <slot
                  v-else
                  :name="`${column.key}-cell`"
                  :row="row"
                  :value="cellValue(row, column)"
                >
                  <span>{{ cellValue(row, column) }}</span>
                </slot>
              </td>
              <td v-if="rowActions.length" class="p-3 text-right">
                <UDropdownMenu :items="rowActionItems(row)">
                  <UButton icon="i-lucide-ellipsis" color="neutral" variant="ghost" size="xs" />
                </UDropdownMenu>
              </td>
            </tr>
            <tr v-if="expandable && isExpanded(row)" class="border-t border-muted bg-elevated/40">
              <td :colspan="visibleColumns.length + 3" class="p-4">
                <slot name="expanded-row" :row="row">
                  <pre class="overflow-auto rounded-md bg-default p-3 text-xs text-muted">{{ row }}</pre>
                </slot>
              </td>
            </tr>
          </template>
        </tbody>

        <tfoot v-if="visibleColumns.some((column) => column.total)" class="border-t border-muted bg-elevated font-medium">
          <tr>
            <td class="p-3" :colspan="expandable ? 2 : 1">Totals</td>
            <td
              v-for="column in visibleColumns"
              :key="column.key"
              class="p-3"
              :class="column.align === 'right' ? 'text-right' : ''"
            >
              <span v-if="column.total">{{ columnTotal(column) }}</span>
            </td>
            <td v-if="rowActions.length" />
          </tr>
        </tfoot>
      </table>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-3 border-t border-muted p-3 text-sm text-muted" data-emhare-pagination>
      <span>{{ selectedKeys.length }} selected · {{ total }} total · Page {{ state.page }} of {{ pageCount }}</span>
      <div class="flex flex-wrap items-center justify-end gap-2">
        <USelect
          :model-value="state.pageSize"
          :items="pageSizeItems"
          class="w-36"
          aria-label="Rows per page"
          @update:model-value="setPageSize"
        />
        <UPagination
          :page="state.page"
          :items-per-page="state.pageSize"
          :total="total"
          @update:page="setPage"
        />
      </div>
    </div>
  </div>
</template>
