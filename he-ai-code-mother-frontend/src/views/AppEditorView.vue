<template>
  <div class="editor-page" :style="pageStyle">
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
      <section class="agent-panel glass-panel">
        <div class="panel-head">
          <div class="panel-title">Agent 协作区</div>
        </div>

        <a-alert
          v-if="selectedElement"
          class="selection-alert"
          type="info"
          show-icon
          closable
          @close="handleClearSelectedElement"
        >
          <template #message>
            已选中元素：{{ selectedElement.tagName }} · {{ selectedElement.selector || '未识别选择器' }}
          </template>
          <template #description>
            {{ selectedElement.text || '该元素暂无可提取文本' }}
          </template>
        </a-alert>

        <div class="stream-panels unified-stream" ref="messagesRef">
          <div v-if="!events.length" class="compact-empty">
            <p>这里会显示你和 AI 的对话，以及本轮生成过程中的关键回复。</p>
          </div>
          <div v-else class="message-list">
            <article
              v-for="item in visibleEvents"
              :key="item.id"
              class="message-card"
              :class="[item.role, { pending: item.pending }]"
            >
              <div class="message-meta">
                <span class="message-role">{{ item.roleLabel }}</span>
                <span class="message-time">{{ item.time }}</span>
              </div>
              <div v-if="item.pending" class="message-loading">
                <span class="loading-dot"></span>
                <span class="loading-dot"></span>
                <span class="loading-dot"></span>
              </div>
              <p class="message-text">{{ item.message }}</p>
            </article>
          </div>
        </div>

        <a-textarea
          v-model:value="inputMsg"
          :rows="4"
          :disabled="streaming"
          class="chat-input"
          placeholder="例如：保留首页布局，把首屏标题改得更轻柔，并让按钮层级更突出"
          @keydown.enter.exact.prevent="handleSend"
        />

        <div class="panel-actions">
          <span class="shortcut-tip">Enter 发送，Shift + Enter 换行</span>
          <div class="action-buttons">
            <a-button
              class="visual-btn"
              :type="visualEditMode ? 'primary' : 'default'"
              :disabled="!projectSnapshot?.previewUrl || streaming"
              @click="toggleVisualEditMode"
            >
              {{ visualEditMode ? '退出编辑模式' : '进入编辑模式' }}
            </a-button>
            <a-button type="primary" :loading="streaming" @click="handleSend">发送给 Agent</a-button>
          </div>
        </div>
      </section>

      <section class="workspace-panel glass-panel">
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
              v-for="file in visibleProjectFiles"
              :key="file.path"
              class="file-item"
              :class="{ active: selectedFilePath === file.path }"
              @click="selectedFilePath = file.path"
            >
              {{ file.path }}
            </button>
            <button
              v-if="hiddenFrontendFiles.length"
              class="file-item more-file-item"
              @click="openFullCodeModal"
            >
              ... 还有 {{ hiddenFrontendFiles.length }} 个文件
            </button>
          </aside>

          <div class="code-viewer">
            <div class="code-header">{{ selectedFilePath || '暂无文件' }}</div>
            <pre><code>{{ selectedFileContent }}</code></pre>
          </div>
        </div>

        <div v-else class="preview-panel">
          <div v-if="projectSnapshot?.previewUrl" class="iframe-wrap">
            <iframe ref="previewFrameRef" :src="resolvedPreviewUrl" title="preview" />
          </div>
          <div v-else class="empty-block preview-empty">
            <h3>当前还没有可用预览</h3>
            <p>先让 Agent 生成一轮工程，或刷新快照等待静态预览构建完成。</p>
          </div>
        </div>
      </section>
    </main>

    <a-modal v-model:open="fullCodeModalOpen" :footer="null" width="1120px" class="glass-code-modal">
      <div class="full-code-modal">
        <aside class="modal-file-tree">
          <div class="modal-title-row">
            <strong>完整前端代码</strong>
            <span>{{ frontendProjectFiles.length }} 个文件</span>
          </div>
          <button
            v-for="file in frontendProjectFiles"
            :key="file.path"
            class="file-item"
            :class="{ active: modalSelectedFilePath === file.path }"
            @click="modalSelectedFilePath = file.path"
          >
            {{ file.path }}
          </button>
        </aside>

        <div class="modal-code-viewer">
          <div class="code-header">{{ modalSelectedFilePath || '暂无文件' }}</div>
          <pre><code>{{ modalSelectedFileContent }}</code></pre>
        </div>
      </div>
    </a-modal>
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
import { listChatHistoryApi, type ChatHistoryItem } from '@/api/chatHistory'
import { useVisualEditor } from '@/composables/useVisualEditor'
import myAxios from '@/plugins/myAxios'

