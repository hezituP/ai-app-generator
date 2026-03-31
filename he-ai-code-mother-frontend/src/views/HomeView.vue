<template>
  <div class="home-page">
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title art-title reveal" :class="{visible:heroVisible}">
          <span class="art-word art-word-ai">
            <span v-for="(ch, i) in 'AI'" :key="i" class="art-char" :style="{animationDelay: (heroVisible ? i * 0.07 + 0.1 : 99) + 's'}">{{ ch }}</span>
          </span>
          <span class="art-space">&nbsp;</span>
          <span class="art-word art-word-main">
            <span v-for="(ch, i) in '应用生成平台'" :key="i" class="art-char art-char-gradient" :style="{animationDelay: (heroVisible ? (2 + i) * 0.07 + 0.1 : 99) + 's'}">{{ ch }}</span>
          </span>
          <span class="art-glow-ring"></span>
        </h1>
        <p class="hero-sub reveal" :class="{visible:heroVisible}" style="transition-delay:0.1s">
          一句话轻松创建网站应用
        </p>
        <div class="hero-input-wrap reveal" :class="{visible:heroVisible}" style="transition-delay:0.2s">
          <a-input
            v-model:value="inputPrompt"
            class="hero-input"
            placeholder="帮我创建个人博客网站"
            size="large"
            @keydown.enter="handleCreate"
          />
          <a-button type="primary" size="large" class="hero-create-btn" @click="handleCreate">
            <ThunderboltOutlined /> 立即生成
          </a-button>
        </div>
        <div class="quick-examples reveal" :class="{visible:heroVisible}" style="transition-delay:0.3s">
          <span
            v-for="q in quickExamples"
            :key="q.label"
            class="example-tag"
            @click="inputPrompt = q.prompt"
          >{{ q.label }}</span>
        </div>
      </div>
    </section>

    <section class="features" ref="featuresRef">
      <div class="features-inner">
        <div v-for="(f,i) in features" :key="f.title" class="feature-item reveal" :class="{visible:featuresVisible}" :style="{transitionDelay:`${i*0.12}s`}">
          <div class="feature-icon-wrap" :class="f.cls"><component :is="f.icon"/></div>
          <div class="feature-text"><h3>{{ f.title }}</h3><p>{{ f.desc }}</p></div>
        </div>
      </div>
    </section>

    <!-- IDE Code Generation Animation -->
    <section class="ide-section" ref="ideRef">
      <div class="ide-section-inner reveal" :class="{visible:ideVisible}">
        <div class="ide-label"><span class="ide-dot ide-dot-r"></span><span class="ide-dot ide-dot-y"></span><span class="ide-dot ide-dot-g"></span><span class="ide-filename">app.vue <span class="ide-badge">AI 生成中</span></span></div>
        <div class="ide-body">
          <div class="ide-editor">
            <div class="ide-lines">
              <span v-for="n in 18" :key="n" class="ide-ln">{{ n }}</span>
            </div>
            <div class="ide-code-area">
              <div v-for="(line, i) in codeLines" :key="i" class="ide-code-line">
                <span v-if="line.done" v-html="line.html"></span>
                <span v-else class="ide-typing-text">{{ line.display }}</span>
                <span v-if="i === codeLines.length - 1 && ideCursorVisible" class="ide-cursor"></span>
              </div>
            </div>
          </div>
          <div class="ide-preview">
            <div class="ide-preview-bar"><span class="ide-preview-dot"></span><span class="ide-preview-dot"></span><span class="ide-preview-dot"></span><span class="ide-preview-url">localhost:5173</span></div>
            <div class="ide-preview-content">
              <div class="prev-nav"><span class="prev-logo">✦ MyApp</span><span class="prev-links"><span>首页</span><span>关于</span><span>联系</span></span></div>
              <div class="prev-hero">
                <div class="prev-title">欢迎使用 <span class="prev-hi">智能应用</span></div>
                <div class="prev-sub">AI 一键生成，即刻上线</div>
                <div class="prev-btn">立即体验</div>
              </div>
              <div class="prev-cards">
                <div class="prev-card" v-for="k in 3" :key="k"><div class="prev-card-bar"></div><div class="prev-card-line"></div><div class="prev-card-line short"></div></div>
              </div>
              <div class="prev-scan"></div>
            </div>
          </div>
        </div>
        <div class="ide-footer">
          <span class="ide-status"><span class="ide-status-dot"></span> AI 正在生成代码…</span>
          <span class="ide-tokens">tokens: <b>1,842</b></span>
          <span class="ide-lang">Vue 3 · TypeScript</span>
        </div>
      </div>
    </section>

    <section class="good-section" id="good-apps" ref="appsRef">
      <div class="section-intro reveal" :class="{visible:appsVisible}">
        <span class="tag tag-green">精选推荐</span>
        <h2 class="section-h2">精选 AI 应用</h2>
        <div class="section-divider"></div>
        <p class="section-p">社区创作者构建的优质应用，即点即用</p>
      </div>
      <div v-if="loading" class="skeleton-grid">
        <div v-for="n in 6" :key="n" class="skeleton-card">
          <div class="skeleton" style="height:152px;border-radius:10px 10px 0 0"></div>
          <div style="padding:14px;display:flex;flex-direction:column;gap:8px">
            <div class="skeleton" style="height:14px;width:60%;border-radius:4px"></div>
            <div class="skeleton" style="height:10px;width:90%;border-radius:4px"></div>
          </div>
        </div>
      </div>
      <div class="apps-grid" v-else-if="goodApps.length">
        <div v-for="(app,i) in goodApps" :key="app.id" class="app-card card-enter" :style="{animationDelay:`${i*0.06}s`}" @click="goApp(app)">
          <div class="card-cover">
            <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
            <div v-else class="cover-placeholder"><span class="cover-letter">{{ app.appName?.charAt(0)||'A' }}</span></div>
            <span class="type-chip">{{ fmtType(app.codeGenType) }}</span>
            <div class="card-overlay"><span class="overlay-btn">立即体验 →</span></div>
          </div>
          <div class="card-body">
            <h4 class="card-title">{{ app.appName }}</h4>
            <p class="card-desc">{{ app.initPrompt||'暂无描述' }}</p>
            <div class="card-footer">
              <div class="card-author" v-if="app.userVO">
                <a-avatar :size="16" :src="app.userVO.userAvatar" :style="{background:'linear-gradient(135deg,#c471ed,#f64f59)',fontSize:'9px'}">{{ app.userVO.userName?.charAt(0)||'U' }}</a-avatar>
                <span>{{ app.userVO.userName||'匿名' }}</span>
              </div>
              <a-button type="primary" size="small" class="try-btn" @click.stop="goApp(app)">体验</a-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-hint"><AppstoreOutlined /><p>暂无精选应用</p></div>
      <div class="page-wrap" v-if="total > pageSize">
        <a-pagination v-model:current="pageNum" :total="total" :page-size="pageSize" @change="fetchApps" :show-size-changer="false"/>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ThunderboltOutlined, AppstoreOutlined, RocketOutlined, CodeOutlined, GlobalOutlined } from '@ant-design/icons-vue'
