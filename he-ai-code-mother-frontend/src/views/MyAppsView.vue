<template>
  <div class="my-apps-page" :style="pageStyle">
    <div class="page-header">
      <div>
        <h1>我的应用</h1>
        <p>创建 HTML、多文件或完整 Vue3 + Vite 工程项目</p>
      </div>
      <a-button type="primary" class="create-btn" @click="showCreateModal = true">
        <PlusOutlined /> 创建应用
      </a-button>
    </div>

    <div v-if="apps.length" class="apps-grid">
      <article v-for="app in apps" :key="app.id" class="app-card">
        <div class="card-top" @click="goToEditor(app)">
          <div class="cover" v-if="app.cover">
            <img :src="app.cover" :alt="app.appName" />
          </div>
          <div v-else class="cover placeholder">{{ app.appName?.slice(0, 1) || 'A' }}</div>
          <span class="type-badge">{{ formatCodeGenType(app.codeGenType) }}</span>
        </div>

        <div class="card-body">
          <div class="title-row">
            <h3>{{ app.appName }}</h3>
            <a-dropdown>
              <a-button type="text"><EllipsisOutlined /></a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="openRenameModal(app)"><EditOutlined /> 重命名</a-menu-item>
                  <a-menu-item @click="handleDownload(app)"><DownloadOutlined /> 下载代码</a-menu-item>
                  <a-menu-item danger @click="handleDelete(app)"><DeleteOutlined /> 删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <p>{{ app.initPrompt || '暂无描述' }}</p>
          <div class="card-actions">
            <a-button size="small" @click="handleDeploy(app)" :loading="deployingId === app.id">
              <RocketOutlined /> 部署
            </a-button>
            <a-button type="primary" size="small" @click="goToEditor(app)">打开</a-button>
          </div>
        </div>
      </article>
    </div>

    <a-empty v-else class="empty" description="还没有应用，先创建一个吧" />

    <div class="pagination-wrap" v-if="total > pageSize">
      <a-pagination
        v-model:current="pageNum"
        :page-size="pageSize"
        :total="total"
        :show-size-changer="false"
        @change="fetchMyApps"
      />
    </div>

    <a-modal v-model:open="showCreateModal" title="创建应用" :footer="null" width="560px">
      <a-form layout="vertical" :model="createForm" @finish="handleCreate">
        <a-form-item label="应用名称" name="appName">
          <a-input v-model:value="createForm.appName" placeholder="例如：营销落地页生成器" />
        </a-form-item>
        <a-form-item label="生成模式" name="codeGenType">
          <div class="type-grid">
            <button
              v-for="item in codeGenTypes"
              :key="item.value"
              type="button"
              class="type-card"
              :class="{ active: createForm.codeGenType === item.value }"
              @click="createForm.codeGenType = item.value"
            >
              <strong>{{ item.label }}</strong>
              <span>{{ item.desc }}</span>
            </button>
          </div>
        </a-form-item>
        <a-form-item
          label="需求描述"
          name="initPrompt"
          :rules="[{ required: true, message: '请输入需求描述' }]"
        >
          <a-textarea
            v-model:value="createForm.initPrompt"
            :rows="4"
            placeholder="描述你希望平台生成的站点、页面和功能"
          />
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="showCreateModal = false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="creating">创建并进入编辑器</a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal v-model:open="showRenameModal" title="重命名应用" :footer="null" width="420px">
      <a-form layout="vertical" :model="renameForm" @finish="handleRename">
        <a-form-item label="应用名称" :rules="[{ required: true, message: '请输入名称' }]">
          <a-input v-model:value="renameForm.appName" />
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="showRenameModal = false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="renaming">保存</a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EllipsisOutlined,
  PlusOutlined,
  RocketOutlined,
} from '@ant-design/icons-vue'
import {
  addAppApi,
  deleteAppApi,
  deployAppApi,
  downloadAppCodeApi,
  listMyAppVOByPageApi,
  type AppVO,
  updateAppApi,
} from '@/api/app'