interface TimelineEvent {
  id: number
  type: 'assistant' | 'assistant_delta' | 'status' | 'result' | 'error' | 'done' | 'user'
  role: 'agent' | 'user' | 'system'
  roleLabel: string
  title: string
  message: string
  time: string
  pending?: boolean
}

type PhaseStatus = 'pending' | 'active' | 'done' | 'error'
type PhaseKey = 'analysis' | 'generation' | 'build' | 'result'

const route = useRoute()
const appId = route.params.id as string

const app = ref<AppVO | null>(null)
const projectSnapshot = ref<AppProjectSnapshotVO | null>(null)
const activeTab = ref<'project' | 'preview'>('project')
const inputMsg = ref('')
const streaming = ref(false)
const deploying = ref(false)
const selectedFilePath = ref('')
const fullCodeModalOpen = ref(false)
const modalSelectedFilePath = ref('')
const messagesRef = ref<HTMLElement | null>(null)
const previewFrameRef = ref<HTMLIFrameElement | null>(null)
const events = ref<TimelineEvent[]>([])
const visualEditMode = ref(false)
const selectedWallpaper = ref<'bg1' | 'bg2' | 'bg3'>('bg1')
const activePhaseKey = ref<PhaseKey>('analysis')
const phaseState = ref<Record<PhaseKey, PhaseStatus>>({
  analysis: 'pending',
  generation: 'pending',
  build: 'pending',
  result: 'pending',
})
let eventId = 0
let eventSource: EventSource | null = null
let pendingAssistantEventId: number | null = null

const wallpaperMap: Record<'bg1' | 'bg2' | 'bg3', string> = {
  bg1: '/images/home-bg1.jpg',
  bg2: '/images/home-bg2.jpg',
  bg3: '/images/home-bg3.jpg',
}

const pageStyle = computed(() => ({
  '--editor-bg-image': `url('${wallpaperMap[selectedWallpaper.value]}')`,
}))

const typeLabel = computed(() => {
  const map: Record<string, string> = {
    html: '原生 HTML 模式',
    multi_file: '多文件站点模式',
    vue_project: 'Vue3 + Vite 工程模式',
  }
  return map[app.value?.codeGenType || ''] || app.value?.codeGenType || ''
})

const projectFiles = computed(() => projectSnapshot.value?.files || [])
const frontendProjectFiles = computed(() =>
  projectFiles.value.filter((file) => isFrontendProjectFile(file.path)),
)
const visibleProjectFiles = computed(() => frontendProjectFiles.value.slice(0, 10))
const hiddenFrontendFiles = computed(() => frontendProjectFiles.value.slice(10))
const selectedFileContent = computed(() => {
  const file = projectFiles.value.find((item) => item.path === selectedFilePath.value)
  return file?.content || ''
})
const modalSelectedFileContent = computed(() => {
  const file = frontendProjectFiles.value.find((item) => item.path === modalSelectedFilePath.value)
  return file?.content || ''
})
const resolvedPreviewUrl = computed(() => {
  if (!projectSnapshot.value?.previewUrl) return ''
  const baseURL = (myAxios.defaults.baseURL || '') as string
  return `${baseURL.replace(/\/api$/, '')}${projectSnapshot.value.previewUrl}`
})
const visibleEvents = computed(() =>
  events.value.filter(
    (item) =>
      item.type !== 'status'
      && item.type !== 'done'
      && item.type !== 'assistant_delta'
      && item.type !== 'result',
  ),
)
const {
  selectedElement,
  clearSelectedElement,
  syncVisualEditor,
} = useVisualEditor({
  iframeRef: previewFrameRef,
  enabledRef: visualEditMode,
})

