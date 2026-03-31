import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUserApi } from '@/api/user'
import type { LoginUserVO } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const loginUser = ref<LoginUserVO | null>(null)
  const loading = ref(false)

  async function fetchLoginUser() {
    try {
      loading.value = true
      const res = await getLoginUserApi()
      if (res.data.code === 0) loginUser.value = res.data.data
      else loginUser.value = null
    } catch { loginUser.value = null }
    finally { loading.value = false }
  }

  function setLoginUser(user: LoginUserVO | null) { loginUser.value = user }
  function isAdmin() { return loginUser.value?.userRole === 'admin' }

  return { loginUser, loading, fetchLoginUser, setLoginUser, isAdmin }
})
