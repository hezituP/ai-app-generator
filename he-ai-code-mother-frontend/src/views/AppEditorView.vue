<template>
  <div class="editor-page" :style="pageStyle">
    <header class="page-header">
      <div class="header-main">
        <router-link to="/my-apps" class="back-link">返回我的应用</router-link>
        <h1>{{ app?.appName || 'AI 生成协作区' }}</h1>
        <p>{{ typeLabel }}</p>
      </div>
      <div class="header-actions">
        <a-select
          v-model:value="uiScale"
          class="ui-scale-select"
          size="small"
          :options="uiScaleOptions"
        />
        <a-button @click="handleDownload">
          <DownloadOutlined />
          下载代码
        </a-button>
        <a-button type="primary" :loading="deploying" @click="handleDeploy">
          <RocketOutlined />
          {{ app?.deployKey ? '打开已部署站点' : '部署站点' }}
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
            已选中元素：{{ selectedElement.tagName }}
            <span v-if="selectedElement.selector"> · {{ selectedElement.selector }}</span>
          </template>
          <template #description>
            {{ selectedElement.text || '当前元素没有明显文本内容。' }}
          </template>
        </a-alert>

        <div ref="messagesRef" class="stream-output">
          <div v-if="!displayEvents.length" class="stream-empty">
            这里会直接展示 AI 返回的生成内容。
          </div>
          <div v-else class="stream-lines">
            <article
              v-for="item in displayEvents"
              :key="item.id"
              class="stream-line"
              :class="[item.role, item.type, { pending: item.pending }]"
            >
              <div class="line-meta">
                <span class="line-badge">{{ item.roleLabel }}</span>
                <span class="line-time">{{ item.time }}</span>
              </div>
              <div v-if="item.pending" class="message-loading">
                <span class="loading-dot"></span>
                <span class="loading-dot"></span>
                <span class="loading-dot"></span>
              </div>
              <p class="line-message">{{ item.message }}</p>
            </article>
          </div>
        </div>

        <a-alert
          v-if="systemNotice"
          class="system-notice"
          type="warning"
          show-icon
          closable
          @close="systemNotice = ''"
        >
          <template #message>
            {{ systemNotice }}
          </template>
        </a-alert>

        <a-textarea
          v-model:value="inputMsg"
          :rows="4"
          :disabled="streaming"
          class="chat-input"
          placeholder="描述你想生成或修改的页面、组件、布局和风格。"
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
            <a-button type="primary" :loading="streaming" @click="handleSend">
              发送给 AI
            </a-button>
          </div>
        </div>
      </section>

      <section class="workspace-panel glass-panel">
        <div class="workspace-header">
          <div class="tabs">
            <button :class="{ active: activeTab === 'project' }" @click="activeTab = 'project'">
              工程文件
            </button>
            <button :class="{ active: activeTab === 'preview' }" @click="activeTab = 'preview'">
              网站快照
            </button>
          </div>
          <a-button size="small" @click="refreshSnapshot">刷新快照</a-button>
        </div>

        <div v-if="activeTab === 'project'" class="project-panel">
          <aside class="file-tree">
            <div class="summary-card">
              <strong>工程摘要</strong>
              <p>{{ projectSnapshot?.summary || '生成完成后会在这里展示前端工程结构与摘要。' }}</p>
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
              ... 还有 {{ hiddenFrontendFiles.length }} 个前端文件
            </button>
          </aside>

          <div class="code-viewer">
            <div class="code-header">{{ selectedFilePath || '请选择一个文件查看代码' }}</div>
            <pre><code>{{ selectedFileContent || '// 暂无代码内容' }}</code></pre>
          </div>
        </div>

        <div v-else class="preview-panel">
          <div v-if="projectSnapshot?.previewUrl" class="iframe-wrap">
            <iframe ref="previewFrameRef" :src="resolvedPreviewUrl" title="preview" />
          </div>
          <div v-else class="empty-block preview-empty">
            <h3>还没有可预览的站点</h3>
            <p>发送一次生成或修改需求后，这里会自动展示静态预览。</p>
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
          <div class="code-header">{{ modalSelectedFilePath || '请选择一个文件查看代码' }}</div>
          <pre><code>{{ modalSelectedFileContent || '// 暂无代码内容' }}</code></pre>
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
  resolveDeployUrl,
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
  message: string
  time: string
  pending?: boolean
}

