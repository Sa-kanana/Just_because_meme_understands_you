<template>
  <div class="profile-page">
    <div v-if="loading" class="profile-loading-page">
      <span class="meme-loading-spinner"></span>
      <span>加载个人主页中...</span>
    </div>
    <vs-alert v-else-if="errorMessage" color="warn" class="profile-error">
      {{ errorMessage }}
    </vs-alert>
    <div v-else class="profile-layout">
      <header class="profile-hero">
        <div class="profile-hero__cover" aria-hidden="true" />
        <div class="profile-hero__inner">
          <div class="profile-hero__main">
            <ui-avatar
              class="profile-hero__avatar"
              :size="88"
              :src="profile.avatar"
              :fallback="profile.nickname || 'U'"
              pointer
              @click="openAvatarViewer"
            />
            <div class="profile-hero__meta">
              <p class="meme-section-kicker profile-hero__kicker">PROFILE</p>
              <h1 class="profile-hero__name">{{ profile.nickname }}</h1>
              <p class="profile-hero__signature">
                {{ profile.signature || '这个人很懒，什么都没留下~' }}
              </p>
              <div class="profile-hero__stats">
                <button type="button" class="profile-stat-chip" @click="openRelationDrawer('following')">
                  <strong>{{ formatNum(profile.stats.followCount) }}</strong>
                  <span>关注</span>
                </button>
                <button type="button" class="profile-stat-chip" @click="openRelationDrawer('followers')">
                  <strong>{{ formatNum(profile.stats.fansCount) }}</strong>
                  <span>粉丝</span>
                </button>
                <span class="profile-uid-chip">
                  <span class="profile-uid-chip__label">UID</span>
                  <strong>#{{ profile.userId || '-' }}</strong>
                </span>
              </div>
            </div>
          </div>
          <div class="profile-hero__actions">
            <vs-button
              v-if="showEditButton"
              type="border"
              color="primary"
              class="profile-hero__edit-btn"
              @click="handleEditProfile"
            >
              <i class="ri-edit-line" aria-hidden="true" />
              编辑信息
            </vs-button>
            <FollowButton
              v-else-if="profile.userId"
              v-model="profile.isFollow"
              :user-id="profile.userId"
              :mutual="followMutual"
              size="lg"
              class="profile-hero__follow-btn"
              @change="onProfileFollowChange"
            />
          </div>
        </div>
      </header>

      <div class="profile-metrics">
        <div class="profile-metric">
          <i class="ri-image-line profile-metric__icon" aria-hidden="true" />
          <strong>{{ formatNum(profile.stats.memeCount) }}</strong>
          <span>发布梗图</span>
        </div>
        <div class="profile-metric">
          <i class="ri-heart-3-line profile-metric__icon" aria-hidden="true" />
          <strong>{{ formatNum(profile.stats.likeReceived) }}</strong>
          <span>获赞总数</span>
        </div>
        <div class="profile-metric">
          <i class="ri-star-line profile-metric__icon" aria-hidden="true" />
          <strong>{{ formatNum(profile.stats.favoriteCount) }}</strong>
          <span>收藏总数</span>
        </div>
        <div class="profile-metric">
          <i class="ri-user-heart-line profile-metric__icon" aria-hidden="true" />
          <strong>{{ formatNum(profile.stats.fansCount) }}</strong>
          <span>粉丝总数</span>
        </div>
      </div>

      <vs-card class="profile-content-card">
        <template #text>
          <ui-tabs v-model="activeTab" class="profile-tabs">
            <ui-tab-pane :label="`发布梗图 (${publishedDisplayTotal})`" name="published">
              <div class="published-panel">
                <div v-if="isOwnProfile" class="published-panel-header">
                  <div class="published-panel-head">
                    <p class="meme-section-kicker">MY MEMES</p>
                    <h3 class="meme-section-title published-panel-title">我整的梗</h3>
                    <p class="published-panel-hint">点击卡片看详情；想下架时点右上角 ⋮，公域刷不到后可在「已下架」里翻黑历史</p>
                  </div>
                  <vs-button
                    color="primary"
                    class="published-create-btn"
                    @click="$router.push(publishLocation)"
                  >
                    <i class="ri-add-line" aria-hidden="true" />
                    发布新梗
                  </vs-button>
                </div>

                <div v-if="isOwnProfile && publishedList.length" class="published-filter-bar">
                  <button
                    v-for="opt in publishedFilterOptions"
                    :key="`pub-filter-${opt.key}`"
                    type="button"
                    class="published-filter-chip"
                    :class="{ 'is-active': publishedStatusFilter === opt.key }"
                    @click="publishedStatusFilter = opt.key"
                  >
                    {{ opt.label }}
                    <span v-if="opt.count != null" class="published-filter-count">{{ opt.count }}</span>
                  </button>
                </div>

                <div v-if="publishedLoading && !publishedList.length" class="published-skeleton">
                  <ui-skeleton :rows="3" animated />
                </div>
                <div v-else-if="filteredPublishedList.length" class="meme-grid published-meme-grid">
                  <div
                    v-for="item in filteredPublishedList"
                    :key="`published-${item.id}`"
                    class="meme-card-item meme-card-item--published"
                    :class="{ 'meme-card-item--deleted': item.status === 3 }"
                    @click="handlePublishedCardClick(item)"
                  >
                    <MemeCard
                      :name="item.name"
                      :image="item.image"
                      :page-views="item.pageViews"
                      :likes="item.likes"
                      :comments="item.comments"
                      :release-time="item.releaseTime || item.createTime"
                      :update-time="item.updateTime"
                      :show-stats="item.status !== 3"
                      :show-author="false"
                      :meta-text="item.status === 3 ? '已下架' : ''"
                    >
                      <template #cover-extra>
                        <span
                          v-if="isOwnProfile && item.status != null && item.status !== 1"
                          class="published-status-badge"
                          :class="`published-status-badge--${item.status}`"
                        >
                          {{ item.statusDesc || statusText(item.status) }}
                        </span>
                        <div v-if="item.status === 3" class="published-deleted-overlay">
                          <span class="published-deleted-label">已下架</span>
                        </div>
                      </template>
                      <template #title-extra>
                        <ui-dropdown
                          v-if="isOwnProfile && item.status !== 3"
                          trigger="click"
                          class="meme-action-dropdown"
                          @click.stop
                          @command="(cmd) => handlePublishedMemeCommand(cmd, item)"
                        >
                          <button
                            type="button"
                            class="meme-more-btn published-more-btn"
                            aria-label="更多操作"
                            @click.stop
                          >
                            ⋮
                          </button>
                          <template #dropdown>
                            <ui-dropdown-menu>
                              <ui-dropdown-item command="delete">
                                <span class="published-menu-item published-menu-item--danger"><i class="ri-inbox-unarchive-line" /> 下架梗</span>
                              </ui-dropdown-item>
                            </ui-dropdown-menu>
                          </template>
                        </ui-dropdown>
                        <ui-dropdown
                          v-else-if="isOwnProfile && item.status === 3"
                          trigger="click"
                          class="meme-action-dropdown"
                          @click.stop
                          @command="(cmd) => handlePublishedMemeCommand(cmd, item)"
                        >
                          <button
                            type="button"
                            class="meme-more-btn published-more-btn"
                            aria-label="更多操作"
                            @click.stop
                          >
                            ⋮
                          </button>
                          <template #dropdown>
                            <ui-dropdown-menu>
                              <ui-dropdown-item command="restore">
                                <span class="published-menu-item"><i class="ri-arrow-go-back-line" /> 恢复上架</span>
                              </ui-dropdown-item>
                              <ui-dropdown-item command="purge" divided>
                                <span class="published-menu-item published-menu-item--danger"><i class="ri-delete-bin-6-line" /> 彻底删除</span>
                              </ui-dropdown-item>
                            </ui-dropdown-menu>
                          </template>
                        </ui-dropdown>
                      </template>
                    </MemeCard>
                  </div>
                </div>
                <ui-empty
                  v-else-if="publishedList.length && isOwnProfile"
                  :image-size="72"
                  description="当前筛选下没有梗图"
                  class="published-filter-empty"
                />
                <ui-empty v-else description="这个用户还没有发布梗图" />
                <ListLoadFooter
                  :has-more="publishedHasMore"
                  :loading="publishedLoading"
                  :item-count="filteredPublishedList.length"
                  @load-more="loadMorePublished"
                />
              </div>
            </ui-tab-pane>

            <ui-tab-pane :label="`收藏梗图 (${favoriteTotal})`" name="favorite">
              <div class="favorite-panel">
                <!-- 收藏夹卡片列表 -->
                <template v-if="favoriteViewMode === 'folders'">
                  <div class="favorite-panel-header">
                    <div>
                      <p class="meme-section-kicker">FAVORITES</p>
                      <h3 class="meme-section-title favorite-panel-title">收藏夹</h3>
                    </div>
                    <vs-button
                      v-if="isOwnProfile"
                      color="primary"
                      class="favorite-create-btn"
                      @click="openCreateFolderDialog"
                    >
                      <i class="ri-add-line" aria-hidden="true" />
                      创建新收藏夹
                    </vs-button>
                  </div>

                  <div class="folder-card-grid" :class="{ 'is-loading': folderLoading }">
                    <div v-if="folderLoading" class="folder-card-loading">
                      <span class="meme-loading-spinner"></span>
                    </div>
                    <div
                      v-for="f in folderList"
                      :key="`folder-card-${f.id}`"
                      class="folder-card"
                      @click="openFolderDetail(f)"
                    >
                      <div class="folder-card-top">
                        <i class="ri-folder-3-line folder-card-icon" aria-hidden="true" />
                        <span class="folder-card-name" :title="f.name">{{ f.name }}</span>
                        <ui-dropdown
                          v-if="isOwnProfile"
                          trigger="click"
                          @click.stop
                          @command="(cmd) => handleFolderCommand(cmd, f)"
                        >
                          <button type="button" class="folder-card-more" @click.stop>⋯</button>
                          <template #dropdown>
                            <ui-dropdown-menu>
                              <ui-dropdown-item command="edit">编辑</ui-dropdown-item>
                              <ui-dropdown-item v-if="!f.isDefault" command="delete" divided>删除</ui-dropdown-item>
                            </ui-dropdown-menu>
                          </template>
                        </ui-dropdown>
                      </div>
                      <div class="folder-card-bottom">
                        <span class="folder-card-count">{{ f.memeCount || 0 }} 个梗图</span>
                        <span
                          v-if="f.isDefault"
                          class="folder-card-status folder-card-status-default"
                        >默认</span>
                        <span
                          v-else-if="Number(f.isPublic) === 0"
                          class="folder-card-status folder-card-status-private"
                        >私密</span>
                        <span
                          v-else
                          class="folder-card-status folder-card-status-public"
                        >公开</span>
                      </div>
                    </div>

                    <ui-empty
                      v-if="!folderLoading && !folderList.length"
                      :image-size="72"
                      description="还没有收藏夹"
                      class="folder-card-empty"
                    >
                      <vs-button v-if="isOwnProfile" color="primary" @click="openCreateFolderDialog">
                        创建新收藏夹
                      </vs-button>
                    </ui-empty>
                  </div>
                </template>

                <!-- 夹内梗图详情 -->
                <template v-else>
                  <div class="folder-detail-header">
                    <button type="button" class="folder-back-btn" @click="backToFolderGrid">
                      <i class="ri-arrow-left-line" aria-hidden="true" />
                      返回收藏夹
                    </button>
                    <h3 class="folder-detail-title">{{ selectedFolderName }}</h3>
                  </div>

                  <div
                    class="folder-content"
                    :class="{ 'folder-content--batch-active': isOwnProfile && selectedMemeIds.length > 0 }"
                  >
                    <div v-if="folderContentLoading && !folderContentList.length" class="published-skeleton">
                      <ui-skeleton :rows="3" animated />
                    </div>
                    <template v-else>
                      <div
                        v-if="isOwnProfile && folderContentList.length"
                        class="folder-batch-bar"
                        :class="{ 'is-active': selectedMemeIds.length > 0 }"
                      >
                        <vs-checkbox
                          v-model="folderSelectAll"
                          class="folder-batch-check"
                          @change="onSelectAllFolderItems"
                        >
                          全选
                        </vs-checkbox>
                        <span v-if="selectedMemeIds.length" class="folder-batch-count">
                          已选 {{ selectedMemeIds.length }} 项
                        </span>
                        <span v-else class="folder-batch-hint">勾选后可批量管理</span>
                      </div>
                      <div v-if="folderContentList.length" class="meme-grid folder-meme-grid">
                        <div
                          v-for="item in folderContentList"
                          :key="`fav-${item.favoriteId || item.id}`"
                          class="meme-card-item meme-card-item--favorite"
                          :class="{ 'meme-card-item--selected': isMemeSelected(item.id) }"
                          @click="goMemeDetail(item.id)"
                        >
                          <MemeCard
                            :name="item.name"
                            :image="item.image"
                            :page-views="item.pageViews"
                            :likes="item.likes"
                            :comments="item.comments"
                            :release-time="item.releaseTime"
                            :update-time="item.updateTime"
                            :meta-text="isOwnProfile ? formatFavoriteTime(item.favoriteTime) : ''"
                          >
                            <template #cover-extra>
                              <button
                                v-if="isOwnProfile"
                                type="button"
                                class="meme-select-toggle"
                                :class="{ 'is-checked': isMemeSelected(item.id) }"
                                aria-label="选择梗图"
                                @click.stop="toggleMemeSelect(item.id, !isMemeSelected(item.id))"
                              />
                            </template>
                            <template #title-extra>
                              <ui-dropdown
                                v-if="isOwnProfile"
                                trigger="click"
                                class="meme-action-dropdown"
                                @click.stop
                                @command="(cmd) => handleFavoriteMemeCommand(cmd, item)"
                              >
                                <button
                                  type="button"
                                  class="meme-more-btn"
                                  aria-label="更多操作"
                                  @click.stop
                                >
                                  ⋮
                                </button>
                                <template #dropdown>
                                  <ui-dropdown-menu>
                                    <ui-dropdown-item command="move">移动至</ui-dropdown-item>
                                    <ui-dropdown-item command="remove">取消收藏</ui-dropdown-item>
                                  </ui-dropdown-menu>
                                </template>
                              </ui-dropdown>
                            </template>
                          </MemeCard>
                        </div>
                      </div>
                      <ui-empty v-else description="这个收藏夹还没有梗图" />
                      <ListLoadFooter
                        :has-more="folderContentHasMore"
                        :loading="folderContentLoading"
                        :item-count="folderContentList.length"
                        @load-more="loadMoreFolderContent"
                      />
                      <Transition name="folder-batch-dock">
                        <div
                          v-if="isOwnProfile && selectedMemeIds.length > 0"
                          class="folder-batch-dock"
                        >
                          <div class="folder-batch-dock-inner">
                            <div class="folder-batch-dock-left">
                              <span class="folder-batch-dock-count">已选 {{ selectedMemeIds.length }} 项</span>
                              <button type="button" class="folder-batch-dock-clear" @click="clearMemeSelection">
                                取消选择
                              </button>
                            </div>
                            <div class="folder-batch-dock-actions">
                              <button
                                type="button"
                                class="folder-batch-dock-btn folder-batch-dock-btn-move"
                                @click="openBatchMoveDialog"
                              >
                                移动至
                              </button>
                              <button
                                type="button"
                                class="folder-batch-dock-btn folder-batch-dock-btn-remove"
                                @click="confirmBatchRemoveFavorites"
                              >
                                取消收藏
                              </button>
                            </div>
                          </div>
                        </div>
                      </Transition>
                    </template>
                  </div>
                </template>
              </div>
            </ui-tab-pane>
          </ui-tabs>
        </template>
      </vs-card>
    </div>

    <vs-dialog
      v-model="editDialogVisible"
      width="560px"
      prevent-close
      class="edit-profile-dialog"
    >
      <template #header>
        <div class="edit-dialog-head">
          <p class="edit-dialog-kicker">PROFILE</p>
          <h3 class="edit-dialog-title">编辑资料</h3>
        </div>
      </template>

      <div v-if="editEchoLoading" class="edit-echo-skeleton">
        <ui-skeleton animated>
          <div class="edit-skeleton-shape edit-skeleton-shape--circle" />
          <div class="edit-skeleton-shape edit-skeleton-shape--line" style="width: 46%;" />
          <div class="edit-skeleton-shape edit-skeleton-shape--line" />
          <div class="edit-skeleton-shape edit-skeleton-shape--line" />
          <div class="edit-skeleton-shape edit-skeleton-shape--line" style="width: 66%;" />
        </ui-skeleton>
      </div>

      <ui-form
        v-else
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-position="top"
        class="edit-form"
      >
        <section class="edit-section">
          <div class="edit-section-head">
            <span class="edit-section-icon" aria-hidden="true">
              <i class="ri-user-smile-line" />
            </span>
            <div>
              <h4 class="edit-section-title">头像</h4>
              <p class="edit-section-hint">点击头像或按钮更换，支持 JPG / PNG / WEBP</p>
            </div>
          </div>

          <div class="avatar-edit-row">
            <button
              type="button"
              class="avatar-edit-hit"
              :disabled="avatarUploading || editSubmitting"
              aria-label="更换头像"
              @click="triggerAvatarUpload"
            >
              <ui-avatar
                :size="88"
                :src="editForm.avatar"
                :fallback="profile.nickname || 'U'"
                class="avatar-edit-preview"
              />
              <span class="avatar-edit-overlay" aria-hidden="true">
                <i :class="avatarUploading ? 'ri-loader-4-line is-spinning' : 'ri-camera-line'" />
              </span>
            </button>
            <div class="avatar-edit-actions">
              <vs-button
                type="border"
                color="primary"
                class="avatar-change-btn"
                :loading="avatarUploading"
                :disabled="avatarUploading || editSubmitting"
                @click="triggerAvatarUpload"
              >
                <i class="ri-upload-2-line" aria-hidden="true" />
                {{ avatarUploading ? '上传中…' : '更换头像' }}
              </vs-button>
              <p class="avatar-edit-tip">大小不超过 5MB，建议正方形</p>
            </div>
          </div>
          <input
            ref="avatarFileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
            class="avatar-file-input"
            @change="onAvatarFileChange"
          />
        </section>

        <section class="edit-section edit-section--last">
          <div class="edit-section-head">
            <span class="edit-section-icon edit-section-icon--primary" aria-hidden="true">
              <i class="ri-edit-2-line" />
            </span>
            <div>
              <h4 class="edit-section-title">基本信息</h4>
              <p class="edit-section-hint">昵称与签名会展示在个人主页</p>
            </div>
          </div>

          <ui-form-item label="昵称" prop="nickname">
            <vs-input
              v-model="editForm.nickname"
              block
              clearable
              maxlength="50"
              placeholder="你的昵称"
              class="edit-field"
            >
              <template #icon>
                <i class="ri-user-3-line" aria-hidden="true" />
              </template>
            </vs-input>
          </ui-form-item>

          <ui-form-item label="签名" prop="signature">
            <vs-input
              v-model="editForm.signature"
              type="textarea"
              block
              :rows="3"
              maxlength="255"
              placeholder="写一句能代表你的个性签名"
              class="edit-field edit-field--textarea"
            />
          </ui-form-item>

          <ui-form-item label="性别">
            <div class="gender-segment" role="radiogroup" aria-label="性别">
              <button
                v-for="opt in genderOptions"
                :key="`gender-${opt.value}`"
                type="button"
                class="gender-segment__item"
                :class="{ 'is-active': editForm.gender === opt.value }"
                role="radio"
                :aria-checked="editForm.gender === opt.value"
                @click="editForm.gender = opt.value"
              >
                <i :class="opt.icon" aria-hidden="true" />
                {{ opt.label }}
              </button>
            </div>
          </ui-form-item>

          <ui-form-item label="生日">
            <ui-date-picker
              v-model="editForm.birthday"
              class="edit-date-picker"
              type="date"
              placeholder="选择生日"
            />
          </ui-form-item>
        </section>
      </ui-form>

      <template #footer>
        <div class="edit-dialog-footer">
          <vs-button
            type="border"
            color="primary"
            class="edit-cancel-btn"
            :disabled="editSubmitting"
            @click="editDialogVisible = false"
          >
            取消
          </vs-button>
          <vs-button
            color="primary"
            class="edit-save-btn"
            :loading="editSubmitting"
            :disabled="avatarUploading"
            @click="submitEditProfile"
          >
            <i v-if="!editSubmitting" class="ri-check-line" aria-hidden="true" />
            保存
          </vs-button>
        </div>
      </template>
    </vs-dialog>

    <vs-dialog
      v-model="cropperDialogVisible"
      width="640px"
      prevent-close
      class="cropper-dialog"
    >
      <template #header>
        <div class="cropper-dialog-head">
          <p class="cropper-dialog-kicker">AVATAR</p>
          <h3 class="cropper-dialog-title">裁剪头像</h3>
        </div>
      </template>

      <div class="cropper-body">
        <div class="cropper-panel">
          <Cropper
            ref="avatarCropperRef"
            :key="cropperKey"
            class="avatar-cropper"
            :src="cropperImageSrc"
            :stencil-props="{ aspectRatio: 1 }"
            image-restriction="stencil"
            :auto-zoom="true"
            @change="onCropperChange"
          />
        </div>

        <div class="cropper-controls">
          <div class="cropper-tools">
            <vs-button
              type="border"
              color="primary"
              size="small"
              class="cropper-tool-btn"
              @click="zoomCropper(-1)"
            >
              <i class="ri-zoom-out-line" aria-hidden="true" />
              缩小
            </vs-button>
            <vs-button
              type="border"
              color="primary"
              size="small"
              class="cropper-tool-btn"
              @click="zoomCropper(1)"
            >
              <i class="ri-zoom-in-line" aria-hidden="true" />
              放大
            </vs-button>
            <vs-button
              type="border"
              color="primary"
              size="small"
              class="cropper-tool-btn"
              @click="resetCropper"
            >
              <i class="ri-refresh-line" aria-hidden="true" />
              重置
            </vs-button>
          </div>

          <label class="cropper-step">
            <span class="cropper-step-label">缩放步进</span>
            <input
              v-model.number="zoomStepPercent"
              class="cropper-zoom-range"
              type="range"
              :min="4"
              :max="20"
              :step="1"
            />
            <span class="cropper-step-value">{{ zoomStepPercent }}%</span>
          </label>
        </div>

        <div class="cropper-result-row">
          <div class="cropper-result-meta">
            <p class="cropper-result-kicker">PREVIEW</p>
            <p class="cropper-result-title">裁剪结果</p>
            <p class="cropper-result-hint">拖拽选框或使用缩放，确认后上传为头像</p>
          </div>
          <div class="cropper-result-preview">
            <ui-image
              v-if="cropperResultPreview"
              :src="cropperResultPreview"
              fit="cover"
              class="cropper-result-img"
            />
            <div v-else class="cropper-result-empty">预览</div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="cropper-dialog-footer">
          <vs-button
            type="border"
            color="primary"
            class="cropper-cancel-btn"
            :disabled="avatarUploading"
            @click="cropperDialogVisible = false"
          >
            取消
          </vs-button>
          <vs-button
            color="primary"
            class="cropper-confirm-btn"
            :loading="avatarUploading"
            @click="confirmCropAndUpload"
          >
            <i v-if="!avatarUploading" class="ri-upload-2-line" aria-hidden="true" />
            确认并上传
          </vs-button>
        </div>
      </template>
    </vs-dialog>

    <vs-dialog
      v-model="avatarViewerVisible"
      full-screen
      not-padding
      class="avatar-viewer-dialog"
    >
      <div class="avatar-viewer-body" @click="avatarViewerVisible = false">
        <ui-image
          v-if="profile.avatar"
          :src="profile.avatar"
          fit="contain"
          class="avatar-viewer-img"
        />
      </div>
    </vs-dialog>

    <FollowRelationDrawer
      :visible="relationDrawerVisible"
      :owner-user-id="profile.userId"
      :owner-nickname="profile.nickname"
      :follow-count="profile.stats.followCount"
      :fans-count="profile.stats.fansCount"
      :initial-tab="relationDrawerTab"
      @update:visible="relationDrawerVisible = $event"
      @counts-change="onRelationCountsChange"
    />

    <!-- 删除发布梗确认弹窗 -->
    <vs-dialog
      v-model="deletePublishedDialogVisible"
      width="440px"
      class="delete-published-dialog"
      prevent-close
     
      @closed="resetDeletePublishedDialog"
    >
      <template #header>
        <div class="delete-published-dialog-head">
          <span class="delete-published-dialog-icon" aria-hidden="true">⚠</span>
          <span>下架梗</span>
        </div>
      </template>
      <div v-if="deletePublishedTarget" class="delete-published-body">
        <p class="delete-published-lead">
          确定要把「<strong>{{ deletePublishedTarget.name || '未命名梗图' }}</strong>」下架吗？
        </p>
        <ul class="delete-published-notes">
          <li>下架后，别人在首页、搜索里都刷不到这条梗了</li>
          <li>收藏过它的梗友再点开，会看到「找不到这条梗」</li>
          <li>30 天内可在「已下架」里恢复；也可以彻底删除</li>
        </ul>
        <div class="delete-published-preview">
          <ui-image
            v-if="deletePublishedTarget.image"
            :src="deletePublishedTarget.image"
            fit="cover"
            class="delete-published-preview-img"
          />
          <div class="delete-published-preview-meta">
            <span v-if="deletePublishedTarget.status === 2" class="delete-published-preview-tag">审核中</span>
            <span v-else-if="deletePublishedTarget.status === 1" class="delete-published-preview-tag is-live">已发布</span>
          </div>
        </div>
      </div>
      <template #footer>
        <vs-button :disabled="deletePublishedSubmitting" @click="deletePublishedDialogVisible = false">
          取消
        </vs-button>
        <vs-button
          color="danger"
         
          :loading="deletePublishedSubmitting"
          @click="confirmDeletePublished"
        >
          确认下架
        </vs-button>
      </template>
    </vs-dialog>

    <!-- 恢复下架确认弹窗 -->
    <vs-dialog
      v-model="restorePublishedDialogVisible"
      width="440px"
      class="restore-published-dialog"
      prevent-close
     
      @closed="resetRestorePublishedDialog"
    >
      <template #header>
        <div class="delete-published-dialog-head">
          <span class="delete-published-dialog-icon restore-published-dialog-icon" aria-hidden="true"><i class="ri-arrow-go-back-line" /></span>
          <span>恢复上架</span>
        </div>
      </template>
      <div v-if="restorePublishedTarget" class="delete-published-body">
        <p class="delete-published-lead">
          要把「<strong>{{ restorePublishedTarget.name || '未命名梗图' }}</strong>」重新上架吗？
        </p>
        <ul class="delete-published-notes">
          <li>恢复后会重新进入审核，通过后才会出现在首页和搜索</li>
          <li>请在下架后 30 天内操作</li>
        </ul>
      </div>
      <template #footer>
        <vs-button :disabled="restorePublishedSubmitting" @click="restorePublishedDialogVisible = false">
          取消
        </vs-button>
        <vs-button
          color="primary"
         
          :loading="restorePublishedSubmitting"
          @click="confirmRestorePublished"
        >
          确认恢复
        </vs-button>
      </template>
    </vs-dialog>

    <!-- 彻底删除确认弹窗 -->
    <vs-dialog
      v-model="purgePublishedDialogVisible"
      width="440px"
      class="purge-published-dialog"
      prevent-close
     
      @closed="resetPurgePublishedDialog"
    >
      <template #header>
        <div class="delete-published-dialog-head">
          <span class="delete-published-dialog-icon purge-published-dialog-icon" aria-hidden="true"><i class="ri-delete-bin-6-line" /></span>
          <span>彻底删除</span>
        </div>
      </template>
      <div v-if="purgePublishedTarget" class="delete-published-body">
        <p class="delete-published-lead">
          确定要彻底删除「<strong>{{ purgePublishedTarget.name || '未命名梗图' }}</strong>」吗？
        </p>
        <ul class="delete-published-notes purge-published-notes">
          <li>删除后无法恢复，「我的发布」里也不会再出现</li>
          <li>封面和相关图片会一并清理</li>
          <li>别人的评论、收藏记录仍会保留</li>
        </ul>
        <vs-input
          v-model="purgeConfirmName"
          maxlength="50"
          placeholder="输入梗名称以确认"
          class="purge-confirm-input"
        />
      </div>
      <template #footer>
        <vs-button :disabled="purgePublishedSubmitting" @click="purgePublishedDialogVisible = false">
          取消
        </vs-button>
        <vs-button
          color="danger"
         
          :loading="purgePublishedSubmitting"
          :disabled="!purgeConfirmMatched"
          @click="confirmPurgePublished"
        >
          确认彻底删除
        </vs-button>
      </template>
    </vs-dialog>

    <!-- 收藏夹新建/编辑弹窗 -->
    <vs-dialog
      v-model="folderDialogVisible"
      width="440px"
      prevent-close
      class="profile-folder-dialog"
    >
      <template #header>
        {{ folderEditingId == null ? '新建收藏夹' : '编辑收藏夹' }}
      </template>
      <ui-form
        :model="folderForm"
        label-position="top"
        class="profile-folder-form"
        @submit.prevent
      >
        <ui-form-item label="名称">
          <vs-input
            v-model="folderForm.name"
            block
            maxlength="64"
            show-word-limit
            placeholder="收藏夹名称"
            class="profile-folder-field"
          />
        </ui-form-item>
        <ui-form-item label="简介">
          <vs-input
            v-model="folderForm.description"
            type="textarea"
            block
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="可选，简单介绍这个收藏夹"
            class="profile-folder-field profile-folder-field--textarea"
          />
        </ui-form-item>
        <ui-form-item label="可见性">
          <div class="profile-folder-visibility">
            <button
              type="button"
              class="profile-folder-visibility__btn"
              :class="{ 'is-active': folderForm.isPublic === 1 }"
              @click="folderForm.isPublic = 1"
            >
              <i class="ri-earth-line" aria-hidden="true" />
              公开
            </button>
            <button
              type="button"
              class="profile-folder-visibility__btn"
              :class="{ 'is-active': folderForm.isPublic === 0 }"
              @click="folderForm.isPublic = 0"
            >
              <i class="ri-lock-2-line" aria-hidden="true" />
              私密
            </button>
          </div>
        </ui-form-item>
      </ui-form>
      <template #footer>
        <div class="profile-folder-dialog__actions">
          <button
            type="button"
            class="profile-dlg-btn profile-dlg-btn--ghost"
            :disabled="folderSubmitting"
            @click="folderDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="profile-dlg-btn profile-dlg-btn--primary"
            :disabled="folderSubmitting"
            @click="submitFolderDialog"
          >
            <i
              v-if="folderSubmitting"
              class="ri-loader-4-line profile-dlg-btn__spin"
              aria-hidden="true"
            />
            {{ folderSubmitting ? '保存中...' : '保存' }}
          </button>
        </div>
      </template>
    </vs-dialog>

    <!-- 移动至收藏夹弹窗 -->
    <vs-dialog
      v-model="batchMoveDialogVisible"
      width="420px"
      class="profile-folder-dialog"
      @closed="onMoveDialogClosed"
    >
      <template #header>{{ moveDialogTitle }}</template>
      <div class="favorite-folder-picker">
        <button
          v-for="f in folderList"
          :key="`move-folder-${f.id}`"
          type="button"
          class="favorite-folder-item"
          :class="{ active: sameFolderId(batchMoveTargetId, f.id) }"
          @click="batchMoveTargetId = normalizeFolderId(f.id)"
        >
          <span class="favorite-folder-icon" aria-hidden="true">
            <i :class="f.isDefault ? 'ri-star-line' : 'ri-folder-3-line'" />
          </span>
          <span class="folder-meta">
            <span class="folder-name">{{ f.name }}</span>
            <span class="folder-count">{{ f.memeCount || 0 }} 个梗图</span>
          </span>
          <i
            v-if="sameFolderId(batchMoveTargetId, f.id)"
            class="ri-check-line favorite-folder-check"
            aria-hidden="true"
          />
        </button>
      </div>
      <template #footer>
        <div class="profile-folder-dialog__actions">
          <button
            type="button"
            class="profile-dlg-btn profile-dlg-btn--ghost"
            :disabled="batchMoveSubmitting"
            @click="batchMoveDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="profile-dlg-btn profile-dlg-btn--primary"
            :disabled="batchMoveSubmitting"
            @click="confirmBatchMove"
          >
            <i
              v-if="batchMoveSubmitting"
              class="ri-loader-4-line profile-dlg-btn__spin"
              aria-hidden="true"
            />
            {{ batchMoveSubmitting ? '移动中...' : '移动' }}
          </button>
        </div>
      </template>
    </vs-dialog>
  </div>
