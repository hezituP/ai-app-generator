<template>
  <div class="home-page" :style="homePageStyle">
    <div class="petal petal-a"></div>
    <div class="petal petal-b"></div>
    <div class="petal petal-c"></div>
    <div class="sparkle sparkle-a"></div>
    <div class="sparkle sparkle-b"></div>

    <section class="hero" :class="{ visible: heroVisible }">
      <div class="hero-copy">
        <div class="hero-badge-row">
          <span class="eyebrow">AI 魔法制作中</span>
        </div>

        <h1 class="hero-title">
          <span class="title-main">把脑海里的画面</span>
          <span class="title-sub">变成会发光的页面</span>
        </h1>

        <p class="hero-desc">
          输入一句想法，平台会按 Vue3 + Vite 工程结构生成页面、组件、路由和样式，
          再用 Agent 流式把结果慢慢展开给你看。
        </p>

        <div class="hero-form">
          <a-textarea
            v-model:value="inputPrompt"
            :rows="5"
            class="prompt-box"
            placeholder="例如：生成一个带日系插画氛围的作品集网站，包含首页、角色档案、作品展示和留言页，使用 Vue3 + Vite 工程结构"
          />
          <div class="hero-actions">
            <a-select v-model:value="codeGenType" class="mode-select">
              <a-select-option value="vue_project">Vue3 + Vite 工程</a-select-option>
              <a-select-option value="multi_file">多文件站点</a-select-option>
              <a-select-option value="html">HTML 单页</a-select-option>
            </a-select>
            <a-button type="primary" size="large" class="create-btn" :loading="creating" @click="handleCreate">
              开始创作
            </a-button>
          </div>
        </div>

        <div class="wallpaper-switcher">
          <span class="switcher-label">壁纸切换</span>
          <button
            v-for="item in wallpaperOptions"
            :key="item.value"
            class="wallpaper-btn"
            :class="{ active: selectedWallpaper === item.value }"
            @click="selectedWallpaper = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="hero-metrics">
          <article class="metric-card" v-for="item in metrics" :key="item.label">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>
      </div>
    </section>

    <section ref="overviewRef" class="overview-section" :class="{ visible: overviewVisible }">
      <div class="story-panel">
        <div class="section-copy">
          <span class="eyebrow soft">创作节奏</span>
          <h2>不是冷冰冰的工具页，而是一块有情绪的创作面板</h2>
          <p>
            这一页现在更像一个轻盈的创作入口。上面专注输入需求，下面用更安静的版式解释能力和生成过程，减少噪音，让视觉重点回到“开始创作”本身。
          </p>
        </div>

        <div class="overview-grid">
          <article
            v-for="(item, index) in features"
            :key="item.title"
            class="feature-card"
            :style="{ transitionDelay: `${index * 0.08}s` }"
          >
            <div class="feature-icon">{{ item.icon }}</div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </article>
        </div>
      </div>

      <aside class="process-card">
        <div class="process-head">
          <span class="eyebrow soft">生成流程</span>
          <strong>从一句话到完整工程的 4 个阶段</strong>
        </div>
        <div class="process-list">
          <div class="process-item" v-for="(item, index) in processSteps" :key="item.title">
            <span class="process-index">0{{ index + 1 }}</span>
            <div>
              <h4>{{ item.title }}</h4>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </aside>
    </section>

    <section class="apps-section">
      <div class="section-head compact">
        <div>
          <span class="eyebrow">灵感橱窗</span>
          <h2>社区优秀应用</h2>
        </div>
        <a-button class="refresh-btn" @click="fetchApps">换一批看看</a-button>
      </div>

      <div v-if="loading" class="loading-wrap">
        <a-spin size="large" />
      </div>

      <div v-else-if="goodApps.length" class="apps-grid">
        <article v-for="(app, index) in goodApps" :key="app.id" class="app-card" :style="{ animationDelay: `${index * 0.06}s` }" @click="goApp(app)">
          <div class="app-cover" v-if="app.cover">
            <img :src="app.cover" :alt="app.appName" />
          </div>
          <div v-else class="app-cover placeholder">{{ app.appName?.slice(0, 1) || 'A' }}</div>
          <div class="app-body">
            <div class="app-row">
              <h3>{{ app.appName }}</h3>
              <span class="type-tag">{{ formatType(app.codeGenType) }}</span>
            </div>
            <p>{{ app.initPrompt || '暂无描述' }}</p>
            <div class="app-footer">
              <span>{{ app.user?.userName || app.userVO?.userName || '匿名创作者' }}</span>
              <a-button type="link">查看</a-button>
            </div>
          </div>
        </article>
      </div>

      <a-empty v-else description="还没有精选案例" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { addAppApi, listGoodAppVOByPageApi, type AppVO } from '@/api/app'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const inputPrompt = ref('')
