<script setup lang="ts">
import type { MenuProps } from 'ant-design-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

interface MenuItemConfig {
  key: string
  label: string
  path: string
}

const props = defineProps<{
  menuItems: MenuItemConfig[]
}>()

const route = useRoute()
const router = useRouter()

const selectedKeys = computed(() => {
  const activeItem = props.menuItems.find((item) => item.path === route.path)
  return activeItem ? [activeItem.key] : []
})

const antdMenuItems = computed(() => {
  return props.menuItems.map((item) => ({
    key: item.key,
    label: item.label,
  }))
})

const onMenuClick: MenuProps['onClick'] = ({ key }) => {
  const target = props.menuItems.find((item) => item.key === key)
  if (target) {
    router.push(target.path)
  }
}
</script>

<template>
  <a-layout-header class="global-header">
    <div class="header-content">
      <div class="left-zone">
        <div class="brand">
          <img src="/logo.png" alt="logo" class="logo" />
          <span class="title">AI 代码母体</span>
        </div>

        <a-menu
          mode="horizontal"
          class="header-menu"
          :selected-keys="selectedKeys"
          :items="antdMenuItems"
          @click="onMenuClick"
        />
      </div>

      <div class="right-zone">
        <a-button type="primary">登录</a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.header-content {
  width: min(1200px, 100%);
  height: 100%;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.left-zone {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.brand {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  object-fit: cover;
}

.title {
  color: #111827;
  font-weight: 700;
  font-size: 18px;
  white-space: nowrap;
}

.header-menu {
  min-width: 0;
  flex: 1;
  border-bottom: none;
  background: transparent;
}

.right-zone {
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .global-header {
    height: auto;
    padding: 10px 12px;
  }

  .header-content {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .left-zone {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }
}
</style>