import { listGoodAppVOByPageApi, addAppApi, type AppVO } from '@/api/app'
import { useUserStore } from '@/stores/userStore'
import { message } from 'ant-design-vue'

const router = useRouter()
const userStore = useUserStore()
const goodApps = ref<AppVO[]>([])
const loading = ref(false)
const creating = ref(false)
const pageNum = ref(1)
const pageSize = 12
const total = ref(0)
const heroVisible = ref(false)
const featuresVisible = ref(false)
const appsVisible = ref(false)
const featuresRef = ref<HTMLElement|null>(null)
const appsRef = ref<HTMLElement|null>(null)
const ideRef = ref<HTMLElement|null>(null)
const ideVisible = ref(false)
const ideCursorVisible = ref(false)
const inputPrompt = ref('')
let observer: IntersectionObserver

const rawCodeLines = [
  { html: '<span class="ck">&lt;template&gt;</span>' },
  { html: '&nbsp;&nbsp;<span class="ck">&lt;div</span> <span class="ca">class</span>=<span class="cs">"hero"</span><span class="ck">&gt;</span>' },
  { html: '&nbsp;&nbsp;&nbsp;&nbsp;<span class="ck">&lt;h1</span> <span class="ca">class</span>=<span class="cs">"title"</span><span class="ck">&gt;</span><span class="ct">欢迎使用智能应用</span><span class="ck">&lt;/h1&gt;</span>' },
  { html: '&nbsp;&nbsp;&nbsp;&nbsp;<span class="ck">&lt;p</span> <span class="ca">class</span>=<span class="cs">"sub"</span><span class="ck">&gt;</span><span class="ct">AI 一键生成，即刻上线</span><span class="ck">&lt;/p&gt;</span>' },
  { html: '&nbsp;&nbsp;&nbsp;&nbsp;<span class="ck">&lt;button</span> <span class="ca">@click</span>=<span class="cs">"go"</span><span class="ck">&gt;</span><span class="ct">立即体验</span><span class="ck">&lt;/button&gt;</span>' },
  { html: '&nbsp;&nbsp;<span class="ck">&lt;/div&gt;</span>' },
  { html: '<span class="ck">&lt;/template&gt;</span>' },
  { html: '' },
  { html: '<span class="ck">&lt;script</span> <span class="ca">setup</span> <span class="ca">lang</span>=<span class="cs">"ts"</span><span class="ck">&gt;</span>' },
  { html: '<span class="cw">import</span> { <span class="cv">ref</span> } <span class="cw">from</span> <span class="cs">&#39;vue&#39;</span>' },
  { html: '' },
  { html: '<span class="cw">const</span> <span class="cv">title</span> = <span class="cf">ref</span>(<span class="cs">&#39;欢迎使用智能应用&#39;</span>)' },
  { html: '<span class="cw">const</span> <span class="cv">go</span> = () =&gt; {' },
  { html: '&nbsp;&nbsp;<span class="cv">router</span>.<span class="cf">push</span>(<span class="cs">&#39;/app&#39;</span>)' },
  { html: '}' },
  { html: '<span class="ck">&lt;/script&gt;</span>' },
  { html: '' },
  { html: '<span class="cm">/* AI Generated — 100% ✦ */</span>' },
]
const codeLines = ref<{html:string, visible: boolean}[]>([])