type UiScale = 'compact' | 'default' | 'expanded'

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
const systemNotice = ref('')
const selectedWallpaper = ref<'bg1' | 'bg2' | 'bg3'>('bg1')
const uiScale = ref<UiScale>('expanded')
const previewVersion = ref(0)

let eventId = 0
let eventSource: EventSource | null = null
let pendingAssistantEventId: number | null = null

const wallpaperMap: Record<'bg1' | 'bg2' | 'bg3', string> = {
  bg1: '/images/home-bg1.jpg',
  bg2: '/images/home-bg2.jpg',
  bg3: '/images/home-bg3.jpg',
}

const uiScaleOptions = [
  { value: 'compact', label: '紧凑' },
  { value: 'default', label: '标准' },
  { value: 'expanded', label: '宽松' },
]

const scaleConfigMap: Record<
  UiScale,
  {
    panelMinHeight: string
    agentWidth: string
    workspaceWidth: string
    panelPadding: string
    streamMaxHeight: string
  }
> = {
  compact: {
    panelMinHeight: '760px',
    agentWidth: 'minmax(460px, 0.96fr)',
    workspaceWidth: 'minmax(0, 1.04fr)',
    panelPadding: '22px',
    streamMaxHeight: '510px',
  },
  default: {
    panelMinHeight: '840px',
    agentWidth: 'minmax(540px, 0.96fr)',
    workspaceWidth: 'minmax(0, 1.04fr)',
    panelPadding: '26px',
    streamMaxHeight: '620px',
  },
  expanded: {
    panelMinHeight: '980px',
    agentWidth: 'minmax(600px, 0.9fr)',
    workspaceWidth: 'minmax(0, 1.1fr)',
    panelPadding: '32px',
    streamMaxHeight: '760px',
  },
}

const pageStyle = computed(() => {
  const currentScale = scaleConfigMap[uiScale.value]
  return {
    '--editor-bg-image': `url('${wallpaperMap[selectedWallpaper.value]}')`,
    '--editor-agent-width': currentScale.agentWidth,
    '--editor-workspace-width': currentScale.workspaceWidth,
    '--editor-panel-padding': currentScale.panelPadding,
    '--editor-panel-min-height': currentScale.panelMinHeight,
    '--editor-stream-max-height': currentScale.streamMaxHeight,
  }
})

