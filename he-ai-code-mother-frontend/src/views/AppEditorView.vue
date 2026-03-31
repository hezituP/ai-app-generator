<template>
  <div class="editor-page">
    <div class="editor-topbar">
      <div class="topbar-left">
        <router-link to="/my-apps" class="back-btn"><ArrowLeftOutlined /></router-link>
        <div class="app-title-wrap">
          <span class="app-title">{{ app?.appName || '加载中...' }}</span>
          <span class="app-type-tag" v-if="app">{{ fmtType(app.codeGenType) }}</span>
        </div>
      </div>
      <div class="topbar-actions">
        <a-button size="small" class="btn-deploy-top" @click="handleDeploy" :loading="deploying"><RocketOutlined /> {{ app?.deployKey ? '查看部署' : '部署' }}</a-button>
        <a-button size="small" @click="handleDownload"><DownloadOutlined /> 下载</a-button>
      </div>
    </div>
    <div class="editor-main">
      <div class="chat-panel">
        <div class="chat-header">
          <div class="chat-title"><span class="ai-dot"></span> AI 对话</div>
          <a-tooltip title="清空"><a-button type="text" size="small" class="clear-btn" @click="clearMessages"><DeleteOutlined /></a-button></a-tooltip>
        </div>
        <div class="chat-messages" ref="messagesRef">
          <div class="welcome-msg" v-if="messages.length === 0">
            <div class="welcome-icon">✨</div>
            <h3>开始与 AI 对话</h3>
            <p>描述你想要的功能，AI 将自动生成代码</p>
            <div class="quick-prompts">
              <div v-for="q in quickPrompts" :key="q" class="quick-prompt" @click="sendQuick(q)">{{ q }}</div>
            </div>
          </div>
          <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role]">
            <div class="msg-avatar"><span v-if="msg.role === 'user'">U</span><span v-else>AI</span></div>
            <div class="msg-bubble">
              <div class="msg-content" v-html="msg.content"></div>
              <div class="msg-time">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="streaming" class="msg assistant">
            <div class="msg-avatar"><span>AI</span></div>
            <div class="msg-bubble"><div class="msg-content" v-html="streamingContent || '<span>...</span>'"></div></div>
          </div>
        </div>
        <div class="chat-input-wrap">
          <a-textarea v-model:value="inputMsg" placeholder="请描述你想生成的网站，越详细效果越好哦" :rows="3" class="chat-textarea" @keydown.ctrl.enter="handleSend" :disabled="streaming" />
          <div class="chat-input-footer">
            <span class="hint">Ctrl+Enter 发送</span>
            <a-button type="primary" @click="handleSend" :loading="streaming" :disabled="!inputMsg.trim()" class="send-btn"><SendOutlined /> 发送</a-button>
          </div>
        </div>
      </div>
      <div class="preview-panel">
        <div class="preview-header">
          <div class="preview-tabs">
            <button :class="['tab',{active:activeTab==='preview'}]" @click="activeTab='preview'">预览</button>
            <button :class="['tab',{active:activeTab==='code'}]" @click="activeTab='code'">代码</button>
          </div>
          <div class="preview-actions">
            <a-tooltip title="刷新"><a-button type="text" size="small" class="icon-btn" @click="refreshPreview"><ReloadOutlined /></a-button></a-tooltip>
            <a-tooltip title="新窗口"><a-button type="text" size="small" class="icon-btn" @click="openInNewTab"><ExpandAltOutlined /></a-button></a-tooltip>
          </div>
        </div>
        <div class="preview-body">
          <div v-if="activeTab==='preview'" class="iframe-wrap">
            <div v-if="!previewReady" class="preview-empty"><CodeOutlined class="empty-icon"/><p>发送消息后预览将在这里显示</p></div>
            <iframe v-show="previewReady" ref="iframeRef" class="preview-iframe" :src="previewUrl" sandbox="allow-scripts allow-same-origin allow-forms"></iframe>
          </div>
          <div v-else class="code-wrap">
            <pre class="code-content"><code>{{ previewUrl ? `预览地址: ${previewUrl}` : '// 代码将在 AI 生成完毕后通过静态服务展示' }}</code></pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined, RocketOutlined, DownloadOutlined, DeleteOutlined,
  SendOutlined, ReloadOutlined, ExpandAltOutlined, CodeOutlined
} from '@ant-design/icons-vue'
import { getAppVOByIdApi, deployAppApi, downloadAppCodeApi, type AppVO } from '@/api/app'
import myAxios from '@/plugins/myAxios'

const route = useRoute()
const router = useRouter()
const appId = route.params.id as string

const app = ref<AppVO | null>(null)
const activeTab = ref<'preview' | 'code'>('preview')
const inputMsg = ref('')
const messages = ref<{ id: number; role: 'user' | 'assistant'; content: string; time: string }[]>([])
const streaming = ref(false)
const streamingContent = ref('')
const previewUrl = ref('')
const previewReady = ref(false)
const deploying = ref(false)
const messagesRef = ref<HTMLElement | null>(null)
const iframeRef = ref<HTMLIFrameElement | null>(null)
let msgId = 0
let eventSource: EventSource | null = null

