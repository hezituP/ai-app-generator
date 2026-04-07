<template>
  <div class="editor-page">
    <header class="page-header">
      <div>
        <router-link to="/my-apps" class="back-link">返回应用列表</router-link>
        <h1>{{ app?.appName || '加载中...' }}</h1>
        <p>{{ typeLabel }}</p>
      </div>
      <div class="header-actions">
        <a-button @click="handleDownload"><DownloadOutlined /> 下载代码</a-button>
        <a-button type="primary" :loading="deploying" @click="handleDeploy">
          <RocketOutlined /> {{ app?.deployKey ? '查看部署' : '部署' }}
        </a-button>
      </div>
    </header>

    <main class="editor-layout">
      <section class="agent-panel">
        <div class="panel-head">
          <div class="panel-title">Agent 协作区</div>
          <p>在这里继续描述修改需求，生成过程会按时间顺序返回。</p>
        </div>
        <div class="event-list" ref="messagesRef">
          <div v-if="events.length === 0" class="empty-block">
            <h3>开始一次工程化生成</h3>
            <p>描述页面结构、风格、路由和交互需求，Agent 会以流式方式生成完整工程。</p>
          </div>
          <div v-for="item in events" :key="item.id" class="event-item" :class="item.type">
            <strong>{{ item.title }}</strong>
            <p>{{ item.message }}</p>
          </div>
        </div>
        <a-textarea
          v-model:value="inputMsg"
          :rows="4"
          :disabled="streaming"
          placeholder="例如：生成一个 SaaS 官网，包含首页、价格页、案例页、联系我们表单，并使用清爽的蓝绿色视觉风格"
          @keydown.ctrl.enter.prevent="handleSend"
        />
        <div class="panel-actions">
          <span>Ctrl + Enter 发送</span>
          <a-button type="primary" :loading="streaming" @click="handleSend">发送给 Agent</a-button>
        </div>
      </section>

      <section class="workspace-panel">
        <div class="workspace-header">
          <div class="tabs">
            <button :class="{ active: activeTab === 'project' }" @click="activeTab = 'project'">工程文件</button>
            <button :class="{ active: activeTab === 'preview' }" @click="activeTab = 'preview'">预览</button>
          </div>
          <a-button size="small" @click="refreshSnapshot">刷新快照</a-button>
        </div>

        <div v-if="activeTab === 'project'" class="project-panel">
          <aside class="file-tree">
            <div class="summary-card">
              <strong>工程摘要</strong>
              <p>{{ projectSnapshot?.summary || '生成完成后会展示工程摘要' }}</p>
            </div>
            <button
              v-for="file in projectFiles"
              :key="file.path"
              class="file-item"
              :class="{ active: selectedFilePath === file.path }"
              @click="selectedFilePath = file.path"
            >
              {{ file.path }}
            </button>
          </aside>

          <div class="code-viewer">
            <div class="code-header">{{ selectedFilePath || '暂无文件' }}</div>
            <pre><code>{{ selectedFileContent }}</code></pre>
          </div>
        </div>

        <div v-else class="preview-panel">
          <div v-if="projectSnapshot?.previewUrl" class="iframe-wrap">
            <iframe :src="resolvedPreviewUrl" title="preview"></iframe>
          </div>
          <div v-else class="empty-block preview-empty">
            <h3>当前模式不提供直接静态预览</h3>
            <p>Vue3 + Vite 工程已经生成完成，你可以在右侧文件区查看全部源码，或直接下载整个工程。</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { DownloadOutlined, RocketOutlined } from '@ant-design/icons-vue'
import {
  deployAppApi,
  downloadAppCodeApi,
  getAppVOByIdApi,
  getProjectSnapshotApi,
  type AgentStreamEvent,
  type AppProjectFileVO,
  type AppProjectSnapshotVO,
  type AppVO,
} from '@/api/app'
import myAxios from '@/plugins/myAxios'

const route = useRoute()
const appId = route.params.id as string

const app = ref<AppVO | null>(null)
const projectSnapshot = ref<AppProjectSnapshotVO | null>(null)
const activeTab = ref<'project' | 'preview'>('project')
const inputMsg = ref('')
const streaming = ref(false)
const deploying = ref(false)
const selectedFilePath = ref('')
const messagesRef = ref<HTMLElement | null>(null)
const events = ref<{ id: number; type: string; title: string; message: string }[]>([])
let eventId = 0
let eventSource: EventSource | null = null

const typeLabel = computed(() => {
  const map: Record<string, string> = {
    html: '原生 HTML 模式',
    multi_file: '多文件站点模式',
    vue_project: 'Vue3 + Vite 工程模式',
  }
  return map[app.value?.codeGenType || ''] || app.value?.codeGenType || ''
})