function formatTime(date = new Date()) {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function isFrontendProjectFile(path: string) {
  const normalizedPath = path.replace(/\\/g, '/')
  if (
    normalizedPath.startsWith('src/main/java/')
    || normalizedPath.startsWith('src/test/')
    || normalizedPath.startsWith('backend/')
    || normalizedPath.startsWith('server/')
    || normalizedPath.startsWith('api/')
  ) {
    return false
  }
  return [
    'src/',
    'public/',
    'components/',
    'views/',
    'assets/',
    'pages/',
    'composables/',
    'stores/',
  ].some((prefix) => normalizedPath.startsWith(prefix))
    || [
      'index.html',
      'package.json',
      'package-lock.json',
      'pnpm-lock.yaml',
      'yarn.lock',
      'README.md',
    ].includes(normalizedPath)
    || normalizedPath.startsWith('vite.config')
    || normalizedPath.startsWith('tsconfig')
    || normalizedPath.startsWith('.env')
}

function openFullCodeModal() {
  fullCodeModalOpen.value = true
  if (!modalSelectedFilePath.value || !frontendProjectFiles.value.some((file) => file.path === modalSelectedFilePath.value)) {
    modalSelectedFilePath.value = hiddenFrontendFiles.value[0]?.path || frontendProjectFiles.value[0]?.path || ''
  }
}

function resetPhaseState() {
  phaseState.value = {
    analysis: 'pending',
    generation: 'pending',
    build: 'pending',
    result: 'pending',
  }
  activePhaseKey.value = 'analysis'
}

function updatePhase(messageText: string, type: TimelineEvent['type']) {
  if (type === 'error') {
    phaseState.value[activePhaseKey.value] = 'error'
    return
  }
  if (type === 'result') {
    phaseState.value.analysis = 'done'
    phaseState.value.generation = 'done'
    phaseState.value.build = projectSnapshot.value?.previewUrl ? 'done' : phaseState.value.build
    phaseState.value.result = 'done'
    activePhaseKey.value = 'result'
    return
  }
  if (type === 'done') {
    if (phaseState.value.result === 'active') {
      phaseState.value.result = 'done'
    }
    return
  }

  const text = messageText.toLowerCase()
  if (text.includes('分析') || text.includes('需求')) {
    phaseState.value.analysis = 'active'
    activePhaseKey.value = 'analysis'
    return
  }
  if (text.includes('生成') || text.includes('编排') || text.includes('整理工程结构')) {
    phaseState.value.analysis = 'done'
    phaseState.value.generation = 'active'
    activePhaseKey.value = 'generation'
    return
  }
  if (text.includes('构建') || text.includes('预览') || text.includes('dist')) {
    phaseState.value.analysis = 'done'
    phaseState.value.generation = 'done'
    phaseState.value.build = 'active'
    activePhaseKey.value = 'build'
    return
  }
  if (text.includes('写入') || text.includes('结果') || text.includes('完成')) {
    phaseState.value.analysis = 'done'
    phaseState.value.generation = 'done'
    if (phaseState.value.build === 'active') {
      phaseState.value.build = 'done'
    }
    phaseState.value.result = 'active'
    activePhaseKey.value = 'result'
  }
}

async function loadApp() {
  const res = await getAppVOByIdApi(appId)
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data as AppVO
    app.value = data
    if (!inputMsg.value.trim()) {
      inputMsg.value = route.query.prompt?.toString()
        || data.initPrompt
        || window.localStorage.getItem('pendingAppPrompt')
        || ''
    }
  }
}

