<template>
  <div class="admin-apps-page">
    <div class="page-header">
      <div class="page-header-inner">
        <div>
          <h1 class="page-title">应用管理</h1>
          <p class="page-sub">管理所有用户的应用，仅管理员可见</p>
        </div>
        <a-input-search
          v-model:value="searchName"
          placeholder="搜索应用名称"
          class="search-input"
          @search="handleSearch"
          allow-clear
        />
      </div>
    </div>

    <div class="table-container">
      <a-table
        :columns="columns"
        :data-source="apps"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
        class="apps-table"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'appName'">
            <div class="app-name-cell">
              <div class="app-cover-sm">
                <img v-if="record.cover" :src="record.cover" :alt="record.appName" />
                <span v-else class="cover-letter-sm">{{ record.appName?.charAt(0) || 'A' }}</span>
              </div>
              <div>
                <div class="app-name-text">{{ record.appName }}</div>
                <div class="app-id-text">ID: {{ record.id }}</div>
              </div>
            </div>
          </template>

          <template v-else-if="column.key === 'codeGenType'">
            <a-tag :color="typeColor(record.codeGenType)">{{ fmtType(record.codeGenType) }}</a-tag>
          </template>

          <template v-else-if="column.key === 'priority'">
            <a-tag v-if="record.priority === 99" color="gold">⭐ 精选</a-tag>
            <span v-else class="text-muted">{{ record.priority ?? 0 }}</span>
          </template>

          <template v-else-if="column.key === 'userVO'">
            <div class="user-cell" v-if="record.userVO">
              <a-avatar :size="20" :src="record.userVO.userAvatar"
                :style="{ background: 'linear-gradient(135deg,#4f46e5,#0ea5e9)', fontSize: '10px' }">
                {{ record.userVO.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <span>{{ record.userVO.userName || '未知' }}</span>
            </div>
            <span v-else class="text-muted">—</span>
          </template>

          <template v-else-if="column.key === 'deployKey'">
            <a v-if="record.deployKey" :href="record.deployKey" target="_blank" class="deploy-link">查看</a>
            <span v-else class="text-muted">未部署</span>
          </template>

          <template v-else-if="column.key === 'createTime'">
            <span class="time-text">{{ fmtTime(record.createTime) }}</span>
          </template>

          <template v-else-if="column.key === 'actions'">
            <div class="action-btns">
              <a-button size="small" type="link" class="btn-edit" @click="openEditModal(record)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button
                v-if="record.priority !== 99"
                size="small" type="link" class="btn-featured"
                @click="handleSetFeatured(record)"
                :loading="featuredId === record.id"
              >
                <StarOutlined /> 精选
              </a-button>
              <a-button size="small" type="link" danger @click="handleDelete(record)">
                <DeleteOutlined /> 删除
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 编辑弹窗 -->
    <a-modal v-model:open="showEditModal" title="编辑应用" :footer="null" width="480px" class="edit-modal">
      <a-form :model="editForm" @finish="handleEdit" layout="vertical">
        <a-form-item label="应用名称" name="appName" :rules="[{ required: true, message: '请输入应用名称' }]">
          <a-input v-model:value="editForm.appName" class="edit-input" />
        </a-form-item>
        <a-form-item label="封面 URL" name="cover">
          <a-input v-model:value="editForm.cover" placeholder="https://..." class="edit-input" />
        </a-form-item>
        <a-form-item label="优先级" name="priority">
          <a-input-number v-model:value="editForm.priority" :min="0" :max="999" style="width:100%" />
          <div class="hint-text">优先级 99 = 精选应用</div>
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="showEditModal = false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="editing">保存</a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined, StarOutlined } from '@ant-design/icons-vue'
import {
  adminListAppVOByPageApi,
  adminDeleteAppApi,
  adminUpdateAppApi,
  type AppVO,
  type AppAdminUpdateRequest,
} from '@/api/app'

const apps = ref<AppVO[]>([])
const loading = ref(false)
const searchName = ref('')
const pageNum = ref(1)
const pageSize = ref(15)
const total = ref(0)
const showEditModal = ref(false)
const editing = ref(false)
const featuredId = ref<string | null>(null)

const editForm = reactive<AppAdminUpdateRequest>({
  id: '',
  appName: '',
  cover: '',
  priority: 0,
})

const columns = [
  { title: '应用', key: 'appName', minWidth: 220 },
  { title: '类型', key: 'codeGenType', width: 90 },
  { title: '优先级', key: 'priority', width: 100 },
  { title: '创建者', key: 'userVO', width: 130 },
  { title: '部署', key: 'deployKey', width: 80 },
  { title: '创建时间', key: 'createTime', width: 150 },
  { title: '操作', key: 'actions', width: 180, fixed: 'right' },
]

const pagination = computed<TablePaginationConfig>(() => ({
  current: pageNum.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '15', '20', '50'],
  showTotal: (t: number) => `共 ${t} 条`,
}))

async function fetchApps() {
  loading.value = true
  try {
    const res = await adminListAppVOByPageApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      appName: searchName.value || undefined,
    })
    if (res.data.code === 0) {
      apps.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    }
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchApps()
}