const projectFiles = computed(() => projectSnapshot.value?.files || [])
const selectedFileContent = computed(() => {
  const file = projectFiles.value.find((item) => item.path === selectedFilePath.value)
  return file?.content || ''
})
const resolvedPreviewUrl = computed(() => {
  if (!projectSnapshot.value?.previewUrl) return ''
  const baseURL = (myAxios.defaults.baseURL || '') as string
  return `${baseURL.replace(/\/api$/, '')}${projectSnapshot.value.previewUrl}`
})

async function loadApp() {
  const res = await getAppVOByIdApi(appId)
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data as AppVO
    app.value = data
    activeTab.value = data.codeGenType === 'vue_project' ? 'project' : 'preview'
  }
}

async function refreshSnapshot() {
  try {
    const res = await getProjectSnapshotApi(appId)
    if (res.data.code === 0) {
      applySnapshot(res.data.data)
    }
  } catch {
    projectSnapshot.value = null
  }
}

function applySnapshot(snapshot: AppProjectSnapshotVO | null) {
  projectSnapshot.value = snapshot
  const files = snapshot?.files || []
  if (!files.length) {
    selectedFilePath.value = ''
    return
  }
  const matchedFile = files.find((item) => item.path === selectedFilePath.value)
  if (!selectedFilePath.value || !matchedFile) {
    selectedFilePath.value = snapshot?.entryFilePath || files[0]?.path || ''
  }
}

