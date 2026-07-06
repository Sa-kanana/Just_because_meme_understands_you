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
            <div
              v-for="(url, idx) in safeUrls(link.resourceUrl)"
              :key="`${link.id}-${idx}-${url}`"
              class="detail-link-slot"
            >
              <div v-if="isImageUrl(url)" class="detail-link-image-item">
                <el-image
                  :src="url"
                  fit="cover"
                  class="detail-link-image"
                  :preview-src-list="[url]"
                  preview-teleported
                >
                  <template #error>
                    <div class="detail-link-image-error">图片加载失败</div>
                  </template>
                </el-image>
              </div>
              <el-link
                v-else
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
        </div>
      </el-card>

      <!-- 评论区 -->
      <el-card class="detail-section-card detail-comment-card" shadow="never">
        <template #header>
          <div class="detail-section-header comment-header-row">
            <div>
              <h2 class="detail-section-title">评论区</h2>
              <span class="detail-section-sub">共 {{ formatNum(commentTotal) }} 条评论</span>
            </div>
            <el-radio-group v-model="commentSortType" size="small" @change="reloadComments">
              <el-radio-button label="new">最新</el-radio-button>
              <el-radio-button label="hot">最热</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <div v-if="authStore.isLoggedIn" class="comment-editor">
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
import { addMemeFavorite, removeMemeFavorite, moveMemeFavorite, getMemeFavoriteStatus, getMemeRootComments, getMemeCommentReplies, addMemeComment } from '@/api/meme'
import { getMyFavoriteFolders, createFavoriteFolder, normalizeFolderId, sameFolderId } from '@/api/favoriteFolder'
import { uploadToOss } from '@/api/oss'
import { isAuthErrorHandled } from '@/utils/authSession'
import { watch, computed, ref, onUnmounted, reactive } from 'vue'
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

watch(
  memeId,
  (id) => {
    clearFavoriteDebounceTimer()
    resetCommentState()
    memeDetailStore.fetchDetail(id)
    loadComments(true)
  },
  { immediate: true }
)

onUnmounted(() => {
  clearFavoriteDebounceTimer()
})

function reload() {
  memeDetailStore.fetchDetail(memeId.value)
  loadComments(true)
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
  if (!id) return
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
    ElMessage.error(e.message || '加载评论失败')
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

function goUserProfile(rawId) {
  const userId = rawId != null ? String(rawId).trim() : ''
  if (!userId || !/^\d+$/.test(userId)) return
  router.push({ name: 'userProfile', params: { userId } })
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

function safeUrls(resourceUrl) {
  if (!Array.isArray(resourceUrl)) return []
  return resourceUrl
    .map((u) => String(u || '').trim())
    .filter(Boolean)
}

const IMAGE_EXT_RE = /\.(jpe?g|png|webp|gif|bmp|svg)(\?.*)?$/i

function isImageUrl(url) {
  if (!url) return false
  return IMAGE_EXT_RE.test(String(url))
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

.detail-link-slot {
  display: inline-flex;
  align-items: flex-start;
}

.detail-link-item {
  max-width: 100%;
}

.detail-link-image-item {
  display: inline-block;
}

.detail-link-image {
  width: 160px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  cursor: zoom-in;
}

.detail-link-image-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 12px;
  background: #f3f4f6;
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

.comment-header-row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-editor {
  margin-bottom: 16px;
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
  min-height: 40px;
  padding: 10px 16px;
  line-height: 1.5;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  box-shadow: none;
  resize: none;
  background: #fff;
}

.comment-editor-input :deep(.el-textarea__inner::placeholder) {
  color: #9ca3af;
}

.comment-editor-input :deep(.el-textarea__inner:focus) {
  border-color: #c7d2fe;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
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
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
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
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  border-bottom-left-radius: 6px;
}

.comment-editor-image-add {
  width: 64px;
  height: 64px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #9ca3af;
  font-size: 12px;
  transition: border-color 0.2s, color 0.2s;
}

.comment-editor-image-add:hover {
  border-color: #409eff;
  color: #409eff;
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
  margin-bottom: 16px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 10px;
}

.comment-login-text {
  font-size: 14px;
  color: #6b7280;
}

.comment-loading {
  padding: 12px 0;
}

.comment-list {
  display: flex;
  flex-direction: column;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #e5e7eb;
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

.comment-item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.comment-user {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.comment-time {
  font-size: 12px;
  color: #9ca3af;
}

.comment-content {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
  white-space: pre-wrap;
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
  border-radius: 8px;
}

.comment-item-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.comment-meta {
  font-size: 12px;
  color: #6b7280;
}

.comment-reply-btn.el-button.is-link {
  color: #9ca3af;
  font-size: 13px;
}

.comment-reply-btn.el-button.is-link:hover,
.comment-reply-btn.el-button.is-link:focus {
  color: #409eff;
}

.reply-editor {
  margin-top: 10px;
  padding: 10px;
  background: #f9fafb;
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
  border-top: 1px solid #f3f4f6;
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
  color: #6b7280;
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