const typeLabel = computed(() => {
  const map: Record<string, string> = {
    html: 'HTML 单页模式',
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
  if (!projectSnapshot.value?.previewUrl) {
    return ''
  }
  const baseURL = (myAxios.defaults.baseURL || '') as string
  const separator = projectSnapshot.value.previewUrl.includes('?') ? '&' : '?'
  return `${baseURL.replace(/\/api$/, '')}${projectSnapshot.value.previewUrl}${separator}t=${previewVersion.value}`
})
const displayEvents = computed(() =>
  events.value.filter((item) =>
    item.type === 'assistant'
    || item.type === 'user'
    || item.type === 'error'
    || item.type === 'status',
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
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
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

function scrollMessagesToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function resolveStatusNotice(statusMessage: string) {
  if (statusMessage === 'image-search-timeout') {
    return '本轮图片搜索超时，已自动跳过搜图并继续生成页面。'
  }
  if (statusMessage === 'image-search-skipped') {
    return '本轮没有拿到可用图片资源，系统已跳过搜图并继续生成。'
  }
  if (statusMessage === 'preview-build-failed') {
    return '网站快照构建失败，通常是生成代码结构异常或本地 Node / 依赖环境兼容问题。你仍然可以先查看右侧代码结果。'
  }
  return ''
}

function resolveStatusTimelineMessage(statusMessage: string) {
  const statusMap: Record<string, string> = {
    'workflow:image_collection': '正在收集可用图片与视觉素材…',
    'workflow:prompt_enhancement': '正在整理提示词与视觉资源…',
    'workflow:routing': '正在判断最合适的生成模式…',
    'workflow:code_generation': '代码已生成，正在整理工程文件…',
    'workflow:project_build': '正在构建静态预览与项目快照…',
    'Agent 已接管任务，正在分析你的需求': 'AI 已接收需求，正在分析页面目标…',
    '正在生成 HTML 的工程内容': '正在生成 HTML 页面内容…',
    '正在生成 多文件应用 的工程内容': '正在生成多文件站点内容…',
    '正在生成 Vue3 + Vite 项目 的工程内容': '正在生成 Vue3 + Vite 工程内容…',
    '正在编排项目文件、组件与依赖关系': '正在编排项目文件、组件与依赖关系…',
    '正在整理可落地的工程结构与输出结果': '正在整理可落地的工程结构与输出结果…',
    '代码生成完成，正在解析并写入工程文件': '代码正文已完成，正在写入工程文件…',
    'Vue 工程已生成，正在构建静态预览': 'Vue 工程已生成，正在构建静态预览…',
    '工程快照已准备完成，正在返回文件树与预览信息': '工程快照已准备完成，正在返回文件树与预览信息…',
    'preview-build-success': '静态预览构建完成，可以查看快照了。',
  }
  if (statusMap[statusMessage]) {
    return statusMap[statusMessage]
  }
  if (statusMessage.startsWith('workflow:')) {
    return ''
  }
  if (statusMessage === 'image-search-timeout'
    || statusMessage === 'image-search-skipped'
    || statusMessage === 'preview-build-failed') {
    return ''
  }
  return statusMessage
}

function pushEvent(
  type: TimelineEvent['type'],
  messageText: string,
  role: TimelineEvent['role'] = 'agent',
) {
  const roleLabelMap: Record<TimelineEvent['role'], string> = {
    user: '我',
    agent: 'AI',
    system: type === 'status' ? '进度' : '系统',
  }
  events.value.push({
    id: ++eventId,
    type,
    role,
    roleLabel: roleLabelMap[role],
    message: messageText,
    time: formatTime(),
    pending: false,
  })
  scrollMessagesToBottom()
}

function createPendingAssistant() {
  const id = ++eventId
  pendingAssistantEventId = id
  events.value.push({
    id,
    type: 'assistant',
    role: 'agent',
    roleLabel: 'AI',
    message: '',
    time: formatTime(),
    pending: true,
  })
  scrollMessagesToBottom()
}

function appendPendingAssistant(delta: string) {
  if (!delta) {
    return
  }
  if (pendingAssistantEventId == null) {
    createPendingAssistant()
  }
  const existing = events.value.find((item) => item.id === pendingAssistantEventId)
  if (!existing) {
    return
  }
  existing.message += delta
  existing.pending = true
  existing.time = formatTime()
  scrollMessagesToBottom()
}

function resolvePendingAssistant(finalMessage?: string) {
  if (pendingAssistantEventId == null) {
    if (finalMessage) {
      pushEvent('assistant', finalMessage, 'agent')
    }
    return
  }
  const existing = events.value.find((item) => item.id === pendingAssistantEventId)
  if (existing) {
    if (typeof finalMessage === 'string' && finalMessage.length > 0) {
      existing.message = finalMessage
    }
    existing.pending = false
    existing.time = formatTime()
  }
  pendingAssistantEventId = null
  scrollMessagesToBottom()
}

function buildPromptWithSelection(content: string) {
  if (!selectedElement.value) {
    return content
  }
  const selection = selectedElement.value
  return `${content}

[当前选中元素]
标签：${selection.tagName}
选择器：${selection.selector || '无'}
文本：${selection.text || '无'}
id：${selection.id || '无'}
class：${selection.className || '无'}
请优先围绕这个元素及其相邻区域进行修改。`
}

function openFullCodeModal() {
  fullCodeModalOpen.value = true
  if (!modalSelectedFilePath.value || !frontendProjectFiles.value.some((file) => file.path === modalSelectedFilePath.value)) {
    modalSelectedFilePath.value = hiddenFrontendFiles.value[0]?.path || frontendProjectFiles.value[0]?.path || ''
  }
}

function closeStream() {
  streaming.value = false
  pendingAssistantEventId = null
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

function applySnapshot(snapshot: AppProjectSnapshotVO | null) {
  projectSnapshot.value = snapshot
  previewVersion.value += 1
  const files = snapshot?.files || []
  if (!files.length) {
    selectedFilePath.value = ''
    return
  }
  const matchedFile = files.find((item) => item.path === selectedFilePath.value)
  if (!selectedFilePath.value || !matchedFile) {
    selectedFilePath.value = snapshot?.entryFilePath || frontendProjectFiles.value[0]?.path || files[0]?.path || ''
  }
  if (snapshot?.previewUrl) {
    nextTick(() => syncVisualEditor())
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
      roleLabel: item.messageType === 'user' ? '我' : item.messageType === 'error' ? '系统' : 'AI',
      message: item.message,
      time: formatTime(new Date(item.createTime)),
      pending: false,
    }))
    eventId = events.value.length
    scrollMessagesToBottom()
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

function handleStreamEvent(event: AgentStreamEvent) {
  if (event.type === 'status') {
    const notice = resolveStatusNotice(event.message || '')
    if (notice) {
      systemNotice.value = notice
    }
    const timelineMessage = resolveStatusTimelineMessage(event.message || '')
    if (timelineMessage) {
      pushEvent('status', timelineMessage, 'system')
    }
    return
  }
  if (event.type === 'assistant_delta') {
    appendPendingAssistant(event.message || '')
    return
  }
  if (event.type === 'assistant') {
    resolvePendingAssistant(event.message || '')
    return
  }
  if (event.type === 'result') {
    if (event.data) {
      applySnapshot(event.data)
    }
    resolvePendingAssistant()
    return
  }
  if (event.type === 'error') {
    resolvePendingAssistant()
    pushEvent('error', event.message || '生成失败，请稍后重试。', 'system')
    closeStream()
    return
  }
  if (event.type === 'done') {
    resolvePendingAssistant()
    closeStream()
    void refreshSnapshot()
  }
}

async function handleSend() {
  const content = inputMsg.value.trim()
  if (!content || streaming.value) {
    return
  }

  pushEvent('user', content, 'user')
  streaming.value = true
  systemNotice.value = ''

  const prompt = buildPromptWithSelection(content)
  inputMsg.value = ''
  visualEditMode.value = false
  handleClearSelectedElement()

  const baseURL = (myAxios.defaults.baseURL || '') as string
  const streamUrl = `${baseURL}/app/chat/gen/code?appId=${encodeURIComponent(appId)}&message=${encodeURIComponent(prompt)}`

  try {
    eventSource = new EventSource(streamUrl, { withCredentials: true })
    eventSource.onmessage = (evt) => {
      try {
        const payload = JSON.parse(evt.data) as AgentStreamEvent
        handleStreamEvent(payload)
      } catch {
        pushEvent('error', '流式数据解析失败。', 'system')
        closeStream()
      }
    }
    eventSource.onerror = () => {
      pushEvent('error', '流式连接已断开，请稍后重试。', 'system')
      closeStream()
      void loadHistory()
      void refreshSnapshot()
    }
  } catch {
    pushEvent('error', '无法建立流式连接。', 'system')
    closeStream()
  }
}

function toggleVisualEditMode() {
  if (!projectSnapshot.value?.previewUrl) {
    message.warning('当前还没有可编辑的静态预览，请先生成一次页面。')
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
    window.open(resolveDeployUrl(app.value.deployKey), '_blank')
    return
  }
  deploying.value = true
  try {
    const res = await deployAppApi(appId)
    if (res.data.code === 0) {
      message.success('部署成功，正在打开站点。')
      if (res.data.data) {
        window.open(res.data.data, '_blank')
      }
      await loadApp()
    } else {
      message.error(res.data.message || '部署失败，请稍后重试。')
    }
  } catch {
    message.error('部署失败，请检查后端服务是否正常。')
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
    message.error('下载代码失败，请稍后重试。')
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
  if (!modalSelectedFilePath.value || !frontendProjectFiles.value.some((item) => item.path === modalSelectedFilePath.value)) {
    modalSelectedFilePath.value = frontendProjectFiles.value[0]?.path || ''
  }
})

watch(resolvedPreviewUrl, () => {
  if (!projectSnapshot.value?.previewUrl) {
    visualEditMode.value = false
    handleClearSelectedElement()
  }
})

watch(uiScale, (value) => {
  window.localStorage.setItem('editorUiScale', value)
})

onMounted(async () => {
  const savedWallpaper = window.localStorage.getItem('homeWallpaper')
  if (savedWallpaper === 'bg1' || savedWallpaper === 'bg2' || savedWallpaper === 'bg3') {
    selectedWallpaper.value = savedWallpaper
  }
  const savedUiScale = window.localStorage.getItem('editorUiScale')
  if (savedUiScale === 'compact' || savedUiScale === 'default' || savedUiScale === 'expanded') {
    uiScale.value = savedUiScale
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
  padding: 74px 16px 20px;
  background:
    linear-gradient(rgba(255, 255, 255, 0.12), rgba(240, 246, 255, 0.2)),
    var(--editor-bg-image, none),
    linear-gradient(135deg, #eef5ff 0%, #f8f3ff 42%, #fff6fb 100%);
  background-size: cover, cover, auto;
  background-position: center top, center top, center;
}

.page-header,
.editor-layout {
  max-width: 1640px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  margin-bottom: 16px;
}

.header-main {
  min-width: 0;
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
  color: rgba(52, 67, 97, 0.9);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.ui-scale-select {
  min-width: 104px;
}

.editor-layout {
  display: grid;
  grid-template-columns: var(--editor-agent-width, minmax(600px, 0.9fr)) var(--editor-workspace-width, minmax(0, 1.1fr));
  gap: 16px;
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
  min-height: var(--editor-panel-min-height, 980px);
}

.agent-panel {
  display: flex;
  flex-direction: column;
  padding: var(--editor-panel-padding, 32px);
}

.workspace-panel {
  padding: var(--editor-panel-padding, 32px);
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

.system-notice {
  margin-top: 14px;
  border-radius: 18px;
}

.stream-output {
  flex: 1;
  min-height: 360px;
  max-height: var(--editor-stream-max-height, 760px);
  overflow: auto;
  border-radius: 24px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.stream-empty {
  color: rgba(55, 72, 104, 0.82);
  line-height: 1.8;
}

.stream-lines {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stream-line {
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
}

.stream-line.status {
  padding: 0 0 8px;
  border-bottom: 0;
}

.stream-line:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.stream-line.user .line-badge {
  background: rgba(91, 147, 240, 0.18);
  color: #315d99;
}

.stream-line.agent .line-badge {
  background: rgba(255, 255, 255, 0.32);
  color: #47648a;
}

.stream-line.system .line-badge {
  background: rgba(255, 112, 112, 0.16);
  color: #9b5565;
}

.stream-line.status .line-meta {
  margin-bottom: 4px;
}

.stream-line.status .line-badge {
  background: rgba(83, 106, 145, 0.12);
  color: rgba(70, 92, 129, 0.82);
  font-weight: 600;
}

.stream-line.status .line-time {
  color: rgba(88, 105, 136, 0.48);
}

.line-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.line-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.line-time {
  margin-left: auto;
  font-size: 12px;
  color: rgba(69, 88, 119, 0.66);
}

.line-message {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.85;
  color: rgba(45, 62, 91, 0.94);
}

.stream-line.status .line-message {
  font-size: 13px;
  line-height: 1.5;
  color: rgba(71, 91, 126, 0.74);
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
  margin-top: 16px;
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
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  min-height: 760px;
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

.code-viewer,
.modal-code-viewer {
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
  max-height: 760px;
  color: #31425f;
}

.preview-panel,
.iframe-wrap,
.iframe-wrap iframe {
  min-height: 760px;
}

.iframe-wrap {
  overflow: hidden;
}

.iframe-wrap iframe {
  width: 100%;
  border: 0;
  background: #fff;
}

.empty-block {
  min-height: 220px;
  display: grid;
  place-items: center;
  text-align: center;
  color: rgba(52, 68, 98, 0.82);
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

@media (max-width: 1080px) {
  .editor-layout,
  .project-panel,
  .full-code-modal {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .editor-page {
    padding: 72px 12px 16px;
  }

  .page-header,
  .panel-head,
  .panel-actions,
  .workspace-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions,
  .action-buttons {
    width: 100%;
  }

  .action-buttons :deep(.ant-btn),
  .header-actions :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
