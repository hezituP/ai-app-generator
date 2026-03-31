<template>
  <div class="my-apps-page">
    <div class="page-header">
      <div class="page-header-inner">
        <div><h1 class="page-title">我的应用</h1><p class="page-sub">管理和创建你的 AI 应用</p></div>
        <a-button type="primary" class="btn-create" @click="showCreateModal=true"><PlusOutlined/> 创建新应用</a-button>
      </div>
    </div>
    <div class="apps-container">
      <div v-if="loading" class="loading-wrap"><a-spin size="large"/></div>
      <div v-else-if="apps.length>0" class="apps-grid">
        <div v-for="app in apps" :key="app.id" class="app-card">
          <div class="app-cover" @click="goToEditor(app)">
            <img v-if="app.cover" :src="app.cover" :alt="app.appName"/>
            <div v-else class="app-cover-placeholder"><span class="placeholder-icon">{{ app.appName?.charAt(0)||'A' }}</span></div>
            <div class="app-type-badge">{{ formatCodeGenType(app.codeGenType) }}</div>
            <div class="app-hover-overlay"><a-button type="primary" size="small">打开编辑器</a-button></div>
          </div>
          <div class="app-info">
            <div class="app-info-top">
              <h3 class="app-name">{{ app.appName }}</h3>
              <a-dropdown placement="bottomRight">
                <a-button type="text" class="more-btn"><EllipsisOutlined/></a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="rename" @click="openRenameModal(app)"><EditOutlined/> 重命名</a-menu-item>
                    <a-menu-item key="dl" @click="handleDownload(app)"><DownloadOutlined/> 下载代码</a-menu-item>
                    <a-menu-divider/>
                    <a-menu-item key="del" @click="handleDelete(app)" danger><DeleteOutlined/> 删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <p class="app-prompt">{{ app.initPrompt||'暂无描述' }}</p>
            <div class="app-footer">
              <span class="app-time">{{ formatTime(app.editTime||app.createTime) }}</span>
              <div class="app-actions">
                <a-button size="small" class="btn-deploy" @click="handleDeploy(app)" :loading="deployingId===app.id"><RocketOutlined/> 部署</a-button>
                <a-button size="small" type="primary" @click="goToEditor(app)">编辑</a-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon-wrap"><AppstoreOutlined/></div>
        <h3>还没有应用</h3><p>创建你的第一个 AI 应用吧</p>
        <a-button type="primary" class="btn-create-empty" @click="showCreateModal=true"><PlusOutlined/> 立即创建</a-button>
      </div>
      <div class="pagination-wrap" v-if="total>pageSize">
        <a-pagination v-model:current="pageNum" :total="total" :page-size="pageSize" @change="fetchMyApps" :show-size-changer="false"/>
      </div>
    </div>
    <a-modal v-model:open="showCreateModal" title="创建新应用" :footer="null" class="dark-modal" width="520px">
      <a-form :model="createForm" @finish="handleCreate" layout="vertical">
        <a-form-item label="应用名称" name="appName" :rules="[{required:true,message:'请输入应用名称'}]">
          <a-input v-model:value="createForm.appName" placeholder="例如：个人博客..." class="dark-input"/>
        </a-form-item>
        <a-form-item label="代码类型" name="codeGenType">
          <div class="type-grid">
            <div v-for="t in codeGenTypes" :key="t.value" class="type-card" :class="{active:createForm.codeGenType===t.value}" @click="createForm.codeGenType=t.value">
              <div class="type-icon">{{ t.icon }}</div><div class="type-label">{{ t.label }}</div><div class="type-desc">{{ t.desc }}</div>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="初始描述" name="initPrompt">
          <a-textarea v-model:value="createForm.initPrompt" placeholder="描述你想要创建的应用..." :rows="3" class="dark-input"/>
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="showCreateModal=false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="creating">创建应用</a-button>
        </div>
      </a-form>
    </a-modal>
    <a-modal v-model:open="showRenameModal" title="重命名" :footer="null" class="dark-modal" width="400px">
      <a-form :model="renameForm" @finish="handleRename" layout="vertical">
        <a-form-item label="新名称" name="appName" :rules="[{required:true,message:'请输入名称'}]">
          <a-input v-model:value="renameForm.appName" class="dark-input"/>
        </a-form-item>
        <div class="modal-actions">
          <a-button @click="showRenameModal=false">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="renaming">保存</a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, EllipsisOutlined, EditOutlined, DeleteOutlined, DownloadOutlined, RocketOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import { addAppApi, updateAppApi, deleteAppApi, listMyAppVOByPageApi, deployAppApi, downloadAppCodeApi, type AppVO } from '@/api/app'
