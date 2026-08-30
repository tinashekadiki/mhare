<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    showPagination?: boolean;
    items: any[];
    initialPageSize?: number;
    pageSizeOptions?: number[];
  }>(),
  {
    showPagination: true,
    initialPageSize: 10,
    pageSizeOptions: () => [10, 25, 50, 100],
  },
);

const page = ref(1);
const pageSize = ref(props.initialPageSize);
const pageCount = computed(() => Math.max(1, Math.ceil(props.items.length / pageSize.value)));
const paginatedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return props.items.slice(start, start + pageSize.value);
});
const pageSizeItems = computed(() =>
  props.pageSizeOptions.map((value) => ({
    label: `${value} per page`,
    value,
  })),
);

watch(
  () => props.items,
  () => {
    page.value = 1;
  },
);

watch(pageSize, () => {
  page.value = 1;
});
</script>

<template>
  <div class="min-w-0" data-emhare-paginated-collection>
    <slot :items="paginatedItems" :page="page" :page-size="pageSize" :total="items.length" />
    <div
      v-if="showPagination"
      class="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-muted pt-3 text-sm text-muted"
      data-emhare-pagination
    >
      <span>
        {{ items.length ? (page - 1) * pageSize + 1 : 0 }}–{{
          Math.min(page * pageSize, items.length)
        }}
        of {{ items.length }} records · Page {{ page }} of {{ pageCount }}
      </span>
      <div class="flex flex-wrap items-center justify-end gap-2">
        <USelect
          v-model="pageSize"
          :items="pageSizeItems"
          class="w-36"
          aria-label="Records per page"
        />
        <UPagination
          :page="page"
          :items-per-page="pageSize"
          :total="items.length"
          @update:page="page = $event"
        />
      </div>
    </div>
  </div>
</template>