</template>

<script>
import { Cropper } from 'vue-advanced-cropper'
import { toast, confirmBox } from '@/utils/uiFeedback'
import { getEditProfileEcho, getUserProfile, updateUserProfile } from '@/api/user'
import { pageUserMemes } from '@/api/user'
import { uploadToOss } from '@/api/oss'
import {
  getMyFavoriteFolders,
  getUserFavoriteFolders,
  createFavoriteFolder,
  updateFavoriteFolder,
  deleteFavoriteFolder,
  pageUserFavorites,
  normalizeFolderId,
  sameFolderId,
} from '@/api/favoriteFolder'
import { batchMoveFavorites, removeMemeFavorite, deletePublishedMeme, restorePublishedMeme, purgePublishedMeme } from '@/api/meme'
import { useAuthStore } from '@/stores/auth'
import { useBreadcrumbStore } from '@/stores/breadcrumb'
import ListLoadFooter from '@/components/layout/ListLoadFooter.vue'
import MemeCard from '@/components/meme/MemeCard.vue'
import { resolvePageHasMore } from '@/utils/pagination'
import { buildMemeDetailLocation, buildToolPageLocation } from '@/utils/pageBreadcrumb'
import FollowButton from '@/components/user/FollowButton.vue'
import FollowRelationDrawer from '@/components/user/FollowRelationDrawer.vue'
import { getFollowStatus } from '@/api/follow'

