<template>
  <div id="app-root">
    <AppHeader v-if="showHeader" />
    <main :class="{'with-header':showHeader}"><router-view /></main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
const route = useRoute()
const showHeader = computed(() => !['login','register','app-editor'].includes(route.name as string))
</script>

<style>
#app-root { height: 100%; display: flex; flex-direction: column; }
main { flex: 1; min-height: 0; }
</style>
