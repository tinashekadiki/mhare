<script setup lang="ts">
defineProps<{
  roles: string[]
  permissions: Array<{ code: string, label: string, roles: string[] }>
}>()
</script>

<template>
  <EmharePaginatedCollection :items="permissions" v-slot="{ items: paginatedPermissions }">
  <div class="overflow-auto rounded-md border border-muted">
    <table class="w-full min-w-max text-sm">
      <thead class="bg-elevated text-left text-muted">
        <tr>
          <th class="sticky left-0 bg-elevated p-3">Permission</th>
          <th v-for="role in roles" :key="role" class="p-3 text-center">{{ role }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="permission in paginatedPermissions" :key="permission.code" class="border-t border-muted">
          <td class="sticky left-0 bg-default p-3">
            <p class="font-medium text-highlighted">{{ permission.label }}</p>
            <p class="text-xs text-muted">{{ permission.code }}</p>
          </td>
          <td v-for="role in roles" :key="`${permission.code}-${role}`" class="p-3 text-center">
            <UIcon
              :name="permission.roles.includes(role) ? 'i-lucide-check' : 'i-lucide-minus'"
              class="mx-auto size-4"
              :class="permission.roles.includes(role) ? 'text-success' : 'text-muted'"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  </EmharePaginatedCollection>
</template>
