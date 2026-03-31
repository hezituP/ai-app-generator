import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue') },
    {
      path: '/my-apps',
      name: 'my-apps',
      component: () => import('@/views/MyAppsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/app/:id',
      name: 'app-editor',
      component: () => import('@/views/AppEditorView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/app/:id/edit',
      name: 'app-edit',
      component: () => import('@/views/AppEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/apps',
      name: 'admin-apps',
      component: () => import('@/views/AdminAppsView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  if (!userStore.loginUser && !userStore.loading) await userStore.fetchLoginUser()
  if (to.meta.requiresAuth && !userStore.loginUser) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.meta.requiresAdmin && !userStore.isAdmin()) {
    next({ path: '/' })
  } else {
    next()
  }
})

export default router
