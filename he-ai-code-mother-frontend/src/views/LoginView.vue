<template>
  <div class="auth-page">
    <div class="auth-left">
      <div class="auth-left-content">
        <router-link to="/" class="auth-logo">
          <svg width="28" height="28" viewBox="0 0 26 26" fill="none">
            <rect width="26" height="26" rx="7" fill="url(#alg)"/>
            <path d="M7 13l4.5 4.5L19 8" stroke="white" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
            <defs><linearGradient id="alg" x1="0" y1="0" x2="26" y2="26"><stop offset="0%" stop-color="#4facfe"/><stop offset="100%" stop-color="#00f2fe"/></linearGradient></defs>
          </svg>
          <span class="auth-brand">HeAI</span>
        </router-link>
        <div class="auth-left-body">
          <h2 class="auth-slogan">欢迎回来<br/><span style="opacity:0.78">继续你的创作</span></h2>
          <ul class="auth-features">
            <li v-for="f in leftFeatures" :key="f"><CheckCircleOutlined class="check-icon"/>{{ f }}</li>
          </ul>
        </div>
      </div>
    </div>
    <div class="auth-right">
      <div class="auth-box">
        <h1 class="auth-title">欢迎回来</h1>
        <p class="auth-sub">登录你的账号以继续</p>
        <a-form :model="form" @finish="handleLogin" layout="vertical" class="auth-form">
          <a-form-item name="userAccount" :rules="[{required:true,message:'请输入账号'}]">
            <label class="field-label">账号</label>
            <a-input v-model:value="form.userAccount" placeholder="请输入账号" size="large" class="field-input">
              <template #prefix><UserOutlined class="fi"/></template>
            </a-input>
          </a-form-item>
          <a-form-item name="userPassword" :rules="[{required:true,message:'请输入密码'}]">
            <label class="field-label">密码</label>
            <a-input-password v-model:value="form.userPassword" placeholder="请输入密码" size="large" class="field-input">
              <template #prefix><LockOutlined class="fi"/></template>
            </a-input-password>
          </a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="submit-btn">登录</a-button>
        </a-form>
        <div class="auth-switch">还没有账号？<router-link to="/register" class="switch-link">免费注册</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { userLoginApi } from '@/api/user'
import { useUserStore } from '@/stores/userStore'
const router = useRouter(); const route = useRoute(); const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ userAccount: '', userPassword: '' })
const leftFeatures = ['AI 自动生成前端代码', '一键部署，即时分享', '支持 HTML / Vue / React']
async function handleLogin() {
  loading.value = true
  try {
    const res = await userLoginApi(form)
    if (res.data.code === 0) {
      userStore.setLoginUser(res.data.data)
      message.success('登录成功！')
      router.push((route.query.redirect as string) || '/')
    } else { message.error(res.data.message || '登录失败') }
  } catch { message.error('网络异常') }
  finally { loading.value = false }
}
</script>

<style scoped>
.auth-page{min-height:100vh;display:flex}
.auth-left{width:420px;flex-shrink:0;background:linear-gradient(to right, #4facfe 0%, #00f2fe 100%);display:flex;align-items:stretch;position:relative;overflow:hidden}
.auth-left::before{content:'';position:absolute;inset:0;background-image:radial-gradient(circle at 20% 80%,rgba(255,255,255,0.12) 0%,transparent 50%),radial-gradient(circle at 80% 20%,rgba(255,255,255,0.08) 0%,transparent 50%)}
.auth-left-content{position:relative;z-index:1;padding:40px;display:flex;flex-direction:column;width:100%}
.auth-logo{display:flex;align-items:center;gap:9px;text-decoration:none}
.auth-brand{font-size:20px;font-weight:800;color:white;letter-spacing:-0.5px}
.auth-left-body{flex:1;display:flex;flex-direction:column;justify-content:center;padding-bottom:40px}
.auth-slogan{font-size:32px;font-weight:800;color:white;line-height:1.2;letter-spacing:-1px;margin-bottom:28px}
.auth-features{list-style:none;display:flex;flex-direction:column;gap:14px}
.auth-features li{display:flex;align-items:center;gap:10px;color:rgba(255,255,255,0.88);font-size:15px;font-weight:500}
.check-icon{color:#cffafe;font-size:16px;flex-shrink:0}
.auth-right{flex:1;display:flex;align-items:center;justify-content:center;background:linear-gradient(120deg, #84fab0 0%, #8fd3f4 100%);padding:40px 24px}
.auth-box{width:100%;max-width:400px;background:rgba(255,255,255,0.82);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);border-radius:20px;padding:40px 36px;box-shadow:0 8px 40px rgba(79,172,254,0.18);border:1px solid rgba(255,255,255,0.7);animation:boxIn 0.45s cubic-bezier(0.34,1.4,0.64,1)}
@keyframes boxIn{from{opacity:0;transform:scale(0.96) translateY(12px)}to{opacity:1;transform:scale(1) translateY(0)}}
.auth-title{font-size:26px;font-weight:800;color:#0a4a7a;margin-bottom:4px;letter-spacing:-0.5px}
.auth-sub{font-size:14px;color:#9ca3af;margin-bottom:28px}
.auth-form{display:flex;flex-direction:column}
.field-label{display:block;font-size:13px;font-weight:600;color:#1a5a7a;margin-bottom:6px}
:deep(.field-input){border:1px solid rgba(79,172,254,0.25)!important;border-radius:9px!important;background:rgba(255,255,255,0.7)!important;transition:all 0.18s!important}
:deep(.field-input:hover){border-color:#4facfe!important}
:deep(.field-input:focus-within){border-color:#00b4d8!important;box-shadow:0 0 0 3px rgba(79,172,254,0.2)!important;background:rgba(255,255,255,0.95)!important}
:deep(.field-input .ant-input){background:transparent!important;color:#0a3a5a!important;font-size:14px!important}
.fi{color:#d1d5db;font-size:14px}
.submit-btn{height:46px!important;border-radius:10px!important;font-size:15px!important;font-weight:700!important;margin-top:6px;background:linear-gradient(to right,#4facfe,#00f2fe)!important;border:none!important;color:#0a3a5a!important;box-shadow:0 4px 16px rgba(79,172,254,0.4)!important}
.auth-switch{text-align:center;margin-top:20px;font-size:13px;color:#9ca3af}
.switch-link{color:#0099cc;font-weight:700;margin-left:4px}
.switch-link:hover{color:#4facfe}
</style>
