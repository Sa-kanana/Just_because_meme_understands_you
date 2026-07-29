<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      collapsible
      :collapsed="collapsed"
      @collapse="onCollapse"
      :width="220"
      breakpoint="lg"
    >
      <div class="brand">
        <span v-if="!collapsed">只因梗懂你 · 管理</span>
        <span v-else>梗</span>
      </div>
      <a-menu
        :selected-keys="[selectedKey]"
        @menu-item-click="onMenuClick"
      >
        <a-menu-item key="dashboard">概览</a-menu-item>
        <a-menu-item key="memes">梗管理</a-menu-item>
        <a-menu-item key="users">用户管理</a-menu-item>
        <a-menu-item key="home-images">轮播管理</a-menu-item>
        <a-menu-item key="tags">标签管理</a-menu-item>
        <a-menu-item key="sensitive-words">敏感词</a-menu-item>
        <a-menu-item key="knowledge">知识库</a-menu-item>
        <a-menu-item key="ops">AI 运维</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="admin-header">
        <div class="header-title">{{ pageTitle }}</div>
        <div class="header-right">
          <span class="nickname">{{ auth.user?.nickname || '管理员' }}</span>
          <a-button type="text" @click="onLogout">退出</a-button>
        </div>
      </a-layout-header>
      <a-layout-content class="admin-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { toastSuccess } from '@/utils/uiFeedback'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const selectedKey = computed(() => String(route.name || 'dashboard'))
const pageTitle = computed(() => route.meta?.title || '管理后台')

function onCollapse(v) {
  collapsed.value = v
}

function onMenuClick(key) {
  if (key === selectedKey.value) return
  router.push({ name: key })
}

async function onLogout() {
  await auth.logout()
  toastSuccess('已退出')
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.brand {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.admin-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid #e5e6eb;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname {
  color: #4e5969;
  font-size: 14px;
}
.admin-content {
  margin: 16px;
  padding: 16px;
  background: #fff;
  min-height: calc(100vh - 88px);
  border-radius: 8px;
}
</style>
