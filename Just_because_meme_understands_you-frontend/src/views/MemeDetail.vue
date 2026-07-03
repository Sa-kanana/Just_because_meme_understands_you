<template>
  <div class="page meme-detail-page">
    <el-breadcrumb class="page-breadcrumb" separator=">">
      <el-breadcrumb-item>
        <router-link to="/" class="crumb-link">主页</router-link>
      </el-breadcrumb-item>
      <el-breadcrumb-item>{{ meme?.name || '梗详情' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-if="loading" class="detail-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="error" class="detail-error">
      <el-result icon="warning" title="加载失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="reload">重试</el-button>
        </template>
      </el-result>
    </div>
    <div v-else-if="!meme" class="detail-empty">
      <el-empty description="没有找到这个梗，可能被时光吃掉了～" />
    </div>
    <div v-else class="detail-content">
      <!-- 顶部大卡片：封面 + 基本信息 -->
      <el-card class="detail-hero-card" shadow="never">
        <el-row :gutter="24" class="detail-hero-row">
          <el-col :xs="24" :md="10">
            <div class="detail-cover-wrap">
              <el-image
                v-if="meme.image"
                :src="meme.image"
                :alt="meme.name"
                fit="cover"
                class="detail-cover"
              >
                <template #error>
                  <div class="detail-cover-fallback">加载失败</div>
                </template>
              </el-image>
              <div v-else class="detail-cover-fallback">暂无封面</div>
              <div class="detail-cover-overlay">
                <span class="overlay-stat">👁 {{ formatNum(meme.pageViews) }}</span>
                <span class="overlay-stat">👍 {{ formatNum(meme.likes) }}</span>
                <span class="overlay-stat">💬 {{ formatNum(meme.comments) }}</span>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :md="14">
            <div class="detail-meta">
              <div class="detail-title-row">
                <h1 class="detail-title">{{ meme.name || '未命名梗' }}</h1>
                <el-button
                  class="detail-favorite-btn"
                  :type="isFavorited ? 'warning' : 'default'"
                  :loading="favoriteLoading"
                  :disabled="favoriteLoading"
                  @click="handleFavoriteClick"
                >
                  {{ isFavorited ? '已收藏' : '收藏' }}
                </el-button>
              </div>
              <p v-if="meme.introduction" class="detail-intro">
                {{ meme.introduction }}
              </p>
              <p v-else class="detail-intro muted">
                这个梗还没有详细介绍，欢迎你在评论区或社区里为它补完故事。
              </p>

              <div v-if="detailTags.length" class="detail-tags">
                <span class="detail-tags-label">标签</span>
                <div class="detail-tags-list">
                  <el-tag
                    v-for="tag in detailTags"
                    :key="tag.id"
                    size="small"
                    type="info"
                    class="detail-tag"
                    @click="goSearchByTag(tag)"
                  >
                    {{ tag.name }}
                  </el-tag>
                </div>
              </div>

              <div class="detail-meta-footer">
                <div class="detail-time">
                  <span v-if="meme.releaseTime">
                    首次出现：{{ formatDate(meme.releaseTime) }}
                  </span>
                  <span v-if="meme.updateTime">
                    最近更新：{{ formatDate(meme.updateTime) }}
                  </span>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 相关链接 / 延伸阅读 -->
      <el-card v-if="detailLinks.length" class="detail-section-card" shadow="never">
        <template #header>
          <div class="detail-section-header">
            <h2 class="detail-section-title">相关链接 · 延伸阅读</h2>
            <span class="detail-section-sub">
              帮你从不同角度更完整地理解这个梗
            </span>
          </div>
        </template>
        <div class="detail-links">
          <div
            v-for="link in detailLinks"
            :key="link.id"
            class="detail-link-group"
          >
            <el-link
              v-for="(url, idx) in safeUrls(link.resourceUrl)"
              :key="`${link.id}-${idx}-${url}`"
              :href="url"
              target="_blank"
              type="primary"
              class="detail-link-item"
            >
              <span class="detail-link-icon">🔗</span>
              <span class="detail-link-text">{{ url }}</span>
            </el-link>
          </div>
        </div>
      </el-card>

      <!-- 底部提示 -->
      <div class="detail-footer-tip">
        <span>只因“梗”懂你 · 让每一次会心一笑都有出处。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { useMemeDetailStore } from '@/stores/memeDetail'
import { useAuthStore } from '@/stores/auth'
import { addMemeFavorite, removeMemeFavorite } from '@/api/meme'
import { watch, computed, ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const memeDetailStore = useMemeDetailStore()
const authStore = useAuthStore()

const { meme, loading, error, isFavorited } = storeToRefs(memeDetailStore)
const detailTags = computed(() => memeDetailStore.tags)
const detailLinks = computed(() => memeDetailStore.links)

const memeId = computed(() => route.params.id || route.query.memeId)
const favoriteLoading = ref(false)
let favoriteDebounceTimer = null

function clearFavoriteDebounceTimer() {
  if (favoriteDebounceTimer) {
    clearTimeout(favoriteDebounceTimer)
    favoriteDebounceTimer = null
  }
}

watch(
  memeId,
  (id) => {
    clearFavoriteDebounceTimer()
    memeDetailStore.fetchDetail(id)
  },
  { immediate: true }
)

onUnmounted(() => {
  clearFavoriteDebounceTimer()
})

function reload() {
  memeDetailStore.fetchDetail(memeId.value)
}

function handleFavoriteClick() {
  clearFavoriteDebounceTimer()
  favoriteDebounceTimer = setTimeout(() => {
    favoriteDebounceTimer = null
    toggleFavorite()
  }, 300)
}

async function toggleFavorite() {
  if (!authStore.isLoggedIn) {
    router.push({
      name: 'login',
      query: { redirect: route.fullPath },
    })
    return
  }
  const id = memeId.value
  if (!id || favoriteLoading.value) return

  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      await removeMemeFavorite(id)
      memeDetailStore.setFavorited(false)
      ElMessage.success('已取消收藏')
    } else {
      await addMemeFavorite(id, 0)
      memeDetailStore.setFavorited(true)
      ElMessage.success('收藏成功')
    }
  } catch (e) {
    if (e && (e.status === 401 || e.code === 401)) {
      router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
      return
    }
    ElMessage.error((e && e.message) || '操作失败，请稍后重试')
  } finally {
    favoriteLoading.value = false
  }
}

function formatNum(num) {
  if (num == null) return '0'
  const n = Number(num)
  if (Number.isNaN(n)) return '0'
  if (n >= 1e8) return (n / 1e8).toFixed(1) + '亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1) + '万'
  return String(n)
}

function formatDate(str) {
  if (!str) return ''
  const s = String(str).trim()
  const match = s.match(/^(\d{4}-\d{2}-\d{2})/)
  return match ? match[1] : ''
}

function safeUrls(resourceUrl) {
  if (!Array.isArray(resourceUrl)) return []
  return resourceUrl
    .map((u) => String(u || '').trim())
    .filter(Boolean)
}

function goSearchByTag(tag) {
  if (!tag || !tag.name) return
  router.push({
    name: 'search',
    query: { keyword: tag.name },
  })
}
</script>

<style scoped>
.page {
  padding: 24px 32px;
}

.meme-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: 16px;
}