async function loadHistory() {
  try {
    const res = await listChatHistoryApi(appId, 50)
    if (res.data.code !== 0 || !Array.isArray(res.data.data)) {
      return
    }
    const historyList = [...(res.data.data as ChatHistoryItem[])].reverse()
    events.value = historyList.map((item, index) => ({
      id: index + 1,
      type: item.messageType === 'user' ? 'user' : item.messageType === 'error' ? 'error' : 'assistant',
      role: item.messageType === 'user' ? 'user' : item.messageType === 'error' ? 'system' : 'agent',
      roleLabel: item.messageType === 'user' ? '用户' : item.messageType === 'error' ? '系统' : 'AI',
      title: item.messageType === 'user' ? '我的需求' : item.messageType === 'error' ? '错误信息' : 'AI 回复',
      message: item.message,
      time: formatTime(new Date(item.createTime)),
      pending: false,
    }))
    eventId = events.value.length
  } catch {
    events.value = []
    eventId = 0
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
  activeTab.value = snapshot?.previewUrl ? 'preview' : 'project'
  if (snapshot?.previewUrl) {
    nextTick(() => syncVisualEditor())
  }
}

function pushEvent(type: TimelineEvent['type'], messageText: string, role: TimelineEvent['role'] = 'agent') {
  const titleMap: Record<TimelineEvent['type'], string> = {
    user: '我的需求',
    assistant: 'Agent 回复',
    assistant_delta: 'Agent 正在输入',
    result: '结果已返回',
    error: '处理失败',
    done: '本轮结束',
    status: '处理中',
  }
  const roleLabelMap: Record<TimelineEvent['role'], string> = {
    user: '用户',
    agent: 'Agent',
    system: '系统',
  }
  events.value.push({
    id: ++eventId,
    type,
    role,
    roleLabel: roleLabelMap[role],
    title: titleMap[type],
    message: messageText,
    time: formatTime(),
    pending: false,
  })
  updatePhase(messageText, type)
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function createPendingAssistant() {
  const id = ++eventId
  pendingAssistantEventId = id
  events.value.push({
    id,
    type: 'assistant',
    role: 'agent',
    roleLabel: 'AI',
    title: 'AI 回复',
    message: '正在生成中，请稍候...',
    time: formatTime(),
    pending: true,
  })
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function resolvePendingAssistant(finalMessage: string) {
  if (pendingAssistantEventId == null) {
    pushEvent('assistant', finalMessage)
    return
  }
  const existing = events.value.find((item) => item.id === pendingAssistantEventId)
  if (existing) {
    existing.message = finalMessage
    existing.pending = false
    existing.time = formatTime()
  }
  pendingAssistantEventId = null
}

function buildPromptWithSelection(content: string) {
  if (!selectedElement.value) {
    return content
  }
  const selection = selectedElement.value
  return `${content}\n\n[当前选中元素]\n标签：${selection.tagName}\n选择器：${selection.selector || '无'}\n文本：${selection.text || '无'}\nid：${selection.id || '无'}\nclass：${selection.className || '无'}\n请优先围绕这个元素及其相关区域进行修改。`
}

async function handleSend() {
  const content = inputMsg.value.trim()
  if (!content || streaming.value) return

  resetPhaseState()
  const requestMessage = buildPromptWithSelection(content)
  inputMsg.value = ''
  pendingAssistantEventId = null
  pushEvent('user', content, 'user')
  createPendingAssistant()
  streaming.value = true

  const baseURL = (myAxios.defaults.baseURL || '') as string
  const url = `${baseURL}/app/chat/gen/code?appId=${appId}&message=${encodeURIComponent(requestMessage)}`
  eventSource = new EventSource(url, { withCredentials: true })

  eventSource.onmessage = (event) => {
    const payload = JSON.parse(event.data) as AgentStreamEvent
    if (payload.type === 'result') {
      applySnapshot(payload.data || null)
      resolvePendingAssistant(payload.message || '这轮结果已经准备好了，你可以查看右侧文件和预览。')
      return
    }
    if (payload.type === 'error') {
      resolvePendingAssistant('生成过程中出现了问题，请稍后重试。')
      pushEvent('error', payload.message || '生成失败', 'system')
      return
    }
    if (payload.type === 'done') {
      closeStream()
      return
    }
  }

  eventSource.onerror = () => {
    resolvePendingAssistant('生成过程中断开了连接，请重新尝试。')
    pushEvent('error', '流式连接中断，请稍后重试。', 'system')
    closeStream()
  }

  handleClearSelectedElement()
  visualEditMode.value = false
}

function closeStream() {
  streaming.value = false
  pendingAssistantEventId = null
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (phaseState.value.result === 'active') {
    phaseState.value.result = 'done'
  }
}

function toggleVisualEditMode() {
  if (!projectSnapshot.value?.previewUrl) {
    message.warning('当前没有可编辑预览，请先生成并构建静态预览')
    return
  }
  visualEditMode.value = !visualEditMode.value
  if (visualEditMode.value) {
    activeTab.value = 'preview'
    nextTick(() => syncVisualEditor())
  } else {
    handleClearSelectedElement()
  }
}

function handleClearSelectedElement() {
  clearSelectedElement()
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
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${app.value?.appName || 'app'}.zip`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch {
    message.error('下载失败')
  }
}

watch(projectFiles, (files: AppProjectFileVO[]) => {
  if (!files.length) {
    selectedFilePath.value = ''
    modalSelectedFilePath.value = ''
    return
  }
  if (!selectedFilePath.value || !files.some((item) => item.path === selectedFilePath.value)) {
    selectedFilePath.value = visibleProjectFiles.value[0]?.path || files[0]?.path || ''
  }
  if (!modalSelectedFilePath.value || !files.some((item) => item.path === modalSelectedFilePath.value)) {
    modalSelectedFilePath.value = frontendProjectFiles.value[0]?.path || files[0]?.path || ''
  }
})

watch(activeTab, (tab) => {
  if (tab === 'preview') {
    nextTick(() => syncVisualEditor())
  }
})

watch(resolvedPreviewUrl, () => {
  if (!projectSnapshot.value?.previewUrl) {
    visualEditMode.value = false
    handleClearSelectedElement()
  }
})

onMounted(async () => {
  const savedWallpaper = window.localStorage.getItem('homeWallpaper')
  if (savedWallpaper === 'bg1' || savedWallpaper === 'bg2' || savedWallpaper === 'bg3') {
    selectedWallpaper.value = savedWallpaper
  }
  await loadApp()
  await loadHistory()
  await refreshSnapshot()
  window.localStorage.removeItem('pendingAppPrompt')
})

onUnmounted(() => {
  closeStream()
  handleClearSelectedElement()
})
</script>

<style scoped>
.editor-page {
  min-height: 100vh;
  padding: 82px 24px 28px;
  background:
    linear-gradient(rgba(255, 255, 255, 0.12), rgba(240, 246, 255, 0.2)),
    var(--editor-bg-image, none),
    linear-gradient(135deg, #eef5ff 0%, #f8f3ff 42%, #fff6fb 100%);
  background-size: cover, cover, auto;
  background-position: center top, center top, center;
}

.page-header,
.editor-layout {
  max-width: 1320px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  margin-bottom: 22px;
}

.back-link {
  color: rgba(42, 64, 101, 0.88);
  text-decoration: none;
}

.page-header h1 {
  margin: 8px 0 4px;
  color: #f5fbff;
  font-size: 34px;
  text-shadow: 0 4px 20px rgba(61, 97, 157, 0.14);
}

.page-header p {
  margin: 0;
  color: rgba(52, 67, 97, 0.88);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(380px, 460px) minmax(0, 1fr);
  gap: 20px;
}

.glass-panel {
  border-radius: 30px;
  border: 1px solid rgba(255, 255, 255, 0.44);
  background: rgba(255, 255, 255, 0.18);
  box-shadow: 0 28px 60px rgba(32, 54, 92, 0.14);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.agent-panel,
.workspace-panel {
  min-height: 760px;
}

.agent-panel {
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 20px;
  font-weight: 700;
  color: #f5fbff;
}

.selection-alert {
  margin-bottom: 14px;
  border-radius: 18px;
}

.empty-block {
  min-height: 220px;
  display: grid;
  place-items: center;
  text-align: center;
  color: rgba(52, 68, 98, 0.82);
}

.stream-panels {
  flex: 1;
  min-height: 320px;
  max-height: 470px;
  min-width: 0;
}

.unified-stream {
  overflow: auto;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.28);
  padding: 12px 14px 14px;
}

.compact-empty {
  min-height: 120px;
  padding: 8px 4px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.16);
  box-shadow: 0 8px 24px rgba(43, 63, 98, 0.08);
}

.message-card.user {
  margin-left: 44px;
  background: rgba(110, 168, 255, 0.18);
}

.message-card.agent {
  margin-right: 44px;
  background: rgba(255, 255, 255, 0.22);
}

.message-card.pending {
  border-style: dashed;
}

.message-card.system {
  background: rgba(255, 120, 120, 0.16);
}

.message-card.done {
  background: rgba(255, 255, 255, 0.12);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: rgba(53, 76, 112, 0.8);
  font-size: 12px;
}

.message-role {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.28);
  font-weight: 600;
}

.message-title {
  font-weight: 600;
}

.message-time {
  margin-left: auto;
}

.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: rgba(49, 64, 93, 0.92);
  line-height: 1.8;
}

.message-loading {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #5d7fae;
  animation: loading-bounce 1s infinite ease-in-out;
}

.loading-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.loading-dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes loading-bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.45;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.chat-input {
  margin-top: 14px;
}

:deep(.chat-input textarea.ant-input) {
  border-radius: 22px !important;
  border: 1px solid rgba(255, 255, 255, 0.34) !important;
  background: rgba(255, 255, 255, 0.18) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.24) !important;
  color: #334363 !important;
}

.panel-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
}

.shortcut-tip {
  color: rgba(58, 74, 105, 0.74);
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.workspace-panel {
  padding: 18px;
}

.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.tabs {
  display: flex;
  gap: 10px;
}

.tabs button,
.file-item {
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.14);
  color: #35517c;
}

.tabs button {
  min-width: 100px;
  height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  cursor: pointer;
}

.tabs button.active,
.file-item.active {
  background: rgba(255, 255, 255, 0.32);
  border-color: rgba(255, 255, 255, 0.38);
}

.project-panel {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  min-height: 680px;
}

.file-tree,
.code-viewer,
.iframe-wrap {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.file-tree {
  padding: 16px;
  overflow: auto;
}

.summary-card {
  padding: 16px;
  margin-bottom: 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.18);
}

.summary-card strong,
.code-header {
  color: #f5fbff;
}

.summary-card p {
  margin: 10px 0 0;
  color: rgba(52, 67, 96, 0.86);
  line-height: 1.8;
}

.file-item {
  width: 100%;
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 16px;
  text-align: left;
  cursor: pointer;
}

.more-file-item {
  color: #476798;
  font-weight: 600;
  border-style: dashed;
}

.code-viewer {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.code-header {
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.22);
}

.code-viewer pre {
  flex: 1;
  margin: 0;
  padding: 18px;
  overflow: auto;
  min-height: 0;
  max-height: 640px;
  color: #31425f;
}

.full-code-modal {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  min-height: 640px;
}

.modal-file-tree,
.modal-code-viewer {
  min-height: 0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.modal-file-tree {
  padding: 16px;
  overflow: auto;
}

.modal-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #3b5b87;
}

.modal-code-viewer {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.modal-code-viewer pre {
  flex: 1;
  margin: 0;
  padding: 18px;
  overflow: auto;
  min-height: 0;
  max-height: 72vh;
  color: #31425f;
}

:deep(.glass-code-modal .ant-modal-content) {
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.38);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 28px 70px rgba(32, 54, 92, 0.18);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

:deep(.glass-code-modal .ant-modal-close) {
  color: #45648e;
}

.preview-panel,
.iframe-wrap,
.iframe-wrap iframe {
  min-height: 680px;
}

.iframe-wrap {
  overflow: hidden;
}

.iframe-wrap iframe {
  width: 100%;
  border: 0;
  background: #fff;
}

@media (max-width: 1080px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .project-panel {
    grid-template-columns: 1fr;
  }

  .full-code-modal {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .editor-page {
    padding: 76px 14px 18px;
  }

  .page-header,
  .panel-head,
  .stream-overview,
  .panel-actions,
  .workspace-header {
    flex-direction: column;
    align-items: stretch;
  }

  .overview-side,
  .header-actions,
  .action-buttons {
    width: 100%;
  }

  .phase-list {
    grid-template-columns: 1fr;
  }

  .action-buttons :deep(.ant-btn),
  .header-actions :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