export default {
  name: 'UserProfilePage',
  components: {
    Cropper,
    ListLoadFooter,
    MemeCard,
    FollowButton,
    FollowRelationDrawer,
  },
  data() {
    return {
      activeTab: 'published',
      loading: false,
      errorMessage: '',
      avatarViewerVisible: false,
      editDialogVisible: false,
      editEchoLoading: false,
      cropperDialogVisible: false,
      editSubmitting: false,
      avatarUploading: false,
      cropperImageSrc: '',
      cropperResultPreview: '',
      cropperKey: 0,
      zoomStepPercent: 8,
      editForm: {
        nickname: '',
        signature: '',
        gender: 0,
        birthday: '',
        avatar: '',
      },
      genderOptions: [
        { value: 0, label: '保密', icon: 'ri-eye-off-line' },
        { value: 1, label: '男', icon: 'ri-men-line' },
        { value: 2, label: '女', icon: 'ri-women-line' },
      ],
      editRules: {
        nickname: [{ max: 50, message: '昵称长度最多 50 个字符', trigger: 'blur' }],
        signature: [{ max: 255, message: '签名最多 255 个字符', trigger: 'blur' }],
      },
      profile: {
        userId: '',
        nickname: '',
        avatar: '',
        signature: '',
        gender: 0,
        birthday: '',
        isSelf: false,
        isFollow: false,
        memes: [],
        favorites: [],
        stats: {
          memeCount: 0,
          likeReceived: 0,
          favoriteCount: 0,
          followCount: 0,
          fansCount: 0,
        },
      },
      followMutual: false,
      relationDrawerVisible: false,
      relationDrawerTab: 'following',
      followStatusEpoch: 0,
      publishedPage: {
        list: [],
        total: 0,
        hasMore: false,
        isOwner: false,
      },
      publishedPageNo: 1,
      publishedPageSize: 16,
      publishedLoading: false,
      publishedStatusFilter: 'all',
      deletePublishedDialogVisible: false,
      deletePublishedTarget: null,
      deletePublishedSubmitting: false,
      restorePublishedDialogVisible: false,
      restorePublishedTarget: null,
      restorePublishedSubmitting: false,
      purgePublishedDialogVisible: false,
      purgePublishedTarget: null,
      purgePublishedSubmitting: false,
      purgeConfirmName: '',
      // 收藏夹
      folderList: [],
      folderLoading: false,
      selectedFolderId: '0',
      folderContentPage: { list: [], total: 0, hasMore: false },
      folderContentPageNo: 1,
      folderContentPageSize: 12,
      folderContentLoading: false,
      folderSelectAll: false,
      selectedMemeIds: [],
      // 收藏夹 CRUD 弹窗
      folderDialogVisible: false,
      folderEditingId: null,
      folderSubmitting: false,
      folderForm: { name: '', description: '', isPublic: 1 },
      // 批量移动
      batchMoveDialogVisible: false,
      batchMoveTargetId: '0',
      batchMoveSubmitting: false,
      singleMoveMemeId: null,
      favoriteViewMode: 'folders',
    }
  },
  computed: {
    currentUser() {
      return useAuthStore().currentUser
    },
    authToken() {
      return useAuthStore().token
    },
    publishLocation() {
      return buildToolPageLocation('publishMeme', {
        fromRoute: this.$route,
        profileName: this.profile?.nickname || '',
      })
    },
    routeUserId() {
      return this.$route.params.userId != null
        ? String(this.$route.params.userId).trim()
        : ''
    },
    currentUserId() {
      const user = this.currentUser
      if (!user || typeof user !== 'object') return ''
      if (user.id != null) return String(user.id).trim()
      if (user.userId != null) return String(user.userId).trim()
      return ''
    },
    requestUserId() {
      if (this.routeUserId && /^\d+$/.test(this.routeUserId)) {
        return this.routeUserId
      }
      if (this.currentUserId && /^\d+$/.test(this.currentUserId)) {
        return this.currentUserId
      }
      return ''
    },
    isRequestUserIdValid() {
      return /^\d+$/.test(this.requestUserId)
    },
    isOwnProfile() {
      if (this.profile.isSelf) return true
      return !!this.currentUserId && this.currentUserId === String(this.profile.userId)
    },
    showEditButton() {
      return this.isOwnProfile
    },
    avatarFallback() {
      const nickname = this.profile.nickname || ''
      return nickname ? nickname.charAt(0).toUpperCase() : 'U'
    },
    publishedList() {
      return Array.isArray(this.publishedPage.list) ? this.publishedPage.list : []
    },
    publishedDisplayTotal() {
      const apiTotal = Number(this.publishedPage.total) || 0
      const listLen = this.publishedList.length
      return Math.max(apiTotal, listLen)
    },
    filteredPublishedList() {
      if (!this.isOwnProfile || this.publishedStatusFilter === 'all') {
        return this.publishedList
      }
      const status = Number(this.publishedStatusFilter)
      return this.publishedList.filter((item) => Number(item.status) === status)
    },
    publishedFilterOptions() {
      if (!this.isOwnProfile) return []
      const list = this.publishedList
      const countBy = (status) => {
        if (status === 'all') return list.length
        return list.filter((item) => Number(item.status) === status).length
      }
      return [
        { key: 'all', label: '全部', count: countBy('all') },
        { key: 1, label: '已发布', count: countBy(1) },
        { key: 2, label: '审核中', count: countBy(2) },
        { key: 3, label: '已下架', count: countBy(3) },
      ]
    },
    publishedHasMore() {
      return !!this.publishedPage.hasMore
    },
    purgeConfirmMatched() {
      if (!this.purgePublishedTarget) return false
      const expected = String(this.purgePublishedTarget.name || '未命名梗图').trim()
      return String(this.purgeConfirmName || '').trim() === expected
    },
    favoriteList() {
      return Array.isArray(this.profile.favorites) ? this.profile.favorites : []
    },
    favoriteTotal() {
      return this.folderList.reduce((sum, f) => sum + (Number(f.memeCount) || 0), 0)
    },
    folderContentList() {
      return Array.isArray(this.folderContentPage.list) ? this.folderContentPage.list : []
    },
    folderContentHasMore() {
      return !!this.folderContentPage.hasMore
    },
    selectedFolderName() {
      const folder = this.folderList.find((f) => sameFolderId(f.id, this.selectedFolderId))
      return folder ? folder.name : '收藏夹'
    },
    moveDialogTitle() {
      if (this.singleMoveMemeId != null) return '移动至'
      if (this.selectedMemeIds.length > 1) return `移动 ${this.selectedMemeIds.length} 项到收藏夹`
      return '移动到收藏夹'
    },
  },
  watch: {
    '$route.params.userId': {
      immediate: true,
      handler() {
        this.syncRouteUserId()
        this.resetFavoriteViewState()
        this.applyRouteTab()
        this.loadProfile()
      },
    },
    '$route.query.tab'() {
      this.applyRouteTab()
    },
    currentUserId() {
      this.syncRouteUserId()
      this.loadProfile()
    },
    activeTab(val) {
      if (val === 'favorite') {
        this.loadFolders()
        if (this.isOwnProfile && this.$route.query.tab !== 'favorite') {
          this.$router.replace({
            name: 'userProfile',
            params: this.$route.params,
            query: { ...this.$route.query, tab: 'favorite' },
          })
        }
      } else {
        this.favoriteViewMode = 'folders'
        if (this.$route.query.tab === 'favorite') {
          const query = { ...this.$route.query }
          delete query.tab
          this.$router.replace({
            name: 'userProfile',
            params: this.$route.params,
            query,
          })
        }
      }
    },
  },
  methods: {
    normalizeFolderId,
    sameFolderId,
    syncRouteUserId() {
      if (this.$route.name !== 'userProfile') return
      const isMeAlias = this.routeUserId === 'me'
      const hasNumericUserId = this.routeUserId && /^\d+$/.test(this.routeUserId)
      if (hasNumericUserId && !isMeAlias) return
      if (!this.currentUserId || !/^\d+$/.test(this.currentUserId)) return
      this.$router.replace({
        name: 'userProfile',
        params: { userId: this.currentUserId },
        query: this.$route.query,
      })
    },
    applyRouteTab() {
      const tab = this.$route.query.tab != null ? String(this.$route.query.tab).trim() : ''
      if (tab !== 'favorite') return
      const isSelfRoute = this.isOwnProfile
        || this.routeUserId === 'me'
        || (this.currentUserId && this.routeUserId === this.currentUserId)
      if (isSelfRoute) {
        this.activeTab = 'favorite'
      }
    },
    resetFavoriteViewState() {
      this.favoriteViewMode = 'folders'
      this.folderList = []
      this.selectedFolderId = '0'
      this.folderContentPage = { list: [], total: 0, hasMore: false }
      this.selectedMemeIds = []
      this.folderSelectAll = false
    },
    async loadProfile() {
      if (!this.requestUserId) {
        this.errorMessage = '无效的用户ID'
        this.syncBreadcrumb()
        return
      }
      if (!this.isRequestUserIdValid) {
        this.errorMessage = '用户ID格式错误'
        this.syncBreadcrumb()
        return
      }
      this.loading = true
      this.syncBreadcrumb()
      this.errorMessage = ''
      try {
        const data = await getUserProfile(this.requestUserId, this.authToken)
        this.profile = {
          ...this.profile,
          ...data,
          isFollow: Boolean(data.isFollow),
          stats: {
            ...this.profile.stats,
            ...(data.stats || {}),
          },
        }
        this.followMutual = false
        this.publishedPageNo = 1
        this.loadPublishedMemes()
        this.applyRouteTab()
        this.refreshFollowMutual()
        if (this.activeTab === 'favorite') {
          this.loadFolders()
        }
      } catch (error) {
        this.errorMessage = error && error.message ? error.message : '个人主页加载失败'
      } finally {
        this.loading = false
        this.syncBreadcrumb()
      }
    },
    syncBreadcrumb() {
      useBreadcrumbStore().setPatch({
        nickname: this.profile?.nickname || '',
        loading: this.loading,
      })
    },
    openAvatarViewer() {
      if (!this.profile.avatar) return
      this.avatarViewerVisible = true
    },
    goMemeDetail(id) {
      if (id == null || id === '') return
      let options = {}
      if (this.isOwnProfile) {
        if (this.activeTab === 'published') {
          options = { from: 'published' }
        } else if (this.activeTab === 'favorite') {
          options = { from: 'favorite' }
        }
      } else {
        options = {
          from: 'profile',
          userId: this.routeUserId,
          profileName: this.profile.nickname || '',
        }
      }
      this.$router.push(buildMemeDetailLocation(id, options))
    },
    handlePublishedCardClick(item) {
      if (!item) return
      if (item.status === 3) {
        if (!this.isOwnProfile) {
          toast.info('这条梗已经下线啦，黑历史只能你自己翻')
          return
        }
        this.goMemeDetail(item.id)
        return
      }
      this.goMemeDetail(item.id)
    },
    handlePublishedMemeCommand(command, item) {
      if (!item) return
      if (command === 'delete') {
        this.openDeletePublishedDialog(item)
      } else if (command === 'restore') {
        this.openRestorePublishedDialog(item)
      } else if (command === 'purge') {
        this.openPurgePublishedDialog(item)
      }
    },
    openDeletePublishedDialog(item) {
      if (!item || item.status === 3) return
      this.deletePublishedTarget = { ...item }
      this.deletePublishedDialogVisible = true
    },
    resetDeletePublishedDialog() {
      this.deletePublishedTarget = null
      this.deletePublishedSubmitting = false
    },
    async confirmDeletePublished() {
      if (!this.deletePublishedTarget) return
      await this.doDeletePublishedMeme(this.deletePublishedTarget.id)
      this.deletePublishedDialogVisible = false
    },
    confirmDeletePublishedMeme(item) {
      this.openDeletePublishedDialog(item)
    },
    async doDeletePublishedMeme(memeId) {
      const id = this.normalizeMemeId(memeId)
      if (!id) return
      this.deletePublishedSubmitting = true
      try {
        await deletePublishedMeme(id)
        toast.success('梗已下架')
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        toast.error((e && e.message) || '删除失败')
      } finally {
        this.deletePublishedSubmitting = false
      }
    },
    openRestorePublishedDialog(item) {
      if (!item || item.status !== 3) return
      this.restorePublishedTarget = { ...item }
      this.restorePublishedDialogVisible = true
    },
    resetRestorePublishedDialog() {
      this.restorePublishedTarget = null
      this.restorePublishedSubmitting = false
    },
    async confirmRestorePublished() {
      if (!this.restorePublishedTarget) return
      const id = this.normalizeMemeId(this.restorePublishedTarget.id)
      if (!id) return
      this.restorePublishedSubmitting = true
      try {
        const data = await restorePublishedMeme(id)
        toast.success(`已恢复，当前状态：${data.statusDesc || '审核中'}`)
        this.restorePublishedDialogVisible = false
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        toast.error((e && e.message) || '恢复失败')
      } finally {
        this.restorePublishedSubmitting = false
      }
    },
    openPurgePublishedDialog(item) {
      if (!item || item.status !== 3) return
      this.purgePublishedTarget = { ...item }
      this.purgeConfirmName = ''
      this.purgePublishedDialogVisible = true
    },
    resetPurgePublishedDialog() {
      this.purgePublishedTarget = null
      this.purgePublishedSubmitting = false
      this.purgeConfirmName = ''
    },
    async confirmPurgePublished() {
      if (!this.purgePublishedTarget || !this.purgeConfirmMatched) return
      const id = this.normalizeMemeId(this.purgePublishedTarget.id)
      if (!id) return
      this.purgePublishedSubmitting = true
      try {
        await purgePublishedMeme(id)
        toast.success('已从发布列表彻底删除')
        this.purgePublishedDialogVisible = false
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        toast.error((e && e.message) || '彻底删除失败')
      } finally {
        this.purgePublishedSubmitting = false
      }
    },
    async loadPublishedMemes() {
      if (!this.requestUserId || !/^\d+$/.test(this.requestUserId)) return
      this.publishedLoading = true
      try {
        const data = await pageUserMemes(this.requestUserId, {
          page: this.publishedPageNo,
          size: this.publishedPageSize,
        })
        const list = Array.isArray(data.list) ? data.list : []
        // 新接口字段为 memeId / id，统一映射到组件内部用的 id
        const normalized = list.map((it) => ({
          ...it,
          id: it.id ?? it.memeId,
          author: it.author || data.author || null,
        }))
        if (this.publishedPageNo === 1) {
          this.publishedPage = {
            list: normalized,
            total: Number(data.total) || 0,
            hasMore: resolvePageHasMore(data, normalized.length, this.publishedPageSize, normalized.length),
            isOwner: !!(data.isOwner ?? data.owner ?? this.isOwnProfile),
          }
          this.publishedStatusFilter = 'all'
        } else {
          const merged = this.publishedPage.list.concat(normalized)
          this.publishedPage = {
            ...this.publishedPage,
            list: merged,
            total: Number(data.total) || this.publishedPage.total,
            hasMore: resolvePageHasMore(data, normalized.length, this.publishedPageSize, merged.length),
            isOwner: !!(data.isOwner ?? data.owner ?? this.publishedPage.isOwner),
          }
        }
      } catch (e) {
        toast.error(e.message || '发布列表加载失败')
      } finally {
        this.publishedLoading = false
      }
    },
    loadMorePublished() {
      if (this.publishedLoading || !this.publishedHasMore) return
      this.publishedPageNo += 1
      this.loadPublishedMemes()
    },
    statusText(status) {
      if (status === 2) return '审核中'
      if (status === 3) return '已下架'
      return ''
    },
    async handleEditProfile() {
      this.editDialogVisible = true
      this.editEchoLoading = true
      try {
        const echo = await getEditProfileEcho(this.authToken)
        this.editForm = {
          nickname: echo.nickname || '',
          signature: echo.signature || '',
          gender: Number.isFinite(Number(echo.gender)) ? Number(echo.gender) : 0,
          birthday: this.normalizeDate(echo.birthday),
          avatar: echo.avatar || '',
        }
      } catch (error) {
        this.editForm = {
          nickname: this.profile.nickname || '',
          signature: this.profile.signature || '',
          gender: Number.isFinite(Number(this.profile.gender)) ? Number(this.profile.gender) : 0,
          birthday: this.normalizeDate(this.profile.birthday),
          avatar: this.profile.avatar || '',
        }
        toast.warning(error && error.message ? error.message : '回显接口异常，已使用当前页面数据')
      } finally {
        this.editEchoLoading = false
      }
    },
    triggerAvatarUpload() {
      const input = this.$refs.avatarFileInput
      if (input && input.click) input.click()
    },
    async onAvatarFileChange(event) {
      const file = event && event.target && event.target.files ? event.target.files[0] : null
      try {
        if (!file) return
        const isImage = /image\/(jpeg|jpg|png|webp)/i.test(file.type)
        if (!isImage) {
          toast.warning('仅支持 JPG/PNG/WEBP 格式')
          return
        }
        if (file.size > 5 * 1024 * 1024) {
          toast.warning('头像大小不能超过 5MB')
          return
        }
        this.cropperImageSrc = await this.readFileAsDataURL(file)
        this.cropperResultPreview = this.cropperImageSrc
        this.cropperKey += 1
        this.cropperDialogVisible = true
      } catch (error) {
        toast.error(error && error.message ? error.message : '读取头像失败')
      } finally {
        if (event && event.target) {
          event.target.value = ''
        }
      }
    },
    async confirmCropAndUpload() {
      const cropper = this.$refs.avatarCropperRef
      if (!cropper || !cropper.getResult) {
        toast.warning('裁剪器初始化中，请稍后重试')
        return
      }
      try {
        const result = cropper.getResult()
        const canvas = result && result.canvas ? result.canvas : null
        if (!canvas || !canvas.toBlob) {
          toast.warning('裁剪结果无效，请重新选择图片')
          return
        }
        this.avatarUploading = true
        const blob = await new Promise((resolve, reject) => {
          canvas.toBlob(
            (fileBlob) => {
              if (fileBlob) resolve(fileBlob)
              else reject(new Error('头像裁剪失败'))
            },
            'image/jpeg',
            0.92
          )
        })
        const uploadFile = new File([blob], `avatar_${Date.now()}.jpg`, { type: 'image/jpeg' })
        const url = await uploadToOss(uploadFile, 'avatar', `avatar_${Date.now()}.jpg`)
        this.editForm.avatar = url
        this.cropperDialogVisible = false
        toast.success('头像上传成功')
      } catch (error) {
        toast.error(error && error.message ? error.message : '头像上传失败')
      } finally {
        this.avatarUploading = false
      }
    },
    onCropperChange(changeResult) {
      const canvas = changeResult && changeResult.canvas ? changeResult.canvas : null
      if (!canvas || !canvas.toDataURL) return
      this.cropperResultPreview = canvas.toDataURL('image/jpeg', 0.88)
    },
    zoomCropper(direction) {
      const cropper = this.$refs.avatarCropperRef
      if (!cropper || typeof cropper.zoom !== 'function') return
      const step = Math.max(0.04, Number(this.zoomStepPercent || 8) / 100)
      const factor = direction > 0 ? 1 + step : 1 - step
      cropper.zoom(factor)
    },
    resetCropper() {
      this.cropperKey += 1
      this.$nextTick(() => {
        this.cropperResultPreview = this.cropperImageSrc
      })
    },
    readFileAsDataURL(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => {
          if (typeof reader.result === 'string') {
            resolve(reader.result)
          } else {
            reject(new Error('图片读取失败'))
          }
        }
        reader.onerror = () => reject(new Error('图片读取失败'))
        reader.readAsDataURL(file)
      })
    },
    submitEditProfile() {
      if (this.editSubmitting || this.avatarUploading) return
      this.$refs.editFormRef.validate(async (valid) => {
        if (!valid) return
        this.editSubmitting = true
        try {
          const payload = {
            nickname: this.editForm.nickname,
            signature: this.editForm.signature,
            gender: this.editForm.gender,
            birthday: this.editForm.birthday || '',
            avatar: this.editForm.avatar || '',
          }
          await updateUserProfile(payload, this.authToken)

          this.profile = {
            ...this.profile,
            ...payload,
          }
          const authStore = useAuthStore()
          authStore.updateCurrentUser({
            nickname: payload.nickname,
            signature: payload.signature,
            avatar: payload.avatar,
            gender: payload.gender,
            birthday: payload.birthday,
          })

          this.editDialogVisible = false
          toast.success('资料更新成功')
          await this.loadProfile()
        } catch (error) {
          toast.error(error && error.message ? error.message : '资料更新失败')
        } finally {
          this.editSubmitting = false
        }
      })
    },
    normalizeDate(value) {
      if (!value) return ''
      const str = String(value).trim()
      const match = str.match(/^(\d{4}-\d{2}-\d{2})/)
      return match ? match[1] : ''
    },
    formatNum(num) {
      if (num == null) return '0'
      const n = Number(num)
      if (!Number.isFinite(n)) return '0'
      if (n >= 1e8) return `${(n / 1e8).toFixed(1)}亿`
      if (n >= 1e4) return `${(n / 1e4).toFixed(1)}万`
      return String(Math.floor(n))
    },
    async refreshFollowMutual() {
      if (!this.authToken || this.isOwnProfile || !this.profile.userId) {
        this.followMutual = false
        return
      }
      const userId = String(this.profile.userId)
      const epoch = ++this.followStatusEpoch
      try {
        const status = await getFollowStatus(userId, this.authToken)
        if (epoch !== this.followStatusEpoch) return
        if (String(this.profile.userId) !== userId) return
        this.profile.isFollow = Boolean(status.followed)
        this.followMutual = Boolean(status.mutual)
        if (status.fansCount != null) {
          this.profile.stats.fansCount = Number(status.fansCount) || 0
        }
        if (status.followCount != null) {
          this.profile.stats.followCount = Number(status.followCount) || 0
        }
      } catch (_) {
        // 状态刷新失败不影响主页展示
      }
    },
    openRelationDrawer(tab) {
      if (!this.profile.userId) return
      this.relationDrawerTab = tab === 'followers' ? 'followers' : 'following'
      this.relationDrawerVisible = true
    },
    onProfileFollowChange(payload) {
      if (!payload) return
      // 作废在途的状态同步，避免把刚关注的结果冲掉
      this.followStatusEpoch += 1
      this.profile.isFollow = Boolean(payload.followed)
      this.followMutual = Boolean(payload.mutual)
      if (payload.fansCount != null) {
        this.profile.stats.fansCount = Number(payload.fansCount) || 0
      }
    },
    onRelationCountsChange(payload) {
      if (!payload) return
      const targetId = payload.targetUserId != null ? String(payload.targetUserId) : ''
      const tab = payload.tab || this.relationDrawerTab
      // 在本人「关注」列表里关注/取消别人 → 更新自己的关注数
      if (this.isOwnProfile && tab === 'following' && targetId) {
        const delta = payload.followed ? 1 : -1
        this.profile.stats.followCount = Math.max(
          0,
          (Number(this.profile.stats.followCount) || 0) + delta
        )
        return
      }
      // 操作的是主页主人本人 → 同步粉丝数
      if (targetId && targetId === String(this.profile.userId) && payload.fansCount != null) {
        this.profile.stats.fansCount = Number(payload.fansCount) || 0
        this.profile.isFollow = Boolean(payload.followed)
        this.followMutual = Boolean(payload.mutual)
      }
    },
    formatFavoriteTime(t) {
      if (!t) return ''
      const m = String(t).match(/(\d{4}-\d{2}-\d{2})/)
      return m ? m[1] : ''
    },
    // ==================== 收藏夹 ====================
    async loadFolders() {
      const userId = String(this.profile.userId || this.requestUserId || '').trim()
      if (!/^\d+$/.test(userId)) return
      this.folderLoading = true
      try {
        const api = this.isOwnProfile ? getMyFavoriteFolders : getUserFavoriteFolders
        const { folders } = await api(this.isOwnProfile ? undefined : userId)
        this.folderList = folders || []
        if (!this.folderList.length) {
          this.selectedFolderId = '0'
          this.folderContentPage = { list: [], total: 0, hasMore: false }
          return
        }
        const exists = this.folderList.some((f) => sameFolderId(f.id, this.selectedFolderId))
        if (!exists) {
          this.selectedFolderId = normalizeFolderId(this.folderList[0].id)
        }
        this.folderContentPageNo = 1
        this.selectedMemeIds = []
        this.folderSelectAll = false
        if (this.favoriteViewMode === 'folder-detail') {
          await this.loadFolderContent()
        }
      } catch (e) {
        toast.error((e && e.message) || '收藏夹加载失败')
      } finally {
        this.folderLoading = false
      }
    },
    selectFolder(folderId) {
      if (sameFolderId(this.selectedFolderId, folderId)) return
      this.selectedFolderId = normalizeFolderId(folderId)
      this.folderContentPageNo = 1
      this.selectedMemeIds = []
      this.folderSelectAll = false
      this.loadFolderContent()
    },
    openFolderDetail(folder) {
      if (!folder) return
      if (!sameFolderId(this.selectedFolderId, folder.id)) {
        this.selectFolder(folder.id)
      } else if (!this.folderContentLoading && !this.folderContentList.length) {
        this.loadFolderContent()
      }
      this.favoriteViewMode = 'folder-detail'
    },
    backToFolderGrid() {
      this.favoriteViewMode = 'folders'
      this.selectedMemeIds = []
      this.folderSelectAll = false
    },
    async loadFolderContent() {
      const userId = String(this.profile.userId || this.requestUserId || '').trim()
      if (!/^\d+$/.test(userId)) return
      this.folderContentLoading = true
      try {
        const data = await pageUserFavorites(userId, {
          folderId: this.selectedFolderId,
          page: this.folderContentPageNo,
          size: this.folderContentPageSize,
        })
        const list = Array.isArray(data.list) ? data.list : []
        if (this.folderContentPageNo === 1) {
          this.folderContentPage = {
            list,
            total: Number(data.total) || 0,
            hasMore: resolvePageHasMore(data, list.length, this.folderContentPageSize, list.length),
          }
        } else {
          const merged = this.folderContentPage.list.concat(list)
          this.folderContentPage = {
            list: merged,
            total: Number(data.total) || this.folderContentPage.total,
            hasMore: resolvePageHasMore(data, list.length, this.folderContentPageSize, merged.length),
          }
        }
        this.refreshFolderSelectAll()
      } catch (e) {
        toast.error((e && e.message) || '收藏列表加载失败')
      } finally {
        this.folderContentLoading = false
      }
    },
    loadMoreFolderContent() {
      if (this.folderContentLoading || !this.folderContentHasMore) return
      this.folderContentPageNo += 1
      this.loadFolderContent()
    },
    handleFolderCommand(cmd, folder) {
      if (cmd === 'edit') this.openEditFolderDialog(folder)
      else if (cmd === 'delete') this.confirmDeleteFolder(folder)
    },
    openCreateFolderDialog() {
      this.folderEditingId = null
      this.folderForm = { name: '', description: '', isPublic: 1 }
      this.folderDialogVisible = true
    },
    openEditFolderDialog(folder) {
      this.folderEditingId = normalizeFolderId(folder.id)
      this.folderForm = {
        name: folder.name || '',
        description: folder.description || '',
        isPublic: Number(folder.isPublic) || 1,
      }
      this.folderDialogVisible = true
    },
    async submitFolderDialog() {
      const name = (this.folderForm.name || '').trim()
      if (!name) {
        toast.warning('请输入收藏夹名称')
        return
      }
      this.folderSubmitting = true
      try {
        if (this.folderEditingId == null) {
          await createFavoriteFolder({
            name,
            description: this.folderForm.description,
            isPublic: this.folderForm.isPublic,
          })
          toast.success('收藏夹已创建')
        } else {
          await updateFavoriteFolder(this.folderEditingId, {
            name,
            description: this.folderForm.description,
            isPublic: this.folderForm.isPublic,
          })
          toast.success('收藏夹已更新')
        }
        this.folderDialogVisible = false
        await this.loadFolders()
      } catch (e) {
        toast.error((e && e.message) || '保存失败')
      } finally {
        this.folderSubmitting = false
      }
    },
    confirmDeleteFolder(folder) {
      if (folder && folder.isDefault) {
        toast.warning('默认收藏夹不可删除')
        return
      }
      confirmBox(`删除「${folder.name}」？夹内梗图将移入默认收藏夹，且不会取消收藏。`, '提示', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }).then(() => this.doDeleteFolder(folder)).catch((e) => { if (e === 'cancel') return })
    },
    async doDeleteFolder(folder) {
      try {
        await deleteFavoriteFolder(folder.id)
        toast.success('收藏夹已删除')
        if (sameFolderId(this.selectedFolderId, folder.id)) {
          this.selectedFolderId = '0'
          this.favoriteViewMode = 'folders'
        }
        await this.loadFolders()
      } catch (e) {
        toast.error((e && e.message) || '删除失败')
      }
    },
    normalizeMemeId(memeId) {
      return memeId != null ? String(memeId).trim() : ''
    },
    isMemeSelected(memeId) {
      const id = this.normalizeMemeId(memeId)
      return this.selectedMemeIds.some((x) => this.normalizeMemeId(x) === id)
    },
    toggleMemeSelect(memeId, val) {
      const id = this.normalizeMemeId(memeId)
      if (!id) return
      if (val) {
        if (!this.isMemeSelected(id)) this.selectedMemeIds.push(id)
      } else {
        this.selectedMemeIds = this.selectedMemeIds.filter(
          (x) => this.normalizeMemeId(x) !== id
        )
      }
      this.refreshFolderSelectAll()
    },
    onSelectAllFolderItems(val) {
      if (val) {
        this.selectedMemeIds = this.folderContentList.map((item) => this.normalizeMemeId(item.id))
      } else {
        this.selectedMemeIds = []
      }
    },
    refreshFolderSelectAll() {
      const all = this.folderContentList.map((item) => this.normalizeMemeId(item.id))
      this.folderSelectAll =
        all.length > 0 && all.every((id) => this.selectedMemeIds.some((x) => this.normalizeMemeId(x) === id))
    },
    handleFavoriteMemeCommand(command, item) {
      if (command === 'move') {
        this.openMoveDialog(item.id)
        return
      }
      if (command === 'remove') {
        this.confirmRemoveFavorite(item)
      }
    },
    openMoveDialog(memeId) {
      this.singleMoveMemeId = memeId != null ? this.normalizeMemeId(memeId) : null
      this.batchMoveTargetId = '0'
      this.batchMoveDialogVisible = true
    },
    openBatchMoveDialog() {
      if (!this.selectedMemeIds.length) return
      this.singleMoveMemeId = null
      this.batchMoveTargetId = '0'
      this.batchMoveDialogVisible = true
    },
    clearMemeSelection() {
      this.selectedMemeIds = []
      this.folderSelectAll = false
    },
    onMoveDialogClosed() {
      this.singleMoveMemeId = null
    },
    async confirmBatchMove() {
      const ids =
        this.singleMoveMemeId != null
          ? [this.singleMoveMemeId]
          : this.selectedMemeIds.slice()
      if (!ids.length) return
      if (sameFolderId(this.batchMoveTargetId, this.selectedFolderId)) {
        toast.warning('已在当前收藏夹中')
        return
      }
      this.batchMoveSubmitting = true
      try {
        await batchMoveFavorites(this.batchMoveTargetId, ids)
        toast.success(ids.length > 1 ? `已移动 ${ids.length} 项` : '已移动')
        this.batchMoveDialogVisible = false
        this.selectedMemeIds = []
        this.folderSelectAll = false
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        toast.error((e && e.message) || '移动失败')
      } finally {
        this.batchMoveSubmitting = false
      }
    },
    confirmRemoveFavorite(item) {
      const name = (item && item.name) || '未命名梗图'
      confirmBox(`确定取消收藏「${name}」？`, '取消收藏', {
        type: 'warning',
        confirmButtonText: '取消收藏',
        cancelButtonText: '返回',
      })
        .then(() => this.doRemoveFavorite(item.id)).catch((e) => { if (e === 'cancel') return })
    },
    confirmBatchRemoveFavorites() {
      const count = this.selectedMemeIds.length
      if (!count) return
      const tip =
        count > 1 ? `确定取消收藏已选的 ${count} 个梗图？` : '确定取消收藏已选的梗图？'
      confirmBox(tip, '批量取消收藏', {
        type: 'warning',
        confirmButtonText: '取消收藏',
        cancelButtonText: '返回',
      })
        .then(() => this.doBatchRemoveFavorites()).catch((e) => { if (e === 'cancel') return })
    },
    async doBatchRemoveFavorites() {
      const ids = this.selectedMemeIds.slice()
      if (!ids.length) return
      try {
        await Promise.all(ids.map((id) => removeMemeFavorite(id)))
        toast.success(ids.length > 1 ? `已取消收藏 ${ids.length} 项` : '已取消收藏')
        this.selectedMemeIds = []
        this.folderSelectAll = false
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        toast.error((e && e.message) || '取消收藏失败')
      }
    },
    async doRemoveFavorite(memeId) {
      const id = this.normalizeMemeId(memeId)
      if (!id) return
      try {
        await removeMemeFavorite(id)
        toast.success('已取消收藏')
        this.selectedMemeIds = this.selectedMemeIds.filter(
          (x) => this.normalizeMemeId(x) !== id
        )
        this.refreshFolderSelectAll()
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        toast.error((e && e.message) || '取消收藏失败')
      }
    },
  },
}
</script>

