<template>
  <div class="page meme-detail-page">
    <div v-if="loading" class="detail-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="error" class="detail-error">
      <el-result
        :icon="detailErrorIcon"
        :title="detailErrorTitle"
        :sub-title="detailErrorSubtitle"
      >
        <template #extra>
          <div class="detail-error-actions">
            <el-button v-if="authStore.isLoggedIn" round @click="goMyPublished">
              回到我的发布
            </el-button>
            <el-button type="primary" round @click="reload">重试</el-button>
            <el-button round @click="goHome">返回主页</el-button>
          </div>
        </template>
      </el-result>
    </div>
    <div v-else-if="!meme" class="detail-empty">
      <el-empty description="没有找到这个梗，可能被时光吃掉了～" />
    </div>
    <div v-else class="detail-content" :class="{ 'detail-content--preview': ownerPreview }">
      <MemeDetailPreviewBanner
        v-if="ownerPreview"
        :status="meme.status"
        :status-desc="meme.statusDesc || meme.status_desc"
        :refreshing="previewRefreshing"
        @back-published="goMyPublished"
        @refresh="handlePreviewRefresh"
      />

      <!-- 顶部大卡片：封面 + 基本信息 -->
      <el-card class="detail-hero-card" :class="{ 'detail-hero-card--preview': ownerPreview }" shadow="never">
        <el-row :gutter="20" class="detail-hero-row">
          <el-col :xs="24" :md="10" :lg="9">
            <div class="detail-cover-wrap">
              <el-image
                v-if="meme.image"
                :src="meme.image"
                :alt="meme.name"
                fit="cover"
                class="detail-cover"
              >
                <template #error>
                  <div class="detail-cover-fallback">
                    <span class="detail-cover-fallback-icon" aria-hidden="true">🖼</span>
                    <span>封面加载失败</span>
                  </div>
                </template>
              </el-image>
              <div v-else class="detail-cover-fallback">
                <span class="detail-cover-fallback-icon" aria-hidden="true">🖼</span>
                <span>暂无封面</span>
              </div>
              <div v-if="ownerPreview" class="detail-cover-preview-tag">仅发布者可见</div>
              <div
                v-if="!ownerPreview"
                class="detail-cover-overlay detail-cover-overlay--mobile"
                aria-hidden="true"
              >
                <span class="overlay-stat">👁 {{ formatNum(meme.pageViews) }}</span>
                <span class="overlay-stat">👍 {{ formatNum(meme.likes) }}</span>
                <span class="overlay-stat">💬 {{ formatNum(meme.comments) }}</span>
              </div>
              <div v-else class="detail-cover-overlay detail-cover-overlay--muted detail-cover-overlay--mobile">
                <span class="overlay-stat overlay-stat--hint">公域暂未展示</span>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :md="14" :lg="15">
            <div class="detail-meta">
              <div class="detail-meta-head">
                <div class="detail-title-block">
                  <span
                    v-if="ownerPreview && memeStatusLabel"
                    class="detail-status-chip"
                    :class="`detail-status-chip--${meme.status}`"
                  >
                    {{ memeStatusLabel }}
                  </span>
                  <h1 class="detail-title">{{ meme.name || '未命名梗' }}</h1>
                  <button
                    v-if="authorUserId"
                    type="button"
                    class="detail-author"
                    @click="goUserProfile(authorUserId)"
                  >
                    <el-avatar :size="28" :src="authorAvatar" class="detail-author-avatar">
                      {{ authorAvatarFallback }}
                    </el-avatar>
                    <span class="detail-author-name">{{ authorNickname }}</span>
                  </button>
                  <FollowButton
                    v-if="authorUserId && !isAuthorSelf"
                    :key="`detail-follow-${authorUserId}`"
                    v-model="authorFollowed"
                    :user-id="authorUserId"
                    :mutual="authorMutual"
                    size="sm"
                    class="detail-follow-btn"
                    fetch-on-mount
                    @change="onAuthorFollowChange"
                  />
                </div>
                <div class="detail-meta-actions">
                  <MemeDetailLikeBtn
                    class="detail-meta-like"
                    compact
                    :active="isLiked"
                    :loading="likeLoading"
                    :disabled="ownerPreview"
                    :preview="ownerPreview"
                    @click="handleLikeClick"
                  />
                  <MemeDetailFavoriteBtn
                    class="detail-meta-favorite"
                    compact
                    :active="isFavorited"
                    :loading="favoriteLoading"
                    :disabled="ownerPreview"
                    :preview="ownerPreview"
                    @click="handleFavoriteClick"
                  />
                </div>
              </div>

              <div v-if="!ownerPreview" class="detail-stats-row" role="group" aria-label="梗数据统计">
                <div class="detail-stat-item">
                  <span class="detail-stat-value">{{ formatNum(meme.pageViews) }}</span>
                  <span class="detail-stat-label">浏览</span>
                </div>
                <span class="detail-stat-divider" aria-hidden="true" />
                <div class="detail-stat-item">
                  <span class="detail-stat-value">{{ formatNum(meme.likes) }}</span>
                  <span class="detail-stat-label">点赞</span>
                </div>
                <span class="detail-stat-divider" aria-hidden="true" />
                <div class="detail-stat-item">
                  <span class="detail-stat-value">{{ formatNum(meme.comments) }}</span>
                  <span class="detail-stat-label">评论</span>
                </div>
              </div>

              <div class="detail-meta-body">
                <section class="detail-intro-panel">
                  <p class="detail-intro-label">梗介绍</p>
                  <p v-if="meme.introduction" class="detail-intro">
                    {{ meme.introduction }}
                  </p>
                  <p v-else class="detail-intro detail-intro--placeholder">
                    {{ ownerPreview ? '预览模式下暂无介绍，可在发布页补充后再提交审核。' : '这个梗还没有详细介绍，欢迎你在评论区或社区里为它补完故事。' }}
                  </p>
                </section>

                <section v-if="detailTags.length" class="detail-tags-section">
                  <span class="detail-tags-label">标签</span>
                  <div class="detail-tags-list">
                    <button
                      v-for="tag in detailTags"
                      :key="tag.id"
                      type="button"
                      class="detail-tag-chip"
                      @click="goSearchByTag(tag)"
                    >
                      #{{ tag.name }}
                    </button>
                  </div>
                </section>

                <footer v-if="meme.releaseTime || meme.updateTime" class="detail-meta-footer">
                  <div class="detail-time">
                    <span v-if="meme.releaseTime" class="detail-time-item">
                      首次出现 {{ formatDate(meme.releaseTime) }}
                    </span>
                    <span v-if="meme.releaseTime && meme.updateTime" class="detail-time-sep">·</span>
                    <span v-if="meme.updateTime" class="detail-time-item">
                      最近更新 {{ formatDate(meme.updateTime) }}
                    </span>
                  </div>
                </footer>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 相关链接 / 延伸阅读 -->
      <el-card v-if="normalizedLinks.length" class="detail-section-card" shadow="never">
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
            v-for="link in normalizedLinks"
            :key="link.key"
            class="detail-link-slot"
          >
            <div v-if="link.isMedia && isImageUrl(link.url)" class="detail-link-image-item">
              <el-image
                :src="link.url"
                fit="cover"
                class="detail-link-image"
                :preview-src-list="[link.url]"
                preview-teleported
              >
                <template #error>
                  <div class="detail-link-image-error">图片加载失败</div>
                </template>
              </el-image>
            </div>
            <el-link
              v-else
              :href="link.url"
              target="_blank"
              rel="noopener noreferrer"
              type="primary"
              class="detail-link-item"
            >
              <span class="detail-link-icon">{{ linkTypeIcon(link.type) }}</span>
              <span class="detail-link-text">{{ link.displayTitle }}</span>
            </el-link>
          </div>
        </div>
      </el-card>

      <!-- 评论区 -->
      <el-card class="detail-section-card detail-comment-card" :class="{ 'detail-comment-card--preview': ownerPreview }" shadow="never">
        <template #header>
          <div class="detail-section-header comment-header-row">
            <div>
              <h2 class="detail-section-title">
                {{ ownerPreview ? '评论区（预览未开放）' : '评论区' }}
              </h2>
              <span class="detail-section-sub">
                {{ ownerPreview ? '审核通过后将开放互动' : `共 ${formatNum(commentTotal)} 条评论` }}
              </span>
            </div>
            <el-radio-group
              v-if="commentsEnabled"
              v-model="commentSortType"
              size="small"
              class="comment-sort-tabs"
              @change="reloadComments"
            >
              <el-radio-button label="new">最新</el-radio-button>
              <el-radio-button label="hot">最热</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <div v-if="ownerPreview" class="comment-preview-disabled">
          <p class="comment-preview-disabled-title">评论区暂未开放</p>
          <p class="comment-preview-disabled-desc">
            {{ previewCommentHint }}
          </p>
        </div>
        <template v-else>
        <div v-if="authStore.isLoggedIn" class="comment-composer">
          <div class="comment-editor">
          <div class="comment-editor-row">
            <el-avatar
              :size="40"
              :src="currentUserAvatar"
              class="comment-editor-avatar"
              @click="goCurrentUserProfile"
            >
              {{ commentAvatarFallback }}
            </el-avatar>
            <div class="comment-editor-input-wrap">
              <el-input
                v-model="commentDraft"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 6 }"
                maxlength="2000"
                class="comment-editor-input"
                placeholder="只是一直在等你而已，才不是想被评论呢～"
                @focus="commentEditorFocused = true"
                @blur="onCommentEditorBlur"
                @keydown.ctrl.enter.prevent="submitRootComment"
                @keydown.meta.enter.prevent="submitRootComment"
              />
            </div>
          </div>
          <div
            v-if="commentEditorFocused || commentDraft.trim() || commentImages.length"
            class="comment-editor-actions"
          >
            <div class="comment-editor-images">
              <div
                v-for="(img, idx) in commentImages"
                :key="`comment-draft-${idx}`"
                class="comment-editor-image-item"
              >
                <el-image :src="img" fit="cover" class="comment-editor-image-thumb" />
                <span class="comment-editor-image-remove" @click="removeCommentImage(idx)">×</span>
              </div>
              <label v-if="commentImages.length < 3" class="comment-editor-image-add">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  class="comment-image-file-input"
                  @change="onCommentImageChange"
                />
                <span class="comment-editor-image-add-inner">
                  <span v-if="commentImageUploading">上传中…</span>
                  <span v-else>＋ 图片</span>
                </span>
              </label>
            </div>
            <el-button
              type="primary"
              :loading="commentSubmitting"
              :disabled="commentSubmitting || (!commentDraft.trim() && !commentImages.length)"
              @click="submitRootComment"
            >
              发表评论
            </el-button>
          </div>
          </div>
        </div>
        <div v-else class="comment-login-prompt">
          <span class="comment-login-text">登录后可发表评论</span>
          <el-button type="primary" @click="goToLogin">登录</el-button>
        </div>

        <div v-if="commentsLoading" class="comment-loading">
          <el-skeleton :rows="3" animated />
        </div>
        <el-empty v-else-if="!rootComments.length" description="还没有评论，来做第一个吧" />
        <div v-else class="comment-list">
          <div v-for="item in rootComments" :key="item.id" class="comment-item">
            <div class="comment-item-body">
              <el-avatar
                :size="36"
                :src="getCommentAvatar(item)"
                class="comment-item-avatar"
                @click="goUserProfile(item.userId)"
              >
                {{ getCommentAvatarFallback(item.userName) }}
              </el-avatar>
              <div class="comment-item-main">
                <div class="comment-item-head">
                  <span class="comment-user">{{ item.userName || '匿名用户' }}</span>
                  <span class="comment-time">{{ formatDate(item.createTime) }}</span>
                </div>
                <p class="comment-content">{{ item.content }}</p>
                <div v-if="item.images && item.images.length" class="comment-images">
                  <el-image
                    v-for="(img, idx) in item.images"
                    :key="`${item.id}-img-${idx}`"
                    :src="img"
                    fit="cover"
                    class="comment-image"
                  />
                </div>
                <div class="comment-item-footer">
                  <span v-if="item.likes != null" class="comment-meta">👍 {{ formatNum(item.likes) }}</span>
              <el-button
                v-if="item.replyCount > 0"
                link
                class="comment-reply-btn"
                @click="toggleReplies(item)"
              >
                {{ expandedRoots.has(String(item.id)) ? '收起' : '展开' }}
                {{ item.replyCount }} 条回复
              </el-button>
                  <el-button link class="comment-reply-btn" @click="startReply(item, item)">回复</el-button>
                </div>

                <div v-if="expandedRoots.has(String(item.id))" class="reply-list">
                  <div v-if="repliesLoadingMap[String(item.id)]" class="comment-loading">
                    <el-skeleton :rows="2" animated />
                  </div>
                  <template v-else>
                    <div
                      v-for="reply in repliesMap[String(item.id)] || []"
                      :key="reply.id"
                      class="reply-item"
                    >
                      <el-avatar
                        :size="28"
                        :src="getCommentAvatar(reply)"
                        class="comment-item-avatar reply-item-avatar"
                        @click="goUserProfile(reply.userId)"
                      >
                        {{ getCommentAvatarFallback(reply.userName) }}
                      </el-avatar>
                      <div class="reply-item-main">
                        <div class="reply-item-head">
                          <span class="comment-user">{{ reply.userName || '匿名用户' }}</span>
                          <span v-if="reply.replyToUserName" class="reply-target">回复 @{{ reply.replyToUserName }}</span>
                          <span class="comment-time">{{ formatDate(reply.createTime) }}</span>
                        </div>
                        <p class="comment-content">{{ reply.content }}</p>
                        <div class="reply-item-footer">
                          <el-button link class="comment-reply-btn" @click="startReply(item, reply)">回复</el-button>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>

                <div v-if="replyDraftRootId === String(item.id)" class="reply-editor">
                  <el-input
                    v-model="replyDraft"
                    type="textarea"
                    :rows="2"
                    maxlength="2000"
                    :placeholder="`回复 ${replyToName || 'TA'}…`"
                  />
                  <div class="comment-editor-actions">
                    <el-button size="small" @click="cancelReply">取消</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="replySubmitting"
                      :disabled="replySubmitting || !replyDraft.trim()"
                      @click="submitReply()"
                    >
                      发送回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="commentHasMore" class="comment-load-more">
          <el-button :loading="commentsLoading" @click="loadMoreComments">加载更多</el-button>
        </div>
        </template>
      </el-card>

      <!-- 底部提示 -->
      <div class="detail-footer-tip">
        <span>只因“梗”懂你 · 让每一次会心一笑都有出处。</span>
      </div>
    </div>

    <!-- 收藏夹选择弹窗 -->
    <el-dialog
      v-model="favoriteDialogVisible"
      :title="isFavorited ? '管理收藏' : '收藏到收藏夹'"
      width="440px"
      append-to-body
      class="favorite-folder-dialog"
    >
      <div v-loading="favoriteFoldersLoading" class="favorite-folder-picker">
        <div
          v-for="f in favoriteFolders"
          :key="f.id"
          class="favorite-folder-item"
          :class="{ active: sameFolderId(selectedFolderId, f.id) }"
          @click="selectedFolderId = normalizeFolderId(f.id)"
        >
          <div class="favorite-folder-icon">{{ f.isDefault ? '☆' : '📁' }}</div>
          <div class="favorite-folder-meta">
            <div class="favorite-folder-name">
              {{ f.name }}
              <el-tag v-if="f.isDefault" size="small" type="info" effect="plain">默认</el-tag>
              <el-tag v-else-if="Number(f.isPublic) === 0" size="small" type="warning" effect="plain">私密</el-tag>
            </div>
            <div class="favorite-folder-count">{{ f.memeCount || 0 }} 个梗图</div>
          </div>
          <span v-if="sameFolderId(selectedFolderId, f.id)" class="favorite-folder-check">✓</span>
        </div>
        <div class="favorite-folder-item favorite-folder-create" @click="openCreateFolderDialog">
          <div class="favorite-folder-icon favorite-folder-icon-create">＋</div>
          <div class="favorite-folder-meta">
            <div class="favorite-folder-name">新建收藏夹</div>
            <div class="favorite-folder-count">创建自定义分类</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button v-if="isFavorited" @click="removeFavoriteFromDialog" :loading="favoriteLoading">
          取消收藏
        </el-button>
        <el-button @click="favoriteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="favoriteLoading" @click="confirmFavorite">
          {{ isFavorited && Number(selectedFolderId) === Number(currentFavoriteFolderId) ? '确定' : (isFavorited ? '移动到此夹' : '收藏到此夹') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建收藏夹弹窗 -->
    <el-dialog
      v-model="createFolderDialogVisible"
      title="新建收藏夹"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="72px" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="createFolderForm.name" maxlength="64" show-word-limit placeholder="收藏夹名称" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="createFolderForm.isPublic">
            <el-radio :label="1">公开</el-radio>
            <el-radio :label="0">私密</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createFolderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createFolderSubmitting" @click="submitCreateFolder">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { useMemeDetailStore } from '@/stores/memeDetail'
import { useAuthStore } from '@/stores/auth'
import { addMemeFavorite, removeMemeFavorite, moveMemeFavorite, getMemeFavoriteStatus, addMemeLike, removeMemeLike, reportMemeView, getMemeRootComments, getMemeCommentReplies, addMemeComment } from '@/api/meme'
import { getMyFavoriteFolders, createFavoriteFolder, normalizeFolderId, sameFolderId } from '@/api/favoriteFolder'
import { uploadToOss } from '@/api/oss'
import { isAuthErrorHandled } from '@/utils/authSession'
import MemeDetailPreviewBanner from '@/components/meme/MemeDetailPreviewBanner.vue'
import MemeDetailFavoriteBtn from '@/components/meme/MemeDetailFavoriteBtn.vue'
import MemeDetailLikeBtn from '@/components/meme/MemeDetailLikeBtn.vue'
import FollowButton from '@/components/user/FollowButton.vue'
import { sanitizeExternalUrl } from '@/utils/safeUrl'
import { watch, computed, ref, onUnmounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import { buildUserProfileLocation, buildSearchLocation } from '@/utils/pageBreadcrumb'

const route = useRoute()
const router = useRouter()
const memeDetailStore = useMemeDetailStore()
const authStore = useAuthStore()
const breadcrumbStore = useBreadcrumbStore()

const { meme, loading, error, isFavorited, isLiked } = storeToRefs(memeDetailStore)
const detailErrorCode = computed(() => memeDetailStore.errorCode)
const detailTags = computed(() => memeDetailStore.tags)
const detailLinks = computed(() => memeDetailStore.links)

const ownerPreview = computed(() => memeDetailStore.isOwnerPreview)

watch(
  [meme, loading, error, ownerPreview],
  () => {
    breadcrumbStore.setPatch({
      meme: meme.value,
      loading: loading.value,
      error: error.value,
      ownerPreview: ownerPreview.value,
    })
  },
  { immediate: true, deep: true }
)

const memeStatusLabel = computed(() => {
  const desc = meme.value?.statusDesc || meme.value?.status_desc
  if (desc) return desc
  const status = Number(meme.value?.status)
  if (status === 2) return '审核中'
  if (status === 3) return '已下架'
  return ''
})
const authorInfo = computed(() => {
  const raw = meme.value?.author
  return raw && typeof raw === 'object' ? raw : null
})
const authorUserId = computed(() => {
  const id = authorInfo.value?.userId
  return id != null ? String(id).trim() : ''
})
const authorNickname = computed(() => {
  const name = authorInfo.value?.nickname
  const text = name != null ? String(name).trim() : ''
  return text || '匿名用户'
})
const authorAvatar = computed(() => {
  const avatar = authorInfo.value?.avatar
  return avatar != null ? String(avatar).trim() : ''
})
const authorAvatarFallback = computed(() => authorNickname.value.charAt(0).toUpperCase())

const authorFollowed = ref(false)
const authorMutual = ref(false)
const isAuthorSelf = computed(() => {
  const me = authStore.currentUser
  if (!me || !authorUserId.value) return false
  const mid = me.id != null ? String(me.id) : me.userId != null ? String(me.userId) : ''
  return !!mid && mid === authorUserId.value
})

watch(authorUserId, () => {
  authorFollowed.value = false
  authorMutual.value = false
})

function onAuthorFollowChange(payload) {
  if (!payload) return
  authorFollowed.value = Boolean(payload.followed)
  authorMutual.value = Boolean(payload.mutual)
}

const commentsEnabled = computed(() => {
  if (!meme.value) return false
  if (meme.value.viewMode === 'owner_preview') return false
  if (meme.value.commentsEnabled != null) return !!meme.value.commentsEnabled
  if (meme.value.comments_enabled != null) return !!meme.value.comments_enabled
  return Number(meme.value.status) === 1
})
const previewCommentHint = computed(() => {
  if (Number(meme.value?.status) === 3) {
    return '下架梗不会出现在公域，评论区也不会对外开放。'
  }
  return '审核通过后才会在首页展示，并开放评论与收藏。'
})

const isNotFoundError = computed(() => {
  const code = detailErrorCode.value
  if (code === 404 || code === 0) return true
  const msg = String(error.value || '')
  return msg.includes('不存在') || msg.includes('找不到')
})

const detailErrorIcon = computed(() => (isNotFoundError.value ? 'info' : 'warning'))
const detailErrorTitle = computed(() => {
  if (isNotFoundError.value && authStore.isLoggedIn) {
    return '这条梗暂时看不了'
  }
  if (isNotFoundError.value) return '梗不存在'
  return '加载失败'
})
const detailErrorSubtitle = computed(() => {
  if (isNotFoundError.value && authStore.isLoggedIn) {
    return '可能还在审核、已下架，或已被彻底删除。审核中/已下架的梗只有发布者本人能预览。'
  }
  if (isNotFoundError.value) {
    return '链接可能有误，或者这条梗还没公开展示。登录发布者账号后可预览审核中的梗。'
  }
  return error.value || '网络开小差了，稍后再试'
})

const normalizedLinks = computed(() => {
  const items = []
  for (const link of detailLinks.value) {
    if (!link) continue
    if (link.url) {
      const type = String(link.type || 'link').toLowerCase()
      const url = sanitizeExternalUrl(String(link.url).trim())
      if (!url) continue
      items.push({
        key: `link-${link.id ?? items.length}`,
        url,
        displayTitle: String(link.title || link.url).trim(),
        type,
        isMedia: type === 'media' || type === 'image',
      })
      continue
    }
    for (const [idx, url] of safeUrls(link.resourceUrl).entries()) {
      items.push({
        key: `legacy-${link.id ?? items.length}-${idx}`,
        url,
        displayTitle: url,
        type: 'link',
        isMedia: false,
      })
    }
  }
  return items.filter((item) => item.url)
})

const memeId = computed(() => route.params.id || route.query.memeId)
const favoriteLoading = ref(false)
const likeLoading = ref(false)
const previewRefreshing = ref(false)
let favoriteDebounceTimer = null
let viewReportTimer = null
let viewReportSeq = 0

// 收藏夹选择弹窗
const favoriteDialogVisible = ref(false)
const favoriteFolders = ref([])
const favoriteFoldersLoading = ref(false)
const selectedFolderId = ref('0')
const currentFavoriteFolderId = ref(null)

const createFolderDialogVisible = ref(false)
const createFolderSubmitting = ref(false)
const createFolderForm = ref({ name: '', isPublic: 1 })

const commentSortType = ref('new')
const rootComments = ref([])
const commentTotal = ref(0)
const commentHasMore = ref(false)
const commentPage = ref(1)
const commentsLoading = ref(false)
const commentDraft = ref('')
const commentSubmitting = ref(false)
const expandedRoots = ref(new Set())
const repliesMap = reactive({})
const repliesLoadingMap = reactive({})
const replyDraftRootId = ref('')
const replyDraft = ref('')
const replyParentId = ref(null)
const replyToName = ref('')
const replySubmitting = ref(false)
const commentEditorFocused = ref(false)
const commentImages = ref([])
const commentImageUploading = ref(false)

const currentUserAvatar = computed(() => {
  const avatar = authStore.currentUser?.avatar
  return avatar != null ? String(avatar).trim() : ''
})

const commentAvatarFallback = computed(() => {
  const user = authStore.currentUser
  const name = user?.nickname || user?.username || 'U'
  return String(name).charAt(0).toUpperCase()
})

function clearFavoriteDebounceTimer() {
  if (favoriteDebounceTimer) {
    clearTimeout(favoriteDebounceTimer)
    favoriteDebounceTimer = null
  }
}

function clearViewReportTimer() {
  if (viewReportTimer) {
    clearTimeout(viewReportTimer)
    viewReportTimer = null
  }
}

function scheduleViewReport() {
  clearViewReportTimer()
  if (ownerPreview.value) return

  const routeId = memeId.value != null ? String(memeId.value).trim() : ''
  const loaded = meme.value
  if (!routeId || !loaded || loaded.id == null || String(loaded.id) !== routeId) {
    return
  }

  const seq = ++viewReportSeq
  viewReportTimer = setTimeout(async () => {
    if (seq !== viewReportSeq) return
    if (ownerPreview.value) return
    const currentId = memeId.value != null ? String(memeId.value).trim() : ''
    if (!currentId || currentId !== routeId) return

    try {
      const res = await reportMemeView(currentId, { source: 'detail' })
      if (seq !== viewReportSeq) return
      if (res?.pageViews != null) {
        memeDetailStore.setPageViews(res.pageViews)
      }
    } catch {
      // 浏览上报失败静默处理，不影响详情页体验
    }
  }, 1000)
}

watch(
  memeId,
  (id) => {
    clearFavoriteDebounceTimer()
    clearViewReportTimer()
    viewReportSeq += 1
    resetCommentState()
    memeDetailStore.fetchDetail(id)
  },
  { immediate: true }
)

/** 详情加载完成后延迟上报浏览（公域梗、非预览） */
const viewReportBootstrapKey = computed(() => {
  const routeId = memeId.value != null ? String(memeId.value).trim() : ''
  const loaded = meme.value
  if (!routeId || !loaded || loaded.id == null || String(loaded.id) !== routeId) {
    return ''
  }
  if (ownerPreview.value) return ''
  return routeId
})

watch(viewReportBootstrapKey, (key, prevKey) => {
  if (!key || key === prevKey) return
  scheduleViewReport()
})

/** 评论区仅在梗 id / 开放状态变化时初始化，避免点赞等局部更新触发重载 */
const commentBootstrapKey = computed(() => {
  const routeId = memeId.value != null ? String(memeId.value).trim() : ''
  const loaded = meme.value
  if (!routeId || !loaded || loaded.id == null || String(loaded.id) !== routeId) {
    return ''
  }
  const enabled = loaded.commentsEnabled ?? loaded.comments_enabled
  if (enabled === false) return ''
  return `${routeId}:${enabled === true ? 1 : 0}`
})

watch(commentBootstrapKey, (key, prevKey) => {
  if (!key || key === prevKey) return
  resetCommentState()
  loadComments(true)
})

onUnmounted(() => {
  clearFavoriteDebounceTimer()
  clearViewReportTimer()
  viewReportSeq += 1
})

function reload() {
  memeDetailStore.fetchDetail(memeId.value)
}

async function handlePreviewRefresh() {
  const prevStatus = Number(meme.value?.status)
  previewRefreshing.value = true
  try {
    await memeDetailStore.fetchDetail(memeId.value)
    const nextStatus = Number(memeDetailStore.meme?.status)
    const stillPreview = memeDetailStore.isOwnerPreview
    if (!stillPreview && prevStatus === 2 && nextStatus === 1) {
      ElMessage.success('审核已通过，已进入公开展示')
      loadComments(true)
      return
    }
    if (stillPreview) {
      ElMessage.success('状态已刷新')
    }
  } finally {
    previewRefreshing.value = false
  }
}

function resetCommentState() {
  rootComments.value = []
  commentTotal.value = 0
  commentHasMore.value = false
  commentPage.value = 1
  commentDraft.value = ''
  commentEditorFocused.value = false
  commentImages.value = []
  expandedRoots.value = new Set()
  Object.keys(repliesMap).forEach((key) => delete repliesMap[key])
  Object.keys(repliesLoadingMap).forEach((key) => delete repliesLoadingMap[key])
  cancelReply()
}

async function loadComments(reset = false) {
  const id = memeId.value
  if (!id || !commentsEnabled.value) return
  if (reset) {
    commentPage.value = 1
    rootComments.value = []
  }
  commentsLoading.value = true
  try {
    const data = await getMemeRootComments(id, {
      page: commentPage.value,
      size: 10,
      sortType: commentSortType.value,
    })
    const list = Array.isArray(data.list) ? data.list : []
    rootComments.value = reset ? list : rootComments.value.concat(list)
    commentTotal.value = Number(data.total) || rootComments.value.length
    commentHasMore.value = !!data.hasMore
  } catch (e) {
    if (commentsEnabled.value) {
      ElMessage.error(e.message || '加载评论失败')
    }
  } finally {
    commentsLoading.value = false
  }
}

function reloadComments() {
  loadComments(true)
}

function loadMoreComments() {
  if (commentsLoading.value || !commentHasMore.value) return
  commentPage.value += 1
  loadComments(false)
}

function goToLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function goHome() {
  router.push({ name: 'home' })
}

function goMyPublished() {
  const user = authStore.currentUser
  const rawId = user?.id ?? user?.userId
  const userId = rawId != null ? String(rawId).trim() : ''
  if (!userId || !/^\d+$/.test(userId)) {
    goToLogin()
    return
  }
  router.push(
    buildUserProfileLocation(userId, {
      fromRoute: route,
      memeName: meme.value?.name,
      tab: 'published',
    })
  )
}

function goUserProfile(rawId) {
  const userId = rawId != null ? String(rawId).trim() : ''
  if (!userId || !/^\d+$/.test(userId)) return
  router.push(
    buildUserProfileLocation(userId, {
      fromRoute: route,
      memeName: meme.value?.name,
    })
  )
}

function goCurrentUserProfile() {
  const user = authStore.currentUser
  const rawId = user?.id ?? user?.userId
  const userId = rawId != null ? String(rawId).trim() : ''
  if (!userId || !/^\d+$/.test(userId)) {
    ElMessage.warning('登录态中的用户ID异常，请重新登录后再试')
    goToLogin()
    return
  }
  goUserProfile(userId)
}

function getCommentAvatar(comment) {
  const avatar = comment?.userAvatar ?? comment?.avatar
  return avatar != null ? String(avatar).trim() : ''
}

function getCommentAvatarFallback(name) {
  const label = name || 'U'
  return String(label).charAt(0).toUpperCase()
}

function onCommentEditorBlur() {
  window.setTimeout(() => {
    if (!commentDraft.value.trim()) {
      commentEditorFocused.value = false
    }
  }, 150)
}

function requireLoginForComment() {
  if (authStore.isLoggedIn) return true
  goToLogin()
  return false
}

async function onCommentImageChange(event) {
  if (!requireLoginForComment()) {
    event.target.value = ''
    return
  }
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (commentImages.value.length >= 3) {
    ElMessage.warning('最多上传 3 张图片')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单张图片不能超过 10MB')
    return
  }
  commentImageUploading.value = true
  try {
    const url = await uploadToOss(file, 'comment')
    commentImages.value.push(url)
  } catch (e) {
    ElMessage.error(e.message || '图片上传失败')
  } finally {
    commentImageUploading.value = false
  }
}

function removeCommentImage(idx) {
  commentImages.value.splice(idx, 1)
}

async function submitRootComment() {
  if (!requireLoginForComment()) return
  const content = commentDraft.value.trim()
  if (!content && !commentImages.value.length) return
  commentSubmitting.value = true
  try {
    const data = await addMemeComment({
      memeId: memeId.value,
      rootId: '0',
      parentId: '0',
      content,
      imageUrls: commentImages.value.slice(),
    })
    commentDraft.value = ''
    const uploadedImages = commentImages.value.slice()
    commentImages.value = []
    commentEditorFocused.value = false
    rootComments.value.unshift({
      id: data.commentId,
      userId: authStore.currentUser?.id,
      userName: authStore.currentUser?.nickname || authStore.currentUser?.username || '我',
      userAvatar: currentUserAvatar.value,
      content: data.content,
      images: uploadedImages,
      replyCount: 0,
      likes: 0,
      createTime: data.createTime,
    })
    commentTotal.value += 1
    if (meme.value) {
      meme.value.comments = (Number(meme.value.comments) || 0) + 1
    }
    ElMessage.success('评论成功')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    ElMessage.error(e.message || '发表评论失败')
  } finally {
    commentSubmitting.value = false
  }
}

function startReply(rootItem, parentItem) {
  if (!requireLoginForComment()) return
  replyDraftRootId.value = String(rootItem.id)
  replyParentId.value = parentItem.id
  replyToName.value = parentItem.userName || 'TA'
  replyDraft.value = ''
}

function cancelReply() {
  replyDraftRootId.value = ''
  replyParentId.value = null
  replyToName.value = ''
  replyDraft.value = ''
}

async function submitReply() {
  if (!requireLoginForComment()) return
  const content = replyDraft.value.trim()
  if (!content) return
  const rootId = replyDraftRootId.value
  const parentId = replyParentId.value
  if (!rootId || parentId == null) return
  replySubmitting.value = true
  try {
    const data = await addMemeComment({
      memeId: memeId.value,
      rootId,
      parentId: String(parentId),
      content,
      imageUrls: [],
    })
    if (!repliesMap[rootId]) {
      repliesMap[rootId] = []
    }
    repliesMap[rootId].push({
      id: data.commentId,
      parentId,
      userId: authStore.currentUser?.id,
      userName: authStore.currentUser?.nickname || authStore.currentUser?.username || '我',
      userAvatar: currentUserAvatar.value,
      content: data.content,
      images: [],
      createTime: data.createTime,
    })
    const root = rootComments.value.find((c) => String(c.id) === rootId)
    if (root) {
      root.replyCount = (Number(root.replyCount) || 0) + 1
    }
    expandedRoots.value.add(rootId)
    cancelReply()
    if (meme.value) {
      meme.value.comments = (Number(meme.value.comments) || 0) + 1
    }
    ElMessage.success('回复成功')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    ElMessage.error(e.message || '发表回复失败')
  } finally {
    replySubmitting.value = false
  }
}

async function toggleReplies(item) {
  const key = String(item.id)
  if (expandedRoots.value.has(key)) {
    expandedRoots.value.delete(key)
    return
  }
  expandedRoots.value.add(key)
  if (Array.isArray(repliesMap[key]) && repliesMap[key].length) {
    return
  }
  repliesLoadingMap[key] = true
  try {
    repliesMap[key] = await getMemeCommentReplies(item.id, { page: 1, size: 50 })
  } catch (e) {
    ElMessage.error(e.message || '加载回复失败')
    expandedRoots.value.delete(key)
  } finally {
    repliesLoadingMap[key] = false
  }
}

function handleFavoriteClick() {
  clearFavoriteDebounceTimer()
  favoriteDebounceTimer = setTimeout(() => {
    favoriteDebounceTimer = null
    openFavoriteDialog()
  }, 300)
}

function handleLikeClick() {
  toggleLike()
}

async function toggleLike() {
  if (ownerPreview.value) return
  if (!authStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  const id = memeId.value
  if (!id || likeLoading.value) return

  const prevLiked = isLiked.value
  const prevCount = Number(meme.value?.likes) || 0
  const nextLiked = !prevLiked
  const nextCount = Math.max(0, prevCount + (nextLiked ? 1 : -1))

  memeDetailStore.applyLikeState({ liked: nextLiked, likeCount: nextCount })

  likeLoading.value = true
  try {
    const res = nextLiked ? await addMemeLike(id) : await removeMemeLike(id)
    memeDetailStore.applyLikeState({
      liked: nextLiked,
      likeCount: res?.likeCount != null ? res.likeCount : nextCount,
    })
  } catch (e) {
    memeDetailStore.applyLikeState({ liked: prevLiked, likeCount: prevCount })
    if (!isAuthErrorHandled(e)) {
      ElMessage.error(e.message || (prevLiked ? '取消点赞失败' : '点赞失败'))
    }
  } finally {
    likeLoading.value = false
  }
}

async function openFavoriteDialog() {
  if (!authStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  const id = memeId.value
  if (!id) return
  favoriteDialogVisible.value = true
  favoriteFoldersLoading.value = true
  selectedFolderId.value = '0'
  currentFavoriteFolderId.value = isFavorited.value ? '0' : null
  try {
    const { folders } = await getMyFavoriteFolders()
    favoriteFolders.value = folders || []
    if (isFavorited.value) {
      // 已收藏：默认选中当前夹
      try {
        const status = await getMemeFavoriteStatus(id)
        if (status && status.favorited && status.folderId != null) {
          currentFavoriteFolderId.value = status.folderId
          selectedFolderId.value = status.folderId
        }
      } catch (ignored) {
        // 状态查询失败不影响选夹
      }
    }
  } catch (e) {
    ElMessage.error((e && e.message) || '加载收藏夹失败')
    favoriteDialogVisible.value = false
  } finally {
    favoriteFoldersLoading.value = false
  }
}

async function confirmFavorite() {
  const id = memeId.value
  if (!id || favoriteLoading.value) return
  const folderId = normalizeFolderId(selectedFolderId.value)
  favoriteLoading.value = true
  try {
    if (isFavorited.value) {
      const current = currentFavoriteFolderId.value
      if (current != null && sameFolderId(current, folderId)) {
        await removeMemeFavorite(id)
        memeDetailStore.setFavorited(false)
        ElMessage.success('已取消收藏')
      } else {
        await moveMemeFavorite(id, folderId)
        currentFavoriteFolderId.value = folderId
        ElMessage.success('已移动到「' + folderName(folderId) + '」')
      }
    } else {
      await addMemeFavorite(id, folderId)
      memeDetailStore.setFavorited(true)
      currentFavoriteFolderId.value = folderId
      ElMessage.success('已收藏到「' + folderName(folderId) + '」')
    }
    favoriteDialogVisible.value = false
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    ElMessage.error((e && e.message) || '操作失败，请稍后重试')
  } finally {
    favoriteLoading.value = false
  }
}

function folderName(folderId) {
  const f = favoriteFolders.value.find((x) => sameFolderId(x.id, folderId))
  return f ? f.name : '默认收藏夹'
}

function openCreateFolderDialog() {
  createFolderForm.value = { name: '', isPublic: 1 }
  createFolderDialogVisible.value = true
}

async function submitCreateFolder() {
  const name = (createFolderForm.value.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入收藏夹名称')
    return
  }
  createFolderSubmitting.value = true
  try {
    const created = await createFavoriteFolder({
      name,
      isPublic: createFolderForm.value.isPublic,
    })
    const { folders } = await getMyFavoriteFolders()
    favoriteFolders.value = folders || []
    const newId = created?.id != null ? normalizeFolderId(created.id) : null
    if (newId != null) {
      selectedFolderId.value = newId
    }
    createFolderDialogVisible.value = false
    ElMessage.success('收藏夹已创建')
  } catch (e) {
    ElMessage.error((e && e.message) || '创建失败')
  } finally {
    createFolderSubmitting.value = false
  }
}

async function removeFavoriteFromDialog() {
  const id = memeId.value
  if (!id || favoriteLoading.value) return
  favoriteLoading.value = true
  try {
    await removeMemeFavorite(id)
    memeDetailStore.setFavorited(false)
    ElMessage.success('已取消收藏')
    favoriteDialogVisible.value = false
  } catch (e) {
    ElMessage.error((e && e.message) || '取消收藏失败')
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

function linkTypeIcon(type) {
  const normalized = String(type || 'link').toLowerCase()
  if (normalized === 'video') return '▶'
  if (normalized === 'article') return '📄'
  if (normalized === 'media' || normalized === 'image') return '🖼'
  return '🔗'
}

function safeUrls(resourceUrl) {
  if (!Array.isArray(resourceUrl)) return []
  return resourceUrl
    .map((u) => sanitizeExternalUrl(String(u || '').trim()))
    .filter(Boolean)
}

const IMAGE_EXT_RE = /\.(jpe?g|png|webp|gif|bmp|svg)(\?.*)?$/i

function isImageUrl(url) {
  if (!url) return false
  return IMAGE_EXT_RE.test(String(url))
}

function goSearchByTag(tag) {
  if (!tag || !tag.name) return
  router.push(
    buildSearchLocation({
      keyword: tag.name,
      fromRoute: route,
      memeName: meme.value?.name,
    })
  )
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

.detail-loading,
.detail-error,
.detail-empty {
  padding: 40px 0;
}

.detail-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-content--preview {
  gap: 16px;
}

.detail-hero-card {
  border-radius: var(--meme-radius-xl);
  border: 1px solid var(--meme-border);
  background: var(--meme-gradient-hero);
  box-shadow: var(--meme-shadow-card);
  overflow: hidden;
}

.detail-hero-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.detail-hero-card--preview {
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-gradient-card);
}

.detail-hero-row {
  align-items: stretch;
}

.detail-cover-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  min-height: 180px;
  max-height: 280px;
  border-radius: var(--meme-radius-lg);
  background:
    radial-gradient(circle at 20% 20%, var(--meme-primary-soft), transparent 45%),
    var(--meme-bg-cover);
  overflow: hidden;
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--meme-text-muted);
  font-size: 13px;
}

.detail-cover-fallback-icon {
  font-size: 28px;
  opacity: 0.55;
}

.detail-cover-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 12px;
  background: linear-gradient(transparent, rgba(15, 23, 42, 0.78));
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.detail-cover-overlay--mobile {
  display: flex;
}

.detail-cover-overlay--muted {
  background: linear-gradient(transparent, rgba(15, 23, 42, 0.72));
  justify-content: center;
}

.overlay-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  letter-spacing: 0.02em;
}

.overlay-stat--hint {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.detail-meta {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 2px 0 0;
}

.detail-meta-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-meta-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.detail-meta-like,
.detail-meta-favorite {
  flex-shrink: 0;
}

.detail-title-block {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}

.detail-title-block .detail-title {
  flex: 1 1 100%;
}

.detail-title {
  margin: 0;
  font-size: clamp(20px, 2.2vw, 26px);
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.detail-author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  margin: 2px 0 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.detail-follow-btn {
  margin: 2px 0 0;
  flex-shrink: 0;
}

.detail-author:hover .detail-author-name {
  color: var(--meme-primary);
}

.detail-author-avatar {
  flex-shrink: 0;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 12px;
}

.detail-author-name {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--meme-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.detail-stats-row {
  display: none;
  align-items: center;
  gap: 0;
  padding: 8px 12px;
  border-radius: var(--meme-radius-md);
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
}

.detail-stat-item {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.detail-stat-divider {
  width: 1px;
  height: 24px;
  background: var(--meme-border-strong);
  flex-shrink: 0;
}

.detail-stat-value {
  display: block;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--meme-text);
}

.detail-stat-label {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  font-weight: 600;
  color: var(--meme-text-muted);
}

.detail-meta-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
}

.detail-intro-panel {
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
}

.detail-intro-label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--meme-text-secondary);
}

.detail-intro-panel .detail-intro {
  padding-left: 10px;
  border-left: 3px solid var(--meme-primary);
}

.detail-intro {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-intro--placeholder {
  color: var(--meme-text-muted);
  font-style: normal;
}

.detail-tags-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-top: 2px;
}

.detail-tags-label {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--meme-text-secondary);
}

.detail-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag-chip {
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-primary-soft);
  color: var(--meme-accent-text);
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    transform 0.12s ease;
}

.detail-tag-chip:hover {
  background: var(--meme-primary-soft);
  border-color: var(--meme-primary);
}

.detail-tag-chip:active {
  transform: scale(0.98);
}

.detail-meta-footer {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--meme-border);
}

.detail-time {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.detail-time-item {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.detail-time-sep {
  color: var(--meme-text-muted);
  font-size: 12px;
  user-select: none;
}

@media (min-width: 768px) {
  .detail-cover-wrap {
    height: 100%;
    max-height: none;
    aspect-ratio: auto;
    min-height: 220px;
  }

  .detail-cover-overlay--mobile:not(.detail-cover-overlay--muted) {
    display: none;
  }

  .detail-stats-row {
    display: flex;
  }
}

@media (max-width: 767px) {
  .detail-hero-card :deep(.el-card__body) {
    padding: 16px;
  }

  .detail-meta {
    gap: 10px;
    padding-top: 2px;
  }

  .detail-meta-head {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .detail-meta-actions {
    width: 100%;
  }

  .detail-meta-like,
  .detail-meta-favorite {
    flex: 1;
  }

  .detail-stat-value {
    font-size: 14px;
  }

  .detail-meta-body {
    padding: 10px 12px;
    gap: 8px;
  }
}

.detail-cover-preview-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(15, 23, 42, 0.62);
  backdrop-filter: blur(4px);
}

.detail-status-chip {
  align-self: flex-start;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.detail-status-chip--2 {
  color: var(--meme-accent-text);
  background: var(--meme-primary-soft);
}

.detail-status-chip--3 {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
}

.detail-comment-card--preview :deep(.el-card__header) {
  background: var(--meme-bg-muted);
}

.comment-preview-disabled {
  padding: 28px 16px;
  text-align: center;
  border-radius: var(--meme-radius-md);
  background: var(--meme-bg-muted);
  border: 1px dashed var(--meme-border);
}

.comment-preview-disabled-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meme-text-secondary);
}

.comment-preview-disabled-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--meme-text-muted);
}

