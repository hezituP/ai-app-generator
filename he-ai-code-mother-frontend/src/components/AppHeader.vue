<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link to="/" class="logo">
        <div class="logo-mark">
          <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
            <rect width="26" height="26" rx="7" fill="url(#lg)"/>
            <path d="M7 13l4.5 4.5L19 8" stroke="white" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
            <defs><linearGradient id="lg" x1="0" y1="0" x2="26" y2="26"><stop offset="0%" stop-color="#84fab0"/><stop offset="100%" stop-color="#3ab0a0"/></linearGradient></defs>
          </svg>
        </div>
        <span class="logo-text">He<span class="gradient-text">AI</span></span>
      </router-link>

      <nav class="nav-links">
        <router-link to="/" class="nav-link" :class="{ active: $route.path === '/' }">
          <HomeOutlined /> 精选应用
        </router-link>
        <router-link
          to="/my-apps"
          class="nav-link"
          :class="{ active: $route.path === '/my-apps' }"
          v-if="userStore.loginUser"
        >
          <AppstoreOutlined /> 我的应用
        </router-link>
        <router-link
          to="/admin/apps"
          class="nav-link nav-link-admin"
          :class="{ active: $route.path.startsWith('/admin') }"
          v-if="userStore.isAdmin()"
        >
          <SettingOutlined /> 应用管理
        </router-link>
      </nav>

      <div class="header-right">
        <template v-if="userStore.loginUser">
          <a-dropdown placement="bottomRight">
            <div class="user-pill">
              <a-avatar
                :size="28"
                :src="userStore.loginUser.userAvatar"
                :style="{ background: 'linear-gradient(135deg,#84fab0,#3ab0a0)', fontSize: '12px', fontWeight: '700' }"
              >{{ userStore.loginUser.userName?.charAt(0) || 'U' }}</a-avatar>
              <span class="user-name">{{ userStore.loginUser.userName || '用户' }}</span>
              <DownOutlined style="font-size:10px;opacity:0.5" />
            </div>
            <template #overlay>
              <a-menu class="light-dropdown">
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <router-link to="/login"><a-button class="btn-ghost">登录</a-button></router-link>
          <router-link to="/register"><a-button type="primary" class="btn-reg">免费注册</a-button></router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/userStore'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userLogoutApi } from '@/api/user'
import {
  HomeOutlined,
  AppstoreOutlined,
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

const userStore = useUserStore()
const router = useRouter()

async function handleLogout() {
  await userLogoutApi()
  userStore.setLoginUser(null)
  message.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: 58px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(99, 102, 241, 0.1);
  box-shadow: 0 1px 0 rgba(99, 102, 241, 0.06), 0 4px 16px rgba(99, 102, 241, 0.05);
}

.header-inner {
  max-width: 1240px;
  margin: 0 auto;
  height: 100%;
  padding: 0 28px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-mark {
  display: flex;
  filter: drop-shadow(0 2px 6px rgba(58, 176, 160, 0.3));
}

.logo-text {
  font-size: 19px;
  font-weight: 800;
  color: #1e1b4b;
  letter-spacing: -0.5px;
}

.gradient-text {
  background: linear-gradient(135deg, #4f46e5, #0ea5e9);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 13px;
  border-radius: 8px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.18s;
}

.nav-link:hover {
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.07);
}

.nav-link.active {
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.1);
  font-weight: 600;
}

.nav-link-admin {
  color: #d97706;
}

.nav-link-admin:hover {
  color: #b45309;
  background: rgba(217, 119, 6, 0.07);
}

.nav-link-admin.active {
  color: #b45309;
  background: rgba(217, 119, 6, 0.1);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.user-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 11px 3px 4px;
  border-radius: 40px;
  background: #f5f3ff;
  border: 1px solid rgba(99, 102, 241, 0.18);
  cursor: pointer;
  transition: all 0.18s;
}

.user-pill:hover {
  background: #ede9fe;
  border-color: rgba(99, 102, 241, 0.35);
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1a5a3a;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-ghost {
  background: transparent !important;
  border: 1px solid rgba(99, 102, 241, 0.25) !important;
  color: #4f46e5 !important;
  border-radius: 8px !important;
  font-weight: 600 !important;
  height: 34px !important;
}

.btn-ghost:hover {
  background: rgba(99, 102, 241, 0.06) !important;
  border-color: #4f46e5 !important;
}

.btn-reg {
  border-radius: 8px !important;
  font-weight: 600 !important;
  height: 34px !important;
}

:deep(.light-dropdown) {
  background: white !important;
  border: 1px solid rgba(99, 102, 241, 0.12) !important;
  border-radius: 10px !important;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12) !important;
}

:deep(.light-dropdown .ant-menu-item) {
  color: #1a3a2a !important;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.light-dropdown .ant-menu-item:hover) {
  background: #f5f3ff !important;
  color: #4f46e5 !important;
}
</style>
