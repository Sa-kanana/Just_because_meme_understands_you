<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      class="admin-sider"
      :class="{ 'admin-sider--collapsed': collapsed }"
      theme="light"
      collapsible
      :collapsed="collapsed"
      :width="236"
      :collapsed-width="72"
      breakpoint="md"
      @collapse="onCollapse"
    >
      <div class="brand" :class="{ 'brand--collapsed': collapsed }" title="只因“梗”懂你 · 管理端">
        <span class="brand-mark">
          <img class="brand-logo" src="/icon.svg" alt="" />
        </span>
        <span v-if="!collapsed" class="brand-copy">
          <strong>只因“梗”懂你</strong>
          <small>ADMIN CONSOLE</small>
        </span>
      </div>

      <a-menu
        class="admin-menu"
        :class="{ 'admin-menu--collapsed': collapsed }"
        theme="light"
        :collapsed="collapsed"
        :tooltip-props="{ disabled: true, showArrow: false }"
        :selected-keys="[selectedKey]"
        @menu-item-click="onMenuClick"
      >
        <template v-if="collapsed">
          <a-menu-item
            v-for="item in flatNavigation"
            :key="item.key"
            :aria-label="item.label"
          >
            <template #icon><component :is="item.icon" /></template>
          </a-menu-item>
        </template>
        <template v-else>
          <a-menu-item-group v-for="group in navigationGroups" :key="group.title" :title="group.title">
            <a-menu-item v-for="item in group.items" :key="item.key">
              <template #icon><component :is="item.icon" /></template>
              {{ item.label }}
            </a-menu-item>
          </a-menu-item-group>
        </template>
      </a-menu>
    </a-layout-sider>

    <a-layout class="admin-main">
      <a-layout-header class="admin-header">
        <div class="header-left">
          <button
            type="button"
            class="mobile-menu-toggle"
            aria-label="打开管理导航"
            @click="mobileNavVisible = true"
          >
            <icon-menu />
          </button>
          <div class="page-heading">
            <h1 class="header-title">{{ pageTitle }}</h1>
          </div>
        </div>

        <div class="header-right">
          <a-avatar class="user-avatar" :size="38">{{ userInitial }}</a-avatar>
          <div class="user-summary">
            <strong>{{ auth.user?.nickname || '管理员' }}</strong>
            <span>管理员账号</span>
          </div>
          <a-divider direction="vertical" class="header-divider" />
          <a-button class="logout-button" type="text" @click="onLogout">
            <template #icon><icon-export /></template>
            退出
          </a-button>
        </div>
      </a-layout-header>

      <a-layout-content class="admin-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>

  <a-drawer
    v-model:visible="mobileNavVisible"
    class="mobile-nav-drawer"
    title="管理后台导航"
    placement="left"
    :width="280"
    :footer="false"
  >
    <div class="mobile-nav-brand">
      <span class="brand-mark"><img src="/icon.svg" alt="" /></span>
      <span>只因“梗”懂你</span>
    </div>
    <a-menu
      class="mobile-nav-menu"
      :selected-keys="[selectedKey]"
      @menu-item-click="onMobileMenuClick"
    >
      <a-menu-item-group v-for="group in navigationGroups" :key="group.title" :title="group.title">
        <a-menu-item v-for="item in group.items" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </a-menu-item>
      </a-menu-item-group>
    </a-menu>
  </a-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  IconApps,
  IconBook,
  IconDashboard,
  IconEdit,
  IconExclamationCircle,
  IconImage,
  IconRobot,
  IconSafe,
  IconTags,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import { useAuthStore } from '@/stores/auth'
import { toastSuccess } from '@/utils/uiFeedback'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileNavVisible = ref(false)