const codeGenType = ref<'vue_project' | 'multi_file' | 'html'>('vue_project')
const creating = ref(false)
const loading = ref(false)
const goodApps = ref<AppVO[]>([])
const heroVisible = ref(false)
const overviewVisible = ref(false)
const overviewRef = ref<HTMLElement | null>(null)
const selectedWallpaper = ref<'bg1' | 'bg2' | 'bg3'>('bg1')
const wallpaperOptions = [
  { label: '壁纸 1', value: 'bg1', url: '/images/home-bg1.jpg' },
  { label: '壁纸 2', value: 'bg2', url: '/images/home-bg2.jpg' },
  { label: '壁纸 3', value: 'bg3', url: '/images/home-bg3.jpg' },
] as const
const homePageStyle = computed(() => {
  const current = wallpaperOptions.find((item) => item.value === selectedWallpaper.value) || wallpaperOptions[0]
  return {
    '--home-bg-image': `url('${current.url}')`,
  }
})

const features = [
  { icon: '花', title: '完整工程生成', desc: '直接生成 Vue3 + Vite 工程目录、入口文件与页面结构，不再只是零碎代码。' },
  { icon: '流', title: 'Agent 流式协作', desc: '生成状态会持续回传，用户能实时看到当前阶段与阶段结果。' },
  { icon: '改', title: '继续对话迭代', desc: '工程完成后还能继续追问，让 Agent 按已有代码上下文继续修改。' },
]

const metrics = [
  { value: 'Vue3 + Vite', label: '完整工程输出' },
  { value: 'Agent Streaming', label: '流式状态返回' },
  { value: 'Project Snapshot', label: '文件树与源码预览' },
]

const processSteps = [
  { title: '输入灵感', desc: '一句话描述页面风格、结构或交互目标。' },
  { title: '拆解任务', desc: 'Agent 自动分析模块边界、页面层级和组件关系。' },
  { title: '生成工程', desc: '按 Vue3 + Vite 工程结构输出文件、代码和页面骨架。' },
  { title: '继续润色', desc: '生成完成后还能继续对话式修改与补全。' },
]

let observer: IntersectionObserver | null = null

