<template>
  <header class="app-header">
    <div class="header-inner">
      <nav class="nav-links">
        <router-link to="/" class="nav-link" :class="{ active: $route.path === '/' }">
          <HomeOutlined /> 精选应用
        </router-link>
        <router-link
          v-if="userStore.loginUser"
          to="/my-apps"
          class="nav-link"
          :class="{ active: $route.path === '/my-apps' }"
        >
          <AppstoreOutlined /> 我的应用
        </router-link>
        <router-link
          v-if="userStore.isAdmin()"
          to="/admin/apps"
          class="nav-link nav-link-admin"
          :class="{ active: $route.path.startsWith('/admin') }"
        >
          <SettingOutlined /> 应用管理
        </router-link>
      </nav>

      <div class="header-right">
        <template v-if="userStore.loginUser">
          <a-dropdown placement="bottomRight">
            <button class="user-pill" type="button">
              <a-avatar
                :size="30"
                :src="userStore.loginUser.userAvatar"
                :style="avatarStyle"
              >
                {{ userStore.loginUser.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <span class="user-name">{{ userStore.loginUser.userName || '用户' }}</span>
              <DownOutlined class="down-icon" />
            </button>
            <template #overlay>
              <a-menu class="light-dropdown">
                <a-menu-item key="rename" @click="openRenameModal">
                  <EditOutlined /> 修改用户名
                </a-menu-item>
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

    <a-modal v-model:open="renameModalOpen" title="修改用户名" :footer="null" width="420px">
      <a-form layout="vertical" @finish="handleRenameSubmit">
        <a-form-item label="新的用户名" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="renameValue" :maxlength="20" placeholder="请输入新的用户名" />
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="renameModalOpen = false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="renaming">保存</a-button>
        </div>
      </a-form>
    </a-modal>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  DownOutlined,
  EditOutlined,
  HomeOutlined,
  LogoutOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { updateMyUserApi, userLogoutApi } from '@/api/user'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()
const renameModalOpen = ref(false)
const renameValue = ref('')
const renaming = ref(false)

const avatarStyle = computed(() => ({
  background: 'linear-gradient(135deg, rgba(114, 182, 255, 0.9), rgba(198, 228, 255, 0.9))',
  color: '#1d4674',
  fontSize: '12px',
  fontWeight: '700',
}))

function openRenameModal() {
  renameValue.value = userStore.loginUser?.userName || ''
  renameModalOpen.value = true
}

async function handleRenameSubmit() {
  const userName = renameValue.value.trim()
  if (!userName) {
    message.warning('请输入用户名')
    return
  }
  renaming.value = true
  try {
    const res = await updateMyUserApi({ userName })
    if (res.data.code === 0) {
      userStore.setLoginUser(res.data.data)
      renameModalOpen.value = false
      message.success('用户名已更新')
    } else {
      message.error(res.data.message || '修改失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    renaming.value = false
  }
}

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
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.28);
  box-shadow: 0 6px 28px rgba(39, 59, 94, 0.08);
}

.header-inner {
  max-width: 1240px;
  height: 100%;
  margin: 0 auto;
  padding: 0 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border-radius: 999px;
  color: #45638f;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid transparent;
  transition: all 0.22s ease;
}

.nav-link:hover,
.nav-link.active {
  color: #244a7a;
  background: rgba(255, 255, 255, 0.28);
  border-color: rgba(255, 255, 255, 0.36);
}

.nav-link-admin {
  color: #7b4b24;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-pill {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 4px 12px 4px 4px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.36);
  background: rgba(255, 255, 255, 0.22);
  color: #2e4a76;
  cursor: pointer;
}

.user-name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.down-icon {
  font-size: 10px;
  opacity: 0.6;
}

.btn-ghost,
.btn-reg {
  height: 36px !important;
  border-radius: 999px !important;
}

.btn-ghost {
  background: rgba(255, 255, 255, 0.18) !important;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  color: #345684 !important;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.light-dropdown) {
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.88) !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 18px 40px rgba(35, 54, 89, 0.12) !important;
  backdrop-filter: blur(16px);
}

:deep(.light-dropdown .ant-menu-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #35527d !important;
}

:deep(.ant-modal-content) {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 24px 50px rgba(40, 58, 89, 0.14);
  backdrop-filter: blur(18px);
}

:deep(.ant-modal-header) {
  background: transparent;
}

:deep(.ant-input) {
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.38) !important;
  background: rgba(255, 255, 255, 0.3) !important;
}
</style>