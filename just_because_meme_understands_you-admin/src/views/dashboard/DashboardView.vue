<template>
  <div class="overview-page">
    <section class="welcome-panel">
      <div class="welcome-copy">
        <span class="welcome-eyebrow">运营数据</span>
        <h2>把握站点运营动态</h2>
        <p>内容审核、用户与站点配置，一处掌握日常运营情况。</p>
      </div>
      <a-button class="refresh-button" :loading="refreshing" @click="loadStats">
        <template #icon><icon-refresh /></template>
        刷新数据
      </a-button>
    </section>

    <section class="stats-grid" aria-label="运营数据">
      <a-card
        v-for="item in statItems"
        :key="item.key"
        class="stat-card"
        :class="`stat-card--${item.tone}`"
        :bordered="false"
      >
        <div class="stat-card-top">
          <span class="stat-icon"><component :is="item.icon" /></span>
          <span v-if="item.error" class="stat-status stat-status--error">
            <i />暂不可用
          </span>
        </div>
        <p class="stat-label">{{ item.label }}</p>
        <div v-if="item.loading" class="stat-skeleton" aria-label="正在加载" />
        <p v-else-if="item.error" class="stat-value stat-value--error">--</p>
        <p v-else class="stat-value">{{ formatCount(item.value) }}</p>
        <div class="stat-card-bottom">
          <span>{{ item.error ? '点击右上角刷新重试' : item.note }}</span>
        </div>
      </a-card>
    </section>

    <section class="quick-panel">
      <div class="section-heading">
        <div>
          <span class="section-eyebrow">日常管理</span>
          <h3>快捷入口</h3>
        </div>
        <span class="section-note">常用管理操作</span>
      </div>

      <div class="quick-grid">
        <router-link
          v-for="item in quickLinks"
          :key="item.title"
          class="quick-link"
          :to="item.to"
        >
          <span class="quick-icon" :class="`quick-icon--${item.tone}`">
            <component :is="item.icon" />
          </span>
          <span class="quick-copy">
            <strong>{{ item.title }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <icon-arrow-right class="quick-arrow" />
        </router-link>
      </div>
    </section>

    <p v-if="hasLoadError" class="load-notice" role="status">
      <icon-exclamation-circle />
      部分统计暂时无法加载，其他卡片不受影响；可点击“刷新数据”重试。
    </p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  IconApps,
  IconArrowRight,
  IconExclamationCircle,
  IconImage,
  IconRobot,
  IconTags,
  IconUserGroup,
} from '@arco-design/web-vue/es/icon'
import { fetchAdminMemes } from '@/api/adminMeme'
import { fetchAdminUsers } from '@/api/adminUser'
import { fetchAdminHomeImages } from '@/api/adminHomeImage'
import { fetchAdminTags } from '@/api/adminTag'
import { IconRefresh } from '@arco-design/web-vue/es/icon'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError } from '@/utils/uiFeedback'

const refreshing = ref(false)
/** 中文注释：统计卡片字段为 key（取数器标识）、label（标题）、note（说明）、value（数值）、loading（加载状态）、error（错误状态）、tone（配色主题）和 icon（图标组件）。 */
const statItems = ref([
  {
    key: 'reviewing',
    label: '待审核梗',
    note: '等待审核的内容',
    value: null,
    loading: true,
    error: false,
    tone: 'blue',
    icon: IconImage,
  },
  {
    key: 'users',
    label: '用户总数',
    note: '已注册用户',
    value: null,
    loading: true,
    error: false,
    tone: 'violet',
    icon: IconUserGroup,
  },
  {
    key: 'images',
    label: '轮播图',
    note: '站点展示素材',
    value: null,
    loading: true,
    error: false,
    tone: 'teal',
    icon: IconApps,
  },
  {
    key: 'tags',
    label: '标签数',
    note: '内容分类标签',
    value: null,
    loading: true,
    error: false,
    tone: 'amber',
    icon: IconTags,
  },
])

/** 中文注释：快捷入口字段为 title（入口标题）、description（用途说明）、to（目标路由）、icon（图标组件）和 tone（配色主题）。 */
const quickLinks = [
  {
    title: '内容审核',
    description: '查看并处理待审梗图',
    to: { name: 'memes', query: { status: 2 } },
    icon: IconImage,
    tone: 'blue',
  },
  {
    title: '举报处理',
    description: '跟进用户提交的举报',
    to: { name: 'reports' },
    icon: IconExclamationCircle,
    tone: 'rose',
  },
  {
    title: '用户管理',
    description: '查看账号与权限状态',
    to: { name: 'users' },
    icon: IconUserGroup,
    tone: 'violet',
  },
  {
    title: 'AI 运维',
    description: '查看智能服务运行情况',
    to: { name: 'ops' },
    icon: IconRobot,
    tone: 'teal',
  },
]

const hasLoadError = ref(false)

/** 中文注释：统计取数器以统计卡片 key 为索引，分别请求对应管理分页接口。 */
const statLoaders = {
  reviewing: () => fetchAdminMemes({ page: 1, size: 1, status: 2 }),
  users: () => fetchAdminUsers({ page: 1, size: 1 }),
  images: () => fetchAdminHomeImages({ page: 1, size: 1 }),
  tags: () => fetchAdminTags({ page: 1, size: 1 }),
}

function formatCount(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value) || 0)
}