function pushEvent(type: string, messageText: string) {
  const titleMap: Record<string, string> = {
    status: '状态更新',
    result: '结果就绪',
    error: '发生错误',
    done: '流程结束',
  }
  events.value.push({
    id: ++eventId,
    type,
    title: titleMap[type] || '消息',
    message: messageText,
  })
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function handleSend() {
  const content = inputMsg.value.trim()
  if (!content || streaming.value) return
  inputMsg.value = ''
  pushEvent('status', `需求已提交：${content}`)
  streaming.value = true

  const baseURL = (myAxios.defaults.baseURL || '') as string
  const url = `${baseURL}/app/chat/gen/code?appId=${appId}&message=${encodeURIComponent(content)}`
  eventSource = new EventSource(url, { withCredentials: true })

  eventSource.onmessage = (event) => {
    const payload = JSON.parse(event.data) as AgentStreamEvent
    if (payload.type === 'result') {
      applySnapshot(payload.data || null)
      pushEvent(payload.type, payload.message || '工程生成完成')
      activeTab.value = 'project'
      return
    }
    if (payload.type === 'done') {
      pushEvent(payload.type, '本轮 Agent 任务已结束')
      closeStream()
      return
    }
    pushEvent(payload.type, payload.message || '收到新的 Agent 状态')
  }

  eventSource.onerror = () => {
    pushEvent('error', '流式连接已中断')
    closeStream()
  }
}

function closeStream() {
  streaming.value = false
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

async function handleDeploy() {
  if (app.value?.deployKey) {
    window.open(`/preview/${app.value.deployKey}`, '_blank')
    return
  }
  deploying.value = true
  try {
    const res = await deployAppApi(appId)
    if (res.data.code === 0) {
      message.success('部署成功')
      if (res.data.data) {
        window.open(res.data.data, '_blank')
      }
      await loadApp()
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    deploying.value = false
  }
}

async function handleDownload() {
  try {
    const res = await downloadAppCodeApi(appId)
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${app.value?.appName || 'app'}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    message.error('下载失败')
  }
}

watch(projectFiles, (files: AppProjectFileVO[]) => {
  if (!files.length) {
    selectedFilePath.value = ''
  }
})

onMounted(async () => {
  await loadApp()
  await refreshSnapshot()
})

onUnmounted(closeStream)
</script>

<style scoped>
.editor-page {
  min-height: 100vh;
  padding: 82px 24px 28px;
  background: linear-gradient(135deg, #f5e7ff 0%, #ffe6ef 34%, #fff1dc 68%, #edf6ff 100%);
}

.editor-page::before,
.editor-page::after {
  content: '';
  position: fixed;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(18px);
  z-index: 0;
}

.editor-page::before {
  top: 86px;
  left: -90px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(162, 155, 254, 0.26), transparent 70%);
}

.editor-page::after {
  right: -70px;
  bottom: 140px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(72, 219, 251, 0.22), transparent 72%);
}

.page-header,
.editor-layout {
  position: relative;
  z-index: 1;
}

.page-header {
  max-width: 1400px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.back-link {
  color: #7b5adb;
}

.page-header h1 {
  margin: 8px 0 4px;
  background: linear-gradient(135deg, #fd79a8 0%, #a29bfe 55%, #48dbfb 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.page-header p {
  margin: 0;
  color: #786f91;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-actions :deep(.ant-btn) {
  border-radius: 999px;
}

.editor-layout {
  max-width: 1400px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
}

.agent-panel,
.workspace-panel {
  background: rgba(255, 255, 255, 0.5);
  border-radius: 30px;
  border: 1px solid rgba(255, 255, 255, 0.66);
  box-shadow: 0 18px 36px rgba(130, 112, 162, 0.08);
  backdrop-filter: blur(12px);
}

.agent-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: rgba(255, 255, 255, 0.44);
}

.panel-head p {
  margin: 6px 0 0;
  color: #857b9f;
  font-size: 13px;
  line-height: 1.6;
}

.panel-title {
  font-size: 18px;
  font-weight: 700;
  color: #3b3457;
}

.event-list {
  min-height: 420px;
  max-height: 60vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 2px 0;
}

.event-item,
.empty-block,
.summary-card,
.file-item {
  background: rgba(255, 255, 255, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.52);
  box-shadow: none;
}

.event-item {
  position: relative;
  padding: 14px 14px 14px 18px;
  border-radius: 18px;
  animation: riseIn 0.35s ease both;
}

.event-item.status {
  border-left: 3px solid #6c5ce7;
}

.event-item.result {
  border-left: 3px solid #00b894;
}

.event-item.error {
  border-left: 3px solid #ff7675;
}

.event-item.done {
  border-left: 3px solid #8e8ca8;
}

.event-item strong {
  display: block;
  margin-bottom: 5px;
  color: #3b3457;
  font-size: 14px;
}

.event-item p {
  margin: 0;
  color: #746b90;
  line-height: 1.65;
}

.panel-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #7b7394;
  padding: 0 2px;
}

.agent-panel :deep(.ant-input) {
  border: none !important;
  border-radius: 20px !important;
  background: rgba(255, 255, 255, 0.26) !important;
  color: #4f4768 !important;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.48) !important;
  backdrop-filter: blur(8px);
}

.agent-panel :deep(.ant-input:focus),
.agent-panel :deep(.ant-input-focused) {
  box-shadow: inset 0 0 0 1px rgba(135, 176, 255, 0.38) !important;
}

.workspace-panel {
  overflow: hidden;
}

.workspace-header {
  padding: 16px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.34);
}

.tabs {
  display: flex;
  gap: 8px;
}

.tabs button {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  color: #665d84;
  background: rgba(245, 241, 251, 0.94);
  box-shadow: 8px 8px 16px rgba(208, 203, 223, 0.55), -8px -8px 16px rgba(255, 255, 255, 0.96);
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.tabs button.active {
  background: linear-gradient(135deg, #fd79a8, #a29bfe);
  color: white;
  box-shadow: 0 16px 32px rgba(162, 155, 254, 0.22);
}

.project-panel {
  display: grid;
  grid-template-columns: 300px 1fr;
  min-height: 70vh;
}

.file-tree {
  padding: 18px;
  border-right: 1px solid rgba(255, 255, 255, 0.32);
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}

.summary-card {
  padding: 16px;
  border-radius: 20px;
}

.summary-card strong {
  color: #3d355a;
}

.summary-card p {
  margin: 8px 0 0;
  color: #766d92;
}

.file-item {
  text-align: left;
  border-radius: 16px;
  padding: 10px 12px;
  color: #4a4265;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.file-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 24px rgba(163, 153, 196, 0.18);
}

.file-item.active {
  background: linear-gradient(135deg, rgba(253, 121, 168, 0.16), rgba(162, 155, 254, 0.18));
  color: #5b49b5;
}

.code-viewer {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.code-header {
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.34);
  font-weight: 600;
  color: #4d4468;
}

.code-viewer pre {
  margin: 0;
  padding: 18px;
  overflow: auto;
  height: 100%;
  background: linear-gradient(160deg, rgba(43, 35, 74, 0.94), rgba(20, 30, 57, 0.94));
  color: #edf1ff;
}

.preview-panel,
.iframe-wrap,
iframe {
  width: 100%;
  min-height: 70vh;
}

iframe {
  border: none;
}

.empty-block {
  padding: 24px;
  border-radius: 22px;
}

.preview-empty {
  margin: 24px;
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .project-panel {
    grid-template-columns: 1fr;
  }

  .file-tree {
    border-right: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.32);
  }
}

</style>