<style scoped>
.profile-page {
  margin: -16px -32px -32px;
  padding: 16px 24px 32px;
  min-height: calc(100vh - 56px);
  background: var(--meme-gradient-page);
  width: calc(100% + 64px);
}

.profile-loading-page {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--meme-text-secondary);
}

.profile-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1120px;
  margin: 0 auto;
}

.profile-hero {
  position: relative;
  border-radius: var(--meme-radius-lg);
  overflow: hidden;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.profile-hero__cover {
  height: 96px;
  background:
    radial-gradient(circle at 88% 18%, rgba(49, 138, 239, 0.22), transparent 52%),
    radial-gradient(circle at 12% 82%, rgba(82, 199, 184, 0.14), transparent 48%),
    linear-gradient(135deg, var(--meme-primary-soft) 0%, var(--meme-bg-muted) 100%);
}

.profile-hero__inner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px 18px;
  margin-top: -36px;
  flex-wrap: wrap;
}

.profile-hero__main {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  min-width: 0;
  flex: 1;
}

.profile-hero__avatar {
  flex-shrink: 0;
  cursor: zoom-in;
  width: 88px;
  height: 88px;
  border: 3px solid var(--meme-bg-card);
  box-shadow: var(--meme-shadow-card);
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
}

.profile-hero__meta {
  min-width: 0;
  padding-bottom: 2px;
}