async function loadStats() {
  if (refreshing.value) return
  refreshing.value = true
  hasLoadError.value = false
  statItems.value.forEach((item) => {
    item.loading = true
    item.error = false
  })

  let hasReportableError = false
  await Promise.all(
    statItems.value.map(async (item) => {
      try {
        const result = await statLoaders[item.key]()
        item.value = Number(result?.total) || 0
      } catch (error) {
        item.value = null
        item.error = true
        hasLoadError.value = true
        if (!isAuthErrorHandled(error)) {
          hasReportableError = true
        }
      } finally {
        item.loading = false
      }
    })
  )
  refreshing.value = false
  if (hasReportableError) toastError('部分统计暂时无法加载，可点击刷新数据重试')
}

onMounted(loadStats)
</script>

<style scoped>
.overview-page {
  display: flex;
  max-width: 1480px;
  flex-direction: column;
  gap: 22px;
  margin: 0 auto;
}

.welcome-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 142px;
  padding: 26px 30px;
  border: 1px solid #e9edf2;
  border-radius: 12px;
  background: #fff;
}

.welcome-copy {
  min-width: 0;
}

.welcome-eyebrow,
.section-eyebrow {
  color: #718099;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.welcome-copy h2 {
  margin: 8px 0 6px;
  color: #25334a;
  font-size: 23px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.welcome-copy p {
  margin: 0;
  color: #7c889a;
  font-size: 13px;
  line-height: 1.6;
}

.refresh-button {
  height: 36px;
  flex: 0 0 auto;
  padding: 0 13px;
  border-color: #e1e7f0;
  border-radius: 8px;
  color: #4b5d77;
  background: #fff;
  font-weight: 550;
}

.refresh-button:hover {
  border-color: #cbd9f1;
  color: #315fbe;
  background: #f8faff;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  min-width: 0;
  border: 1px solid #e9edf2;
  border-radius: 12px;
  background: #fff;
  transition: border-color 150ms ease;
}

.stat-card:hover {
  border-color: #d7e0ed;
}

.stat-card :deep(.arco-card-body) {
  padding: 18px 20px;
}

.stat-card-top {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
}

.stat-icon,
.quick-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 10px;
}

.stat-icon {
  width: 36px;
  height: 36px;
  font-size: 17px;
}

.stat-card--blue .stat-icon,
.quick-icon--blue {
  color: #3e6fc4;
  background: #eef3fc;
}

.stat-card--violet .stat-icon,
.quick-icon--violet {
  color: #7162ad;
  background: #f1eff9;
}

.stat-card--teal .stat-icon,
.quick-icon--teal {
  color: #348c7d;
  background: #ebf5f2;
}

.stat-card--amber .stat-icon {
  color: #ad7b37;
  background: #f8f2e8;
}

.quick-icon--rose {
  color: #b85f6a;
  background: #f8edef;
}

.stat-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 7px;
  border-radius: 16px;
  color: #a2672d;
  background: #fbf4e9;
  font-size: 10px;
  white-space: nowrap;
}

.stat-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d79742;
}

.stat-label {
  margin: 15px 0 4px;
  color: #758196;
  font-size: 12px;
  font-weight: 550;
}

.stat-value {
  margin: 0;
  color: #26344b;
  font-size: 29px;
  font-weight: 650;
  letter-spacing: -0.03em;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-value--error {
  color: #aab2bf;
}

.stat-skeleton {
  width: 84px;
  height: 30px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e8ebf0 45%, #f0f2f5 65%);
  background-size: 220% 100%;
  animation: shimmer 1.25s ease-in-out infinite;
}

.stat-card-bottom {
  min-height: 18px;
  margin-top: 9px;
  color: #9aa4b2;
  font-size: 10px;
}

.quick-panel {
  padding: 21px;
  border: 1px solid #e9edf2;
  border-radius: 12px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 15px;
}

.section-heading h3 {
  margin: 5px 0 0;
  color: #25334a;
  font-size: 16px;
  font-weight: 650;
}

.section-note {
  color: #929cad;
  font-size: 11px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.quick-link {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #edf0f4;
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
  transition: border-color 150ms ease, background-color 150ms ease;
}

.quick-link:hover {
  border-color: #d6e0f0;
  background: #fafbfd;
}

.quick-icon {
  width: 34px;
  height: 34px;
  font-size: 16px;
}

.quick-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.quick-copy strong {
  overflow: hidden;
  color: #354259;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-copy small {
  overflow: hidden;
  color: #929cad;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-arrow {
  flex: 0 0 auto;
  color: #aab2bf;
  font-size: 13px;
  transition: color 150ms ease;
}

.quick-link:hover .quick-arrow {
  color: #416fc0;
}

.load-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -6px 2px 0;
  color: #967143;
  font-size: 12px;
}

@keyframes shimmer {
  to { background-position: -220% 0; }
}

@media (max-width: 1100px) {
  .stats-grid,
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .overview-page {
    gap: 15px;
  }

  .welcome-panel {
    align-items: flex-start;
    min-height: 0;
    flex-direction: column;
    gap: 16px;
    padding: 21px 18px;
  }

  .welcome-copy h2 {
    font-size: 20px;
  }

  .welcome-copy p {
    max-width: 280px;
  }

  .stats-grid,
  .quick-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 9px;
  }

  .stat-card :deep(.arco-card-body) {
    padding: 16px;
  }

  .stat-value {
    font-size: 26px;
  }

  .quick-panel {
    padding: 17px 13px;
  }

  .quick-link {
    padding: 11px;
  }

  .section-note {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>