async function fetchApps() {
  loading.value = true
  try {
    const res = await listGoodAppVOByPageApi({ pageNum: 1, pageSize: 8 })
    if (res.data.code === 0) {
      goodApps.value = res.data.data?.records || []
    }
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  const prompt = inputPrompt.value.trim()
  if (!prompt) {
    message.warning('请输入应用需求')
    return
  }
  if (!userStore.loginUser) {
    await router.push('/login')
    return
  }
  creating.value = true
  try {
    const res = await addAppApi({
      appName: prompt.slice(0, 16),
      codeGenType: codeGenType.value,
      initPrompt: prompt,
    })
    if (res.data.code === 0) {
      await router.push(`/app/${res.data.data}`)
    } else {
      message.error(res.data.message || '创建失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    creating.value = false
  }
}

function goApp(app: AppVO) {
  router.push(`/app/${app.id}`)
}

function formatType(type: string) {
  return ({
    html: 'HTML 单页',
    multi_file: '多文件站点',
    vue_project: 'Vue3 + Vite 工程',
  } as Record<string, string>)[type] || type
}

onMounted(() => {
  const savedWallpaper = window.localStorage.getItem('homeWallpaper')
  if (savedWallpaper && wallpaperOptions.some((item) => item.value === savedWallpaper)) {
    selectedWallpaper.value = savedWallpaper as 'bg1' | 'bg2' | 'bg3'
  }
  fetchApps()
  setTimeout(() => {
    heroVisible.value = true
  }, 80)
  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.target === overviewRef.value && entry.isIntersecting) {
        overviewVisible.value = true
      }
    })
  }, { threshold: 0.18 })
  if (overviewRef.value) {
    observer.observe(overviewRef.value)
  }
})

onUnmounted(() => {
  observer?.disconnect()
})

watch(selectedWallpaper, (value) => {
  window.localStorage.setItem('homeWallpaper', value)
})
</script>

<style scoped>
.home-page {
  --glass-bg: rgba(255, 247, 252, 0.34);
  --glass-border: rgba(255, 255, 255, 0.68);
  --soft-text: #786b91;
  --title-text: #44345f;
  --accent-pink: #ff8fb1;
  --accent-purple: #b09bff;
  --accent-blue: #8ed8ff;
  position: relative;
  min-height: 100vh;
  padding: 92px 28px 56px;
  overflow: hidden;
  background:
    var(--home-bg-image, none),
    linear-gradient(180deg, #fffafb 0%, #f7f4ff 58%, #eff6ff 100%);
  background-size: cover, auto;
  background-position: center center, 0 0;
  background-repeat: no-repeat, no-repeat;
}

.petal,
.sparkle {
  position: absolute;
  pointer-events: none;
}

.petal {
  width: 96px;
  height: 68px;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.95), rgba(255, 183, 210, 0.85) 55%, rgba(255, 143, 177, 0.18) 100%);
  border-radius: 70% 30% 65% 35% / 55% 45% 55% 45%;
  filter: blur(0.3px);
  opacity: 0.42;
  animation: drift 14s ease-in-out infinite;
}

.petal-a {
  top: 110px;
  left: 4%;
  transform: rotate(-18deg);
}

.petal-b {
  top: 360px;
  right: 6%;
  width: 120px;
  height: 82px;
  transform: rotate(22deg);
  animation-duration: 18s;
}

.petal-c {
  bottom: 150px;
  left: 10%;
  width: 76px;
  height: 54px;
  transform: rotate(10deg);
  animation-duration: 16s;
}

.sparkle {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 1), rgba(255, 255, 255, 0));
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.72);
  animation: twinkle 4s ease-in-out infinite;
}

.sparkle-a {
  top: 180px;
  right: 18%;
}

.sparkle-b {
  top: 420px;
  left: 16%;
  animation-delay: -1.5s;
}

.hero,
.overview-section,
.apps-section {
  position: relative;
  z-index: 1;
  max-width: 1180px;
  margin: 0 auto;
}

.hero {
  opacity: 0;
  transform: translateY(18px);
  transition: opacity 0.7s ease, transform 0.7s ease;
}

.hero.visible {
  opacity: 1;
  transform: translateY(0);
}

.hero-copy,
.metric-card,
.story-panel,
.feature-card,
.process-card,
.app-card {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  box-shadow: 0 24px 60px rgba(204, 165, 195, 0.16), inset 0 1px 0 rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
}

.hero-copy {
  padding: 38px;
  border-radius: 38px;
}

.hero-badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.eyebrow,
.mood-pill {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.eyebrow {
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.76);
  color: #8b6fb0;
}

.eyebrow.soft {
  background: rgba(255, 255, 255, 0.42);
}

.mood-pill {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.18), rgba(176, 155, 255, 0.18));
  color: #9d6ea0;
}