.profile-hero__kicker {
  margin-bottom: 2px;
}

.profile-hero__name {
  margin: 0 0 6px;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--meme-text);
  word-break: break-all;
}

.profile-hero__signature {
  margin: 0 0 10px;
  color: var(--meme-text-secondary);
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}

.profile-hero__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.profile-stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--meme-border);
  border-radius: 999px;
  background: var(--meme-bg-muted);
  cursor: pointer;
  color: inherit;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.profile-stat-chip:hover {
  border-color: var(--meme-border-accent);
  background: var(--meme-primary-soft);
}

.profile-stat-chip strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
}

.profile-stat-chip span {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.profile-uid-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  border: 1px solid var(--meme-border);
}

.profile-uid-chip__label {
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.profile-uid-chip strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text-secondary);
}

.profile-hero__actions {
  flex-shrink: 0;
  padding-bottom: 4px;
}

.profile-hero__edit-btn {
  min-width: 120px;
  height: 40px !important;
  border-radius: 999px !important;
  font-weight: 650 !important;
  color: var(--meme-primary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border)) !important;
  box-shadow: var(--meme-shadow-soft) !important;
}

.profile-hero__edit-btn:hover:not(:disabled) {
  background: var(--meme-primary-soft) !important;
  border-color: var(--meme-primary) !important;
  color: var(--meme-primary-dark) !important;
  filter: none !important;
}

