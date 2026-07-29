<template>
  <div class="page meme-detail-page">
    <div v-if="loading" class="detail-loading">
      <ui-skeleton :rows="5" animated />
    </div>
    <div v-else-if="error" class="detail-error">
      <ui-result
        :icon="detailErrorIcon"
        :title="detailErrorTitle"
        :sub-title="detailErrorSubtitle"
      >
        <template #extra>
          <div class="detail-error-actions">
            <vs-button v-if="authStore.isLoggedIn" @click="goMyPublished">
              回到我的发布
            </vs-button>
            <vs-button color="primary" @click="reload">重试</vs-button>
            <vs-button @click="goHome">返回主页</vs-button>
          </div>
        </template>
      </ui-result>
    </div>
    <div v-else-if="!meme" class="detail-empty">
      <ui-empty description="没有找到这个梗，可能被时光吃掉了～" />
    </div>
    <div v-else class="detail-content" :class="{ 'detail-content--preview': ownerPreview }">
      <MemeDetailPreviewBanner
        v-if="ownerPreview"
        :status="meme.status"
        :status-desc="meme.statusDesc || meme.status_desc"
        :refreshing="previewRefreshing"
        :restoring="restoreSubmitting"
        :purging="purgeSubmitting"
        @back-published="goMyPublished"
        @refresh="handlePreviewRefresh"
        @restore="handleRestorePublished"
        @purge="openPurgeDialog"
      />

      <!-- 顶部信息条：封面缩略图 + 信息流 -->
      <vs-card class="detail-hero-card" :class="{ 'detail-hero-card--preview': ownerPreview }">
        <template #text>
          <div class="detail-hero-layout">
            <div class="detail-cover-wrap">
              <ui-image
                v-if="meme.image && !coverLoadFailed"
                :src="meme.image"
                :alt="meme.name"
                fit="cover"
                class="detail-cover"
                @error="coverLoadFailed = true"
              />
              <div v-else class="detail-cover-fallback">
                <i class="ri-image-line detail-cover-fallback-icon" aria-hidden="true" />
                <span>{{ meme.image && coverLoadFailed ? '加载失败' : '暂无封面' }}</span>
              </div>
              <div v-if="ownerPreview" class="detail-cover-preview-tag">仅发布者可见</div>
            </div>

            <div class="detail-meta">
              <div class="detail-meta-top">
                <div class="detail-meta-main">
                  <span
                    v-if="ownerPreview && memeStatusLabel"
                    class="detail-status-chip"
                    :class="`detail-status-chip--${meme.status}`"
                  >
                    {{ memeStatusLabel }}
                  </span>
                  <h1 class="detail-title">{{ meme.name || '未命名梗' }}</h1>
                  <div class="detail-author-row">
                    <button
                      v-if="authorUserId"
                      type="button"
                      class="detail-author"
                      @click="goUserProfile(authorUserId)"
                    >
                      <ui-avatar
                        :src="authorAvatar"
                        :fallback="authorNickname"
                        :size="26"
                        class="detail-author-avatar"
                      />
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

              <div
                v-if="!ownerPreview"
                class="detail-stats-inline"
                role="group"
                aria-label="梗数据统计"
              >
                <span class="detail-stat-pill">
                  <i class="ri-eye-line" aria-hidden="true" />
                  <strong>{{ formatNum(meme.pageViews) }}</strong> 浏览
                </span>
                <span class="detail-stat-pill">
                  <i class="ri-thumb-up-line" aria-hidden="true" />
                  <strong>{{ formatNum(meme.likes) }}</strong> 点赞
                </span>
                <span class="detail-stat-pill">
                  <i class="ri-chat-3-line" aria-hidden="true" />
                  <strong>{{ formatNum(meme.comments) }}</strong> 评论
                </span>
              </div>

              <p
                class="detail-intro"
                :class="{ 'detail-intro--placeholder': !meme.introduction }"
              >
                <template v-if="meme.introduction">{{ meme.introduction }}</template>
                <template v-else>
                  {{ ownerPreview ? '预览模式下暂无介绍，可在发布页补充后再提交审核。' : '这个梗还没有详细介绍，欢迎在评论区补完故事。' }}
                </template>
              </p>

              <div class="detail-meta-foot">
                <div v-if="detailTags.length" class="detail-tags-list">
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
                <div v-if="meme.releaseTime || meme.updateTime" class="detail-time">
                  <span v-if="meme.releaseTime" class="detail-time-item">
                    {{ formatDate(meme.releaseTime) }}
                  </span>
                  <span v-if="meme.releaseTime && meme.updateTime" class="detail-time-sep">·</span>
                  <span v-if="meme.updateTime" class="detail-time-item">
                    更新于 {{ formatDate(meme.updateTime) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </vs-card>

      <!-- 相关链接 / 延伸阅读 -->
      <vs-card v-if="normalizedLinks.length" class="detail-section-card">
        <template #title>
          <div class="detail-section-header">
            <p class="meme-section-kicker">RELATED</p>
            <h2 class="detail-section-title">相关链接 · 延伸阅读</h2>
            <span class="detail-section-sub">
              帮你从不同角度更完整地理解这个梗
            </span>
          </div>
        </template>
        <template #text>
        <div class="detail-links">
          <div
            v-for="link in normalizedLinks"
            :key="link.key"
            class="detail-link-slot"
          >
            <div v-if="link.isMedia && isImageUrl(link.url)" class="detail-link-image-item">
              <ui-image
                :src="link.url"
                fit="cover"
                class="detail-link-image"
                @click="openImagePreview([link.url], 0)"
              />
            </div>
            <ui-link
              v-else
              :href="link.url"
              target="_blank"
              rel="noopener noreferrer"
              class="detail-link-item"
            >
              <span class="detail-link-icon">{{ linkTypeIcon(link.type) }}</span>
              <span class="detail-link-text">{{ link.displayTitle }}</span>
            </ui-link>
          </div>
        </div>
        </template>
      </vs-card>

      <!-- 评论区 -->
      <vs-card
        id="meme-comments"
        class="detail-section-card detail-comment-card"
        :class="{ 'detail-comment-card--preview': ownerPreview }"
      >
        <template #title>
          <div class="detail-section-header comment-header-row">
            <div>
              <p class="meme-section-kicker">COMMENTS</p>
              <h2 class="detail-section-title">
                {{ ownerPreview ? '评论区（预览未开放）' : '评论区' }}
              </h2>
              <span class="detail-section-sub">
                {{ ownerPreview ? '审核通过后将开放互动' : `共 ${formatNum(commentTotal)} 条评论` }}
              </span>
            </div>
            <div
              v-if="commentsEnabled"
              class="comment-sort-tabs"
              role="tablist"
              aria-label="评论排序"
            >
              <button
                type="button"
                role="tab"
                class="comment-sort-tab"
                :class="{ 'is-active': commentSortType === 'new' }"
                :aria-selected="commentSortType === 'new'"
                @click="setCommentSort('new')"
              >
                最新
              </button>
              <button
                type="button"
                role="tab"
                class="comment-sort-tab"
                :class="{ 'is-active': commentSortType === 'hot' }"
                :aria-selected="commentSortType === 'hot'"
                @click="setCommentSort('hot')"
              >
                最热
              </button>
            </div>
          </div>
        </template>
        <template #text>
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
              <ui-avatar
                :src="currentUserAvatar"
                :fallback="commentAvatarFallback"
                class="comment-editor-avatar"
                @click="goCurrentUserProfile"
              />
              <div class="comment-editor-main">
                <div class="comment-editor-input-wrap">
                  <vs-input
                    v-model="commentDraft"
                    type="textarea"
                    maxlength="2000"
                    class="comment-editor-input"
                    placeholder="只是一直在等你而已，才不是想被评论呢～"
                    block
                    @focus="commentEditorFocused = true"
                    @blur="onCommentEditorBlur"
                    @keydown.ctrl.enter.prevent="submitRootComment"
                    @keydown.meta.enter.prevent="submitRootComment"
                  />
                </div>
                <div
                  v-if="commentImages.length"
                  class="comment-editor-images"
                >
                  <div
                    v-for="(img, idx) in commentImages"
                    :key="`comment-draft-${idx}`"
                    class="comment-editor-image-item"
                  >
                    <ui-image
                      :src="img"
                      fit="cover"
                      class="comment-editor-image-thumb"
                      @click="openImagePreview(commentImages, idx)"
                    />
                    <button
                      type="button"
                      class="comment-editor-image-remove"
                      aria-label="移除图片"
                      @click="removeCommentImage(idx)"
                    >
                      ×
                    </button>
                  </div>
                </div>
                <div class="comment-editor-toolbar">
                  <div class="comment-editor-tools">
                    <label class="comment-tool-btn" :class="{ 'is-disabled': commentImages.length >= 3 || commentImageUploading }">
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp,image/gif"
                        class="comment-image-file-input"
                        :disabled="commentImages.length >= 3 || commentImageUploading"
                        @change="onCommentImageChange"
                      />
                      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                        <rect x="3.5" y="5" width="17" height="14" rx="2.5" stroke="currentColor" stroke-width="1.75" />
                        <circle cx="9" cy="10" r="1.6" fill="currentColor" />
                        <path d="M7 16.5 10.2 12.8a1 1 0 0 1 1.5 0L14 15l1.3-1.4a1 1 0 0 1 1.5.1L18.5 16.5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                      <span>{{ commentImageUploading ? '上传中' : '图片' }}</span>
                    </label>
                    <span class="comment-editor-hint">Ctrl + Enter 发送 · 最多 3 张图</span>
                  </div>
                  <vs-button
                    color="primary"
                    class="comment-submit-btn"
                    :loading="commentSubmitting"
                    :disabled="commentSubmitting || (!commentDraft.trim() && !commentImages.length)"
                    @click="submitRootComment"
                  >
                    发表评论
                  </vs-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="comment-login-prompt">
          <div class="comment-login-copy">
            <strong>登录后参与讨论</strong>
            <span>说说你对这个梗的看法，或补充出处与用法</span>
          </div>
          <vs-button color="primary" @click="goToLogin">去登录</vs-button>
        </div>

        <div v-if="commentsLoading" class="comment-loading">
          <ui-skeleton :rows="3" animated />
        </div>
        <div v-else-if="!rootComments.length" class="comment-empty">
          <ui-empty description="还没有评论，来做第一个吧" :image-size="72" />
        </div>
        <div v-else class="comment-list">
          <article
            v-for="item in rootComments"
            :key="item.id"
            :id="`comment-${item.id}`"
            class="comment-item"
            :class="{ 'is-focus-target': String(focusedCommentId) === String(item.id) }"
            :data-comment-id="item.id"
          >
            <div class="comment-item-body">
              <ui-avatar
                :src="getCommentAvatar(item)"
                :fallback="item.userName || 'U'"
                class="comment-item-avatar"
                @click="goUserProfile(item.userId)"
              />
              <div class="comment-item-main">
                <div class="comment-item-head">
                  <button type="button" class="comment-user" @click="goUserProfile(item.userId)">
                    {{ item.userName || '匿名用户' }}
                  </button>
                  <time class="comment-time" :datetime="item.createTime">{{ formatCommentTime(item.createTime) }}</time>
                </div>
                <p v-if="item.content" class="comment-content">{{ item.content }}</p>
                <div v-if="item.images && item.images.length" class="comment-images">
                  <ui-image
                    v-for="(img, idx) in item.images"
                    :key="`${item.id}-img-${idx}`"
                    :src="img"
                    fit="cover"
                    class="comment-image"
                    @click="openImagePreview(item.images, idx)"
                  />
                </div>
                <div class="comment-item-footer">
                  <button
                    type="button"
                    class="comment-action"
                    :class="{ 'is-liked': item.liked }"
                    :disabled="commentLikeLoadingMap[String(item.id)]"
                    @click="toggleCommentLike(item)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M7 10.5V20M7 10.5 10.5 4.5a1.5 1.5 0 0 1 2.6-.9L14 10.5h4.5a2 2 0 0 1 1.98 2.35l-1.2 6A2 2 0 0 1 17.32 20H7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    {{ formatNum(item.likes) }}
                  </button>
                  <button type="button" class="comment-action" @click="startReply(item, item)">
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M7.5 8.5h9M7.5 12h6M7.5 15.5h4" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
                      <path d="M6 5.5h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9.5L6 20.5V7.5a2 2 0 0 1 2-2Z" stroke="currentColor" stroke-width="1.75" stroke-linejoin="round" />
                    </svg>
                    回复
                  </button>
                  <button
                    v-if="item.replyCount > 0"
                    type="button"
                    class="comment-action"
                    :class="{ 'is-active': expandedRoots.has(String(item.id)) }"
                    @click="toggleReplies(item)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M8 9h12M4 15h12M10 6l-2 3 2 3M14 12l2 3-2 3" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    {{ expandedRoots.has(String(item.id)) ? '收起' : '展开' }}
                    {{ item.replyCount }} 条回复
                  </button>
                  <button
                    v-if="item.owner"
                    type="button"
                    class="comment-action comment-action--danger"
                    :disabled="commentDeleteLoadingMap[String(item.id)]"
                    @click="removeComment(item)"
                  >
                    删除
                  </button>
                </div>

                <div
                  v-if="expandedRoots.has(String(item.id)) || replyDraftRootId === String(item.id)"
                  class="reply-thread"
                >
                  <div v-if="expandedRoots.has(String(item.id))" class="reply-list">
                    <div v-if="repliesLoadingMap[String(item.id)]" class="comment-loading">
                      <ui-skeleton :rows="2" animated />
                    </div>
                    <template v-else>
                      <div
                        v-for="reply in repliesMap[String(item.id)] || []"
                        :key="reply.id"
                        :id="`comment-${reply.id}`"
                        class="reply-item"
                        :class="{ 'is-focus-target': String(focusedCommentId) === String(reply.id) }"
                        :data-comment-id="reply.id"
                      >
                        <ui-avatar
                          :src="getCommentAvatar(reply)"
                          :fallback="reply.userName || 'U'"
                          class="comment-item-avatar reply-item-avatar"
                          @click="goUserProfile(reply.userId)"
                        />
                        <div class="reply-item-main">
                          <div class="reply-item-head">
                            <button type="button" class="comment-user" @click="goUserProfile(reply.userId)">
                              {{ reply.userName || '匿名用户' }}
                            </button>
                            <span v-if="reply.replyToUserName" class="reply-target">
                              回复 <em>@{{ reply.replyToUserName }}</em>
                            </span>
                            <time class="comment-time" :datetime="reply.createTime">{{ formatCommentTime(reply.createTime) }}</time>
                          </div>
                          <p class="comment-content">{{ reply.content }}</p>
                          <div class="reply-item-footer">
                            <button
                              type="button"
                              class="comment-action"
                              :class="{ 'is-liked': reply.liked }"
                              :disabled="commentLikeLoadingMap[String(reply.id)]"
                              @click="toggleCommentLike(reply, item)"
                            >
                              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                <path d="M7 10.5V20M7 10.5 10.5 4.5a1.5 1.5 0 0 1 2.6-.9L14 10.5h4.5a2 2 0 0 1 1.98 2.35l-1.2 6A2 2 0 0 1 17.32 20H7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
                              </svg>
                              {{ formatNum(reply.likes) }}
                            </button>
                            <button type="button" class="comment-action" @click="startReply(item, reply)">
                              回复
                            </button>
                            <button
                              v-if="reply.owner"
                              type="button"
                              class="comment-action comment-action--danger"
                              :disabled="commentDeleteLoadingMap[String(reply.id)]"
                              @click="removeComment(reply, item)"
                            >
                              删除
                            </button>
                          </div>
                        </div>
                      </div>
                    </template>
                  </div>

                  <div v-if="replyDraftRootId === String(item.id)" class="reply-editor">
                    <ui-avatar
                      :src="currentUserAvatar"
                      :fallback="commentAvatarFallback"
                      class="reply-editor-avatar"
                    />
                    <div class="reply-editor-main">
                      <vs-input
                        v-model="replyDraft"
                        type="textarea"
                        maxlength="2000"
                        class="reply-editor-input"
                        :placeholder="`回复 ${replyToName || 'TA'}…`"
                        block
                        @keydown.ctrl.enter.prevent="submitReply()"
                        @keydown.meta.enter.prevent="submitReply()"
                      />
                      <div class="reply-editor-actions">
                        <span class="comment-editor-hint">Ctrl + Enter 发送</span>
                        <div class="reply-editor-btns">
                          <vs-button size="small" @click="cancelReply">取消</vs-button>
                          <vs-button
                            size="small"
                            color="primary"
                            :loading="replySubmitting"
                            :disabled="replySubmitting || !replyDraft.trim()"
                            @click="submitReply()"
                          >
                            发送回复
                          </vs-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>

        <div v-if="commentHasMore" class="comment-load-more">
          <vs-button
            class="meme-load-more-btn"
            type="border"
            color="primary"
            :loading="commentsLoading"
            @click="loadMoreComments"
          >
            <i v-if="!commentsLoading" class="ri-arrow-down-s-line" aria-hidden="true" />
            加载更多评论
          </vs-button>
        </div>
        </template>
        </template>
      </vs-card>

      <!-- 底部提示 -->
      <div class="detail-footer-tip">
        <span>只因“梗”懂你 · 让每一次会心一笑都有出处。</span>
      </div>
    </div>

    <!-- 彻底删除确认弹窗（已下架预览） -->
    <vs-dialog
      v-model="purgeDialogVisible"
      width="440px"
      class="detail-purge-dialog"
      prevent-close
      @closed="resetPurgeDialog"
    >
      <template #header>
        <div class="detail-purge-dialog__head">
          <span class="detail-purge-dialog__icon" aria-hidden="true">
            <i class="ri-delete-bin-6-line" />
          </span>
          <span>彻底删除</span>
        </div>
      </template>
      <div class="detail-purge-dialog__body">
        <p class="detail-purge-dialog__lead">
          确定要彻底删除「<strong>{{ purgeMemeName }}</strong>」吗？
        </p>
        <ul class="detail-purge-dialog__notes">
          <li>删除后无法恢复，发布列表里也不会再出现</li>
          <li>封面和相关图片会一并清理</li>
          <li>别人的评论、收藏记录仍会保留</li>
        </ul>
        <vs-input
          v-model="purgeConfirmName"
          maxlength="50"
          placeholder="输入梗名称以确认"
          class="detail-purge-dialog__input"
        />
      </div>
      <template #footer>
        <div class="detail-purge-dialog__actions">
          <button
            type="button"
            class="detail-purge-btn detail-purge-btn--ghost"
            :disabled="purgeSubmitting"
            @click="purgeDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="detail-purge-btn detail-purge-btn--danger"
            :disabled="purgeSubmitting || !purgeConfirmMatched"
            @click="confirmPurgePublished"
          >
            <i
              v-if="purgeSubmitting"
              class="ri-loader-4-line detail-purge-btn__spin"
              aria-hidden="true"
            />
            {{ purgeSubmitting ? '删除中…' : '确认彻底删除' }}
          </button>
        </div>
      </template>
    </vs-dialog>

    <!-- 收藏夹选择弹窗 -->
    <vs-dialog
      v-model="favoriteDialogVisible"
      width="440px"
      class="favorite-folder-dialog"
    >
      <template #header>
        {{ isFavorited ? '管理收藏' : '收藏到收藏夹' }}
      </template>
      <div class="favorite-folder-picker">
        <ui-skeleton v-if="favoriteFoldersLoading" :rows="4" animated />
        <template v-else>
          <button
            v-for="f in favoriteFolders"
            :key="f.id"
            type="button"
            class="favorite-folder-item"
            :class="{ active: sameFolderId(selectedFolderId, f.id) }"
            @click="selectedFolderId = normalizeFolderId(f.id)"
          >
            <span class="favorite-folder-icon" aria-hidden="true">
              <i :class="f.isDefault ? 'ri-star-line' : 'ri-folder-3-line'" />
            </span>
            <span class="favorite-folder-meta">
              <span class="favorite-folder-name">
                {{ f.name }}
                <ui-tag v-if="f.isDefault" size="small" plain>默认</ui-tag>
                <ui-tag v-else-if="Number(f.isPublic) === 0" type="warning" size="small" plain>私密</ui-tag>
              </span>
              <span class="favorite-folder-count">{{ f.memeCount || 0 }} 个梗图</span>
            </span>
            <i
              v-if="sameFolderId(selectedFolderId, f.id)"
              class="ri-check-line favorite-folder-check"
              aria-hidden="true"
            />
          </button>
          <button
            type="button"
            class="favorite-folder-item favorite-folder-create"
            @click="openCreateFolderDialog"
          >
            <span class="favorite-folder-icon favorite-folder-icon-create" aria-hidden="true">
              <i class="ri-add-line" />
            </span>
            <span class="favorite-folder-meta">
              <span class="favorite-folder-name">新建收藏夹</span>
              <span class="favorite-folder-count">创建自定义分类</span>
            </span>
          </button>
        </template>
      </div>
      <template #footer>
        <div class="favorite-folder-dialog__actions">
          <button
            v-if="isFavorited"
            type="button"
            class="fav-dlg-btn fav-dlg-btn--danger"
            :disabled="favoriteLoading"
            @click="removeFavoriteFromDialog"
          >
            取消收藏
          </button>
          <div class="favorite-folder-dialog__actions-end">
            <button
              type="button"
              class="fav-dlg-btn fav-dlg-btn--ghost"
              :disabled="favoriteLoading"
              @click="favoriteDialogVisible = false"
            >
              取消
            </button>
            <button
              type="button"
              class="fav-dlg-btn fav-dlg-btn--primary"
              :disabled="favoriteLoading"
              @click="confirmFavorite"
            >
              <i
                v-if="favoriteLoading"
                class="ri-loader-4-line fav-dlg-btn__spin"
                aria-hidden="true"
              />
              {{
                isFavorited && Number(selectedFolderId) === Number(currentFavoriteFolderId)
                  ? '确定'
                  : isFavorited
                    ? '移动到此夹'
                    : '收藏到此夹'
              }}
            </button>
          </div>
        </div>
      </template>
    </vs-dialog>

    <vs-dialog
      v-model="createFolderDialogVisible"
      width="400px"
      class="favorite-folder-dialog create-folder-dialog"
    >
      <template #header>新建收藏夹</template>
      <ui-form label-position="top" class="create-folder-form" @submit.prevent>
        <ui-form-item label="名称">
          <vs-input
            v-model="createFolderForm.name"
            maxlength="64"
            placeholder="收藏夹名称"
            block
            class="create-folder-input"
          />
        </ui-form-item>
        <ui-form-item label="可见性">
          <div class="folder-visibility-toggle">
            <button
              type="button"
              class="folder-visibility-toggle__btn"
              :class="{ 'is-active': createFolderForm.isPublic === 1 }"
              @click="createFolderForm.isPublic = 1"
            >
              公开
            </button>
            <button
              type="button"
              class="folder-visibility-toggle__btn"
              :class="{ 'is-active': createFolderForm.isPublic === 0 }"
              @click="createFolderForm.isPublic = 0"
            >
              私密
            </button>
          </div>
        </ui-form-item>
      </ui-form>
      <template #footer>
        <div class="favorite-folder-dialog__actions favorite-folder-dialog__actions--end">
          <button
            type="button"
            class="fav-dlg-btn fav-dlg-btn--ghost"
            :disabled="createFolderSubmitting"
            @click="createFolderDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="fav-dlg-btn fav-dlg-btn--primary"
            :disabled="createFolderSubmitting"
            @click="submitCreateFolder"
          >
            <i
              v-if="createFolderSubmitting"
              class="ri-loader-4-line fav-dlg-btn__spin"
              aria-hidden="true"
            />
            {{ createFolderSubmitting ? '创建中...' : '创建' }}
          </button>
        </div>
      </template>
    </vs-dialog>

    <vs-dialog v-model="imagePreviewVisible" width="90vw" class="image-preview-dialog">
      <div class="image-preview-body">
        <ui-image :src="imagePreviewSrc" fit="contain" class="image-preview-img" />
      </div>
    </vs-dialog>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { useMemeDetailStore } from '@/stores/memeDetail'
import { useAuthStore } from '@/stores/auth'
import { addMemeFavorite, removeMemeFavorite, moveMemeFavorite, getMemeFavoriteStatus, addMemeLike, removeMemeLike, reportMemeView, getMemeRootComments, getMemeCommentReplies, addMemeComment, deleteMemeComment, likeMemeComment, unlikeMemeComment, getMemeCommentAnchor, restorePublishedMeme, purgePublishedMeme } from '@/api/meme'
import { getMyFavoriteFolders, createFavoriteFolder, normalizeFolderId, sameFolderId } from '@/api/favoriteFolder'
import { uploadToOss } from '@/api/oss'
import { isAuthErrorHandled } from '@/utils/authSession'
import MemeDetailPreviewBanner from '@/components/meme/MemeDetailPreviewBanner.vue'
import MemeDetailFavoriteBtn from '@/components/meme/MemeDetailFavoriteBtn.vue'
import MemeDetailLikeBtn from '@/components/meme/MemeDetailLikeBtn.vue'
import FollowButton from '@/components/user/FollowButton.vue'
import { sanitizeExternalUrl } from '@/utils/safeUrl'
import { watch, computed, ref, onUnmounted, reactive, nextTick } from 'vue'
import { toast, confirmBox } from '@/utils/uiFeedback'
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
  if (status === 5) return '下架锁定'
  if (status === 6) return '恢复审核中'
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
const coverLoadFailed = ref(false)
const imagePreviewVisible = ref(false)
const imagePreviewSrc = ref('')

watch(memeId, () => {
  coverLoadFailed.value = false
})

function openImagePreview(srcOrList, index = 0) {
  const list = Array.isArray(srcOrList) ? srcOrList.filter(Boolean) : [srcOrList].filter(Boolean)
  if (!list.length) return
  const idx = Math.max(0, Math.min(index, list.length - 1))
  imagePreviewSrc.value = String(list[idx])
  imagePreviewVisible.value = true
}

function setCommentSort(type) {
  if (commentSortType.value === type) return
  commentSortType.value = type
  reloadComments()
}

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
const commentLikeLoadingMap = reactive({})
const commentDeleteLoadingMap = reactive({})
const replyDraftRootId = ref('')
const replyDraft = ref('')
const replyParentId = ref(null)
const replyToName = ref('')
const replySubmitting = ref(false)
const commentEditorFocused = ref(false)
const commentImages = ref([])
const commentImageUploading = ref(false)
/** 消息跳转高亮的目标评论 id */
const focusedCommentId = ref('')
let commentFocusSeq = 0
let commentFocusClearTimer = null

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
  loadComments(true).then(() => {
    focusCommentFromRoute()
  })
})