.hero-title {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 18px 0 14px;
  line-height: 0.98;
}

.title-main,
.title-sub {
  font-family: 'Hiragino Mincho ProN', 'Yu Mincho', 'Noto Serif SC', 'Songti SC', 'STSong', serif;
  font-weight: 900;
  letter-spacing: -0.06em;
  background: linear-gradient(135deg, #ffffff 0%, #dff4ff 36%, #9fd8ff 72%, #67bfff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 10px 24px rgba(103, 191, 255, 0.18);
}

.title-main {
  font-size: 70px;
}

.title-sub {
  font-size: 58px;
}

.hero-desc {
  margin: 0;
  max-width: 820px;
  color: var(--soft-text);
  font-size: 17px;
  line-height: 1.9;
}

.hero-form {
  margin-top: 26px;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.prompt-box :deep(.ant-input) {
  min-height: 144px;
  border: none !important;
  border-radius: 24px !important;
  background: rgba(255, 255, 255, 0.18) !important;
  color: #5f5477;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.36) !important;
  backdrop-filter: blur(10px);
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.mode-select {
  min-width: 230px;
}

.mode-select :deep(.ant-select-selector) {
  height: 48px !important;
  border: none !important;
  border-radius: 999px !important;
  background: rgba(255, 255, 255, 0.18) !important;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.34) !important;
  backdrop-filter: blur(10px);
  display: flex !important;
  align-items: center !important;
  padding: 0 18px !important;
}

.mode-select :deep(.ant-select-selection-wrap) {
  display: flex !important;
  align-items: center !important;
}

.mode-select :deep(.ant-select-selection-search),
.mode-select :deep(.ant-select-selection-item),
.mode-select :deep(.ant-select-selection-placeholder) {
  display: flex !important;
  align-items: center !important;
  height: 48px !important;
  line-height: 48px !important;
}

.create-btn {
  min-width: 154px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff8fb1 0%, #b09bff 55%, #8ed8ff 100%);
  box-shadow: 0 18px 34px rgba(176, 155, 255, 0.28);
}

.wallpaper-switcher {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
}

.switcher-label {
  color: var(--soft-text);
  font-size: 13px;
  font-weight: 700;
}

.wallpaper-btn {
  padding: 9px 14px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 999px;
  background: rgba(255, 250, 252, 0.72);
  color: #7a6d95;
  box-shadow: 6px 6px 14px rgba(231, 214, 227, 0.45), -6px -6px 14px rgba(255, 255, 255, 0.92);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.wallpaper-btn:hover {
  transform: translateY(-1px);
}

.wallpaper-btn.active {
  background: linear-gradient(135deg, rgba(103, 191, 255, 0.24), rgba(255, 255, 255, 0.88));
  color: #3b6fa6;
  box-shadow: 0 12px 24px rgba(103, 191, 255, 0.18);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.metric-card {
  position: relative;
  padding: 18px;
  border-radius: 24px;
}

.metric-card::after {
  content: '✦';
  position: absolute;
  top: 14px;
  right: 16px;
  color: rgba(176, 155, 255, 0.55);
}

.metric-card strong {
  display: block;
  color: var(--title-text);
  font-size: 18px;
}

.metric-card span {
  display: block;
  margin-top: 6px;
  color: var(--soft-text);
  font-size: 13px;
}

.overview-section {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(300px, 0.78fr);
  gap: 22px;
  margin-top: 28px;
  opacity: 0;
  transform: translateY(18px);
  transition: opacity 0.6s ease, transform 0.6s ease;
}

.overview-section.visible {
  opacity: 1;
  transform: translateY(0);
}

.story-panel {
  padding: 26px;
  border-radius: 34px;
}

.section-copy {
  margin-bottom: 16px;
}

.section-copy h2 {
  margin: 14px 0 10px;
  color: var(--title-text);
  font-size: 34px;
  letter-spacing: -0.04em;
}

.section-copy p {
  margin: 0;
  color: var(--soft-text);
  line-height: 1.82;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.feature-card {
  padding: 22px;
  border-radius: 28px;
  opacity: 0;
  transform: translateY(18px);
  transition: transform 0.45s ease, opacity 0.45s ease, box-shadow 0.25s ease;
}

.overview-section.visible .feature-card {
  opacity: 1;
  transform: translateY(0);
}

.feature-icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.2), rgba(176, 155, 255, 0.2));
  color: #8d68d6;
  font-weight: 900;
  box-shadow: 8px 8px 16px rgba(231, 214, 227, 0.58), -8px -8px 16px rgba(255, 255, 255, 0.96);
}

.feature-card h3 {
  margin: 16px 0 10px;
  color: var(--title-text);
  font-size: 19px;
}

.feature-card p {
  margin: 0;
  color: var(--soft-text);
  line-height: 1.72;
}

.process-card {
  padding: 24px;
  border-radius: 34px;
}

.process-head strong {
  display: block;
  margin-top: 12px;
  color: var(--title-text);
  font-size: 26px;
  line-height: 1.3;
}

.process-list {
  display: grid;
  gap: 14px;
  margin-top: 20px;
}

.process-item {
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 12px;
  align-items: start;
  padding: 16px;
  border-radius: 22px;
  background: rgba(255, 250, 252, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.7);
}

.process-index {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.18), rgba(176, 155, 255, 0.2));
  color: #8d68d6;
  font-weight: 800;
}

