<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      collapsible
      :collapsed="collapsed"
      @collapse="onCollapse"
      :width="220"
      breakpoint="md"
    >
      <div class="brand" :class="{ 'brand--collapsed': collapsed }" title="只因“梗”懂你 · 管理端">
        <img class="brand-logo" src="/icon.svg" alt="" />
        <span v-if="!collapsed" class="brand-name">只因“梗”懂你</span>
        <span v-if="!collapsed" class="brand-context">管理端</span>
      </div>
      <a-menu
        :selected-keys="[selectedKey]"
        @menu-item-click="onMenuClick"
      >
        <a-menu-item v-for="item in navigationItems" :key="item.key">
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="admin-header">
        <button
          type="button"
          class="mobile-menu-toggle"
          aria-label="打开管理导航"
          @click="mobileNavVisible = true"
        >
          <span aria-hidden="true">☰</span>
        </button>
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

  <a-drawer
    v-model:visible="mobileNavVisible"
    title="管理后台导航"
    placement="left"
    :width="248"
    :footer="false"
  >
    <div class="mobile-nav-brand">
      <img src="/icon.svg" alt="" />
      <span>只因“梗”懂你</span>
    </div>
    <a-menu :selected-keys="[selectedKey]" @menu-item-click="onMobileMenuClick">
      <a-menu-item v-for="item in navigationItems" :key="item.key">
        {{ item.label }}
      </a-menu-item>
    </a-menu>
  </a-drawer>
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
const mobileNavVisible = ref(false)

const navigationItems = [
  { key: 'dashboard', label: '概览' },
  { key: 'memes', label: '梗管理' },
  { key: 'reports', label: '举报处理' },
  { key: 'users', label: '用户管理' },
  { key: 'home-images', label: '轮播管理' },
  { key: 'tags', label: '标签管理' },
  { key: 'sensitive-words', label: '敏感词' },
  { key: 'knowledge', label: '知识库' },
  { key: 'knowledge-corrections', label: '资料纠错审核' },
  { key: 'ops', label: 'AI 运维' },
]

const selectedKey = computed(() => String(route.name || 'dashboard'))
const pageTitle = computed(() => route.meta?.title || '管理后台')

function onCollapse(v) {
  collapsed.value = v
}

function onMenuClick(key) {
  if (key === selectedKey.value) return
  router.push({ name: key })
}

function onMobileMenuClick(key) {
  mobileNavVisible.value = false
  onMenuClick(key)
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
  justify-content: flex-start;
  gap: 9px;
  padding: 0 15px;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
}
.brand-logo {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
}
.brand-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
}
.brand-context {
  margin-left: auto;
  color: rgba(255, 255, 255, 0.64);
  font-size: 11px;
}
.brand--collapsed {
  justify-content: center;
  padding: 0;
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
  min-width: 0;
}
.mobile-menu-toggle {
  display: none;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fff;
  color: #1d2129;
  font-size: 19px;
  cursor: pointer;
}
.mobile-nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px 16px;
  font-weight: 650;
}
.mobile-nav-brand img {
  width: 32px;
  height: 32px;
}

@media (max-width: 768px) {
  .admin-layout {
    min-width: 0;
    overflow-x: hidden;
  }
  .admin-layout :deep(.arco-layout-sider) {
    display: none;
  }
  .admin-header {
    gap: 10px;
    padding: 0 12px;
  }
  .mobile-menu-toggle {
    display: inline-flex;
  }
  .header-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
  }
  .header-right {
    flex: 0 0 auto;
    gap: 4px;
  }
  .nickname {
    display: none;
  }
  .admin-content {
    margin: 10px;
    padding: 10px;
    min-height: calc(100vh - 76px);
    overflow: hidden;
  }
  .admin-content :deep(.toolbar) {
    flex-wrap: wrap;
    gap: 8px;
  }
  .admin-content :deep(.arco-table-content-scroll-x) {
    max-width: 100%;
    overflow-x: auto;
  }
  .admin-content :deep(.arco-table-element) {
    min-width: 640px;
  }
}

@media (max-width: 380px) {
  .admin-header {
    padding: 0 8px;
    gap: 6px;
  }
  .admin-content {
    margin: 8px;
    padding: 8px;
  }
}
</style>