const quickExamples = [
  {
    label: '个人博客',
    prompt: '帮我创建一个现代风格的个人博客网站，包含首页、文章列表页、文章详情页和关于我页面。首页展示最新文章摘要和个人简介，支持暗色模式切换，整体风格简洁优雅，使用渐变色作为主题色。'
  },
  {
    label: '产品落地页',
    prompt: '帮我设计一个 SaaS 产品落地页，产品名称是智能简历助手，主要功能包括：AI 生成简历、一键导出 PDF、多种模板选择。页面需要包含 Hero 区域、功能介绍、用户评价、定价方案和 CTA 按钮，配色以深蓝和青色为主。'
  },
  {
    label: '作品集展示',
    prompt: '帮我制作一个设计师作品集网站，包含个人介绍、技能标签云、作品展示网格（支持悬停放大效果）、工作经历时间轴和联系方式表单。整体风格偏暗系，使用紫色和渐变色高亮，字体现代感强。'
  },
  {
    label: '餐厅官网',
    prompt: '帮我创建一个高档中餐厅官网，包含首页大图轮播、招牌菜品展示、关于我们故事介绍、在线预订表单和门店位置地图嵌入。整体风格古典优雅，以红色和金色为主题色，搭配毛笔字风格标题。'
  },
]

const features = [
  {icon:CodeOutlined,title:'AI 代码生成',desc:'描述需求，自动输出高质量前端代码',cls:'icon-pink'},
  {icon:RocketOutlined,title:'一键部署',desc:'生成完成即可部署，获得独立访问链接',cls:'icon-purple'},
  {icon:GlobalOutlined,title:'即时分享',desc:'部署后随时分享，无需额外配置',cls:'icon-peach'},
]

function setupObserver() {
  observer = new IntersectionObserver((entries)=> {
    entries.forEach(e=> {
      if (e.target===featuresRef.value && e.isIntersecting) featuresVisible.value = true
      if (e.target===appsRef.value && e.isIntersecting) appsVisible.value = true
      if (e.target===ideRef.value && e.isIntersecting) {
        ideVisible.value = true
        startIdeAnimation()
        observer.unobserve(e.target)
      }
    })
  }, {threshold:0.15})
  if (featuresRef.value) observer.observe(featuresRef.value)
  if (appsRef.value) observer.observe(appsRef.value)
  if (ideRef.value) observer.observe(ideRef.value)
}

