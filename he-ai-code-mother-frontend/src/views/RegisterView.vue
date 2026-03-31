<template>
  <div class="auth-page">
    <div class="auth-left" style="background:linear-gradient(145deg,#059669 0%,#0ea5e9 100%)">
      <div class="auth-left-content">
        <router-link to="/" class="auth-logo">
          <span class="auth-brand">HeAI</span>
        </router-link>
        <div class="auth-left-body">
          <h2 class="auth-slogan">加入我们<br/><span style="opacity:0.78">开始你的 AI 之旅</span></h2>
          <ul class="auth-features">
            <li v-for="f in leftFeatures" :key="f"><CheckCircleOutlined class="check-icon"/>{{ f }}</li>
          </ul>
        </div>
      </div>
    </div>
    <div class="auth-right">
      <div class="auth-box">
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-sub">填写以下信息完成注册</p>
        <a-form :model="form" @finish="handleRegister" layout="vertical">
          <a-form-item name="userAccount" :rules="[{required:true,min:4,message:'账号至少4位'}]">
            <label class="field-label">账号</label>
            <a-input v-model:value="form.userAccount" placeholder="至少4位" size="large" class="field-input"><template #prefix><UserOutlined class="fi"/></template></a-input>
          </a-form-item>
          <a-form-item name="userPassword" :rules="[{required:true,min:8,message:'密码至少8位'}]">
            <label class="field-label">密码</label>
            <a-input-password v-model:value="form.userPassword" placeholder="至少8位" size="large" class="field-input"><template #prefix><LockOutlined class="fi"/></template></a-input-password>
          </a-form-item>
          <a-form-item name="checkPassword" :rules="[{required:true,validator:checkPwd}]">
            <label class="field-label">确认密码</label>
            <a-input-password v-model:value="form.checkPassword" placeholder="再次输入" size="large" class="field-input"><template #prefix><SafetyOutlined class="fi"/></template></a-input-password>
          </a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="submit-btn green-btn">立即注册</a-button>
        </a-form>
        <div class="auth-switch">已有账号？<router-link to="/login" class="switch-link">去登录</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, SafetyOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { userRegisterApi } from '@/api/user'
const router = useRouter()
const loading = ref(false)
const form = reactive({ userAccount: '', userPassword: '', checkPassword: '' })
const leftFeatures = ['永久免费注册', 'AI 自动生成代码', '一键部署分享']
function checkPwd(_: unknown, value: string) {
  if (!value) return Promise.reject('请确认密码')
  if (value !== form.userPassword) return Promise.reject('两次密码不一致')
  return Promise.resolve()
}
async function handleRegister() {
  loading.value = true
  try {
    const res = await userRegisterApi(form)
    if (res.data.code === 0) { message.success('注册成功！请登录'); router.push('/login') }
    else message.error(res.data.message || '注册失败')
  } catch { message.error('网络异常') }
  finally { loading.value = false }
}
</script>

<style scoped>
.auth-page{min-height:100vh;display:flex}
.auth-left{width:420px;flex-shrink:0;display:flex;align-items:stretch;position:relative;overflow:hidden}
.auth-left::before{content:'';position:absolute;inset:0;background-image:radial-gradient(circle at 20% 80%,rgba(255,255,255,0.12) 0%,transparent 50%),radial-gradient(circle at 80% 20%,rgba(255,255,255,0.08) 0%,transparent 50%)}
.auth-left-content{position:relative;z-index:1;padding:40px;display:flex;flex-direction:column;width:100%}
.auth-logo{display:flex;align-items:center;text-decoration:none}
.auth-brand{font-size:20px;font-weight:800;color:white;letter-spacing:-0.5px}
.auth-left-body{flex:1;display:flex;flex-direction:column;justify-content:center;padding-bottom:40px}
.auth-slogan{font-size:32px;font-weight:800;color:white;line-height:1.2;letter-spacing:-1px;margin-bottom:28px}
.auth-features{list-style:none;display:flex;flex-direction:column;gap:14px}
.auth-features li{display:flex;align-items:center;gap:10px;color:rgba(255,255,255,0.88);font-size:15px;font-weight:500}
.check-icon{color:#a7f3d0;font-size:16px;flex-shrink:0}
.auth-right{flex:1;display:flex;align-items:center;justify-content:center;background:#f8fafc;padding:40px 24px}
.auth-box{width:100%;max-width:400px;background:white;border-radius:20px;padding:40px 36px;box-shadow:0 4px 32px rgba(5,150,105,0.08);border:1px solid rgba(5,150,105,0.1);animation:boxIn 0.45s cubic-bezier(0.34,1.4,0.64,1)}
@keyframes boxIn{from{opacity:0;transform:scale(0.96) translateY(12px)}to{opacity:1;transform:scale(1) translateY(0)}}
.auth-title{font-size:26px;font-weight:800;color:#1e1b4b;margin-bottom:4px}
.auth-sub{font-size:14px;color:#9ca3af;margin-bottom:28px}
.field-label{display:block;font-size:13px;font-weight:600;color:#374151;margin-bottom:6px}
:deep(.field-input){border:1px solid #e5e7eb!important;border-radius:9px!important;background:#fafafa!important;transition:all 0.18s!important}
:deep(.field-input:hover){border-color:#6ee7b7!important}
:deep(.field-input:focus-within){border-color:#059669!important;box-shadow:0 0 0 3px rgba(5,150,105,0.12)!important;background:white!important}
:deep(.field-input .ant-input){background:transparent!important;color:#1e1b4b!important}
.fi{color:#d1d5db;font-size:14px}
.submit-btn{height:46px!important;border-radius:10px!important;font-size:15px!important;font-weight:700!important;margin-top:6px}
.green-btn{background:linear-gradient(135deg,#059669 0%,#0ea5e9 100%)!important;border:none!important;box-shadow:0 2px 8px rgba(5,150,105,0.25)!important}
.green-btn:hover{filter:brightness(1.07);box-shadow:0 4px 16px rgba(5,150,105,0.3)!important}
.auth-switch{text-align:center;margin-top:20px;font-size:13px;color:#9ca3af}
.switch-link{color:#059669;font-weight:700;margin-left:4px}
.switch-link:hover{color:#0ea5e9}
</style>
