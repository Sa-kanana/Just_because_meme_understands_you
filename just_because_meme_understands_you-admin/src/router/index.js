import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'memes',
        name: 'memes',
        component: () => import('@/views/memes/MemeListView.vue'),
        meta: { title: '梗管理' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UserListView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'home-images',
        name: 'home-images',
        component: () => import('@/views/home-images/HomeImageListView.vue'),
        meta: { title: '轮播管理' },
      },
      {
        path: 'tags',
        name: 'tags',
        component: () => import('@/views/tags/TagListView.vue'),
        meta: { title: '标签管理' },
      },
      {
        path: 'sensitive-words',
        name: 'sensitive-words',
        component: () => import('@/views/sensitive-words/SensitiveWordListView.vue'),
        meta: { title: '敏感词' },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/knowledge/KnowledgeListView.vue'),
        meta: { title: '知识库' },
      },
      {
        path: 'ops',
        name: 'ops',
        component: () => import('@/views/ops/AiOpsView.vue'),
        meta: { title: 'AI 运维' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta && to.meta.public) {
    if (auth.isLoggedIn && auth.isAdmin && to.name === 'login') {
      return { name: 'dashboard' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.isAdmin) {
    auth.clearAuthState()
    return { name: 'login', query: { reason: 'forbidden' } }
  }
  return true
})

export default router