let ideTimer: ReturnType<typeof setTimeout>
function startIdeAnimation() {
  codeLines.value = []
  ideCursorVisible.value = true
  const plainLines = [
    '<template>',
    '  <div class="hero">',
    '    <h1 class="title">欢迎使用智能应用</h1>',
    '    <p class="sub">AI 一键生成，即刻上线</p>',
    '    <button @click="go">立即体验</button>',
    '  </div>',
    '</template>',
    '',
    '<script setup lang="ts">',
    "import { ref } from 'vue'",
    '',
    "const title = ref('欢迎使用智能应用')",
    'const go = () => {',
    "  router.push('/app')",
    '}',
    '</' + 'script>',
    '',
    '/* AI Generated — 100% ✦ */',
  ]
  const highlighted = rawCodeLines.map(l => l.html)
  let globalCharIdx = 0
  let lineIdx = 0

  function typeNextChar() {
    if (lineIdx >= plainLines.length) {
      ideCursorVisible.value = false
      return
    }
    const line = plainLines[lineIdx]
    const currentLine = codeLines.value[lineIdx]
    const charPos = currentLine ? currentLine.typed : 0

    if (charPos >= line.length) {
      if (currentLine) currentLine.done = true
      lineIdx++
      if (lineIdx < plainLines.length) {
        codeLines.value.push({ html: highlighted[lineIdx] || '', typed: 0, done: false, display: '' })
      }
      globalCharIdx++
      const delay = plainLines[lineIdx - 1] === '' ? 40 : 30
      ideTimer = setTimeout(typeNextChar, delay)
      return
    }

    currentLine.typed = charPos + 1
    currentLine.display = line.substring(0, charPos + 1)
    globalCharIdx++
    const ch = line[charPos]
    const delay = ch === ' ' ? 18 : ch === '\n' ? 60 : Math.random() > 0.92 ? 80 : 28
    ideTimer = setTimeout(typeNextChar, delay)
  }

  codeLines.value.push({ html: highlighted[0], typed: 0, done: false, display: '' })
  ideTimer = setTimeout(typeNextChar, 300)
}

async function fetchApps() {
  loading.value = true
  try {
    const res = await listGoodAppVOByPageApi({pageNum:pageNum.value,pageSize})
    if (res.data.code===0) {goodApps.value=res.data.data?.records||[];total.value=res.data.data?.total||0}
  } finally { loading.value = false }
}