const router = useRouter()
const apps = ref<AppVO[]>([]); const loading = ref(false); const pageNum = ref(1); const pageSize = 12; const total = ref(0)
const showCreateModal = ref(false); const creating = ref(false); const showRenameModal = ref(false); const renaming = ref(false)
const deployingId = ref<string|null>(null); const currentApp = ref<AppVO|null>(null)
const codeGenTypes = [{value:'html',label:'HTML',icon:'🌐',desc:'单文件'},{value:'vue',label:'Vue',icon:'💚',desc:'Vue 3'},{value:'react',label:'React',icon:'⚛️',desc:'React'},{value:'multi_file',label:'多文件',icon:'📦',desc:'多文件'}]
const createForm = reactive({appName:'',codeGenType:'html',initPrompt:''})
const renameForm = reactive({appName:''})
async function fetchMyApps(){loading.value=true;try{const res=await listMyAppVOByPageApi({pageNum:pageNum.value,pageSize});if(res.data.code===0){apps.value=res.data.data?.records||[];total.value=res.data.data?.total||0}}catch(e){console.error(e)}finally{loading.value=false}}
async function handleCreate(){creating.value=true;try{const res=await addAppApi(createForm);if(res.data.code===0){message.success('创建成功！');showCreateModal.value=false;Object.assign(createForm,{appName:'',codeGenType:'html',initPrompt:''});router.push(`/app/${res.data.data}`)}else message.error(res.data.message||'创建失败')}catch{message.error('网络异常')}finally{creating.value=false}}
function openRenameModal(app:AppVO){currentApp.value=app;renameForm.appName=app.appName;showRenameModal.value=true}
async function handleRename(){if(!currentApp.value)return;renaming.value=true;try{const res=await updateAppApi({id:currentApp.value.id,appName:renameForm.appName});if(res.data.code===0){message.success('重命名成功');showRenameModal.value=false;fetchMyApps()}else message.error('失败')}catch{message.error('网络异常')}finally{renaming.value=false}}
function handleDelete(app:AppVO){Modal.confirm({title:'确认删除',content:`确定删除「${app.appName}」？`,okText:'删除',okType:'danger',cancelText:'取消',async onOk(){const res=await deleteAppApi(app.id);if(res.data.code===0){message.success('已删除');fetchMyApps()}else message.error('删除失败')}})}
async function handleDeploy(app:AppVO){deployingId.value=app.id;try{const res=await deployAppApi(app.id);if(res.data.code===0){message.success('部署成功！');const url=res.data.data;if(url)window.open(url,'_blank');fetchMyApps()}else message.error(res.data.message||'部署失败')}catch{message.error('网络异常')}finally{deployingId.value=null}}
async function handleDownload(app:AppVO){try{const res=await downloadAppCodeApi(app.id);const blob=new Blob([res.data]);const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`${app.appName}.zip`;a.click();URL.revokeObjectURL(url)}catch{message.error('下载失败')}}
function goToEditor(app:AppVO){router.push(`/app/${app.id}`)}
function formatCodeGenType(t:string){return({html:'HTML',vue:'Vue',react:'React',multi_file:'多文件'} as Record<string,string>)[t]||t}
function formatTime(t:string){if(!t)return'';return new Date(t).toLocaleDateString('zh-CN',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'})}
onMounted(()=>{fetchMyApps()})
</script>

<style scoped>
.my-apps-page{min-height:100vh;padding-top:58px;background:linear-gradient(120deg, #84fab0 0%, #8fd3f4 100%);}
.page-header{background:rgba(255,255,255,0.65);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);border-bottom:1px solid rgba(132,250,176,0.3);padding:28px 0 20px}
.page-header-inner{max-width:1240px;margin:0 auto;padding:0 28px;display:flex;align-items:center;justify-content:space-between}
.page-title{font-size:26px;font-weight:800;background:linear-gradient(120deg,#3ab0a0,#2d8a7a);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;letter-spacing:-0.5px;margin-bottom:3px}
.page-sub{font-size:13px;color:#9ca3af}
.btn-create{height:38px!important;padding:0 18px!important;border-radius:9px!important;font-weight:700!important;background:linear-gradient(135deg,#84fab0,#3ab0a0)!important;border:none!important;color:#1a3a2a!important;display:flex!important;align-items:center!important;gap:6px!important}
.apps-container{max-width:1240px;margin:0 auto;padding:28px}
.loading-wrap{display:flex;justify-content:center;padding:80px 0}
.apps-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:20px}
.app-card{background:rgba(255,255,255,0.75);border:1px solid rgba(132,250,176,0.25);border-radius:16px;overflow:hidden;transition:all 0.22s;box-shadow:0 2px 10px rgba(99,102,241,0.06);animation:cardEnter 0.45s cubic-bezier(0.34,1.2,0.64,1) both}
.app-card:nth-child(1){animation-delay:0s}.app-card:nth-child(2){animation-delay:.05s}.app-card:nth-child(3){animation-delay:.1s}.app-card:nth-child(4){animation-delay:.15s}.app-card:nth-child(5){animation-delay:.2s}.app-card:nth-child(6){animation-delay:.25s}.app-card:nth-child(7){animation-delay:.3s}.app-card:nth-child(8){animation-delay:.35s}
.app-card:hover{border-color:rgba(58,176,160,0.4);transform:translateY(-3px);box-shadow:0 8px 28px rgba(132,250,176,0.25)}
.app-cover{position:relative;height:148px;cursor:pointer;overflow:hidden;background:#f0f4ff}
.app-cover img{width:100%;height:100%;object-fit:cover;transition:transform 0.4s}
.app-card:hover .app-cover img{transform:scale(1.05)}
.app-cover-placeholder{width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,rgba(132,250,176,0.2),rgba(143,211,244,0.15))}
.placeholder-icon{font-size:44px;font-weight:800;color:rgba(58,176,160,0.3)}
.app-type-badge{position:absolute;top:9px;left:9px;padding:2px 9px;background:rgba(255,255,255,0.9);backdrop-filter:blur(8px);border:1px solid rgba(58,176,160,0.35);border-radius:20px;font-size:11px;font-weight:700;color:#1a6a4f}
.app-hover-overlay{position:absolute;inset:0;background:rgba(132,250,176,0.15);display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity 0.2s;backdrop-filter:blur(2px)}
.app-cover:hover .app-hover-overlay{opacity:1}
.app-info{padding:13px 15px}
.app-info-top{display:flex;align-items:center;justify-content:space-between;margin-bottom:5px}
.app-name{font-size:14px;font-weight:700;color:#1a3a2a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1}
.more-btn{color:#d1d5db!important;padding:0 4px!important;height:24px!important}
.more-btn:hover{color:#6b7280!important}
.app-prompt{font-size:12px;color:#9ca3af;line-height:1.55;margin-bottom:11px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-height:34px}
.app-footer{display:flex;align-items:center;justify-content:space-between}
.app-time{font-size:11px;color:#d1d5db}
.app-actions{display:flex;gap:6px}
.btn-deploy{border-radius:6px!important;font-size:12px!important;border:1px solid rgba(16,185,129,0.3)!important;color:#059669!important;background:transparent!important;display:flex!important;align-items:center!important;gap:4px!important}
.btn-deploy:hover{border-color:#059669!important;background:rgba(16,185,129,0.06)!important}
.empty-state{text-align:center;padding:100px 0;animation:fadeUp 0.5s ease}
.empty-icon-wrap{font-size:52px;color:rgba(58,176,160,0.4);margin-bottom:14px;animation:scaleBreath 3s ease-in-out infinite}
.empty-state h3{font-size:18px;font-weight:700;color:#6b7280;margin-bottom:6px}
.empty-state p{font-size:14px;color:#d1d5db;margin-bottom:22px}
.btn-create-empty{height:40px!important;padding:0 22px!important;border-radius:9px!important;font-weight:700!important;background:linear-gradient(135deg,#84fab0,#3ab0a0)!important;border:none!important;color:#1a3a2a!important}
.pagination-wrap{display:flex;justify-content:center;margin-top:36px}
.type-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:4px}
.type-card{padding:10px 6px;border:1px solid #e5e7eb;border-radius:10px;text-align:center;cursor:pointer;transition:all 0.18s;background:#fafafa}
.type-card:hover{border-color:#84fab0;background:rgba(132,250,176,0.1)}
.type-card.active{border-color:#3ab0a0;background:rgba(132,250,176,0.15)}
.type-icon{font-size:20px;margin-bottom:4px}
.type-label{font-size:12px;font-weight:700;color:#374151}
.type-desc{font-size:10px;color:#9ca3af;margin-top:2px}
.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:8px}
:deep(.dark-modal .ant-modal-content){background:white;border:1px solid rgba(99,102,241,0.1);border-radius:16px;box-shadow:0 16px 48px rgba(99,102,241,0.12)}
:deep(.dark-modal .ant-modal-header){background:transparent;border-bottom:1px solid #f3f4f6}
:deep(.dark-modal .ant-modal-title){color:#1a3a2a;font-weight:700}
:deep(.dark-modal .ant-form-item-label label){color:#374151!important;font-weight:600!important;font-size:13px!important}
:deep(.dark-input){border:1px solid #e5e7eb!important;border-radius:8px!important;background:#fafafa!important}
:deep(.dark-input:hover){border-color:#84fab0!important}
:deep(.dark-input:focus-within){border-color:#3ab0a0!important;box-shadow:0 0 0 3px rgba(132,250,176,0.2)!important}
:deep(.dark-input .ant-input),:deep(.dark-input textarea){color:#1e1b4b!important;background:transparent!important}
:deep(.ant-menu){background:white!important;border:1px solid rgba(99,102,241,0.1)!important;border-radius:10px!important;box-shadow:0 8px 24px rgba(99,102,241,0.1)!important}
:deep(.ant-menu-item){color:#374151!important}
:deep(.ant-menu-item:hover){background:rgba(132,250,176,0.15)!important;color:#1a6a4f!important}
</style>