/** 中文注释：导航组使用 title（分组标题）和 items（组内菜单项）；菜单项使用 key（路由名）、label（显示名称）和 icon（图标组件）。 */
const navigationGroups = [
  {
    title: '工作台',
    items: [{ key: 'dashboard', label: '概览', icon: IconDashboard }],
  },
  {
    title: '内容运营',
    items: [
      { key: 'memes', label: '梗管理', icon: IconImage },
      { key: 'meme-edit-revisions', label: '修改稿审核', icon: IconEdit },
      { key: 'reports', label: '举报处理', icon: IconExclamationCircle },
      { key: 'tags', label: '标签管理', icon: IconTags },
    ],
  },
  {
    // 中文注释：集中展示 AI 检索知识和运行状态管理入口。
    title: 'AI 服务',
    // 中文注释：该分组只包含知识库与 AI 运维两个相关页面。
    items: [
      {
        // 中文注释：路由名称用于定位知识库管理页面。
        key: 'knowledge',
        // 中文注释：侧栏显示的知识库入口名称。
        label: '知识库',
        // 中文注释：知识库入口使用书本图标。
        icon: IconBook,
      },
      {
        // 中文注释：路由名称用于定位 AI 运维页面。
        key: 'ops',
        // 中文注释：侧栏显示的 AI 运维入口名称。
        label: 'AI 运维',
        // 中文注释：AI 运维入口使用机器人图标。
        icon: IconRobot,
      },
    ],
  },
  {
    title: '用户与系统',
    items: [
      { key: 'users', label: '用户管理', icon: IconUserGroup },
      { key: 'home-images', label: '轮播管理', icon: IconApps },
      { key: 'sensitive-words', label: '敏感词', icon: IconSafe },
    ],
  },
]

/** 中文注释：收窄侧栏时使用扁平菜单，只保留图标并通过悬浮提示显示菜单名称。 */
const flatNavigation = computed(() => navigationGroups.flatMap((group) => group.items))

const selectedKey = computed(() => String(route.name || 'dashboard'))
const pageTitle = computed(() => route.meta?.title || '管理后台')
const userInitial = computed(() => String(auth.user?.nickname || '管').trim().slice(0, 1) || '管')

function onCollapse(value) {
  collapsed.value = value
}

function onMenuClick(key) {
  if (key !== selectedKey.value) router.push({ name: key })
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
  background: #f6f7f9;
}

.admin-layout :deep(.arco-layout) {
  min-width: 0;
  background: transparent;
}

.admin-sider {
  position: relative;
  z-index: 2;
  min-height: 100vh;
  border-right: 1px solid #edf0f4;
  background: #fff;
}

.admin-sider :deep(.arco-layout-sider-children) {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.admin-sider :deep(.arco-layout-sider-trigger) {
  height: 48px;
  border-top: 1px solid #edf0f4;
  color: #718096;
  background: #fff;
}

.admin-sider :deep(.arco-layout-sider-trigger:hover) {
  color: #315fbe;
  background: #f7f9fc;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 72px;
  padding: 0 19px;
  overflow: hidden;
  border-bottom: 1px solid #edf0f4;
  white-space: nowrap;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #f7f9fd;
}

.brand-logo {
  width: 28px;
  height: 28px;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  color: #25334b;
}

.brand-copy strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.01em;
  text-overflow: ellipsis;
}

.brand-copy small {
  color: #98a2b2;
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0.12em;
}

.brand--collapsed {
  justify-content: center;
  padding: 0;
}

.admin-menu,
.mobile-nav-menu {
  flex: 1;
  border: 0;
  background: transparent;
}

.admin-menu {
  padding: 12px 12px 20px;
}

.admin-menu :deep(.arco-menu-inner),
.mobile-nav-menu :deep(.arco-menu-inner) {
  padding: 0;
}

.admin-menu :deep(.arco-menu-item-group-title) {
  padding: 17px 12px 7px;
  color: #9aa4b3;
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.06em;
  line-height: 1.4;
}

.admin-menu :deep(.arco-menu-item),
.mobile-nav-menu :deep(.arco-menu-item) {
  height: 42px;
  margin: 2px 0;
  padding: 0 12px;
  border-radius: 8px;
  color: #667085;
  font-size: 13px;
  line-height: 42px;
  transition: background-color 150ms ease, color 150ms ease;
}

.admin-menu :deep(.arco-menu-item:hover) {
  color: #26354d;
  background: #f5f7fa;
}

.admin-menu :deep(.arco-menu-item.arco-menu-selected) {
  position: relative;
  color: #2c63c9;
  background: #edf3ff;
  box-shadow: none;
  font-weight: 600;
}

.admin-menu :deep(.arco-menu-item.arco-menu-selected::before) {
  position: absolute;
  top: 11px;
  bottom: 11px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: #4679dc;
  content: '';
}