const router = useRouter()
const apps = ref<AppVO[]>([])
const pageNum = ref(1)
const pageSize = 12
const total = ref(0)
const showCreateModal = ref(false)
const creating = ref(false)
const showRenameModal = ref(false)
const renaming = ref(false)
const deployingId = ref<string | null>(null)
const currentApp = ref<AppVO | null>(null)
const selectedWallpaper = ref<'bg1' | 'bg2' | 'bg3'>('bg1')

const wallpaperMap: Record<'bg1' | 'bg2' | 'bg3', string> = {
  bg1: '/images/home-bg1.jpg',
  bg2: '/images/home-bg2.jpg',
  bg3: '/images/home-bg3.jpg',
}

const pageStyle = computed(() => ({
  '--my-apps-bg-image': `url('${wallpaperMap[selectedWallpaper.value]}')`,
}))

const codeGenTypes = [
  { value: 'html', label: 'HTML 单页', desc: '适合快速原型和静态页面' },
  { value: 'multi_file', label: '多文件站点', desc: 'HTML / CSS / JS 分离' },
  { value: 'vue_project', label: 'Vue3 + Vite 工程', desc: '生成完整工程目录与源码' },
]

const createForm = reactive({
  appName: '',
  codeGenType: 'vue_project',
  initPrompt: '',
})

const renameForm = reactive({
  appName: '',
})

async function fetchMyApps() {
  const res = await listMyAppVOByPageApi({ pageNum: pageNum.value, pageSize })
  if (res.data.code === 0) {
    apps.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  }
}

