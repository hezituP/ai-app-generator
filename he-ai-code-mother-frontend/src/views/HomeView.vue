<template>
  <div class="home-page" :style="homePageStyle">
    <section class="hero" :class="{ visible: heroVisible }">
      <div class="hero-copy glass-pane glass-pane--top" :style="heroPaneStyle">
        <h1 class="hero-title">
          <span class="title-main">心之所向</span>
          <span class="title-sub">页之所成</span>
        </h1>

        <p class="hero-desc">
          输入想法，Agent 会把它生成为可继续编辑的前端工程。
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
      </div>
    </section>

    <section ref="overviewRef" class="overview-section" :class="{ visible: overviewVisible }">
      <div class="story-panel glass-pane glass-pane--middle" :style="storyPaneStyle">
        <div class="section-copy">
          <span class="eyebrow soft">创作节奏</span>
          <h2>更轻一点，也更专注一点</h2>
          <p>
            把注意力留给灵感本身，不让说明文字抢走画面。
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

      <aside class="process-card glass-pane glass-pane--side" :style="processPaneStyle">
        <div class="process-head">
          <span class="eyebrow soft">生成流程</span>
          <strong>从一句话到工程落地</strong>
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
        <article
          v-for="(app, index) in goodApps"
          :key="app.id"
          class="app-card"
          :style="{ animationDelay: `${index * 0.06}s` }"
          @click="goApp(app)"
        >
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
const scrollY = ref(0)

const wallpaperOptions = [
  { label: '壁纸 1', value: 'bg1', url: '/images/home-bg1.jpg' },
  { label: '壁纸 2', value: 'bg2', url: '/images/home-bg2.jpg' },
  { label: '壁纸 3', value: 'bg3', url: '/images/home-bg3.jpg' },
] as const

const features = [
  { icon: '花', title: '完整工程', desc: '直接生成可运行的 Vue3 + Vite 项目。' },
  { icon: '流', title: '流式返回', desc: '生成过程会持续展示，不用等待黑盒结果。' },
  { icon: '改', title: '继续修改', desc: '生成后还能围绕现有工程继续对话。' },
]

const processSteps = [
  { title: '输入想法', desc: '一句话描述你想做的页面。' },
  { title: '拆解结构', desc: 'Agent 自动整理页面与模块。' },
  { title: '生成工程', desc: '输出完整文件与代码。' },
  { title: '继续润色', desc: '再通过对话继续改。' },
]

const homePageStyle = computed(() => {
  const current = wallpaperOptions.find((item) => item.value === selectedWallpaper.value) || wallpaperOptions[0]
  return {
    '--home-bg-image': `url('${current.url}')`,
  }
})

const heroPaneStyle = computed(() => ({
  transform: `translate3d(0, ${Math.min(scrollY.value * 0.12, 32)}px, 0)`,
}))

const storyPaneStyle = computed(() => ({
  transform: `translate3d(0, ${Math.min(scrollY.value * 0.08, 24)}px, 0)`,
}))

const processPaneStyle = computed(() => ({
  transform: `translate3d(0, ${Math.max(-scrollY.value * 0.07, -24)}px, 0)`,
}))

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
    window.localStorage.setItem('pendingAppPrompt', prompt)
    const res = await addAppApi({
      appName: prompt.slice(0, 16),
      codeGenType: codeGenType.value,
      initPrompt: prompt,
    })
    if (res.data.code === 0) {
      await router.push(`/app/${res.data.data}?prompt=${encodeURIComponent(prompt)}`)
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

function handleScroll() {
  scrollY.value = window.scrollY || 0
}

onMounted(async () => {
  const savedWallpaper = window.localStorage.getItem('homeWallpaper')
  if (savedWallpaper === 'bg1' || savedWallpaper === 'bg2' || savedWallpaper === 'bg3') {
    selectedWallpaper.value = savedWallpaper
  }
  heroVisible.value = true
  await fetchApps()
  observer = new IntersectionObserver((entries) => {
    const entry = entries[0]
    if (entry?.isIntersecting) {
      overviewVisible.value = true
    }
  }, { threshold: 0.2 })
  if (overviewRef.value) {
    observer.observe(overviewRef.value)
  }
  window.addEventListener('scroll', handleScroll, { passive: true })
  handleScroll()
})

watch(selectedWallpaper, (value) => {
  window.localStorage.setItem('homeWallpaper', value)
})

onUnmounted(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 92px 24px 56px;
  background-image: var(--home-bg-image);
  background-position: center top;
  background-repeat: no-repeat;
  background-size: cover;
}

.hero,
.overview-section,
.apps-section {
  max-width: 1240px;
  margin: 0 auto;
}

.hero {
  display: flex;
  min-height: 72vh;
  align-items: center;
  opacity: 0;
  transform: translateY(20px);
  transition: opacity 0.75s ease, transform 0.75s ease;
}

.hero.visible,
.overview-section.visible {
  opacity: 1;
  transform: translateY(0);
}

.glass-pane {
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(221, 234, 255, 0.12));
  box-shadow: 0 28px 80px rgba(32, 54, 92, 0.16);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  will-change: transform;
  transition: transform 0.18s ease-out, box-shadow 0.3s ease;
}

.glass-pane--middle,
.glass-pane--side {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1), rgba(221, 234, 255, 0.05));
  border-color: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.hero-copy {
  width: min(920px, 100%);
  padding: 42px 42px 34px;
  border-radius: 34px;
}

.hero-title {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-family: 'STSong', 'Songti SC', 'Noto Serif SC', serif;
  line-height: 0.96;
}