async function handleCreate() {
  const prompt = inputPrompt.value.trim()
  if (!prompt) { message.warning('请输入应用描述'); return }
  if (!userStore.loginUser) { router.push('/login'); return }
  creating.value = true
  try {
    const res = await addAppApi({ appName: prompt.substring(0,12), codeGenType: 'html', initPrompt: prompt })
    if (res.data.code === 0) {
      const appId = res.data.data
      router.push(`/app/${appId}`)
    } else {
      message.error(res.data.message || '创建失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    creating.value = false
  }
}

function goApp(app: AppVO) { router.push(`/app/${app.id}`) }
function fmtType(t: string) { return ({html:'HTML',vue:'Vue',react:'React',multi_file:'多文件'} as Record<string,string>)[t]||t }

onMounted(()=> {
  setTimeout(()=>{heroVisible.value=true},80)
  fetchApps()
  setTimeout(()=>setupObserver(),500)
})
onUnmounted(()=>{ observer?.disconnect(); if(ideTimer) clearTimeout(ideTimer) })

</script>
<style scoped>
.home-page { min-height:100vh; padding-top:58px; position:relative; overflow-x:hidden; background-image:linear-gradient(120deg,#84fab0 0%,#8fd3f4 100%); width:100%; }
.hero { position:relative; z-index:1; max-width:860px; margin:0 auto; padding:100px 28px 80px; display:flex; flex-direction:column; align-items:center; text-align:center; }
.hero-content { display:flex; flex-direction:column; align-items:center; gap:24px; width:100%; }
.hero-title { font-size:58px; font-weight:900; line-height:1.1; letter-spacing:-2.5px; color:#3b0764; margin:0; position:relative; display:inline-flex; align-items:baseline; flex-wrap:wrap; justify-content:center; gap:0; }
.art-title { font-family:'Noto Serif SC',serif; letter-spacing:0.02em; }
.art-word { display:inline-flex; align-items:baseline; }
.art-space { display:inline-block; width:0.28em; }
.art-char { display:inline-block; opacity:0; transform:translateY(36px) scale(0.65) rotate(-10deg); animation:artCharIn 0.6s cubic-bezier(0.34,1.56,0.64,1) forwards; color:#0a0a0a; text-shadow:3px 3px 0 rgba(0,0,0,0.18),0 6px 20px rgba(0,0,0,0.12); }
.art-char-gradient { color:#0a0a0a; -webkit-text-fill-color:#0a0a0a; animation:artCharIn 0.6s cubic-bezier(0.34,1.56,0.64,1) forwards; text-shadow:3px 3px 0 rgba(0,0,0,0.18),0 6px 20px rgba(0,0,0,0.12); filter:none; }
.art-word-ai .art-char { font-family:'Poppins',sans-serif; font-weight:900; letter-spacing:-3px; color:#0a0a0a; text-shadow:4px 4px 0 rgba(0,0,0,0.22),0 8px 24px rgba(0,0,0,0.15); -webkit-text-stroke:1.5px rgba(0,0,0,0.08); }
@keyframes artCharIn { 0%{opacity:0;transform:translateY(36px) scale(0.65) rotate(-10deg)} 55%{opacity:1} 78%{transform:translateY(-5px) scale(1.06) rotate(2deg)} 100%{opacity:1;transform:translateY(0) scale(1) rotate(0deg)} }
.art-glow-ring { position:absolute; inset:-10px -20px; border-radius:28px; background:radial-gradient(ellipse at 50% 65%,rgba(13,191,138,0.14) 0%,rgba(10,112,176,0.09) 45%,transparent 72%); animation:glowPulse 3.5s ease-in-out 1.2s infinite alternate; pointer-events:none; z-index:-1; }
@keyframes glowPulse { 0%{opacity:0.5;transform:scale(0.95)} 100%{opacity:1;transform:scale(1.05)} }
.hero-sub { font-size:20px; color:#7e4a8a; font-weight:500; margin:0; letter-spacing:0.2px; }
.hero-input-wrap { display:flex; width:100%; max-width:640px; gap:0; background:rgba(255,255,255,0.75); backdrop-filter:blur(20px); -webkit-backdrop-filter:blur(20px); border-radius:16px; box-shadow:0 8px 32px rgba(196,113,237,0.2),0 2px 8px rgba(196,113,237,0.1); overflow:hidden; }
:deep(.hero-input .ant-input) { background:transparent !important; border:none !important; box-shadow:none !important; font-size:15px !important; color:#1a3a2a !important; padding:14px 18px !important; height:52px !important; }
:deep(.hero-input .ant-input::placeholder) { color:#8fd3c4 !important; }
:deep(.hero-input.ant-input-affix-wrapper) { border:none !important; box-shadow:none !important; background:transparent !important; flex:1; }
:deep(.hero-input) { flex:1; border:none !important; box-shadow:none !important; background:transparent !important; }
.hero-create-btn { height:52px !important; padding:0 28px !important; border-radius:0 16px 16px 0 !important; font-size:15px !important; font-weight:700 !important; background:linear-gradient(135deg,#84fab0,#3ab0a0) !important; border:none !important; box-shadow:0 4px 16px rgba(132,250,176,0.5) !important; display:flex !important; align-items:center !important; gap:6px !important; white-space:nowrap; flex-shrink:0; }
.hero-create-btn:hover { box-shadow:0 8px 28px rgba(132,250,176,0.6) !important; transform:translateY(-1px); }
.quick-examples { display:flex; gap:10px; flex-wrap:wrap; justify-content:center; max-width:640px; }
.example-tag { padding:6px 16px; background:rgba(255,255,255,0.55); backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px); border:1px solid rgba(132,250,176,0.4); border-radius:40px; font-size:13px; font-weight:600; color:#2d6a4f; cursor:pointer; transition:all 0.18s; white-space:nowrap; }
.example-tag:hover { background:rgba(255,255,255,0.85); border-color:rgba(196,113,237,0.6); color:#c471ed; transform:translateY(-2px); box-shadow:0 4px 12px rgba(132,250,176,0.3); }
.features { position:relative; z-index:1; background:rgba(255,255,255,0.35); backdrop-filter:blur(20px); -webkit-backdrop-filter:blur(20px); border-top:1px solid rgba(255,255,255,0.5); border-bottom:1px solid rgba(255,255,255,0.5); padding:56px 28px; }
.features-inner { max-width:1240px; margin:0 auto; display:grid; grid-template-columns:repeat(3,1fr); gap:28px; }
.feature-item { display:flex; align-items:flex-start; gap:16px; padding:26px; border-radius:16px; background:rgba(255,255,255,0.5); border:1px solid rgba(255,255,255,0.7); transition:all 0.25s; cursor:default; box-shadow:0 2px 10px rgba(196,113,237,0.06); }
.feature-item:hover { background:rgba(255,255,255,0.85); border-color:rgba(196,113,237,0.25); box-shadow:0 8px 28px rgba(196,113,237,0.15); transform:translateY(-4px); }
.feature-icon-wrap { width:46px; height:46px; border-radius:13px; display:flex; align-items:center; justify-content:center; font-size:20px; flex-shrink:0; transition:transform 0.3s; box-shadow:0 2px 8px rgba(0,0,0,0.06); }
.feature-item:hover .feature-icon-wrap { transform:scale(1.12) rotate(-4deg); }
.icon-pink { background:linear-gradient(135deg,rgba(132,250,176,0.25),rgba(132,250,176,0.1)); color:#2d9a6a; }
.icon-purple { background:linear-gradient(135deg,rgba(143,211,244,0.3),rgba(143,211,244,0.12)); color:#1a7a9a; }
.icon-peach { background:linear-gradient(135deg,rgba(58,176,160,0.2),rgba(58,176,160,0.08)); color:#2d8a7a; }
.feature-text h3 { font-size:15px; font-weight:700; color:#1a3a2a; margin-bottom:5px; }
.feature-text p { font-size:13px; color:#2d6a4f; line-height:1.65; }
.good-section { position:relative; z-index:1; max-width:1240px; margin:0 auto; padding:72px 28px 88px; }
.section-intro { text-align:center; margin-bottom:48px; }
.tag { display:inline-block; padding:3px 12px; border-radius:20px; font-size:11px; font-weight:800; letter-spacing:0.5px; margin-bottom:10px; text-transform:uppercase; }
.tag-green { background:rgba(16,185,129,0.1); border:1px solid rgba(16,185,129,0.25); color:#059669; }
.section-divider { width:40px; height:3px; background:linear-gradient(90deg,#84fab0,#3ab0a0); border-radius:2px; margin:12px auto 0; }
.section-h2 { font-size:36px; font-weight:800; color:#1a3a2a; letter-spacing:-1px; margin:10px 0 0; }
.section-p { font-size:15px; color:#2d6a4f; margin-top:12px; }

.skeleton-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(270px,1fr)); gap:22px; }
.skeleton-card { background:rgba(255,255,255,0.5); border-radius:16px; overflow:hidden; border:1px solid rgba(255,255,255,0.7); }
.skeleton { background:linear-gradient(90deg,rgba(132,250,176,0.1) 25%,rgba(132,250,176,0.22) 50%,rgba(132,250,176,0.1) 75%); background-size:200% 100%; animation:shimmer 1.5s infinite; }
@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
.apps-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(270px,1fr)); gap:24px; }
.app-card { background:rgba(255,255,255,0.65); backdrop-filter:blur(16px); -webkit-backdrop-filter:blur(16px); border:1px solid rgba(255,255,255,0.85); border-radius:18px; overflow:hidden; cursor:pointer; transition:all 0.24s; box-shadow:0 4px 16px rgba(196,113,237,0.08); }
.app-card:hover { border-color:rgba(196,113,237,0.3); transform:translateY(-5px); box-shadow:0 16px 40px rgba(196,113,237,0.18); }
.card-cover { position:relative; height:152px; overflow:hidden; background:linear-gradient(135deg,#fad0c4,#ffd1ff); }
.card-cover img { width:100%; height:100%; object-fit:cover; transition:transform 0.4s; }
.app-card:hover .card-cover img { transform:scale(1.06); }
.cover-placeholder { width:100%; height:100%; display:flex; align-items:center; justify-content:center; }
.cover-letter { font-size:52px; font-weight:800; color:rgba(196,113,237,0.25); }
.type-chip { position:absolute; top:9px; right:9px; padding:2px 9px; background:rgba(255,255,255,0.88); backdrop-filter:blur(10px); border:1px solid rgba(58,176,160,0.35); border-radius:20px; font-size:11px; font-weight:700; color:#1a6a4f; }
.card-overlay { position:absolute; inset:0; background:linear-gradient(180deg,transparent 40%,rgba(196,113,237,0.12)); display:flex; align-items:center; justify-content:center; opacity:0; transition:opacity 0.24s; backdrop-filter:blur(2px); }
.app-card:hover .card-overlay { opacity:1; }
.overlay-btn { padding:7px 18px; background:rgba(255,255,255,0.94); border-radius:40px; font-size:13px; font-weight:700; color:#c471ed; box-shadow:0 4px 14px rgba(196,113,237,0.25); transform:translateY(6px); transition:transform 0.24s; }
.app-card:hover .overlay-btn { transform:translateY(0); }
.card-body { padding:14px 16px; }
.card-title { font-size:15px; font-weight:700; color:#1a3a2a; margin-bottom:5px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.card-desc { font-size:12px; color:#9a6aaa; line-height:1.55; margin-bottom:12px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; min-height:36px; }
.card-footer { display:flex; align-items:center; justify-content:space-between; }
.card-author { display:flex; align-items:center; gap:5px; font-size:12px; color:#9a6aaa; }
.try-btn { border-radius:6px !important; font-size:12px !important; height:26px !important; padding:0 11px !important; background:linear-gradient(135deg,#84fab0,#3ab0a0) !important; border:none !important; color:#1a3a2a !important; }
.empty-hint { text-align:center; padding:60px; color:#c4a0cc; font-size:15px; display:flex; flex-direction:column; align-items:center; gap:10px; }
.page-wrap { display:flex; justify-content:center; margin-top:36px; }
.reveal { opacity:0; transform:translateY(20px); transition:opacity 0.6s ease,transform 0.6s ease; }
.reveal.visible { opacity:1; transform:translateY(0); }
.card-enter { animation:cardIn 0.5s cubic-bezier(0.34,1.2,0.64,1) both; }
@keyframes cardIn { from{opacity:0;transform:translateY(18px) scale(0.97)} to{opacity:1;transform:translateY(0) scale(1)} }
@media(max-width:900px) {
  .hero { padding:60px 20px 48px; }
  .hero-title { font-size:38px; }
  .features-inner { grid-template-columns:1fr; }
  .hero-input-wrap { flex-direction:column; border-radius:16px; }
  .hero-create-btn { border-radius:0 0 16px 16px !important; width:100%; justify-content:center; }
}
/* ===== IDE Animation Section ===== */
.ide-section { position:relative; z-index:1; max-width:1100px; margin:0 auto; padding:64px 28px 40px; }
.ide-section-inner { background:#1e1e2e; border-radius:18px; overflow:hidden; box-shadow:0 0 0 1px rgba(0,0,0,0.4),0 24px 64px rgba(0,0,0,0.5),0 0 60px rgba(13,191,138,0.10); }
.ide-label { display:flex; align-items:center; gap:7px; padding:11px 16px; background:#181825; border-bottom:1px solid #313244; }
.ide-dot { width:12px; height:12px; border-radius:50%; display:inline-block; }
.ide-dot-r { background:#ff5f57; }
.ide-dot-y { background:#ffbd2e; }
.ide-dot-g { background:#28c840; }
.ide-filename { margin-left:6px; font-size:12px; color:#6c7086; font-family:'JetBrains Mono',monospace; display:flex; align-items:center; gap:8px; }
.ide-badge { display:inline-block; padding:1px 8px; border-radius:20px; background:linear-gradient(135deg,rgba(13,191,138,0.2),rgba(10,112,176,0.18)); border:1px solid rgba(13,191,138,0.5); color:#a6e3a1; font-size:10px; font-weight:700; letter-spacing:0.3px; animation:badgePulse 2s ease-in-out infinite; }
@keyframes badgePulse { 0%,100%{opacity:1;box-shadow:0 0 0 0 rgba(13,191,138,0.3)} 50%{opacity:0.85;box-shadow:0 0 0 5px rgba(13,191,138,0)} }
.ide-body { display:grid; grid-template-columns:1fr 1fr; min-height:340px; }
.ide-editor { display:flex; border-right:1px solid #313244; font-family:'JetBrains Mono',monospace; font-size:12.5px; line-height:1.75; overflow:hidden; background:#1e1e2e; }
.ide-lines { display:flex; flex-direction:column; padding:16px 0; min-width:36px; text-align:right; color:#45475a; font-size:11px; line-height:1.75; user-select:none; border-right:1px solid #313244; padding-right:10px; padding-left:8px; background:#181825; }
.ide-ln { display:block; }
.ide-code-area { flex:1; padding:16px 16px; overflow:hidden; display:flex; flex-direction:column; }
.ide-code-line { white-space:pre; min-height:1.75em; }
.ide-typing-text { color:#cdd6f4; }
:deep(.ck) { color:#89dceb; }
:deep(.ca) { color:#cba6f7; }
:deep(.cs) { color:#a6e3a1; }
:deep(.ct) { color:#cdd6f4; }
:deep(.cw) { color:#f38ba8; }
:deep(.cv) { color:#fab387; }
:deep(.cf) { color:#89b4fa; }
:deep(.cm) { color:#585b70; font-style:italic; }
.ide-cursor { display:inline-block; width:2px; height:1.1em; background:#a6e3a1; vertical-align:text-bottom; margin-left:1px; border-radius:1px; animation:cursorBlink 0.9s steps(1) infinite; }
@keyframes cursorBlink { 0%,100%{opacity:1} 50%{opacity:0} }
.ide-preview { display:flex; flex-direction:column; background:#181825; }
.ide-preview-bar { display:flex; align-items:center; gap:5px; padding:8px 12px; background:#181825; border-bottom:1px solid #313244; }
.ide-preview-dot { width:9px; height:9px; border-radius:50%; background:#45475a; }
.ide-preview-url { margin-left:6px; font-size:11px; color:#45475a; font-family:'JetBrains Mono',monospace; background:#11111b; border:1px solid #313244; border-radius:6px; padding:2px 10px; }
.ide-preview-content { flex:1; padding:14px; display:flex; flex-direction:column; gap:10px; position:relative; overflow:hidden; background:#1e1e2e; }
.prev-nav { display:flex; align-items:center; justify-content:space-between; padding:6px 10px; background:#181825; border-radius:8px; border:1px solid #313244; box-shadow:0 1px 3px rgba(0,0,0,0.3); }
.prev-logo { font-size:12px; font-weight:800; color:#a6e3a1; letter-spacing:-0.5px; }
.prev-links { display:flex; gap:10px; }
.prev-links span { font-size:10px; color:#6c7086; }
.prev-hero { background:linear-gradient(135deg,#1a2b22 0%,#1a1f2e 100%); border-radius:10px; padding:18px 16px; display:flex; flex-direction:column; gap:7px; border:1px solid #2a3d30; animation:previewFadeIn 0.8s ease 1.2s both; }
@keyframes previewFadeIn { from{opacity:0;transform:translateY(8px)} to{opacity:1;transform:translateY(0)} }
.prev-title { font-size:13px; font-weight:800; color:#cdd6f4; line-height:1.3; }
.prev-hi { color:#a6e3a1; }
.prev-sub { font-size:10px; color:#6c7086; }
.prev-btn { display:inline-block; margin-top:4px; padding:4px 12px; background:linear-gradient(135deg,#a6e3a1,#89b4fa); border-radius:6px; font-size:10px; font-weight:700; color:#1e1e2e; width:fit-content; box-shadow:0 2px 8px rgba(166,227,161,0.3); }
.prev-cards { display:grid; grid-template-columns:repeat(3,1fr); gap:7px; animation:previewFadeIn 0.8s ease 1.5s both; }
.prev-card { background:#181825; border-radius:7px; border:1px solid #313244; padding:8px; display:flex; flex-direction:column; gap:5px; box-shadow:0 1px 4px rgba(0,0,0,0.3); }
.prev-card-bar { height:32px; border-radius:5px; background:linear-gradient(135deg,#2a3d30,#1e2d40); }
.prev-card-line { height:7px; border-radius:3px; background:#313244; }
.prev-card-line.short { width:65%; }
.prev-scan { position:absolute; left:0; right:0; height:2px; background:linear-gradient(90deg,transparent,rgba(166,227,161,0.6),transparent); animation:scanLine 2.5s ease-in-out 0.5s infinite; pointer-events:none; }
@keyframes scanLine { 0%{top:0%;opacity:0} 5%{opacity:1} 95%{opacity:1} 100%{top:100%;opacity:0} }
.ide-footer { display:flex; align-items:center; gap:16px; padding:8px 16px; background:#181825; border-top:1px solid #313244; font-size:11px; font-family:'JetBrains Mono',monospace; color:#45475a; }
.ide-status { display:flex; align-items:center; gap:6px; color:#a6e3a1; }
.ide-status-dot { width:7px; height:7px; border-radius:50%; background:#a6e3a1; box-shadow:0 0 6px rgba(166,227,161,0.6); animation:badgePulse 1.5s ease-in-out infinite; }
.ide-tokens { margin-left:auto; }
.ide-tokens b { color:#cdd6f4; }
.ide-lang { color:#89b4fa; }
@media(max-width:900px) {
  .ide-body { grid-template-columns:1fr; }
  .ide-preview { display:none; }
}
</style>
