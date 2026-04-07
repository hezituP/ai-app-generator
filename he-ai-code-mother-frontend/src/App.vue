<template>
  <div id="app-root">
    <AppHeader v-if="showHeader" />
    <main :class="{ 'with-header': showHeader }">
      <router-view v-slot="{ Component, route }">
        <Transition :name="getRouteTransitionName(route.name as string | undefined)" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </Transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
const route = useRoute()
const showHeader = computed(() => !['login', 'register', 'app-editor'].includes(route.name as string))

function getRouteTransitionName(routeName?: string) {
  if (routeName === 'home' || routeName === 'my-apps') {
    return 'page-fade-slide'
  }
  return 'page-fade'
}
</script>

<style>
#app-root { height: 100%; display: flex; flex-direction: column; }
main { flex: 1; min-height: 0; }

.page-fade-enter-active,
.page-fade-leave-active,
.page-fade-slide-enter-active,
.page-fade-slide-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

.page-fade-slide-enter-from,
.page-fade-slide-leave-to {
  opacity: 0;
  transform: translateY(14px);
}
</style>