.detail-section-card {
  border-radius: 20px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-card);
  overflow: hidden;
}

.detail-section-card :deep(.el-card__header) {
  padding: 18px 24px 14px;
  border-bottom: 1px solid var(--meme-border);
  background: var(--meme-gradient-card);
}

.detail-section-card :deep(.el-card__body) {
  padding: 20px 24px 24px;
}

.detail-section-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
}

.detail-section-sub {
  font-size: 13px;
  color: var(--meme-text-secondary);
  line-height: 1.5;
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

.detail-link-slot {
  display: inline-flex;
  align-items: flex-start;
}

.detail-link-item {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--meme-radius-md);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.detail-link-item:hover {
  background: var(--meme-bg-elevated);
  border-color: var(--meme-border-accent);
}

.detail-link-image-item {
  display: inline-block;
}

.detail-link-image {
  width: 160px;
  height: 120px;
  border-radius: var(--meme-radius-sm);
  overflow: hidden;
  border: 1px solid var(--meme-border);
  cursor: zoom-in;
}

.detail-link-image-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--meme-text-muted);
  font-size: 12px;
  background: var(--meme-bg-muted);
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
  color: var(--meme-text-muted);
  text-align: right;
}

.comment-header-row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-sort-tabs :deep(.el-radio-button__inner) {
  border: none;
  border-radius: 999px !important;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  color: var(--meme-text-secondary);
  background: transparent;
  box-shadow: none !important;
}