.profile-hero__edit-btn :deep(.vs-button__content),
.profile-hero__follow-btn {
  min-width: 120px;
}

.profile-hero__edit-btn :deep(.vs-button__content) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit !important;
}

.profile-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.profile-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 14px 10px 12px;
  border-radius: var(--meme-radius-md);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-card);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.profile-metric:hover {
  border-color: color-mix(in srgb, var(--meme-primary) 30%, var(--meme-border));
  box-shadow: 0 6px 16px var(--meme-focus-ring);
  transform: translateY(-1px);
}

.profile-metric__icon {
  font-size: 16px;
  color: var(--meme-primary);
  line-height: 1;
  margin-bottom: 2px;
}

.profile-metric strong {
  font-size: 22px;
  font-weight: 700;
  color: var(--meme-text);
  line-height: 1.2;
}

.profile-metric span {
  font-size: 12px;
  color: var(--meme-text-secondary);
}

.profile-content-card {
  border-radius: var(--meme-radius-lg) !important;
}

.profile-content-card :deep(.vs-card__text) {
  padding: 16px 18px 18px;
}

.profile-content {
  min-width: 0;
  padding: 16px 18px 18px;
  border-radius: var(--meme-radius-lg);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.profile-tabs :deep(.ui-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.profile-tabs :deep(.ui-tabs__nav) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  margin-bottom: 18px;
  border: 1px solid var(--meme-border);
  border-radius: 12px;
  background: var(--meme-bg-muted);
  width: fit-content;
  max-width: 100%;
  flex-wrap: wrap;
  box-shadow: none;
}

.profile-tabs :deep(.ui-tabs__item) {
  position: relative;
  padding: 9px 18px;
  border: none;
  border-radius: 9px;
  border-bottom: none;
  margin-bottom: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--meme-text-secondary);
  background: transparent;
  box-shadow: none !important;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.profile-tabs :deep(.ui-tabs__item:hover) {
  color: var(--meme-text);
  background: color-mix(in srgb, var(--meme-bg-elevated) 70%, transparent);
}

.profile-tabs :deep(.ui-tabs__item.is-active) {
  background: var(--meme-bg-elevated) !important;
  color: var(--meme-primary) !important;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08) !important;
  font-weight: 700;
}

.profile-tabs :deep(.ui-tabs__body) {
  flex: 1;
  min-height: 0;
}

.meme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
}

.meme-card-item {
  position: relative;
  border: 1px solid var(--meme-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--meme-bg-card);
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.meme-card-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--meme-shadow-soft);
}

.meme-cover {
  width: 100%;
  height: 140px;
  background: var(--meme-bg-muted);
}

.meme-cover-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--meme-text-muted);
  font-size: 13px;
}

.meme-card-item--deleted {
  opacity: 0.78;
  cursor: default;
}

.meme-card-item--deleted:hover {
  transform: none;
  box-shadow: none;
}

/* 发布梗图面板 */
.published-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.published-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 4px;
}

.published-panel-title {
  margin: 0 0 6px;
  font-size: 20px;
}

.published-panel-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--meme-text-secondary);
  max-width: 520px;
}

.published-create-btn {
  flex-shrink: 0;
  height: 36px !important;
  padding: 0 16px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 650 !important;
  color: #fff !important;
  background: var(--meme-primary) !important;
  border: none !important;
  box-shadow: 0 4px 14px var(--meme-focus-ring) !important;
}

.published-create-btn:hover:not(:disabled) {
  background: var(--meme-primary-dark) !important;
  color: #fff !important;
  filter: none !important;
  transform: translateY(-1px);
}

.published-create-btn :deep(.vs-button__content),
.published-create-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #fff !important;
}

.published-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.published-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  font-size: 13px;
  color: var(--meme-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.published-filter-chip:hover {
  border-color: var(--meme-border-accent);
  color: var(--meme-primary);
}

.published-filter-chip.is-active {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-weight: 600;
}

.published-filter-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.published-filter-chip.is-active .published-filter-count {
  background: var(--meme-primary-soft);
}

.published-meme-grid .meme-card-item--published {
  border: none;
  border-radius: 10px;
  background: transparent;
  overflow: visible;
}

.published-meme-grid .meme-card-item--published:hover {
  transform: none;
  box-shadow: none;
}

.published-meme-grid .meme-card-item--published:not(.meme-card-item--deleted):hover :deep(.meme-card__cover) {
  transform: scale(1.02);
}

.published-meme-grid :deep(.meme-card__cover) {
  transition: transform 0.25s ease;
}

.published-status-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--meme-text-inverse);
  backdrop-filter: blur(4px);
}