function handleTableChange(pag: TablePaginationConfig) {
  pageNum.value = pag.current ?? 1
  pageSize.value = pag.pageSize ?? 15
  fetchApps()
}

function openEditModal(app: AppVO) {
  editForm.id = app.id
  editForm.appName = app.appName
  editForm.cover = app.cover || ''
  editForm.priority = app.priority ?? 0
  showEditModal.value = true
}

async function handleEdit() {
  editing.value = true
  try {
    const res = await adminUpdateAppApi({ ...editForm })
    if (res.data.code === 0) {
      message.success('更新成功')
      showEditModal.value = false
      fetchApps()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    editing.value = false
  }
}

async function handleSetFeatured(app: AppVO) {
  featuredId.value = app.id
  try {
    const res = await adminUpdateAppApi({ id: app.id, priority: 99 })
    if (res.data.code === 0) {
      message.success(`「${app.appName}」已设为精选`)
      fetchApps()
    } else {
      message.error(res.data.message || '设置失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    featuredId.value = null
  }
}

function handleDelete(app: AppVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除「${app.appName}」？此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const res = await adminDeleteAppApi(app.id)
      if (res.data.code === 0) {
        message.success('已删除')
        fetchApps()
      } else {
        message.error(res.data.message || '删除失败')
      }
    },
  })
}

function fmtType(t: string) {
  return ({ html: 'HTML', vue: 'Vue', react: 'React', multi_file: '多文件' } as Record<string, string>)[t] || t
}

function typeColor(t: string) {
  return ({ html: 'orange', vue: 'green', react: 'blue', multi_file: 'purple' } as Record<string, string>)[t] || 'default'
}

function fmtTime(t: string) {
  if (!t) return '—'
  return new Date(t).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => { fetchApps() })
</script>

<style scoped>
.admin-apps-page { min-height: 100vh; padding-top: 58px; background: #f8fafc; }
.page-header { background: white; border-bottom: 1px solid rgba(99,102,241,0.08); padding: 24px 0 18px; }
.page-header-inner { max-width: 1400px; margin: 0 auto; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.page-title { font-size: 24px; font-weight: 800; color: #1e1b4b; margin-bottom: 3px; letter-spacing: -0.5px; }
.page-sub { font-size: 13px; color: #9ca3af; }
.search-input { width: 240px; }
.table-container { max-width: 1400px; margin: 24px auto; padding: 0 28px; }
.apps-table { background: white; border-radius: 16px; border: 1px solid rgba(99,102,241,0.08); overflow: hidden; box-shadow: 0 2px 12px rgba(99,102,241,0.06); }
:deep(.ant-table-thead > tr > th) { background: #fafbff !important; color: #6b7280 !important; font-size: 12px !important; font-weight: 700 !important; border-bottom: 1px solid rgba(99,102,241,0.08) !important; }
:deep(.ant-table-tbody > tr > td) { border-bottom: 1px solid #f9fafb !important; vertical-align: middle; }
:deep(.ant-table-tbody > tr:hover > td) { background: #fafbff !important; }
.app-name-cell { display: flex; align-items: center; gap: 10px; }
.app-cover-sm { width: 36px; height: 36px; border-radius: 8px; overflow: hidden; flex-shrink: 0; background: linear-gradient(135deg,rgba(99,102,241,0.1),rgba(14,165,233,0.07)); display: flex; align-items: center; justify-content: center; }
.app-cover-sm img { width: 100%; height: 100%; object-fit: cover; }
.cover-letter-sm { font-size: 15px; font-weight: 800; color: rgba(79,70,229,0.3); }
.app-name-text { font-size: 13px; font-weight: 700; color: #1e1b4b; }
.app-id-text { font-size: 11px; color: #d1d5db; font-family: 'JetBrains Mono', monospace; }
.user-cell { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
.text-muted { color: #d1d5db; font-size: 13px; }
.time-text { font-size: 12px; color: #9ca3af; }
.deploy-link { color: #059669; font-size: 12px; font-weight: 600; }
.action-btns { display: flex; align-items: center; }
.btn-edit { color: #4f46e5 !important; }
.btn-featured { color: #d97706 !important; }
.hint-text { font-size: 11px; color: #9ca3af; margin-top: 4px; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
:deep(.edit-modal .ant-modal-content) { background: white; border: 1px solid rgba(99,102,241,0.1); border-radius: 16px; box-shadow: 0 16px 48px rgba(99,102,241,0.12); }
:deep(.edit-modal .ant-modal-header) { background: transparent; border-bottom: 1px solid #f3f4f6; }
:deep(.edit-modal .ant-modal-title) { color: #1e1b4b; font-weight: 700; }
:deep(.edit-input) { border: 1px solid #e5e7eb !important; border-radius: 8px !important; background: #fafafa !important; }
:deep(.edit-input:hover) { border-color: #a5b4fc !important; }
:deep(.edit-input:focus-within) { border-color: #4f46e5 !important; box-shadow: 0 0 0 3px rgba(99,102,241,0.1) !important; }
</style>