const quickPrompts = [
  '帮我添加一个导航栏',
  '增加响应式布局',
  '添加暗色模式切换',
  '优化页面配色方案',
]

function fmtType(t: string) {
  return ({ html: 'HTML', vue: 'Vue', react: 'React', multi_file: '多文件' } as Record<string, string>)[t] || t
}

function now() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function scrollToBottom() {
  setTimeout(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }, 50)
}

async function loadApp() {
  try {
    const res = await getAppVOByIdApi(appId)
    if (res.data.code === 0) {
      app.value = res.data.data
      if (app.value?.deployKey) {
        previewUrl.value = `/api/preview/${app.value.deployKey}`
        previewReady.value = true
      }
    } else {
      message.error('应用不存在')
      router.push('/my-apps')
    }
  } catch {
    message.error('加载失败')
  }
}

async function handleSend() {
  const content = inputMsg.value.trim()
  if (!content || streaming.value) return
  inputMsg.value = ''
  messages.value.push({ id: ++msgId, role: 'user', content, time: now() })
  scrollToBottom()
  streaming.value = true
  streamingContent.value = ''

  try {
    const baseURL = (myAxios.defaults.baseURL || '') as string
    const url = `${baseURL}/app/chat/gen/code?appId=${appId}&message=${encodeURIComponent(content)}`
    eventSource = new EventSource(url, { withCredentials: true })

    eventSource.onmessage = (e) => {
      try {
        const parsed = JSON.parse(e.data)
        const chunk = parsed.d ?? parsed.content ?? ''
        if (chunk) {
          streamingContent.value += chunk
        }
      } catch {
        if (e.data) {
          streamingContent.value += e.data
        }
      }
      scrollToBottom()
    }

    eventSource.addEventListener('done', () => {
      finishStream()
    })

    eventSource.onerror = () => {
      finishStream()
    }
  } catch {
    message.error('发送失败')
    streaming.value = false
  }
}

function finishStream() {
  if (eventSource) { eventSource.close(); eventSource = null }
  if (streamingContent.value) {
    messages.value.push({ id: ++msgId, role: 'assistant', content: streamingContent.value, time: now() })
    streamingContent.value = ''
  }
  streaming.value = false
  scrollToBottom()
}

function sendQuick(q: string) {
  inputMsg.value = q
  handleSend()
}

function clearMessages() { messages.value = [] }

function refreshPreview() {
  if (iframeRef.value && previewUrl.value) {
    iframeRef.value.src = previewUrl.value
  }
}

function openInNewTab() {
  if (previewUrl.value) window.open(previewUrl.value, '_blank')
}

async function handleDeploy() {
  if (app.value?.deployKey) { window.open(`/preview/${app.value.deployKey}`, '_blank'); return }
  deploying.value = true
  try {
    const res = await deployAppApi(appId)
    if (res.data.code === 0) {
      message.success('部署成功！')
      const url = res.data.data as string
      if (url) window.open(url, '_blank')
      await loadApp()
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch { message.error('网络异常') }
  finally { deploying.value = false }
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
  } catch { message.error('下载失败') }
}

onMounted(() => { loadApp() })
onUnmounted(() => { if (eventSource) { eventSource.close() } })
</script>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(120deg, #84fab0 0%, #8fd3f4 100%);
  color: #1a3a2a;
  overflow: hidden;
  padding-top: 58px;
}

.editor-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 48px;
  background: rgba(255,255,255,0.7);backdrop-filter:blur(12px);
  border-bottom: 1px solid rgba(132,250,176,0.3);
  flex-shrink: 0;
  gap: 12px;
}