.published-status-badge--2 {
  background: rgba(245, 158, 11, 0.92);
}

.published-status-badge--3 {
  background: rgba(107, 114, 128, 0.92);
}

.published-deleted-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--meme-overlay);
  pointer-events: none;
}

.published-deleted-label {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--meme-text-inverse);
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.published-cover-actions {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px;
  background: var(--meme-overlay);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.published-meme-grid .meme-card-item--published:hover .published-cover-actions {
  opacity: 1;
}

.published-action-btn {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: var(--meme-surface-ghost);
  color: var(--meme-text);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}

.published-action-btn:hover {
  background: var(--meme-bg-card);
  transform: translateY(-1px);
}

.published-action-btn--danger {
  background: rgba(239, 68, 68, 0.92);
  border-color: rgba(239, 68, 68, 0.5);
  color: var(--meme-text-inverse);
}

.published-action-btn--danger:hover {
  background: var(--meme-danger);
}

.published-cover-stats {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 10px;
  display: flex;
  pointer-events: none;
}

.published-meme-grid .meme-info {
  padding: 8px 2px 0;
}

.published-meme-grid .meme-info-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.published-meme-grid .meme-title {
  flex: 1;
  min-width: 0;
}

.published-more-btn {
  opacity: 0.85;
}

.published-menu-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.published-menu-item--danger {
  color: var(--meme-danger);
}

.published-filter-empty {
  padding: 24px 0;
}

.delete-published-dialog-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 600;
  color: var(--meme-text);
}

.delete-published-dialog-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--meme-warning-soft);
  color: var(--meme-warning);
  font-size: 16px;
}

.delete-published-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.delete-published-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.delete-published-notes {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--meme-text-secondary);
}

.delete-published-preview {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg);
}

.delete-published-preview-img {
  width: 100%;
  height: 120px;
  display: block;
}

.delete-published-preview-meta {
  position: absolute;
  top: 8px;
  right: 8px;
}

.delete-published-preview-tag {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--meme-text-inverse);
  background: rgba(245, 158, 11, 0.9);
}

.delete-published-preview-tag.is-live {
  background: rgba(34, 197, 94, 0.9);
}

.restore-published-dialog-icon {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.purge-published-dialog-icon {
  background: var(--meme-danger-soft);
  color: var(--meme-danger);
}

.purge-published-notes {
  color: var(--meme-warning);
}

.purge-confirm-input {
  margin-top: 4px;
}

.meme-status-tag {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--meme-text-inverse);
  background: rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.meme-status-tag.meme-status-2 {
  background: rgba(245, 158, 11, 0.9);
}

.meme-status-tag.meme-status-3 {
  background: rgba(239, 68, 68, 0.9);
}

.published-skeleton {
  padding: 16px 0;
}

.meme-info {
  padding: 10px;
}

.meme-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--meme-text);
  line-height: 1.35;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meme-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--meme-text-secondary);
}

.profile-card {
  border-radius: var(--meme-radius-xl);
}

.profile-card :deep(.vs-card) {
  padding: 24px 28px;
}

.profile-loading {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--meme-text-secondary);
}

.profile-error {
  margin: 8px 0;
}

.profile-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-avatar {
  flex-shrink: 0;
  cursor: zoom-in;
  border: 3px solid var(--meme-primary-soft);
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  color: var(--meme-text-inverse);
  font-size: 40px;
  font-weight: 700;
}

.profile-meta {
  min-width: 0;
}

.profile-nickname {
  margin: 0 0 8px;
  font-size: 28px;
  line-height: 1.2;
  color: var(--meme-text);
}

.profile-signature {
  margin: 0;
  color: var(--meme-text-secondary);
  font-size: 15px;
  line-height: 1.6;
}

.profile-edit-btn {
  flex-shrink: 0;
}

.stats-col {
  margin-bottom: 16px;
}

.stats-card {
  text-align: center;
  border-radius: 16px;
}

.stats-card :deep(.vs-card) {
  padding: 18px 10px;
}

.edit-form {
  margin-top: 4px;
}

.edit-dialog-head {
  text-align: left;
}

.edit-dialog-kicker {
  margin: 0 0 2px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--meme-primary);
}

.edit-dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
  letter-spacing: -0.02em;
}

.edit-profile-dialog :deep(.vs-dialog__content) {
  border-radius: 16px !important;
}

.edit-profile-dialog :deep(.vs-dialog-content) {
  padding-top: 8px;
}

.edit-echo-skeleton {
  padding-top: 8px;
}

.edit-skeleton-shape {
  border-radius: 4px;
  background: var(--meme-bg-muted);
  margin-bottom: 10px;
}

.edit-skeleton-shape--circle {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  margin-bottom: 14px;
}

.edit-skeleton-shape--line {
  width: 100%;
  height: 12px;
}

.edit-section {
  padding: 4px 0 18px;
  border-bottom: 1px solid var(--meme-border);
  margin-bottom: 16px;
}

.edit-section--last {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 4px;
}

.edit-section-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.edit-section-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 18px;
}

.edit-section-icon--primary {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.edit-section-title {
  margin: 0 0 2px;
  font-size: 15px;
  font-weight: 700;
  color: var(--meme-text);
}

.edit-section-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--meme-text-muted);
}

.avatar-edit-row {
  display: flex;
  align-items: center;
  gap: 18px;
}

.avatar-edit-hit {
  position: relative;
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-edit-hit:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.avatar-edit-preview {
  width: 88px !important;
  height: 88px !important;
  border: 3px solid var(--meme-primary-soft);
  box-shadow: 0 4px 14px var(--meme-focus-ring);
}

.avatar-edit-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.45);
  color: #fff;
  font-size: 22px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.avatar-edit-hit:hover .avatar-edit-overlay,
.avatar-edit-hit:focus-visible .avatar-edit-overlay {
  opacity: 1;
}

.avatar-edit-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.avatar-change-btn {
  height: 36px !important;
  padding: 0 14px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 650 !important;
  color: var(--meme-primary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 45%, var(--meme-border)) !important;
  box-shadow: none !important;
}

.avatar-change-btn:hover:not(:disabled) {
  background: var(--meme-primary-soft) !important;
  color: var(--meme-primary-dark) !important;
  filter: none !important;
}

.avatar-change-btn :deep(.vs-button__content),
.avatar-change-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit !important;
}

.avatar-edit-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--meme-text-muted);
}

.avatar-file-input {
  display: none;
}

.edit-field {
  width: 100%;
}

.edit-field :deep(.vs-input__wrapper),
.edit-field :deep(.vs-input__original) {
  width: 100%;
}

.edit-field--textarea :deep(textarea),
.edit-field--textarea :deep(.vs-input__original) {
  min-height: 88px;
  line-height: 1.55;
  resize: vertical;
}

.gender-segment {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
}

.gender-segment__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 72px;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.gender-segment__item:hover {
  color: var(--meme-text);
  background: color-mix(in srgb, var(--meme-bg-elevated) 70%, transparent);
}

.gender-segment__item.is-active {
  background: var(--meme-bg-elevated);
  color: var(--meme-primary);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  font-weight: 700;
}

.gender-segment__item i {
  font-size: 15px;
}

.edit-form :deep(.ui-date-picker) {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text);
  font-size: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.edit-form :deep(.ui-date-picker:hover) {
  background: var(--meme-bg-elevated);
  border-color: color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border));
}

.edit-form :deep(.ui-date-picker:focus) {
  outline: none;
  background: var(--meme-bg-elevated);
  border-color: var(--meme-primary);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
}

.edit-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

.edit-profile-dialog :deep(.vs-dialog__footer),
.edit-profile-dialog :deep(.vs-dialog-footer) {
  display: flex;
  justify-content: flex-end;
}

.edit-cancel-btn {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 88px;
  height: 40px !important;
  padding: 0 18px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  color: var(--meme-text-secondary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid var(--meme-border-strong, var(--meme-border)) !important;
  box-shadow: none !important;
}

.edit-cancel-btn:hover:not(:disabled) {
  color: var(--meme-text) !important;
  border-color: var(--meme-text-muted) !important;
  background: var(--meme-bg-muted) !important;
  filter: none !important;
}

.edit-cancel-btn :deep(.vs-button__content) {
  color: inherit !important;
}

.edit-save-btn {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 100px;
  height: 40px !important;
  padding: 0 20px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 650 !important;
  color: #fff !important;
  background: var(--meme-primary) !important;
  border: none !important;
  box-shadow: 0 4px 14px var(--meme-focus-ring) !important;
}

.edit-save-btn:hover:not(:disabled) {
  background: var(--meme-primary-dark) !important;
  color: #fff !important;
  filter: none !important;
  transform: translateY(-1px);
}

.edit-save-btn :deep(.vs-button__content),
.edit-save-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #fff !important;
}

.is-spinning {
  animation: edit-spin 0.8s linear infinite;
}

@keyframes edit-spin {
  to { transform: rotate(360deg); }
}

.avatar-viewer-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: rgba(0, 0, 0, 0.88);
  cursor: zoom-out;
}

.avatar-viewer-img {
  max-width: min(92vw, 720px);
  max-height: 88vh;
}

.cropper-dialog-head {
  text-align: left;
}

.cropper-dialog-kicker {
  margin: 0 0 2px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--meme-primary);
}

.cropper-dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
  letter-spacing: -0.02em;
}

.cropper-dialog :deep(.vs-dialog__content) {
  border-radius: 16px !important;
}

.cropper-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cropper-panel {
  width: 100%;
  height: 360px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--meme-border);
  background: #0f172a;
}

.avatar-cropper {
  width: 100%;
  height: 100%;
}

.cropper-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
}

.cropper-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cropper-tool-btn {
  height: 32px !important;
  padding: 0 12px !important;
  border-radius: 999px !important;
  font-size: 12px !important;
  font-weight: 650 !important;
  color: var(--meme-primary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border)) !important;
  box-shadow: none !important;
}

.cropper-tool-btn:hover:not(:disabled) {
  background: var(--meme-primary-soft) !important;
  filter: none !important;
}

.cropper-tool-btn :deep(.vs-button__content),
.cropper-tool-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: inherit !important;
}

.cropper-step {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.cropper-step-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--meme-text-secondary);
  white-space: nowrap;
}

.cropper-zoom-range {
  width: 120px;
  accent-color: var(--meme-primary);
}

.cropper-step-value {
  min-width: 36px;
  font-size: 12px;
  font-weight: 700;
  color: var(--meme-primary);
  text-align: right;
}

.cropper-result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-elevated);
}

.cropper-result-kicker {
  margin: 0 0 2px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--meme-primary);
}

.cropper-result-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--meme-text);
}

.cropper-result-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--meme-text-muted);
}

.cropper-result-preview {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid var(--meme-primary-soft);
  box-shadow: 0 4px 14px var(--meme-focus-ring);
  background: var(--meme-bg-muted);
}

.cropper-result-img {
  width: 100%;
  height: 100%;
}

.cropper-result-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.cropper-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

.cropper-cancel-btn {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 88px;
  height: 40px !important;
  padding: 0 18px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  color: var(--meme-text-secondary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid var(--meme-border-strong, var(--meme-border)) !important;
  box-shadow: none !important;
}

.cropper-cancel-btn:hover:not(:disabled) {
  color: var(--meme-text) !important;
  background: var(--meme-bg-muted) !important;
  filter: none !important;
}

.cropper-cancel-btn :deep(.vs-button__content) {
  color: inherit !important;
}

.cropper-confirm-btn {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 128px;
  height: 40px !important;
  padding: 0 18px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 650 !important;
  color: #fff !important;
  background: var(--meme-primary) !important;
  border: none !important;
  box-shadow: 0 4px 14px var(--meme-focus-ring) !important;
}

.cropper-confirm-btn:hover:not(:disabled) {
  background: var(--meme-primary-dark) !important;
  color: #fff !important;
  filter: none !important;
  transform: translateY(-1px);
}

.cropper-confirm-btn :deep(.vs-button__content),
.cropper-confirm-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #fff !important;
}

.stats-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--meme-text);
  line-height: 1.2;
}

.stats-label {
  margin-top: 6px;
  color: var(--meme-text-secondary);
  font-size: 14px;
}

.meme-loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: meme-spin 0.8s linear infinite;
}

