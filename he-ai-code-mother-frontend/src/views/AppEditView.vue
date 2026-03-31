<template>
  <div class="app-edit-page">
    <div class="edit-header">
      <div class="edit-header-inner">
        <a-button type="text" class="back-btn" @click="router.back()">
          <ArrowLeftOutlined /> 返回
        </a-button>
        <h1 class="edit-title">编辑应用信息</h1>
      </div>
    </div>

    <div class="edit-body">
      <div v-if="loadingApp" class="loading-wrap"><a-spin size="large" /></div>

      <div v-else class="edit-card">
        <div class="card-top">
          <div class="app-cover-preview">
            <img v-if="coverPreview" :src="coverPreview" alt="封面预览" class="cover-img" />
            <div v-else class="cover-placeholder">
              <span class="cover-letter">{{ form.appName?.charAt(0) || 'A' }}</span>
            </div>
          </div>
          <div class="app-meta">
            <span class="meta-type">{{ fmtType(app?.codeGenType) }}</span>
            <span class="meta-id">ID: {{ appId }}</span>
            <div class="meta-creator" v-if="app?.userVO">
              <a-avatar :size="18" :src="app.userVO.userAvatar"
                :style="{ background: 'linear-gradient(135deg,#4f46e5,#0ea5e9)', fontSize: '9px' }">
                {{ app.userVO.userName?.charAt(0) }}
              </a-avatar>
              <span>{{ app.userVO.userName }}</span>
            </div>
          </div>
        </div>

        <a-divider />

        <a-form :model="form" @finish="handleSave" layout="vertical" class="edit-form">
          <a-form-item
            label="应用名称"
            name="appName"
            :rules="[{ required: true, message: '请输入应用名称' }]"
          >
            <a-input v-model:value="form.appName" placeholder="应用名称" class="field-input" />
          </a-form-item>

          <!-- 管理员专属字段 -->
          <template v-if="userStore.isAdmin()">
            <a-form-item label="封面 URL" name="cover">
              <a-input
                v-model:value="form.cover"
                placeholder="https://..."
                class="field-input"
                @change="onCoverChange"
              />
            </a-form-item>

            <a-form-item label="优先级" name="priority">
              <a-input-number
                v-model:value="form.priority"
                :min="0" :max="999"
                style="width:100%"
                class="field-input"
              />
              <div class="field-hint">设为 99 即标记为精选应用</div>
            </a-form-item>
          </template>

          <div class="form-actions">
            <a-button @click="router.back()">取消</a-button>
            <a-button type="primary" html-type="submit" :loading="saving" class="save-btn">
              <SaveOutlined /> 保存修改
            </a-button>
          </div>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { getAppVOByIdApi, updateAppApi, adminUpdateAppApi, type AppVO } from '@/api/app'
import { useUserStore } from '@/stores/userStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appId = route.params.id as string

const app = ref<AppVO | null>(null)
const loadingApp = ref(false)
const saving = ref(false)

const form = reactive({
  appName: '',
  cover: '',
  priority: 0,
})

const coverPreview = computed(() => form.cover || app.value?.cover || '')

function onCoverChange() {
  // coverPreview is computed — triggers automatically
}

async function loadApp() {
  loadingApp.value = true
  try {
    const res = await getAppVOByIdApi(appId)
    if (res.data.code === 0) {
      app.value = res.data.data
      form.appName = app.value?.appName || ''
      form.cover = app.value?.cover || ''
      form.priority = app.value?.priority ?? 0
    } else {
      message.error('应用不存在')
      router.push('/')
    }
  } catch {
    message.error('加载失败')
  } finally {
    loadingApp.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    let res
    if (userStore.isAdmin()) {
      // 管理员使用 admin 接口，支持更多字段
      res = await adminUpdateAppApi({
        id: appId,
        appName: form.appName,
        cover: form.cover || undefined,
        priority: form.priority,
      })
    } else {
      // 普通用户只能修改名称
      res = await updateAppApi({ id: appId, appName: form.appName })
    }
    if (res.data.code === 0) {
      message.success('保存成功')
      router.back()
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    saving.value = false
  }
}

function fmtType(t?: string) {
  if (!t) return ''
  return ({ html: 'HTML', vue: 'Vue', react: 'React', multi_file: '多文件' } as Record<string, string>)[t] || t
}

onMounted(() => { loadApp() })
</script>

<style scoped>
.app-edit-page {
  min-height: 100vh;
  padding-top: 58px;
  background: #f8fafc;
}

.edit-header {
  background: white;
  border-bottom: 1px solid rgba(99, 102, 241, 0.08);
  padding: 16px 0;
}

.edit-header-inner {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 28px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: #9ca3af !important;
  display: flex !important;
  align-items: center !important;
  gap: 5px !important;
  font-size: 13px !important;
  padding: 0 !important;
}

.back-btn:hover {
  color: #4f46e5 !important;
}

.edit-title {
  font-size: 18px;
  font-weight: 800;
  color: #1e1b4b;
  letter-spacing: -0.3px;
}

.edit-body {
  max-width: 720px;
  margin: 32px auto;
  padding: 0 28px;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.edit-card {
  background: white;
  border: 1px solid rgba(99, 102, 241, 0.09);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 4px 24px rgba(99, 102, 241, 0.07);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 4px;
}

.app-cover-preview {
  width: 72px;
  height: 72px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(99, 102, 241, 0.1);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(99,102,241,0.1), rgba(14,165,233,0.07));
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-letter {
  font-size: 28px;
  font-weight: 800;
  color: rgba(79, 70, 229, 0.25);
}

.app-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-type {
  display: inline-block;
  padding: 2px 10px;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.18);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  color: #4f46e5;
  width: fit-content;
}

.meta-id {
  font-size: 12px;
  color: #d1d5db;
  font-family: 'JetBrains Mono', monospace;
}

.meta-creator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.edit-form {
  margin-top: 4px;
}

:deep(.ant-form-item-label > label) {
  font-size: 13px !important;
  font-weight: 600 !important;
  color: #374151 !important;
}

:deep(.field-input) {
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  background: #fafafa !important;
  transition: all 0.18s !important;
}

:deep(.field-input:hover) {
  border-color: #a5b4fc !important;
}

:deep(.field-input:focus-within) {
  border-color: #4f46e5 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1) !important;
  background: white !important;
}

:deep(.field-input .ant-input) {
  background: transparent !important;
  color: #1e1b4b !important;
}

.field-hint {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.save-btn {
  display: flex !important;
  align-items: center !important;
  gap: 5px !important;
  border-radius: 9px !important;
  font-weight: 700 !important;
  height: 38px !important;
  padding: 0 20px !important;
}
</style>