.crumb-link {
  display: inline-block;
  padding: 2px 4px;
  border-radius: 4px;
  text-decoration: none;
  color: #6b7280;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.crumb-link:hover {
  color: #111827;
  background-color: #f3f4f6;
  text-decoration: none;
}

.detail-loading,
.detail-error,
.detail-empty {
  padding: 40px 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-hero-card {
  border-radius: 24px;
  padding: 20px 24px;
}

.detail-hero-row {
  align-items: stretch;
}

.detail-cover-wrap {
  position: relative;
  width: 100%;
  height: 260px;
  border-radius: 18px;
  background-color: #f3f4f6;
  overflow: hidden;
}

.detail-cover {
  width: 100%;
  height: 100%;
  display: block;
}

.detail-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 14px;
}

.detail-cover-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #fff;
}

.overlay-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.detail-meta {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-top: 4px;
}

.detail-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.detail-favorite-btn {
  flex-shrink: 0;
}

.detail-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.detail-intro {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.7;
  color: #4b5563;
}

.detail-intro.muted {
  color: #9ca3af;
}

.detail-tags {
  margin-bottom: 8px;
}

.detail-tags-label {
  font-size: 13px;
  color: #6b7280;
  margin-right: 4px;
}

.detail-tags-list {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag {
  cursor: pointer;
}

.detail-meta-footer {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.detail-time {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-section-card {
  border-radius: 20px;
}

.detail-section-header {
  display: flex;
  flex-direction: column;
}

.detail-section-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.detail-section-sub {
  font-size: 13px;
  color: #6b7280;
}

.detail-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-link-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-link-item {
  max-width: 100%;
}

.detail-link-icon {
  margin-right: 4px;
}

.detail-link-text {
  max-width: 240px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.detail-footer-tip {
  padding: 8px 4px 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: right;
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .detail-hero-card {
    padding: 16px;
  }

  .detail-cover-wrap {
    margin-bottom: 12px;
  }
}
</style>