.title-main,
.title-sub {
  font-size: clamp(48px, 7vw, 84px);
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #eef7ff;
  text-shadow: 0 6px 30px rgba(70, 117, 196, 0.2), 0 2px 8px rgba(255, 255, 255, 0.35);
}

.hero-desc {
  max-width: 820px;
  margin: 22px 0 0;
  color: rgba(41, 54, 84, 0.92);
  font-size: 29px;
  line-height: 1.9;
  text-shadow: 0 1px 6px rgba(255, 255, 255, 0.22);
}

.hero-form {
  margin-top: 28px;
  padding: 16px;
  border-radius: 28px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.18), rgba(211, 227, 255, 0.08));
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.prompt-box {
  display: block;
}

:deep(.prompt-box .ant-input),
:deep(.prompt-box.ant-input),
:deep(.prompt-box textarea.ant-input) {
  border: 1px solid rgba(255, 255, 255, 0.36) !important;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.22), rgba(226, 238, 255, 0.14)) !important;
  color: #324261 !important;
  border-radius: 22px !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.24), 0 12px 30px rgba(51, 76, 115, 0.08) !important;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

:deep(.prompt-box textarea.ant-input) {
  min-height: 152px;
  padding: 18px 20px;
  font-size: 16px;
  line-height: 1.9;
}

:deep(.prompt-box textarea.ant-input::placeholder) {
  color: rgba(92, 108, 139, 0.5);
}

.hero-actions {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-top: 16px;
}

.mode-select {
  width: 220px;
}

:deep(.mode-select .ant-select-selector) {
  height: 50px !important;
  border-radius: 18px !important;
  border: 1px solid rgba(255, 255, 255, 0.36) !important;
  background: rgba(255, 255, 255, 0.18) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2) !important;
}

:deep(.mode-select .ant-select-selection-item),
:deep(.mode-select .ant-select-selection-placeholder) {
  min-height: 48px;
  line-height: 48px !important;
  color: #334364 !important;
  display: flex;
  align-items: center;
}

.create-btn {
  min-width: 156px;
  height: 50px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, #5ca8ff 0%, #7bb8ff 45%, #cde9ff 100%) !important;
  box-shadow: 0 18px 32px rgba(70, 133, 214, 0.28);
  color: #173b6d;
  font-weight: 700;
}

.wallpaper-switcher {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
}

.switcher-label,
.eyebrow {
  color: rgba(61, 79, 114, 0.86);
  font-size: 13px;
  letter-spacing: 0.18em;
}

.wallpaper-btn {
  min-width: 88px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.36);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: #36507a;
  cursor: pointer;
  transition: all 0.22s ease;
}

.wallpaper-btn.active,
.wallpaper-btn:hover {
  background: rgba(255, 255, 255, 0.34);
  box-shadow: 0 10px 20px rgba(74, 101, 143, 0.16);
}

.overview-section {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 24px;
  margin-top: 12px;
  opacity: 0;
  transform: translateY(24px);
  transition: opacity 0.7s ease, transform 0.7s ease;
}

.story-panel,
.process-card {
  border-radius: 30px;
  padding: 30px;
}

.section-copy h2,
.section-head h2 {
  margin: 10px 0 12px;
  color: #f6fbff;
  font-size: 30px;
  line-height: 1.3;
  text-shadow: 0 4px 20px rgba(69, 94, 137, 0.16);
}

.section-copy p,
.process-item p {
  color: rgba(49, 61, 89, 0.9);
  line-height: 1.9;
  text-shadow: 0 1px 6px rgba(255, 255, 255, 0.18);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.feature-card,
.app-card {
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.feature-card {
  padding: 20px;
  border-radius: 24px;
}

.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.3);
  color: #446394;
  font-weight: 700;
}

.feature-card h3 {
  color: #f5fbff;
}

.process-head strong,
.process-item h4 {
  color: #2f4366;
}

.feature-card p {
  color: rgba(50, 63, 92, 0.9);
  line-height: 1.8;
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}

.process-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.process-index {
  width: 42px;
  height: 42px;
  border-radius: 15px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.32);
  color: #2f4366;
  font-weight: 700;
}

.apps-section {
  margin-top: 34px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.refresh-btn {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.4);
  color: #35517f;
}

.loading-wrap {
  min-height: 220px;
  display: grid;
  place-items: center;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.app-card {
  overflow: hidden;
  border-radius: 26px;
  cursor: pointer;
  box-shadow: 0 20px 48px rgba(37, 56, 89, 0.14);
}

.app-cover {
  height: 180px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.18);
}

.app-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-cover.placeholder {
  display: grid;
  place-items: center;
  font-size: 52px;
  color: rgba(255, 255, 255, 0.88);
}

.app-body {
  padding: 18px;
}

.app-row,
.app-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.app-row h3,
.app-footer span,
.app-body p {
  color: #324766;
}

.type-tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.34);
  color: #456491;
  font-size: 12px;
}

@media (max-width: 960px) {
  .home-page {
    padding: 84px 16px 44px;
  }

  .hero-copy,
  .story-panel,
  .process-card {
    padding: 24px;
    border-radius: 24px;
  }

  .overview-section {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions,
  .section-head {
    flex-direction: column;
    align-items: stretch;
  }

  .mode-select,
  .create-btn {
    width: 100%;
  }

  .hero-desc {
    font-size: 18px;
  }

  .title-main,
  .title-sub {
    font-size: clamp(40px, 12vw, 60px);
  }
}
</style>