.topbar-left { display: flex; align-items: center; gap: 12px; min-width: 0; }

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: rgba(255,255,255,0.4);
  color: #2d6a4f;
  text-decoration: none;
  transition: all 0.18s;
  flex-shrink: 0;
}
.back-btn:hover { background: rgba(132,250,176,0.3); color: #1a6a4f; }

.app-title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
.app-title { font-size: 14px; font-weight: 700; color: #1a3a2a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 260px; }
.app-type-tag { padding: 2px 8px; background: rgba(132,250,176,0.25); border: 1px solid rgba(58,176,160,0.4); border-radius: 12px; font-size: 11px; font-weight: 700; color: #1a6a4f; flex-shrink: 0; }

.topbar-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.btn-deploy-top { border-radius: 7px !important; font-size: 12px !important; font-weight: 700 !important; background: linear-gradient(135deg, #3ab0a0, #2d8a7a) !important; border: none !important; color: white !important; display: flex !important; align-items: center !important; gap: 4px !important; }

.editor-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.chat-panel {
  flex: 2;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(196,113,237,0.12);
  background: rgba(255,255,255,0.55);
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(132,250,176,0.25);
  flex-shrink: 0;
}

.chat-title { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 700; color: #2d6a4f; }
.ai-dot { width: 7px; height: 7px; border-radius: 50%; background: linear-gradient(135deg, #84fab0, #3ab0a0); box-shadow: 0 0 6px rgba(132,250,176,0.6); animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.4; } }
.clear-btn { color: #4a8a6a !important; }
.clear-btn:hover { color: #f64f59 !important; }

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-behavior: smooth;
}
.chat-messages::-webkit-scrollbar { width: 4px; }
.chat-messages::-webkit-scrollbar-track { background: transparent; }
.chat-messages::-webkit-scrollbar-thumb { background: rgba(132,250,176,0.4); border-radius: 2px; }

.welcome-msg { text-align: center; padding: 32px 20px; color: #2d6a4f; }
.welcome-icon { font-size: 36px; margin-bottom: 12px; }
.welcome-msg h3 { font-size: 16px; font-weight: 700; color: #2d6a4f; margin-bottom: 6px; }
.welcome-msg p { font-size: 13px; margin-bottom: 18px; }

.quick-prompts { display: flex; flex-direction: column; gap: 6px; max-width: 280px; margin: 0 auto; }
.quick-prompt { padding: 8px 14px; background: rgba(196,113,237,0.08); border: 1px solid rgba(196,113,237,0.2); border-radius: 8px; font-size: 12px; color: #2d6a4f; cursor: pointer; transition: all 0.16s; text-align: left; }
.quick-prompt:hover { background: rgba(132,250,176,0.3); border-color: rgba(58,176,160,0.5); color: #1a6a4f; }

.msg { display: flex; gap: 10px; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 28px; height: 28px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 800; flex-shrink: 0; }
.msg.user .msg-avatar { background: linear-gradient(135deg, #84fab0, #3ab0a0); color: #1a3a2a; }
.msg.assistant .msg-avatar { background: rgba(143,211,244,0.25); color: #1a6a4f; border: 1px solid rgba(143,211,244,0.4); }
.msg-bubble { max-width: 85%; }
.msg.user .msg-bubble { align-items: flex-end; display: flex; flex-direction: column; }
.msg-content { padding: 9px 13px; border-radius: 10px; font-size: 13px; line-height: 1.6; word-break: break-word; }
.msg.user .msg-content { background: linear-gradient(135deg, rgba(132,250,176,0.35), rgba(143,211,244,0.3)); border: 1px solid rgba(132,250,176,0.4); color: #1a3a2a; border-radius: 10px 2px 10px 10px; }
.msg.assistant .msg-content { background: rgba(255,255,255,0.7); border: 1px solid rgba(143,211,244,0.3); color: #1a3a2a; border-radius: 2px 10px 10px 10px; }
.msg-time { font-size: 10px; color: #5a9a7a; margin-top: 4px; padding: 0 4px; }

.chat-input-wrap {
  padding: 12px 14px;
  border-top: 1px solid rgba(196,113,237,0.1);
  flex-shrink: 0;
  background: rgba(255,255,255,0.4);
}
:deep(.chat-textarea) { border-radius: 10px !important; background: rgba(255,255,255,0.75) !important; border: 1px solid rgba(132,250,176,0.4) !important; color: #1a3a2a !important; font-size: 13px !important; resize: none !important; }
:deep(.chat-textarea:hover) { border-color: rgba(58,176,160,0.6) !important; }
:deep(.chat-textarea:focus-within) { border-color: rgba(58,176,160,0.8) !important; box-shadow: 0 0 0 3px rgba(132,250,176,0.15) !important; }
:deep(.chat-textarea textarea) { background: transparent !important; color: #1a3a2a !important; }
:deep(.chat-textarea textarea::placeholder) { color: #5a9a7a !important; }
.chat-input-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.hint { font-size: 11px; color: #5a9a7a; }
.send-btn { border-radius: 8px !important; font-weight: 700 !important; background: linear-gradient(135deg, #84fab0, #3ab0a0) !important; border: none !important; display: flex !important; align-items: center !important; gap: 5px !important; }

.preview-panel {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: rgba(255,255,255,0.35);
  min-width: 0;
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid rgba(196,113,237,0.1);
  flex-shrink: 0;
  background: rgba(255,255,255,0.6);
}
.preview-tabs { display: flex; gap: 4px; }
.tab { padding: 5px 14px; border-radius: 7px; font-size: 12px; font-weight: 600; background: transparent; border: none; cursor: pointer; color: #2d6a4f; transition: all 0.16s; }
.tab.active { background: rgba(132,250,176,0.3); color: #1a6a4f; }
.tab:hover:not(.active) { color: #2d6a4f; }
.preview-actions { display: flex; gap: 4px; }
.icon-btn { color: #2d6a4f !important; }
.icon-btn:hover { color: #1a6a4f !important; background: rgba(132,250,176,0.2) !important; }

.preview-body { flex: 1; overflow: hidden; position: relative; }
.iframe-wrap { width: 100%; height: 100%; position: relative; }
.preview-iframe { width: 100%; height: 100%; border: none; background: white; }
.preview-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #5a9a7a; gap: 12px; }
.empty-icon { font-size: 40px; }
.code-wrap { height: 100%; overflow: auto; padding: 20px; }
.code-content { font-family: monospace; font-size: 13px; color: #2d6a4f; line-height: 1.6; white-space: pre-wrap; }
</style>