watch(
  () => `${route.query.commentId || ''}:${route.query.rootId || ''}`,
  (key, prevKey) => {
    if (!key || key === prevKey || key === ':') return
    if (!commentBootstrapKey.value) return
    focusCommentFromRoute()
  }
)

onUnmounted(() => {
  clearFavoriteDebounceTimer()
  clearViewReportTimer()
  clearCommentFocusTimer()
  viewReportSeq += 1
  commentFocusSeq += 1
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
      toast.success('审核已通过，已进入公开展示')
      loadComments(true)
      return
    }
    if (stillPreview) {
      toast.success('状态已刷新')
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
  focusedCommentId.value = ''
  clearCommentFocusTimer()
  expandedRoots.value = new Set()
  Object.keys(repliesMap).forEach((key) => delete repliesMap[key])
  Object.keys(repliesLoadingMap).forEach((key) => delete repliesLoadingMap[key])
  cancelReply()
}

function clearCommentFocusTimer() {
  if (commentFocusClearTimer) {
    clearTimeout(commentFocusClearTimer)
    commentFocusClearTimer = null
  }
}

async function focusCommentFromRoute() {
  const commentId = route.query.commentId != null ? String(route.query.commentId).trim() : ''
  if (!commentId || !commentsEnabled.value) return

  const seq = ++commentFocusSeq
  let rootId = route.query.rootId != null ? String(route.query.rootId).trim() : ''
  let parentId = ''
  let isRoot = false

  try {
    if (!rootId) {
      const anchor = await getMemeCommentAnchor(commentId)
      if (seq !== commentFocusSeq) return
      rootId = anchor.rootId || ''
      parentId = anchor.parentId || ''
      isRoot = Boolean(anchor.root)
      if (anchor.memeId && memeId.value && String(anchor.memeId) !== String(memeId.value)) {
        return
      }
    } else {
      isRoot = rootId === commentId
    }

    if (!rootId) return

    // 根评论可能不在首页：最多再翻 5 页寻找
    let attempts = 0
    while (
      !rootComments.value.some((c) => String(c.id) === rootId) &&
      commentHasMore.value &&
      attempts < 5
    ) {
      attempts += 1
      commentPage.value += 1
      await loadComments(false)
      if (seq !== commentFocusSeq) return
    }

    const rootItem = rootComments.value.find((c) => String(c.id) === rootId)
    if (!rootItem && !isRoot) {
      // 根评论不在列表中时，仍尝试展开该楼层回复
      expandedRoots.value.add(rootId)
    }

    if (!isRoot) {
      const ensureKey = rootId
      if (!expandedRoots.value.has(ensureKey)) {
        expandedRoots.value.add(ensureKey)
      }
      if (!Array.isArray(repliesMap[ensureKey]) || !repliesMap[ensureKey].length) {
        repliesLoadingMap[ensureKey] = true
        try {
          const data = await getMemeCommentReplies(ensureKey, { page: 1, size: 50 })
          if (seq !== commentFocusSeq) return
          repliesMap[ensureKey] = Array.isArray(data?.list) ? data.list : []
        } catch (e) {
          if (seq !== commentFocusSeq) return
          toast.error(e.message || '加载回复失败')
          return
        } finally {
          repliesLoadingMap[ensureKey] = false
        }
      }
    }

    await nextTick()
    if (seq !== commentFocusSeq) return

    focusedCommentId.value = commentId
    scrollToCommentEl(commentId)

    // 自动打开回复框，方便直接回这条消息
    const targetRoot = rootComments.value.find((c) => String(c.id) === rootId) || { id: rootId }
    if (isRoot) {
      const root = rootComments.value.find((c) => String(c.id) === commentId)
      if (root && authStore.isLoggedIn) {
        startReply(root, root)
      }
    } else {
      const replies = repliesMap[rootId] || []
      const reply = replies.find((r) => String(r.id) === commentId)
      if (reply && authStore.isLoggedIn) {
        startReply(targetRoot, reply)
      } else if (authStore.isLoggedIn && parentId) {
        const parent =
          replies.find((r) => String(r.id) === parentId) ||
          (String(parentId) === rootId ? targetRoot : null)
        if (parent) {
          startReply(targetRoot, parent)
        }
      }
    }

    clearCommentFocusTimer()
    commentFocusClearTimer = setTimeout(() => {
      if (String(focusedCommentId.value) === commentId) {
        focusedCommentId.value = ''
      }
    }, 4500)

    // 清掉 query，避免重复触发；保留其他溯源字段
    if (route.query.commentId != null || route.query.rootId != null) {
      const nextQuery = { ...route.query }
      delete nextQuery.commentId
      delete nextQuery.rootId
      router.replace({ query: nextQuery }).catch(() => {})
    }
  } catch (e) {
    if (seq !== commentFocusSeq) return
    // 定位失败不打断详情页
  }
}

function scrollToCommentEl(commentId) {
  const el = document.getElementById(`comment-${commentId}`)
  if (!el) {
    const section = document.getElementById('meme-comments')
    section?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
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
      toast.error(e.message || '加载评论失败')
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

const restoreSubmitting = ref(false)
const purgeDialogVisible = ref(false)
const purgeSubmitting = ref(false)
const purgeConfirmName = ref('')

const purgeMemeName = computed(() => {
  const name = meme.value?.name
  const text = name != null ? String(name).trim() : ''
  return text || '未命名梗图'
})

const purgeConfirmMatched = computed(() => {
  return String(purgeConfirmName.value || '').trim() === purgeMemeName.value
})

function openPurgeDialog() {
  if (!meme.value || Number(meme.value.status) !== 3) return
  purgeConfirmName.value = ''
  purgeDialogVisible.value = true
}

function resetPurgeDialog() {
  purgeConfirmName.value = ''
  purgeSubmitting.value = false
}

async function handleRestorePublished() {
  const id = meme.value?.id != null ? String(meme.value.id).trim() : ''
  const status = Number(meme.value?.status)
  if (!id || (status !== 3 && status !== 5) || restoreSubmitting.value || purgeSubmitting.value) {
    return
  }
  const isAppeal = status === 5
  try {
    await confirmBox(
      isAppeal
        ? `要把「${purgeMemeName.value}」提交整改申诉吗？\n提交后进入「恢复审核中」，通过后才会重新上线。`
        : `要把「${purgeMemeName.value}」重新上架吗？\n主动下架可随时恢复，无需再次审核。`,
      isAppeal ? '提交整改申诉' : '重新上架',
      { confirmButtonText: isAppeal ? '确认提交申诉' : '确认重新上架', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  restoreSubmitting.value = true
  try {
    await restorePublishedMeme(id)
    toast.success(isAppeal ? '已提交整改申诉' : '已重新上架')
    await memeDetailStore.fetchDetail(id)
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error((e && e.message) || '恢复失败')
  } finally {
    restoreSubmitting.value = false
  }
}

async function confirmPurgePublished() {
  const id = meme.value?.id != null ? String(meme.value.id).trim() : ''
  if (!id || !purgeConfirmMatched.value || purgeSubmitting.value) return
  purgeSubmitting.value = true
  try {
    await purgePublishedMeme(id)
    toast.success('已彻底删除')
    purgeDialogVisible.value = false
    goMyPublished()
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error((e && e.message) || '彻底删除失败')
  } finally {
    purgeSubmitting.value = false
  }
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
    toast.warning('登录态中的用户ID异常，请重新登录后再试')
    goToLogin()
    return
  }
  goUserProfile(userId)
}

function getCommentAvatar(comment) {
  const avatar = comment?.userAvatar ?? comment?.avatar
  return avatar != null ? String(avatar).trim() : ''
}

function onCommentEditorBlur() {
  window.setTimeout(() => {
    if (!commentDraft.value.trim() && !commentImages.value.length) {
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
    toast.warning('最多上传 3 张图片')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    toast.warning('单张图片不能超过 10MB')
    return
  }
  commentImageUploading.value = true
  try {
    const url = await uploadToOss(file, 'comment')
    commentImages.value.push(url)
  } catch (e) {
    toast.error(e.message || '图片上传失败')
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
    commentImages.value = []
    commentEditorFocused.value = false
    rootComments.value.unshift({
      id: data.commentId,
      userId: data.userId != null ? data.userId : authStore.currentUser?.id,
      userName: data.userName || authStore.currentUser?.nickname || authStore.currentUser?.username || '我',
      userAvatar: data.userAvatar || currentUserAvatar.value,
      content: data.content,
      images: Array.isArray(data.images) ? data.images : [],
      replyCount: Number(data.replyCount) || 0,
      likes: Number(data.likes) || 0,
      liked: Boolean(data.liked),
      owner: true,
      createTime: data.createTime,
    })
    commentTotal.value += 1
    if (meme.value) {
      meme.value.comments = (Number(meme.value.comments) || 0) + 1
    }
    toast.success('评论成功')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error(e.message || '发表评论失败')
  } finally {
    commentSubmitting.value = false
  }
}

function startReply(rootItem, parentItem) {
  if (!requireLoginForComment()) return
  const rootId = String(rootItem.id)
  replyDraftRootId.value = rootId
  replyParentId.value = parentItem.id
  replyToName.value = parentItem.userName || 'TA'
  replyDraft.value = ''
  if ((Number(rootItem.replyCount) || 0) > 0 && !expandedRoots.value.has(rootId)) {
    toggleReplies(rootItem)
  }
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
      parentId: data.parentId != null ? data.parentId : parentId,
      userId: data.userId != null ? data.userId : authStore.currentUser?.id,
      userName: data.userName || authStore.currentUser?.nickname || authStore.currentUser?.username || '我',
      userAvatar: data.userAvatar || currentUserAvatar.value,
      replyToUserName: replyToName.value,
      content: data.content,
      images: Array.isArray(data.images) ? data.images : [],
      likes: Number(data.likes) || 0,
      liked: Boolean(data.liked),
      owner: true,
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
    toast.success('回复成功')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error(e.message || '发表回复失败')
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
    const data = await getMemeCommentReplies(item.id, { page: 1, size: 50 })
    repliesMap[key] = Array.isArray(data?.list) ? data.list : []
  } catch (e) {
    toast.error(e.message || '加载回复失败')
    expandedRoots.value.delete(key)
  } finally {
    repliesLoadingMap[key] = false
  }
}

async function toggleCommentLike(comment) {
  if (!requireLoginForComment()) return
  if (!comment?.id) return
  const key = String(comment.id)
  if (commentLikeLoadingMap[key]) return

  const prevLiked = Boolean(comment.liked)
  const prevCount = Number(comment.likes) || 0
  const nextLiked = !prevLiked
  comment.liked = nextLiked
  comment.likes = Math.max(0, prevCount + (nextLiked ? 1 : -1))
  commentLikeLoadingMap[key] = true
  try {
    const data = nextLiked
      ? await likeMemeComment(comment.id)
      : await unlikeMemeComment(comment.id)
    comment.liked = Boolean(data.liked)
    if (data.likeCount != null) {
      comment.likes = Number(data.likeCount) || 0
    }
  } catch (e) {
    comment.liked = prevLiked
    comment.likes = prevCount
    if (isAuthErrorHandled(e)) return
    toast.error(e.message || (nextLiked ? '点赞失败' : '取消点赞失败'))
  } finally {
    commentLikeLoadingMap[key] = false
  }
}

async function removeComment(comment, rootItem = null) {
  if (!requireLoginForComment()) return
  if (!comment?.id || !comment.owner) return
  const key = String(comment.id)
  if (commentDeleteLoadingMap[key]) return

  try {
    await confirmBox('确认删除这条评论？删除后不可恢复。', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch (e) {
    if (e === 'cancel') return
    return
  }

  commentDeleteLoadingMap[key] = true
  try {
    const data = await deleteMemeComment(comment.id)
    const isRoot = !rootItem
    if (isRoot) {
      rootComments.value = rootComments.value.filter((c) => String(c.id) !== key)
      delete repliesMap[key]
      expandedRoots.value.delete(key)
      if (replyDraftRootId.value === key) cancelReply()
    } else {
      const rootId = String(rootItem.id)
      repliesMap[rootId] = (repliesMap[rootId] || []).filter((r) => String(r.id) !== key)
      rootItem.replyCount = Math.max(0, (Number(rootItem.replyCount) || 0) - 1)
    }
    if (data.memeCommentCount != null && meme.value) {
      meme.value.comments = Number(data.memeCommentCount) || 0
      commentTotal.value = Number(data.memeCommentCount) || commentTotal.value
    } else {
      commentTotal.value = Math.max(0, commentTotal.value - 1)
      if (meme.value) {
        meme.value.comments = Math.max(0, (Number(meme.value.comments) || 0) - 1)
      }
    }
    toast.success('已删除')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error(e.message || '删除失败')
  } finally {
    commentDeleteLoadingMap[key] = false
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
      toast.error(e.message || (prevLiked ? '取消点赞失败' : '点赞失败'))
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
    toast.error((e && e.message) || '加载收藏夹失败')
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
        toast.success('已取消收藏')
      } else {
        await moveMemeFavorite(id, folderId)
        currentFavoriteFolderId.value = folderId
        toast.success('已移动到「' + folderName(folderId) + '」')
      }
    } else {
      await addMemeFavorite(id, folderId)
      memeDetailStore.setFavorited(true)
      currentFavoriteFolderId.value = folderId
      toast.success('已收藏到「' + folderName(folderId) + '」')
    }
    favoriteDialogVisible.value = false
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error((e && e.message) || '操作失败，请稍后重试')
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
    toast.warning('请输入收藏夹名称')
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
    toast.success('收藏夹已创建')
  } catch (e) {
    toast.error((e && e.message) || '创建失败')
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
    toast.success('已取消收藏')
    favoriteDialogVisible.value = false
  } catch (e) {
    toast.error((e && e.message) || '取消收藏失败')
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

function formatCommentTime(str) {
  if (!str) return ''
  const raw = String(str).trim().replace('T', ' ')
  const parsed = new Date(raw.replace(/-/g, '/'))
  if (Number.isNaN(parsed.getTime())) return formatDate(str)

  const now = Date.now()
  const diff = Math.max(0, now - parsed.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`

  const y = parsed.getFullYear()
  const m = String(parsed.getMonth() + 1).padStart(2, '0')
  const d = String(parsed.getDate()).padStart(2, '0')
  const hh = String(parsed.getHours()).padStart(2, '0')
  const mm = String(parsed.getMinutes()).padStart(2, '0')
  if (y === new Date().getFullYear()) return `${m}-${d} ${hh}:${mm}`
  return `${y}-${m}-${d}`
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
  padding: 4px 0 24px;
}

.meme-detail-page {
  max-width: 1120px;
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
  gap: 12px;
}

.detail-content--preview {
  gap: 14px;
}

.detail-hero-card {
  width: 100%;
  max-width: none;
  border-radius: var(--meme-radius-xl) !important;
  border: 1px solid var(--meme-border);
  background: var(--meme-gradient-hero) !important;
  box-shadow: var(--meme-shadow-card) !important;
  overflow: hidden;
}

.detail-hero-card :deep(.vs-card__text) {
  padding: 16px;
}

.detail-hero-card--preview {
  border: 1px solid var(--meme-border-accent);
  background: var(--meme-gradient-card) !important;
}

.detail-hero-layout {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
}

.detail-cover-wrap {
  position: relative;
  width: 148px;
  height: 148px;
  flex-shrink: 0;
  border-radius: 14px;
  background: var(--meme-bg-cover);
  overflow: hidden;
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
}

.detail-cover {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.detail-cover :deep(img),
.detail-cover :deep(.ui-image) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--meme-text-muted);
  font-size: 12px;
  background:
    radial-gradient(circle at 30% 20%, var(--meme-primary-soft), transparent 55%),
    var(--meme-bg-cover);
}

.detail-cover-fallback-icon {
  font-size: 22px;
  opacity: 0.55;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.detail-meta-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-meta-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-meta-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 2px;
}

.detail-title {
  margin: 0;
  font-size: clamp(18px, 2vw, 22px);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.03em;
  color: var(--meme-text);
}

.detail-author-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.detail-author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.detail-follow-btn {
  flex-shrink: 0;
}

.detail-author:hover .detail-author-name {
  color: var(--meme-primary);
}

.detail-author-avatar {
  flex-shrink: 0;
}

.detail-author-name {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--meme-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.detail-stats-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.detail-stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 12px;
  line-height: 1.2;
}

.detail-stat-pill i {
  font-size: 13px;
  color: var(--meme-primary);
}

.detail-stat-pill strong {
  font-weight: 700;
  color: var(--meme-text);
}

.detail-intro {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.detail-intro--placeholder {
  color: var(--meme-text-muted);
}

.detail-meta-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 12px;
}

.detail-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag-chip {
  padding: 3px 10px;
  border: 1px solid var(--meme-border-accent);
  border-radius: 999px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.detail-tag-chip:hover {
  border-color: var(--meme-primary);
}

.detail-time {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.detail-time-item {
  font-size: 11px;
  color: var(--meme-text-muted);
}

.detail-time-sep {
  color: var(--meme-text-muted);
  font-size: 11px;
  user-select: none;
}

@media (max-width: 720px) {
  .detail-hero-layout {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .detail-cover-wrap {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
    max-height: 200px;
  }

  .detail-meta-top {
    flex-wrap: wrap;
  }

  .detail-meta-actions {
    width: 100%;
  }

  .detail-meta-like,
  .detail-meta-favorite {
    flex: 1;
  }

  .detail-meta-foot {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-time {
    margin-left: 0;
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

.detail-comment-card--preview :deep(.vs-card__title) {
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
  width: 100%;
  max-width: none;
  border-radius: 16px !important;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card) !important;
  box-shadow: var(--meme-shadow-card) !important;
  overflow: hidden;
}

.detail-section-card :deep(.vs-card__title) {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--meme-border);
  background: var(--meme-gradient-card);
}

.detail-section-card :deep(.vs-card__text) {
  padding: 14px 16px 16px;
}

.detail-section-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-section-header .meme-section-kicker {
  margin: 0 0 2px;
}

.detail-section-title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.detail-section-sub {
  font-size: 13px;
  color: var(--meme-text-secondary);
  line-height: 1.5;
}

.detail-links {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.detail-link-slot {
  display: flex;
  min-width: 0;
}

.detail-link-item {
  width: 100%;
  max-width: none;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  transition: background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease;
}

.detail-link-item:hover {
  background: var(--meme-bg-elevated);
  border-color: color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border));
  box-shadow: var(--meme-shadow-soft);
  transform: translateY(-1px);
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

.comment-sort-tabs {
  display: inline-grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
  flex-shrink: 0;
}

.comment-sort-tab {
  min-width: 64px;
  height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.comment-sort-tab:hover:not(.is-active) {
  color: var(--meme-text);
  background: color-mix(in srgb, var(--meme-bg-elevated) 70%, transparent);
}

.comment-sort-tab.is-active {
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: #fff;
  box-shadow: 0 4px 12px var(--meme-focus-ring);
}

.comment-sort-tab:active {
  transform: scale(0.98);
}

.comment-composer {
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--meme-bg-elevated) 90%, transparent);
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
}

.comment-editor {
  margin-bottom: 0;
}

.comment-editor-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.comment-editor-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.comment-editor-avatar:hover {
  opacity: 0.85;
}

.comment-editor-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-editor-input-wrap {
  min-width: 0;
}

.comment-editor-input :deep(textarea) {
  min-height: 72px;
  padding: 12px 14px;
  line-height: 1.6;
  border: 1px solid var(--meme-border-strong);
  border-radius: 12px;
  box-shadow: none;
  resize: none;
  background: var(--meme-bg-card);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.comment-editor-input :deep(textarea::placeholder) {
  color: var(--meme-text-muted);
}

.comment-editor-input :deep(textarea:focus) {
  border-color: var(--meme-primary);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
}

.comment-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.comment-editor-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}

.comment-tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  background: var(--meme-bg-card);
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}

.comment-tool-btn svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.comment-tool-btn:hover:not(.is-disabled) {
  border-color: var(--meme-border-accent);
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.comment-tool-btn.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.comment-editor-hint {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.comment-submit-btn {
  min-width: 108px;
  border-radius: 10px !important;
}

.comment-editor-images {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.comment-editor-image-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
}

.comment-editor-image-thumb {
  width: 100%;
  height: 100%;
}

.comment-editor-image-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--meme-overlay);
  color: var(--meme-text-inverse);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.comment-image-file-input {
  display: none;
}

.comment-login-prompt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  padding: 18px 20px;
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
  border-radius: 16px;
}

.comment-login-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.comment-login-copy strong {
  font-size: 15px;
  color: var(--meme-text);
}

.comment-login-copy span {
  font-size: 13px;
  color: var(--meme-text-secondary);
  line-height: 1.5;
}

.comment-loading {
  padding: 16px 0;
}

.comment-empty {
  padding: 12px 0 4px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment-item {
  padding: 16px 14px;
  margin: 0 -6px;
  border-radius: 14px;
  transition: background 0.15s ease, box-shadow 0.2s ease;
}

.comment-item:hover {
  background: var(--meme-bg-muted);
}

.comment-item.is-focus-target,
.reply-item.is-focus-target {
  background: var(--meme-primary-soft);
  box-shadow: 0 0 0 2px var(--meme-focus-ring);
}

.comment-item-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-user {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
  cursor: pointer;
  line-height: 1.3;
}

.comment-user:hover {
  color: var(--meme-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.comment-content {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--meme-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-item-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.comment-item-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
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
  margin-bottom: 10px;
}

.comment-image {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  overflow: hidden;
  cursor: zoom-in;
}

.comment-item-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.comment-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--meme-text-muted);
  font-size: 13px;
  line-height: 1.2;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.comment-action svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.comment-action:hover {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.comment-action.is-active {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.comment-action.is-liked {
  color: var(--meme-warning);
  background: var(--meme-warning-soft);
}

.comment-action--danger {
  color: var(--meme-text-muted);
}

.comment-action--danger:hover {
  color: var(--meme-danger);
  background: var(--meme-danger-soft);
}

.comment-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.reply-thread {
  margin-top: 12px;
  padding: 12px 14px 14px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  border-left: 3px solid var(--meme-primary);
  background: var(--meme-bg-card);
}

.reply-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.reply-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
}

.reply-item + .reply-item {
  border-top: 1px dashed var(--meme-border);
}

.reply-item-main {
  flex: 1;
  min-width: 0;
}

.reply-item-footer {
  display: flex;
  align-items: center;
  gap: 8px;
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

.reply-target em {
  font-style: normal;
  color: var(--meme-primary);
  font-weight: 600;
}

.reply-item-avatar {
  margin-top: 2px;
  width: 32px;
  height: 32px;
}

.reply-editor {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px dashed var(--meme-border);
}

.reply-editor-avatar {
  flex-shrink: 0;
  margin-top: 2px;
  width: 28px;
  height: 28px;
}

.reply-editor-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.reply-editor-input :deep(textarea) {
  padding: 10px 12px;
  line-height: 1.55;
  border: 1px solid var(--meme-border-strong);
  border-radius: 10px;
  background: var(--meme-bg-muted);
  resize: none;
  box-shadow: none;
}

.reply-editor-input :deep(textarea:focus) {
  border-color: var(--meme-primary);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
  background: var(--meme-bg-card);
}

.reply-editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.reply-editor-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-load-more {
  margin-top: 16px;
  padding-top: 4px;
  text-align: center;
}

@media (max-width: 768px) {
  .page {
    padding: 0 0 16px;
  }

  .detail-hero-card {
    padding: 0;
  }

  .detail-cover-wrap {
    margin-bottom: 0;
  }

  .comment-composer {
    padding: 14px;
  }

  .comment-editor-row {
    gap: 10px;
  }

  .comment-editor-avatar {
    display: none;
  }

  .comment-item {
    padding: 14px 8px;
    margin: 0;
  }

  .reply-thread {
    padding: 10px 10px 12px;
  }

  .comment-login-prompt {
    flex-direction: column;
    align-items: stretch;
  }

  .comment-editor-hint {
    display: none;
  }
}

/* 收藏夹选择弹窗 */
.favorite-folder-dialog.vs-dialog-content,
.vs-dialog-content.favorite-folder-dialog,
:deep(.favorite-folder-dialog.vs-dialog-content),
:deep(.vs-dialog-content.favorite-folder-dialog) {
  border-radius: 18px !important;
  border: 1px solid var(--meme-border) !important;
  background: var(--meme-bg-elevated) !important;
  box-shadow: var(--meme-shadow-dialog) !important;
  overflow: hidden;
}

:deep(.favorite-folder-dialog .vs-dialog__header),
:deep(.favorite-folder-dialog .vs-dialog-header) {
  padding: 18px 20px 8px !important;
  text-align: center;
  border-bottom: none !important;
}

:deep(.favorite-folder-dialog .vs-dialog__header h3),
:deep(.favorite-folder-dialog .vs-dialog-header),
:deep(.favorite-folder-dialog .vs-dialog__header *) {
  font-size: 17px !important;
  font-weight: 750 !important;
  letter-spacing: -0.02em;
  color: var(--meme-text) !important;
}

:deep(.favorite-folder-dialog .vs-dialog__content),
:deep(.favorite-folder-dialog .vs-dialog-content) {
  padding: 8px 20px 4px !important;
}

:deep(.favorite-folder-dialog .vs-dialog__footer),
:deep(.favorite-folder-dialog .vs-dialog-footer) {
  padding: 12px 20px 20px !important;
  border-top: 1px solid var(--meme-border) !important;
  background: transparent !important;
}

:deep(.favorite-folder-dialog .vs-dialog__close),
:deep(.favorite-folder-dialog .vs-dialog-close) {
  top: 12px !important;
  right: 12px !important;
  color: var(--meme-text-muted) !important;
}

.favorite-folder-picker {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 8px;
}

.favorite-folder-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--meme-border);
  border-radius: 12px;
  background: var(--meme-bg-elevated);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.favorite-folder-item:hover {
  border-color: color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border));
  background: var(--meme-primary-soft);
}

.favorite-folder-item.active {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--meme-primary) 35%, transparent);
}

.favorite-folder-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 20px;
}

.favorite-folder-icon i {
  line-height: 1;
}

.favorite-folder-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.favorite-folder-name {
  font-size: 14px;
  font-weight: 650;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--meme-text);
}

.favorite-folder-count {
  font-size: 12px;
  color: var(--meme-text-secondary);
}

.favorite-folder-check {
  color: var(--meme-primary);
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
}

.favorite-folder-create {
  border-style: dashed;
}

.favorite-folder-create:hover {
  border-color: var(--meme-primary);
}

.favorite-folder-icon-create {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.favorite-folder-dialog__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.favorite-folder-dialog__actions--end,
.favorite-folder-dialog__actions-end {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-left: auto;
}

.fav-dlg-btn {
  height: 40px;
  min-width: 88px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 14px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.fav-dlg-btn:disabled {
  opacity: 0.65;
  cursor: wait;
}

.fav-dlg-btn--ghost {
  color: var(--meme-text);
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
}

.fav-dlg-btn--ghost:hover:not(:disabled) {
  background: var(--meme-bg-elevated);
  border-color: var(--meme-border-strong);
}

.fav-dlg-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 14px var(--meme-focus-ring);
}

.fav-dlg-btn--primary:hover:not(:disabled) {
  filter: brightness(1.04);
}

.fav-dlg-btn--danger {
  color: var(--meme-danger);
  background: var(--meme-danger-soft);
  border-color: color-mix(in srgb, var(--meme-danger) 28%, transparent);
}

.fav-dlg-btn--danger:hover:not(:disabled) {
  filter: brightness(0.98);
}

.fav-dlg-btn__spin {
  display: inline-block;
  animation: fav-dlg-spin 0.8s linear infinite;
}

@keyframes fav-dlg-spin {
  to {
    transform: rotate(360deg);
  }
}

.create-folder-form :deep(.ui-form-item) {
  margin-bottom: 14px;
}

.create-folder-input :deep(.vs-input__wrapper) {
  min-height: 42px;
  border-radius: 10px !important;
  background: var(--meme-bg-muted) !important;
}

.create-folder-input :deep(.vs-input__original) {
  min-height: 42px;
  border: none !important;
  background: transparent !important;
  color: var(--meme-text) !important;
  box-shadow: none !important;
}

.folder-visibility-toggle {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: var(--meme-bg-muted);
}

.folder-visibility-toggle__btn {
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.folder-visibility-toggle__btn.is-active {
  color: var(--meme-primary);
  background: var(--meme-bg-elevated);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.folder-visibility-toggle__btn:hover:not(.is-active) {
  color: var(--meme-text);
}

.image-preview-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
  padding: 8px;
}

.image-preview-img {
  max-width: 100%;
  max-height: 80vh;
  cursor: zoom-out;
}

.detail-purge-dialog__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 28px;
  font-size: 17px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.detail-purge-dialog__icon {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--meme-danger-soft);
  color: var(--meme-danger);
  font-size: 18px;
}

.detail-purge-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-purge-dialog__lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
}

.detail-purge-dialog__lead strong {
  color: var(--meme-text);
  font-weight: 700;
}

.detail-purge-dialog__notes {
  margin: 0;
  padding: 12px 14px 12px 30px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--meme-warning) 28%, var(--meme-border));
  background: color-mix(in srgb, var(--meme-warning-soft) 55%, transparent);
  font-size: 13px;
  line-height: 1.7;
  color: var(--meme-warning);
}

.detail-purge-dialog__input {
  margin-top: 2px;
}

.detail-purge-dialog__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

.detail-purge-btn {
  height: 40px;
  min-width: 88px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 14px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, filter 0.15s ease;
}

.detail-purge-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.detail-purge-btn--ghost {
  color: var(--meme-text);
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
}

.detail-purge-btn--ghost:hover:not(:disabled) {
  background: var(--meme-bg-elevated);
  border-color: var(--meme-border-strong);
}

.detail-purge-btn--danger {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--meme-danger),
    color-mix(in srgb, var(--meme-danger) 72%, #7f1d1d)
  );
  box-shadow: 0 6px 14px color-mix(in srgb, var(--meme-danger) 32%, transparent);
}

.detail-purge-btn--danger:hover:not(:disabled) {
  filter: brightness(1.04);
}

.detail-purge-btn__spin {
  display: inline-block;
  animation: detail-purge-spin 0.8s linear infinite;
}

@keyframes detail-purge-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

<style>
.detail-purge-dialog.vs-dialog-content,
.vs-dialog-content.detail-purge-dialog {
  border-radius: 18px !important;
  border: 1px solid var(--meme-border) !important;
  background: var(--meme-bg-elevated) !important;
  box-shadow: var(--meme-shadow-dialog) !important;
  overflow: hidden;
}

.detail-purge-dialog .vs-dialog__header,
.detail-purge-dialog .vs-dialog-header {
  padding: 18px 20px 8px !important;
  border-bottom: none !important;
}

.detail-purge-dialog .vs-dialog__content,
.detail-purge-dialog .vs-dialog-content {
  padding: 4px 20px 8px !important;
}

.detail-purge-dialog .vs-dialog__footer,
.detail-purge-dialog .vs-dialog-footer {
  padding: 12px 20px 18px !important;
  border-top: 1px solid color-mix(in srgb, var(--meme-border) 80%, transparent) !important;
  background: color-mix(in srgb, var(--meme-bg-muted) 55%, var(--meme-bg-elevated)) !important;
  display: flex !important;
  justify-content: flex-end !important;
}

.detail-purge-dialog .vs-dialog__close,
.detail-purge-dialog .vs-dialog-close {
  top: 14px !important;
  right: 14px !important;
  color: var(--meme-text-muted) !important;
}
</style>