.admin-menu :deep(.arco-menu-item .arco-icon) {
  color: inherit;
  font-size: 17px;
}

.admin-menu--collapsed {
  width: 100%;
  padding: 12px 0 20px;
}

.admin-menu--collapsed :deep(.arco-menu-inner) {
  padding: 4px 8px;
}

.admin-menu--collapsed :deep(.arco-menu-item) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0;
}

.admin-menu--collapsed :deep(.arco-menu-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-right: 0 !important;
}

.admin-main {
  min-width: 0;
}

.admin-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 32px;
  border-bottom: 1px solid #e9edf2;
  background: rgba(246, 247, 249, 0.96);
  backdrop-filter: blur(10px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.header-left {
  min-width: 0;
  gap: 12px;
}

.page-heading {
  display: flex;
  min-width: 0;
  align-items: center;
}

.header-title {
  margin: 0;
  overflow: hidden;
  color: #202d42;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-right {
  flex: 0 0 auto;
  gap: 10px;
}

.user-avatar {
  color: #356acb;
  background: #e8effd;
  font-weight: 650;
}

.user-summary {
  display: flex;
  min-width: 72px;
  flex-direction: column;
  gap: 2px;
}

.user-summary strong {
  color: #344158;
  font-size: 12px;
  font-weight: 600;
}

.user-summary span {
  color: #98a2b2;
  font-size: 10px;
}

.header-divider {
  height: 23px;
  margin: 0 2px;
  border-color: #e1e5eb;
}

.logout-button {
  height: 34px;
  padding: 0 9px;
  border-radius: 8px;
  color: #68758a;
}

.logout-button:hover {
  color: #c44949;
  background: #fff0f0;
}

.admin-content {
  box-sizing: border-box;
  width: 100%;
  max-width: 1600px;
  min-width: 0;
  min-height: calc(100vh - 68px);
  margin: 0 auto;
  padding: 26px 32px 34px;
}

.admin-content :deep(.toolbar) {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.admin-content :deep(.arco-input-wrapper),
.admin-content :deep(.arco-select-view),
.admin-content :deep(.arco-textarea-wrapper) {
  border-radius: 8px;
}

.admin-content :deep(.arco-btn) {
  border-radius: 8px;
  font-weight: 550;
}

.admin-content :deep(.arco-table-th) {
  color: #596579;
  background: #f7f8fa;
  font-weight: 600;
}

.admin-content :deep(.arco-table-tr:hover .arco-table-td) {
  background: #f8faff;
}

.admin-content :deep(.arco-pagination-item-active) {
  border-radius: 7px;
}

.mobile-menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border: 1px solid #e2e7ee;
  border-radius: 9px;
  background: #fff;
  color: #526178;
  cursor: pointer;
}

.mobile-nav-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  margin: -4px 0 16px;
  color: #25334b;
  font-size: 14px;
  font-weight: 650;
}

.mobile-nav-brand .brand-mark {
  width: 36px;
  height: 36px;
}

.mobile-nav-brand img {
  width: 27px;
  height: 27px;
}

.mobile-nav-menu :deep(.arco-menu-item-group-title) {
  padding: 17px 12px 6px;
  color: #9aa4b3;
  font-size: 10px;
  font-weight: 650;
}

.mobile-nav-menu :deep(.arco-menu-item) {
  color: #53627a;
}

.mobile-nav-menu :deep(.arco-menu-item.arco-menu-selected) {
  color: #2c63c9;
  background: #edf3ff;
  font-weight: 600;
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
    height: 62px;
    padding: 0 16px;
    gap: 8px;
  }

  .mobile-menu-toggle {
    display: inline-flex;
  }

  .header-title {
    font-size: 16px;
  }

  .header-right {
    gap: 7px;
  }

  .user-summary,
  .header-divider {
    display: none;
  }

  .logout-button {
    padding: 0 7px;
    font-size: 12px;
  }

  .admin-content {
    min-height: calc(100vh - 62px);
    padding: 20px 16px 26px;
  }
}

@media (max-width: 380px) {
  .admin-header {
    padding: 0 10px;
  }

  .admin-content {
    padding: 16px 10px 22px;
  }
}
</style>
