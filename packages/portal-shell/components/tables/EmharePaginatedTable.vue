<script setup lang="ts">
defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  data: any[]
  columns?: any[]
  loading?: boolean
  initialPageSize?: number
  pageSizeOptions?: number[]
  manualPagination?: boolean
  page?: number
  pageSize?: number
  totalRecords?: number
}>(), {
  columns: () => [],
  loading: false,
  initialPageSize: 10,
  pageSizeOptions: () => [10, 25, 50, 100],
  manualPagination: false,
  page: 1,
  pageSize: 10,
  totalRecords: 0
})

const emit = defineEmits<{
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
}>()

const internalPage = ref(1)
const internalPageSize = ref(props.initialPageSize)
const currentPage = computed({
  get: () => props.manualPagination ? Math.max(1, props.page) : internalPage.value,
  set: (value: number) => {
    if (props.manualPagination) emit('update:page', value)
    else internalPage.value = value
  }
})
const currentPageSize = computed({
  get: () => props.manualPagination ? props.pageSize : internalPageSize.value,
  set: (value: number) => {
    if (props.manualPagination) {
      emit('update:pageSize', value)
      emit('update:page', 1)
    } else {
      internalPageSize.value = value
      internalPage.value = 1
    }
  }
})
const totalRecordCount = computed(() => props.manualPagination ? props.totalRecords : props.data.length)
const pageCount = computed(() => Math.max(1, Math.ceil(totalRecordCount.value / currentPageSize.value)))
const paginatedData = computed(() => {
  if (props.manualPagination) return props.data
  const start = (currentPage.value - 1) * currentPageSize.value
  return props.data.slice(start, start + currentPageSize.value)
})
const pageSizeItems = computed(() => props.pageSizeOptions.map(value => ({
  label: `${value} per page`,
  value
})))

watch(() => props.data, () => {
  if (!props.manualPagination) internalPage.value = 1
})
</script>

<template>
  <div class="min-w-0 overflow-hidden" data-emhare-paginated-table>
    <UTable
      v-bind="$attrs"
      :data="paginatedData"
      :columns="columns"
      :loading="loading"
    >
      <template v-for="(_, slotName) in $slots" #[slotName]="slotProperties">
        <slot :name="slotName" v-bind="slotProperties ?? {}" />
      </template>
    </UTable>

    <div class="flex flex-wrap items-center justify-between gap-3 border-t border-muted px-3 py-3 text-sm text-muted" data-emhare-pagination>
      <span>
        {{ totalRecordCount ? ((currentPage - 1) * currentPageSize) + 1 : 0 }}–{{ Math.min(currentPage * currentPageSize, totalRecordCount) }}
        of {{ totalRecordCount }} records · Page {{ currentPage }} of {{ pageCount }}
      </span>
      <div class="flex flex-wrap items-center justify-end gap-2">
        <USelect v-model="currentPageSize" :items="pageSizeItems" class="w-36" aria-label="Rows per page" />
        <UPagination
          :page="currentPage"
          :items-per-page="currentPageSize"
          :total="totalRecordCount"
          @update:page="currentPage = $event"
        />
      </div>
    </div>
  </div>
</template>