@keyframes meme-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .profile-page {
    margin: -16px -32px -32px;
    width: calc(100% + 64px);
    padding: 12px 10px 20px;
  }

  .profile-hero__inner {
    flex-direction: column;
    align-items: stretch;
    padding: 0 14px 14px;
    margin-top: -32px;
  }

  .profile-hero__main {
    align-items: flex-start;
  }

  .profile-hero__avatar {
    width: 72px;
    height: 72px;
  }

  .profile-hero__name {
    font-size: 20px;
  }

  .profile-hero__actions {
    width: 100%;
  }

  .profile-hero__edit-btn,
  .profile-hero__follow-btn {
    width: 100%;
  }

  .profile-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-content,
  .profile-content-card :deep(.vs-card__text) {
    padding: 12px 12px 14px;
  }

  .profile-tabs :deep(.ui-tabs__nav) {
    width: 100%;
  }

  .cropper-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .cropper-step {
    margin-left: 0;
    width: 100%;
  }

  .cropper-zoom-range {
    flex: 1;
  }

  .cropper-result-row {
    align-items: flex-start;
  }
}

/* 收藏夹：卡片网格布局 */
.favorite-panel {
  min-height: 320px;
}
.favorite-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.favorite-panel-title {
  margin: 0;
  font-size: 20px;
}
.favorite-create-btn {
  flex-shrink: 0;
  height: 36px !important;
  padding: 0 16px !important;
  border-radius: 999px !important;
  font-size: 13px !important;
  font-weight: 650 !important;
  color: #fff !important;
  background: var(--meme-primary) !important;
  border: none !important;
  box-shadow: 0 4px 14px var(--meme-focus-ring) !important;
}

.favorite-create-btn:hover:not(:disabled) {
  background: var(--meme-primary-dark) !important;
  color: #fff !important;
  filter: none !important;
  transform: translateY(-1px);
}

.favorite-create-btn :deep(.vs-button__content),
.favorite-create-btn :deep(.vs-button__content *) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #fff !important;
}

.folder-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 120px;
  position: relative;
}

.folder-card-grid.is-loading {
  pointer-events: none;
}

.folder-card-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.55);
  border-radius: 12px;
}

html.dark .folder-card-loading {
  background: rgba(15, 23, 42, 0.45);
}
.folder-card {
  background: var(--meme-bg-card);
  border: 1px solid var(--meme-border);
  border-radius: 14px;
  padding: 16px 18px;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-height: 108px;
}
.folder-card:hover {
  border-color: var(--meme-border-accent);
  box-shadow: var(--meme-shadow-soft);
  transform: translateY(-1px);
}
.folder-card-top {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.folder-card-icon {
  flex-shrink: 0;
  font-size: 22px;
  line-height: 1;
  color: var(--meme-primary);
}
.folder-card-name {
  flex: 1;
  min-width: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--meme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-card-more {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease;
}
.folder-card-more:hover {
  background: var(--meme-bg-muted);
  color: var(--meme-text);
}
.folder-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.folder-card-count {
  font-size: 13px;
  color: var(--meme-text-secondary);
}
.folder-card-status {
  font-size: 13px;
  font-weight: 500;
  flex-shrink: 0;
}
.folder-card-status-private {
  color: #e91e8c;
}
.folder-card-status-public {
  color: var(--meme-text-secondary);
}
.folder-card-status-default {
  color: var(--meme-text-secondary);
}
.folder-card-empty {
  grid-column: 1 / -1;
  padding: 24px 0;
}
.folder-detail-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--meme-border);
}
.folder-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: 999px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.folder-back-btn:hover {
  background: var(--meme-bg-muted);
  color: var(--meme-text);
}
.folder-detail-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
}
.folder-content {
  min-width: 0;
}
.folder-content--batch-active {
  padding-bottom: 88px;
}
.folder-batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.folder-batch-bar.is-active {
  border-color: var(--meme-border-accent);
  box-shadow: 0 2px 10px var(--meme-focus-ring);
}
.folder-batch-check :deep(.vs-checkbox__label) {
  font-size: 14px;
  font-weight: 600;
  color: var(--meme-text);
}
.folder-batch-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-primary);
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--meme-primary-soft);
}
.folder-batch-hint {
  font-size: 13px;
  color: var(--meme-text-secondary);
}

.folder-batch-dock {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 200;
  width: min(560px, calc(100vw - 48px));
  pointer-events: none;
}
.folder-batch-dock-inner {
  pointer-events: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: 14px;
  background: var(--meme-bg-card);
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-dialog);
}
.folder-batch-dock-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.folder-batch-dock-count {
  font-size: 14px;
  font-weight: 600;
  color: var(--meme-text);
}
.folder-batch-dock-clear {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--meme-text-secondary);
  cursor: pointer;
  transition: color 0.15s ease;
}
.folder-batch-dock-clear:hover {
  color: var(--meme-primary);
}
.folder-batch-dock-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.folder-batch-dock-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
  border: 1px solid transparent;
}
.folder-batch-dock-btn-move {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  border-color: var(--meme-primary-soft);
}
.folder-batch-dock-btn-move:hover {
  background: var(--meme-primary-soft);
  border-color: var(--meme-border-accent);
}
.folder-batch-dock-btn-remove {
  background: var(--meme-danger-soft);
  color: var(--meme-danger);
  border-color: var(--meme-danger);
}
.folder-batch-dock-btn-remove:hover {
  background: var(--meme-danger-soft);
  border-color: var(--meme-danger);
}
.folder-batch-dock-enter-active,
.folder-batch-dock-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.folder-batch-dock-enter-from,
.folder-batch-dock-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(16px);
}

/* 收藏夹内梗图卡片 */
.folder-meme-grid .meme-card-item--favorite {
  border: none;
  border-radius: 10px;
  background: transparent;
  overflow: visible;
}
.folder-meme-grid .meme-card-item--favorite:hover {
  transform: none;
  box-shadow: none;
}
.folder-meme-grid .meme-card-item--favorite.meme-card-item--selected :deep(.meme-card__cover) {
  box-shadow: 0 0 0 2px var(--meme-primary);
}
.meme-select-toggle {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 2px solid rgba(255, 255, 255, 0.95);
  border-radius: 3px;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.meme-select-toggle:hover {
  border-color: var(--meme-text-inverse);
  transform: scale(1.05);
}
.meme-select-toggle.is-checked {
  background: var(--meme-primary);
  border-color: var(--meme-primary);
}
.meme-select-toggle.is-checked::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 1px;
  width: 5px;
  height: 9px;
  border: 2px solid var(--meme-text-inverse);
  border-top: 0;
  border-left: 0;
  transform: rotate(45deg);
}
.meme-cover-stats {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 6px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  pointer-events: none;
}
.meme-cover-stat {
  font-size: 12px;
  color: var(--meme-text-inverse);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}
.folder-meme-grid .meme-info {
  padding: 8px 2px 0;
}
.meme-info-head {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}
.meme-info-head .meme-title {
  flex: 1;
  min-width: 0;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}
.meme-more-btn {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  margin-top: -2px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--meme-text-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.meme-more-btn:hover {
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
}
.folder-meme-grid .meme-meta {
  font-size: 12px;
  color: var(--meme-text-muted);
}
.meme-meta-time {
  color: var(--meme-text-muted);
  font-size: 12px;
}

/* 复用 MemeDetail 弹窗样式 */
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
.favorite-folder-picker .folder-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.favorite-folder-picker .folder-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--meme-text);
}
.favorite-folder-picker .folder-count {
  font-size: 12px;
  color: var(--meme-text-secondary);
}
.favorite-folder-check {
  color: var(--meme-primary);
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
}

.profile-folder-form :deep(.ui-form-item) {
  margin-bottom: 16px;
}

.profile-folder-form :deep(.ui-form-item__label) {
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--meme-text-secondary);
}

.profile-folder-field {
  width: 100%;
}

.profile-folder-field :deep(.vs-input__wrapper) {
  width: 100%;
  min-height: 44px;
  border-radius: 10px !important;
  background: var(--meme-bg-muted) !important;
  box-shadow: 0 0 0 1px var(--meme-border) inset;
}

.profile-folder-field :deep(.vs-input__original) {
  width: 100% !important;
  min-height: 44px;
  border: none !important;
  background: transparent !important;
  color: var(--meme-text) !important;
  box-shadow: none !important;
}

.profile-folder-field--textarea :deep(.vs-input__original) {
  min-height: 84px;
  padding: 10px 12px !important;
  resize: vertical;
}

.profile-folder-field :deep(.vs-input.is-focus .vs-input__wrapper) {
  background: var(--meme-bg-elevated) !important;
  box-shadow:
    0 0 0 1px var(--meme-primary) inset,
    0 0 0 3px var(--meme-focus-ring) !important;
}

.profile-folder-visibility {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
}

.profile-folder-visibility__btn {
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.profile-folder-visibility__btn.is-active {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
  border-color: color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border));
}

.profile-folder-visibility__btn:hover:not(.is-active) {
  color: var(--meme-text);
  border-color: var(--meme-border-strong);
}

.profile-folder-dialog__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

.profile-dlg-btn {
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
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease, filter 0.15s ease;
}

.profile-dlg-btn:disabled {
  opacity: 0.65;
  cursor: wait;
}

.profile-dlg-btn--ghost {
  color: var(--meme-text);
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
}

.profile-dlg-btn--ghost:hover:not(:disabled) {
  background: var(--meme-bg-elevated);
  border-color: var(--meme-border-strong);
}

.profile-dlg-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
  box-shadow: 0 6px 14px var(--meme-focus-ring);
}

.profile-dlg-btn--primary:hover:not(:disabled) {
  filter: brightness(1.04);
}

.profile-dlg-btn__spin {
  display: inline-block;
  animation: profile-dlg-spin 0.8s linear infinite;
}

@keyframes profile-dlg-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .favorite-panel-header {
    flex-wrap: wrap;
  }
  .favorite-panel-title {
    font-size: 20px;
  }
  .folder-card-grid {
    grid-template-columns: 1fr;
  }
  .folder-batch-dock {
    width: calc(100vw - 32px);
    bottom: 16px;
  }
  .folder-batch-dock-inner {
    flex-direction: column;
    align-items: stretch;
  }
  .folder-batch-dock-actions {
    width: 100%;
  }
  .folder-batch-dock-btn {
    flex: 1;
  }
}
</style>

<style>
/* 个人主页卡片操作菜单 */
.meme-action-dropdown .ui-dropdown__menu {
  z-index: 1300;
  border-radius: 12px !important;
  border: 1px solid var(--meme-border) !important;
  box-shadow: var(--meme-shadow-soft) !important;
  padding: 6px 0 !important;
  min-width: 120px;
  background: var(--meme-bg-elevated) !important;
}
.meme-action-dropdown .ui-dropdown-item {
  padding: 10px 20px;
  font-size: 14px;
  color: var(--meme-text-secondary);
  line-height: 1.2;
}
.meme-action-dropdown .ui-dropdown-item:not(.is-disabled):hover {
  background: var(--meme-bg-muted);
  color: var(--meme-text);
}

/* 收藏夹弹窗（teleport 到 body，需非 scoped） */
.profile-folder-dialog.vs-dialog-content,
.vs-dialog-content.profile-folder-dialog {
  border-radius: 18px !important;
  border: 1px solid var(--meme-border) !important;
  background: var(--meme-bg-elevated) !important;
  box-shadow: var(--meme-shadow-dialog) !important;
  overflow: hidden;
}

.profile-folder-dialog .vs-dialog__header,
.profile-folder-dialog .vs-dialog-header {
  padding: 18px 20px 8px !important;
  text-align: center;
  border-bottom: none !important;
}

.profile-folder-dialog .vs-dialog__header h3,
.profile-folder-dialog .vs-dialog__header *,
.profile-folder-dialog .vs-dialog-header {
  font-size: 17px !important;
  font-weight: 750 !important;
  letter-spacing: -0.02em;
  color: var(--meme-text) !important;
}

.profile-folder-dialog .vs-dialog__content,
.profile-folder-dialog .vs-dialog-content {
  padding: 8px 20px 4px !important;
}

.profile-folder-dialog .vs-dialog__footer,
.profile-folder-dialog .vs-dialog-footer {
  padding: 12px 20px 20px !important;
  border-top: 1px solid var(--meme-border) !important;
  background: transparent !important;
  display: flex !important;
  justify-content: flex-end !important;
}

.profile-folder-dialog .vs-dialog__close,
.profile-folder-dialog .vs-dialog-close {
  top: 12px !important;
  right: 12px !important;
  color: var(--meme-text-muted) !important;
}
</style>
