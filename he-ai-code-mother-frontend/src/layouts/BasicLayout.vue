<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<template>
  <div class="basic-layout">
    <AppHeader />
    <main class="layout-main">
      <RouterView />
    </main>
    <GlobalFooter />
  </div>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