.comment-sort-tabs :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-left: none;
}

.comment-sort-tabs :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: var(--meme-text-inverse);
  background: var(--meme-primary);
}

.comment-sort-tabs {
  padding: 3px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
}

.comment-composer {
  margin-bottom: 20px;
  padding: 14px 16px;
  border-radius: var(--meme-radius-lg);
  background: var(--meme-gradient-card);
  border: 1px solid var(--meme-border);
}

.comment-editor {
  margin-bottom: 0;
}

.comment-editor-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.comment-editor-avatar {
  flex-shrink: 0;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.comment-editor-avatar:hover {
  opacity: 0.85;
}

.comment-editor-input-wrap {
  flex: 1;
  min-width: 0;
}

.comment-editor-input :deep(.el-textarea__inner) {
  min-height: 44px;
  padding: 11px 16px;
  line-height: 1.55;
  border: 1px solid var(--meme-border-strong);
  border-radius: 14px;
  box-shadow: var(--meme-shadow-soft);
  resize: none;
  background: var(--meme-bg-card);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.comment-editor-input :deep(.el-textarea__inner::placeholder) {
  color: var(--meme-text-muted);
}

.comment-editor-input :deep(.el-textarea__inner:focus) {
  border-color: var(--meme-primary);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
}

.comment-editor-actions {
  margin-top: 8px;
  padding-left: 52px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.comment-editor-images {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.comment-editor-image-item {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: var(--meme-radius-sm);
  overflow: hidden;
  border: 1px solid var(--meme-border);
}

.comment-editor-image-thumb {
  width: 100%;
  height: 100%;
}

.comment-editor-image-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--meme-overlay);
  color: var(--meme-text-inverse);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  border-bottom-left-radius: var(--meme-radius-sm);
}

.comment-editor-image-add {
  width: 64px;
  height: 64px;
  border: 1px dashed var(--meme-border-strong);
  border-radius: var(--meme-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--meme-text-muted);
  font-size: 12px;
  transition: border-color 0.2s, color 0.2s;
}

.comment-editor-image-add:hover {
  border-color: var(--meme-primary);
  color: var(--meme-primary);
}

.comment-editor-image-add-inner {
  pointer-events: none;
}

.comment-image-file-input {
  display: none;
}

.comment-login-prompt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px 18px;
  background: var(--meme-gradient-card);
  border: 1px solid var(--meme-border);
  border-radius: 14px;
}

.comment-login-text {
  font-size: 14px;
  color: var(--meme-text-secondary);
}

.comment-loading {
  padding: 12px 0;
}

.comment-list {
  display: flex;
  flex-direction: column;
}

.comment-item {
  padding: 18px 0;
  border-bottom: 1px solid var(--meme-border);
}

.comment-item-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-user {
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
}

.comment-time {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.comment-content {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-item:first-child {
  padding-top: 0;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-item-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.comment-item-avatar {
  flex-shrink: 0;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.comment-item-avatar:hover {
  opacity: 0.85;
}

.comment-item-main {
  flex: 1;
  min-width: 0;
}

.comment-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-image {
  width: 88px;
  height: 88px;
  border-radius: var(--meme-radius-sm);
}

.comment-item-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.comment-meta {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.comment-reply-btn.el-button.is-link {
  color: var(--meme-text-muted);
  font-size: 13px;
}

.comment-reply-btn.el-button.is-link:hover,
.comment-reply-btn.el-button.is-link:focus {
  color: var(--meme-primary);
}

.reply-editor {
  margin-top: 10px;
  padding: 10px;
  background: var(--meme-bg-muted);
  border-radius: 10px;
}

.reply-list {
  margin-top: 10px;
  padding-left: 0;
  border-left: none;
}

.reply-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--meme-border);
}

.reply-item:first-child {
  border-top: none;
  padding-top: 0;
}

.reply-item-main {
  flex: 1;
  min-width: 0;
}

.reply-item-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 2px;
}

.reply-item-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.reply-target {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.reply-item-avatar {
  margin-top: 2px;
}

.comment-load-more {
  margin-top: 12px;
  text-align: center;
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

/* 收藏夹选择弹窗 */
.favorite-folder-picker {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 2px;
}
.favorite-folder-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.favorite-folder-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.favorite-folder-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
.favorite-folder-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  font-size: 22px;
  color: var(--el-text-color-secondary);
}
.favorite-folder-meta {
  flex: 1;
  min-width: 0;
}
.favorite-folder-name {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-primary);
}
.favorite-folder-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.favorite-folder-check {
  color: var(--el-color-primary);
  font-weight: 700;
  font-size: 16px;
}
.favorite-folder-create {
  border-style: dashed;
  color: var(--el-color-primary);
}
.favorite-folder-create:hover {
  border-color: var(--el-color-primary);
}
.favorite-folder-icon-create {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