.process-item h4 {
  margin: 2px 0 6px;
  color: var(--title-text);
  font-size: 17px;
}

.process-item p {
  margin: 0;
  color: var(--soft-text);
  line-height: 1.68;
}

.apps-section {
  margin-top: 40px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-head.compact h2 {
  margin: 12px 0 0;
  font-size: 34px;
  color: var(--title-text);
  letter-spacing: -0.04em;
}

.refresh-btn {
  border: none;
  border-radius: 999px;
  background: rgba(255, 250, 252, 0.78);
  box-shadow: 8px 8px 16px rgba(231, 214, 227, 0.58), -8px -8px 16px rgba(255, 255, 255, 0.96);
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 22px;
}

.app-card {
  overflow: hidden;
  border-radius: 28px;
  cursor: pointer;
  animation: cardIn 0.55s cubic-bezier(0.34, 1.2, 0.64, 1) both;
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.app-card:hover {
  transform: translateY(-7px) rotate(-0.6deg);
}

.app-cover,
.app-cover img {
  width: 100%;
  height: 164px;
  object-fit: cover;
}

.app-cover.placeholder {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.2), rgba(176, 155, 255, 0.22), rgba(142, 216, 255, 0.18));
  color: rgba(84, 68, 118, 0.38);
  font-size: 56px;
  font-weight: 900;
}

.app-body {
  padding: 18px;
}

.app-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.app-row h3 {
  margin: 0;
  font-size: 18px;
  color: var(--title-text);
}

.type-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.52);
  color: #8d68d6;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.app-body p {
  margin: 12px 0 0;
  min-height: 44px;
  color: var(--soft-text);
  line-height: 1.72;
}

.app-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  color: #85789f;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

@keyframes drift {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-18px) rotate(10deg); }
}

@keyframes twinkle {
  0%, 100% { opacity: 0.35; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.2); }
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(18px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (max-width: 1100px) {
  .overview-section,
  .overview-grid,
  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .home-page {
    padding: 78px 18px 44px;
  }

  .hero-copy,
  .story-panel,
  .process-card {
    padding: 24px;
  }

  .title-main {
    font-size: 48px;
  }

  .title-sub {
    font-size: 40px;
  }

  .hero-actions,
  .section-head {
    flex-direction: column;
    align-items: stretch;
  }

  .mode-select {
    width: 100%;
  }
}
</style>