async function handleCreate() {
  creating.value = true
  try {
    const prompt = createForm.initPrompt.trim()
    window.localStorage.setItem('pendingAppPrompt', prompt)
    const res = await addAppApi(createForm)
    if (res.data.code === 0) {
      showCreateModal.value = false
      message.success('应用创建成功')
      const appId = res.data.data
      Object.assign(createForm, { appName: '', codeGenType: 'vue_project', initPrompt: '' })
      await router.push(`/app/${appId}?prompt=${encodeURIComponent(prompt)}`)
    } else {
      message.error(res.data.message || '创建失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    creating.value = false
  }
}

function openRenameModal(app: AppVO) {
  currentApp.value = app
  renameForm.appName = app.appName
  showRenameModal.value = true
}

async function handleRename() {
  if (!currentApp.value) return
  renaming.value = true
  try {
    const res = await updateAppApi({ id: currentApp.value.id, appName: renameForm.appName })
    if (res.data.code === 0) {
      message.success('名称已更新')
      showRenameModal.value = false
      await fetchMyApps()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    renaming.value = false
  }
}

function handleDelete(app: AppVO) {
  Modal.confirm({
    title: '确认删除应用？',
    content: `删除后会移除该应用的生成记录：${app.appName}`,
    okType: 'danger',
    async onOk() {
      const res = await deleteAppApi(app.id)
      if (res.data.code === 0) {
        message.success('已删除')
        await fetchMyApps()
      } else {
        message.error(res.data.message || '删除失败')
      }
    },
  })
}

async function handleDeploy(app: AppVO) {
  deployingId.value = app.id
  try {
    const res = await deployAppApi(app.id)
    if (res.data.code === 0) {
      message.success('部署成功')
      if (res.data.data) {
        window.open(res.data.data, '_blank')
      }
      await fetchMyApps()
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    deployingId.value = null
  }
}

async function handleDownload(app: AppVO) {
  try {
    const res = await downloadAppCodeApi(app.id)
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${app.appName || 'app'}.zip`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch {
    message.error('下载失败')
  }
}

function goToEditor(app: AppVO) {
  router.push(`/app/${app.id}?prompt=${encodeURIComponent(app.initPrompt || '')}`)
}

function formatCodeGenType(type: string) {
  return ({
    html: 'HTML 单页',
    multi_file: '多文件站点',
    vue_project: 'Vue3 + Vite 工程',
  } as Record<string, string>)[type] || type
}

onMounted(() => {
  const savedWallpaper = window.localStorage.getItem('homeWallpaper')
  if (savedWallpaper === 'bg1' || savedWallpaper === 'bg2' || savedWallpaper === 'bg3') {
    selectedWallpaper.value = savedWallpaper
  }
  fetchMyApps()
})
</script>

<style scoped>
.my-apps-page {
  min-height: 100vh;
  padding: 92px 24px 40px;
  background:
    linear-gradient(rgba(247, 244, 255, 0.34), rgba(238, 245, 255, 0.42)),
    var(--my-apps-bg-image, none),
    linear-gradient(135deg, #f8f0ff 0%, #fff0f5 38%, #eef7ff 100%);
  background-size: cover, cover, auto;
  background-position: center top, center top, center;
}

.page-header,
.apps-grid,
.empty,
.pagination-wrap {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  color: #f5fbff;
  font-size: 36px;
  text-shadow: 0 4px 18px rgba(66, 101, 158, 0.15);
}

.page-header p {
  margin: 8px 0 0;
  color: #43597f;
}

.create-btn {
  height: 44px;
  padding: 0 20px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #fd79a8, #a29bfe);
  box-shadow: 0 18px 34px rgba(162, 155, 254, 0.26);
}

.apps-grid {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}

.app-card {
  overflow: hidden;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.34);
  border: 1px solid rgba(255, 255, 255, 0.56);
  box-shadow: 0 24px 50px rgba(130, 112, 162, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(18px);
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.app-card:hover {
  transform: translateY(-7px);
  box-shadow: 0 30px 56px rgba(144, 126, 177, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.56);
}

.card-top {
  position: relative;
  height: 172px;
  cursor: pointer;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.36), rgba(255, 255, 255, 0.12));
  overflow: hidden;
}

.cover,
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  display: grid;
  place-items: center;
  font-size: 54px;
  font-weight: 700;
  color: rgba(66, 52, 104, 0.38);
  background: linear-gradient(135deg, rgba(253, 121, 168, 0.22), rgba(162, 155, 254, 0.22), rgba(72, 219, 251, 0.18));
}

.type-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.56);
  color: #6c5ce7;
  font-size: 12px;
  font-weight: 700;
}

.card-body {
  padding: 18px;
}

.title-row {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 8px;
}

.title-row h3 {
  margin: 0;
  font-size: 18px;
  color: #3b3457;
}

.card-body p {
  min-height: 44px;
  color: #726788;
  line-height: 1.74;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.card-actions :deep(.ant-btn) {
  border-radius: 999px;
}

.type-grid {
  display: grid;
  gap: 12px;
}

.type-card {
  text-align: left;
  border: none;
  background: rgba(245, 241, 251, 0.92);
  border-radius: 18px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #463e61;
  box-shadow: 9px 9px 18px rgba(208, 203, 223, 0.55), -9px -9px 18px rgba(255, 255, 255, 0.96);
}

.type-card.active {
  background: linear-gradient(135deg, rgba(253, 121, 168, 0.14), rgba(162, 155, 254, 0.16));
  box-shadow: inset 10px 10px 18px rgba(210, 204, 227, 0.55), inset -10px -10px 18px rgba(255, 255, 255, 0.98);
}

.type-card span {
  color: #7a7094;
  font-size: 13px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.ant-modal-content) {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.58);
  box-shadow: 0 28px 56px rgba(129, 112, 162, 0.18);
  backdrop-filter: blur(20px);
}

:deep(.ant-modal-header) {
  background: transparent;
}

:deep(.ant-input),
:deep(.ant-input-affix-wrapper),
:deep(.ant-input-textarea textarea) {
  border: none !important;
  border-radius: 18px !important;
  background: rgba(245, 241, 251, 0.94) !important;
  color: #4f4768 !important;
  box-shadow: inset 8px 8px 16px rgba(210, 206, 223, 0.62), inset -8px -8px 16px rgba(255, 255, 255, 0.96) !important;
}

@media (max-width: 768px) {
  .my-apps-page {
    padding: 82px 16px 32px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>