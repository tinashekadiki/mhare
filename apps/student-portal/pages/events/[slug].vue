<script setup lang="ts">
definePageMeta({ public: true });
const route = useRoute();
</script>
<template>
  <EmharePublicContentDetail :slug="String(route.params.slug)" :allowed-kinds="['EVENT']" />
</template>
