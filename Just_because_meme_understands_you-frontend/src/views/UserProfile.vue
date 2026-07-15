<template>
  <div class="profile-page">
    <div v-if="loading" class="profile-loading-page">
      <span class="meme-loading-spinner"></span>
      <span>加载个人主页中...</span>
    </div>
    <el-alert
      v-else-if="errorMessage"
      :title="errorMessage"
      type="warning"
      show-icon
      :closable="false"
      class="profile-error"
    />
    <div v-else class="profile-layout">
      <aside class="profile-sidebar">
        <el-card class="sidebar-card" shadow="never">
          <div class="sidebar-header">
            <el-avatar
              class="sidebar-avatar"
              :size="108"
              :src="profile.avatar"
              @click="openAvatarViewer"
            >
              {{ avatarFallback }}
            </el-avatar>
            <h1 class="sidebar-name">{{ profile.nickname }}</h1>
            <p class="sidebar-signature">{{ profile.signature || '这个人很懒，什么都没留下~' }}</p>
          </div>

          <div class="sidebar-stats">
            <button type="button" class="sidebar-stat" @click="openRelationDrawer('following')">
              <strong>{{ formatNum(profile.stats.followCount) }}</strong>
              <span>关注</span>
            </button>
            <button type="button" class="sidebar-stat" @click="openRelationDrawer('followers')">
              <strong>{{ formatNum(profile.stats.fansCount) }}</strong>
              <span>粉丝</span>
            </button>
            <div class="sidebar-stat sidebar-stat--static">
              <strong>#{{ profile.userId || '-' }}</strong>
              <span>UID</span>
            </div>
          </div>

          <div class="sidebar-actions">
            <el-button
              v-if="showEditButton"
              type="primary"
              class="sidebar-edit-btn"
              @click="handleEditProfile"
            >
              编辑信息
            </el-button>
            <FollowButton
              v-else-if="profile.userId"
              v-model="profile.isFollow"
              :user-id="profile.userId"
              :mutual="followMutual"
              size="lg"
              class="sidebar-follow-btn"
              @change="onProfileFollowChange"
            />
          </div>
        </el-card>
      </aside>

      <section class="profile-main-content">
        <el-row :gutter="14" class="overview-row">
          <el-col :xs="12" :sm="6">
            <el-card class="overview-card" shadow="hover">
              <div class="overview-value">{{ formatNum(profile.stats.memeCount) }}</div>
              <div class="overview-label">发布梗图</div>
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-card class="overview-card" shadow="hover">
              <div class="overview-value">{{ formatNum(profile.stats.likeReceived) }}</div>
              <div class="overview-label">获赞总数</div>
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-card class="overview-card" shadow="hover">
              <div class="overview-value">{{ formatNum(profile.stats.favoriteCount) }}</div>
              <div class="overview-label">收藏总数</div>
            </el-card>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-card class="overview-card" shadow="hover">
              <div class="overview-value">{{ formatNum(profile.stats.fansCount) }}</div>
              <div class="overview-label">粉丝总数</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card class="content-card" shadow="never">
          <el-tabs v-model="activeTab">
            <el-tab-pane :label="`发布梗图 (${publishedPage.total})`" name="published">
              <div class="published-panel">
                <div v-if="isOwnProfile" class="published-panel-header">
                  <div class="published-panel-head">
                    <h3 class="published-panel-title">我整的梗</h3>
                    <p class="published-panel-hint">点击卡片看详情；想下架时点右上角 ⋮，公域刷不到后可在「已下架」里翻黑历史</p>
                  </div>
                  <router-link :to="publishLocation" class="published-create-btn">
                    <span class="published-create-btn-icon">+</span>
                    发布新梗
                  </router-link>
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
                  <el-skeleton :rows="3" animated />
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
                        <el-dropdown
                          v-if="isOwnProfile && item.status !== 3"
                          trigger="click"
                          popper-class="meme-action-popper"
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
                            <el-dropdown-menu>
                              <el-dropdown-item command="delete">
                                <span class="published-menu-item published-menu-item--danger"><span>🗑</span> 下架梗</span>
                              </el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                        <el-dropdown
                          v-else-if="isOwnProfile && item.status === 3"
                          trigger="click"
                          popper-class="meme-action-popper"
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
                            <el-dropdown-menu>
                              <el-dropdown-item command="restore">
                                <span class="published-menu-item"><span>↩</span> 恢复上架</span>
                              </el-dropdown-item>
                              <el-dropdown-item command="purge" divided>
                                <span class="published-menu-item published-menu-item--danger"><span>💀</span> 彻底删除</span>
                              </el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </template>
                    </MemeCard>
                  </div>
                </div>
                <el-empty
                  v-else-if="publishedList.length && isOwnProfile"
                  :image-size="72"
                  description="当前筛选下没有梗图"
                  class="published-filter-empty"
                />
                <el-empty v-else description="这个用户还没有发布梗图" />
                <ListLoadFooter
                  :has-more="publishedHasMore"
                  :loading="publishedLoading"
                  :item-count="filteredPublishedList.length"
                  @load-more="loadMorePublished"
                />
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`收藏梗图 (${favoriteTotal})`" name="favorite">
              <div class="favorite-panel">
                <!-- 收藏夹卡片列表 -->
                <template v-if="favoriteViewMode === 'folders'">
                  <div class="favorite-panel-header">
                    <h3 class="favorite-panel-title">收藏夹</h3>
                    <button
                      v-if="isOwnProfile"
                      type="button"
                      class="favorite-create-btn"
                      @click="openCreateFolderDialog"
                    >
                      <span class="favorite-create-btn-icon">+</span>
                      创建新收藏夹
                    </button>
                  </div>

                  <div v-loading="folderLoading" class="folder-card-grid">
                    <div
                      v-for="f in folderList"
                      :key="`folder-card-${f.id}`"
                      class="folder-card"
                      @click="openFolderDetail(f)"
                    >
                      <div class="folder-card-top">
                        <svg class="folder-card-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                          <path
                            d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9.2l1.8 2H19.5A1.5 1.5 0 0 1 21 9.5v9A1.5 1.5 0 0 1 19.5 20h-15A1.5 1.5 0 0 1 3 18.5v-11Z"
                            stroke="currentColor"
                            stroke-width="1.6"
                            stroke-linejoin="round"
                          />
                        </svg>
                        <span class="folder-card-name" :title="f.name">{{ f.name }}</span>
                        <el-dropdown
                          v-if="isOwnProfile"
                          trigger="click"
                          @click.stop
                          @command="(cmd) => handleFolderCommand(cmd, f)"
                        >
                          <button type="button" class="folder-card-more" @click.stop>⋯</button>
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item command="edit">编辑</el-dropdown-item>
                              <el-dropdown-item v-if="!f.isDefault" command="delete" divided>删除</el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
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

                    <el-empty
                      v-if="!folderLoading && !folderList.length"
                      :image-size="72"
                      description="还没有收藏夹"
                      class="folder-card-empty"
                    >
                      <el-button v-if="isOwnProfile" type="primary" @click="openCreateFolderDialog">
                        创建新收藏夹
                      </el-button>
                    </el-empty>
                  </div>
                </template>

                <!-- 夹内梗图详情 -->
                <template v-else>
                  <div class="folder-detail-header">
                    <button type="button" class="folder-back-btn" @click="backToFolderGrid">
                      <span aria-hidden="true">←</span>
                      返回收藏夹
                    </button>
                    <h3 class="folder-detail-title">{{ selectedFolderName }}</h3>
                  </div>

                  <div
                    class="folder-content"
                    :class="{ 'folder-content--batch-active': isOwnProfile && selectedMemeIds.length > 0 }"
                  >
                    <div v-if="folderContentLoading && !folderContentList.length" class="published-skeleton">
                      <el-skeleton :rows="3" animated />
                    </div>
                    <template v-else>
                      <div
                        v-if="isOwnProfile && folderContentList.length"
                        class="folder-batch-bar"
                        :class="{ 'is-active': selectedMemeIds.length > 0 }"
                      >
                        <el-checkbox
                          v-model="folderSelectAll"
                          class="folder-batch-check"
                          @change="onSelectAllFolderItems"
                        >
                          全选
                        </el-checkbox>
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
                              <el-dropdown
                                v-if="isOwnProfile"
                                trigger="click"
                                popper-class="meme-action-popper"
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
                                  <el-dropdown-menu>
                                    <el-dropdown-item command="move">移动至</el-dropdown-item>
                                    <el-dropdown-item command="remove">取消收藏</el-dropdown-item>
                                  </el-dropdown-menu>
                                </template>
                              </el-dropdown>
                            </template>
                          </MemeCard>
                        </div>
                      </div>
                      <el-empty v-else description="这个收藏夹还没有梗图" />
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
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </section>
    </div>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑资料"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="editEchoLoading" class="edit-echo-skeleton">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="circle" style="width: 72px; height: 72px; margin-bottom: 14px;" />
            <el-skeleton-item variant="text" style="width: 46%; margin-bottom: 10px;" />
            <el-skeleton-item variant="text" style="width: 100%; margin-bottom: 10px;" />
            <el-skeleton-item variant="text" style="width: 100%; margin-bottom: 10px;" />
            <el-skeleton-item variant="text" style="width: 66%;" />
          </template>
        </el-skeleton>
      </div>

      <el-form
        v-else
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-position="top"
        class="edit-form"
      >
        <el-form-item label="头像">
          <div class="avatar-edit-row">
            <el-avatar :size="72" :src="editForm.avatar" class="avatar-edit-preview">
              {{ avatarFallback }}
            </el-avatar>
            <div class="avatar-edit-actions">
              <el-button :loading="avatarUploading" :disabled="avatarUploading || editSubmitting" @click="triggerAvatarUpload">
                {{ avatarUploading ? '上传中...' : '更换头像' }}
              </el-button>
              <div class="avatar-edit-tip">支持 JPG/PNG/WEBP，大小不超过 5MB</div>
            </div>
          </div>
          <input
            ref="avatarFileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
            class="avatar-file-input"
            @change="onAvatarFileChange"
          />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="签名" prop="signature">
          <el-input
            v-model="editForm.signature"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="写一句能代表你的个性签名"
          />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="editForm.gender" class="field-full" placeholder="请选择性别">
                <el-option label="未知" :value="0" />
                <el-option label="男" :value="1" />
                <el-option label="女" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生日">
              <el-date-picker
                v-model="editForm.birthday"
                class="field-full"
                type="date"
                format="YYYY年MM月DD日"
                value-format="YYYY-MM-DD"
                placeholder="选择生日"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button :disabled="editSubmitting" @click="editDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="editSubmitting"
          :disabled="avatarUploading"
          @click="submitEditProfile"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="cropperDialogVisible"
      title="裁剪头像"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
      class="cropper-dialog"
    >
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
      <div class="cropper-toolbar">
        <div class="cropper-toolbar-left">
          <el-button size="small" @click="zoomCropper(-1)">缩小</el-button>
          <el-button size="small" @click="zoomCropper(1)">放大</el-button>
          <el-button size="small" @click="resetCropper">重置</el-button>
        </div>
        <div class="cropper-toolbar-right">
          <span class="cropper-step-label">缩放步进</span>
          <el-slider
            v-model="zoomStepPercent"
            :min="4"
            :max="20"
            :step="1"
            style="width: 160px;"
          />
        </div>
      </div>
      <div class="cropper-preview-wrap">
        <div class="cropper-preview-card">
          <div class="cropper-preview-title">原图预览</div>
          <el-image v-if="cropperImageSrc" :src="cropperImageSrc" fit="cover" class="cropper-preview-img" />
        </div>
        <div class="cropper-preview-card">
          <div class="cropper-preview-title">裁剪后预览</div>
          <el-image v-if="cropperResultPreview" :src="cropperResultPreview" fit="cover" class="cropper-preview-img" />
          <div v-else class="cropper-preview-empty">拖拽或缩放后可预览效果</div>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="avatarUploading" @click="cropperDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="avatarUploading" @click="confirmCropAndUpload">
          确认裁剪并上传
        </el-button>
      </template>
    </el-dialog>

    <el-image-viewer
      v-if="avatarViewerVisible"
      :url-list="avatarViewerList"
      :initial-index="0"
      :infinite="false"
      @close="avatarViewerVisible = false"
    />

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
    <el-dialog
      v-model="deletePublishedDialogVisible"
      width="440px"
      class="delete-published-dialog"
      :close-on-click-modal="false"
      append-to-body
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
          <el-image
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
        <el-button :disabled="deletePublishedSubmitting" round @click="deletePublishedDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="danger"
          round
          :loading="deletePublishedSubmitting"
          @click="confirmDeletePublished"
        >
          确认下架
        </el-button>
      </template>
    </el-dialog>

    <!-- 恢复下架确认弹窗 -->
    <el-dialog
      v-model="restorePublishedDialogVisible"
      width="440px"
      class="restore-published-dialog"
      :close-on-click-modal="false"
      append-to-body
      @closed="resetRestorePublishedDialog"
    >
      <template #header>
        <div class="delete-published-dialog-head">
          <span class="delete-published-dialog-icon restore-published-dialog-icon" aria-hidden="true">↩</span>
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
        <el-button :disabled="restorePublishedSubmitting" round @click="restorePublishedDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          round
          :loading="restorePublishedSubmitting"
          @click="confirmRestorePublished"
        >
          确认恢复
        </el-button>
      </template>
    </el-dialog>

    <!-- 彻底删除确认弹窗 -->
    <el-dialog
      v-model="purgePublishedDialogVisible"
      width="440px"
      class="purge-published-dialog"
      :close-on-click-modal="false"
      append-to-body
      @closed="resetPurgePublishedDialog"
    >
      <template #header>
        <div class="delete-published-dialog-head">
          <span class="delete-published-dialog-icon purge-published-dialog-icon" aria-hidden="true">💀</span>
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
        <el-input
          v-model="purgeConfirmName"
          maxlength="50"
          placeholder="输入梗名称以确认"
          class="purge-confirm-input"
        />
      </div>
      <template #footer>
        <el-button :disabled="purgePublishedSubmitting" round @click="purgePublishedDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="danger"
          round
          :loading="purgePublishedSubmitting"
          :disabled="!purgeConfirmMatched"
          @click="confirmPurgePublished"
        >
          确认彻底删除
        </el-button>
      </template>
    </el-dialog>

    <!-- 收藏夹新建/编辑弹窗 -->
    <el-dialog
      v-model="folderDialogVisible"
      :title="folderEditingId == null ? '新建收藏夹' : '编辑收藏夹'"
      width="440px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form :model="folderForm" label-width="80px" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="folderForm.name" maxlength="64" show-word-limit placeholder="收藏夹名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="folderForm.description" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="可选" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="folderForm.isPublic">
            <el-radio :label="1">公开</el-radio>
            <el-radio :label="0">私密</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSubmitting" @click="submitFolderDialog">保存</el-button>
      </template>
    </el-dialog>

    <!-- 移动至收藏夹弹窗 -->
    <el-dialog
      v-model="batchMoveDialogVisible"
      :title="moveDialogTitle"
      width="420px"
      append-to-body
      @closed="onMoveDialogClosed"
    >
      <div class="favorite-folder-picker">
        <div
          v-for="f in folderList"
          :key="`move-folder-${f.id}`"
          class="favorite-folder-item"
          :class="{ active: sameFolderId(batchMoveTargetId, f.id) }"
          @click="batchMoveTargetId = normalizeFolderId(f.id)"
        >
          <div class="favorite-folder-icon">{{ f.isDefault ? '☆' : '📁' }}</div>
          <div class="folder-meta">
            <div class="folder-name">{{ f.name }}</div>
            <div class="folder-count">{{ f.memeCount || 0 }} 个梗图</div>
          </div>
          <span v-if="sameFolderId(batchMoveTargetId, f.id)" class="favorite-folder-check">✓</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchMoveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchMoveSubmitting" @click="confirmBatchMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { Cropper } from 'vue-advanced-cropper'
import { ElImageViewer, ElMessage, ElMessageBox } from 'element-plus'
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
    ElImageViewer,
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
    avatarViewerList() {
      return this.profile.avatar ? [this.profile.avatar] : []
    },
    publishedList() {
      return Array.isArray(this.publishedPage.list) ? this.publishedPage.list : []
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
          ElMessage.info('这条梗已经下线啦，黑历史只能你自己翻')
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
        ElMessage.success('梗已下架')
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        ElMessage.error((e && e.message) || '删除失败')
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
        ElMessage.success(`已恢复，当前状态：${data.statusDesc || '审核中'}`)
        this.restorePublishedDialogVisible = false
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        ElMessage.error((e && e.message) || '恢复失败')
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
        ElMessage.success('已从发布列表彻底删除')
        this.purgePublishedDialogVisible = false
        this.publishedPageNo = 1
        await this.loadPublishedMemes()
      } catch (e) {
        ElMessage.error((e && e.message) || '彻底删除失败')
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
        this.$message && this.$message.error && this.$message.error(e.message || '发布列表加载失败')
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
        ElMessage.warning(error && error.message ? error.message : '回显接口异常，已使用当前页面数据')
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
          ElMessage.warning('仅支持 JPG/PNG/WEBP 格式')
          return
        }
        if (file.size > 5 * 1024 * 1024) {
          ElMessage.warning('头像大小不能超过 5MB')
          return
        }
        this.cropperImageSrc = await this.readFileAsDataURL(file)
        this.cropperResultPreview = this.cropperImageSrc
        this.cropperKey += 1
        this.cropperDialogVisible = true
      } catch (error) {
        ElMessage.error(error && error.message ? error.message : '读取头像失败')
      } finally {
        if (event && event.target) {
          event.target.value = ''
        }
      }
    },
    async confirmCropAndUpload() {
      const cropper = this.$refs.avatarCropperRef
      if (!cropper || !cropper.getResult) {
        ElMessage.warning('裁剪器初始化中，请稍后重试')
        return
      }
      try {
        const result = cropper.getResult()
        const canvas = result && result.canvas ? result.canvas : null
        if (!canvas || !canvas.toBlob) {
          ElMessage.warning('裁剪结果无效，请重新选择图片')
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
        ElMessage.success('头像上传成功')
      } catch (error) {
        ElMessage.error(error && error.message ? error.message : '头像上传失败')
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
          ElMessage.success('资料更新成功')
          await this.loadProfile()
        } catch (error) {
          ElMessage.error(error && error.message ? error.message : '资料更新失败')
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
        this.$message && this.$message.error && this.$message.error((e && e.message) || '收藏夹加载失败')
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
        this.$message && this.$message.error && this.$message.error((e && e.message) || '收藏列表加载失败')
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
        this.$message && this.$message.warning && this.$message.warning('请输入收藏夹名称')
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
          this.$message && this.$message.success && this.$message.success('收藏夹已创建')
        } else {
          await updateFavoriteFolder(this.folderEditingId, {
            name,
            description: this.folderForm.description,
            isPublic: this.folderForm.isPublic,
          })
          this.$message && this.$message.success && this.$message.success('收藏夹已更新')
        }
        this.folderDialogVisible = false
        await this.loadFolders()
      } catch (e) {
        this.$message && this.$message.error && this.$message.error((e && e.message) || '保存失败')
      } finally {
        this.folderSubmitting = false
      }
    },
    confirmDeleteFolder(folder) {
      if (folder && folder.isDefault) {
        this.$message && this.$message.warning && this.$message.warning('默认收藏夹不可删除')
        return
      }
      this.$confirm
        ? this.$confirm(`删除「${folder.name}」？夹内梗图将移入默认收藏夹，且不会取消收藏。`, '提示', {
            type: 'warning',
            confirmButtonText: '删除',
            cancelButtonText: '取消',
          }).then(() => this.doDeleteFolder(folder))
        : this.doDeleteFolder(folder)
    },
    async doDeleteFolder(folder) {
      try {
        await deleteFavoriteFolder(folder.id)
        this.$message && this.$message.success && this.$message.success('收藏夹已删除')
        if (sameFolderId(this.selectedFolderId, folder.id)) {
          this.selectedFolderId = '0'
          this.favoriteViewMode = 'folders'
        }
        await this.loadFolders()
      } catch (e) {
        this.$message && this.$message.error && this.$message.error((e && e.message) || '删除失败')
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
        ElMessage.warning('已在当前收藏夹中')
        return
      }
      this.batchMoveSubmitting = true
      try {
        await batchMoveFavorites(this.batchMoveTargetId, ids)
        ElMessage.success(ids.length > 1 ? `已移动 ${ids.length} 项` : '已移动')
        this.batchMoveDialogVisible = false
        this.selectedMemeIds = []
        this.folderSelectAll = false
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        ElMessage.error((e && e.message) || '移动失败')
      } finally {
        this.batchMoveSubmitting = false
      }
    },
    confirmRemoveFavorite(item) {
      const name = (item && item.name) || '未命名梗图'
      ElMessageBox.confirm(`确定取消收藏「${name}」？`, '取消收藏', {
        type: 'warning',
        confirmButtonText: '取消收藏',
        cancelButtonText: '返回',
        confirmButtonClass: 'el-button--danger',
      })
        .then(() => this.doRemoveFavorite(item.id))
        .catch(() => {})
    },
    confirmBatchRemoveFavorites() {
      const count = this.selectedMemeIds.length
      if (!count) return
      const tip =
        count > 1 ? `确定取消收藏已选的 ${count} 个梗图？` : '确定取消收藏已选的梗图？'
      ElMessageBox.confirm(tip, '批量取消收藏', {
        type: 'warning',
        confirmButtonText: '取消收藏',
        cancelButtonText: '返回',
        confirmButtonClass: 'el-button--danger',
      })
        .then(() => this.doBatchRemoveFavorites())
        .catch(() => {})
    },
    async doBatchRemoveFavorites() {
      const ids = this.selectedMemeIds.slice()
      if (!ids.length) return
      try {
        await Promise.all(ids.map((id) => removeMemeFavorite(id)))
        ElMessage.success(ids.length > 1 ? `已取消收藏 ${ids.length} 项` : '已取消收藏')
        this.selectedMemeIds = []
        this.folderSelectAll = false
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        ElMessage.error((e && e.message) || '取消收藏失败')
      }
    },
    async doRemoveFavorite(memeId) {
      const id = this.normalizeMemeId(memeId)
      if (!id) return
      try {
        await removeMemeFavorite(id)
        ElMessage.success('已取消收藏')
        this.selectedMemeIds = this.selectedMemeIds.filter(
          (x) => this.normalizeMemeId(x) !== id
        )
        this.refreshFolderSelectAll()
        this.folderContentPageNo = 1
        await this.loadFolderContent()
        await this.loadFolders()
      } catch (e) {
        ElMessage.error((e && e.message) || '取消收藏失败')
      }
    },
  },
}
</script>

<style scoped>
.profile-page {
  margin: -16px -32px -32px;
  padding: 16px 24px 24px;
  min-height: calc(100vh - 56px);
  background: var(--meme-bg);
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
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  min-height: calc(100vh - 96px);
}

.profile-sidebar {
  min-width: 0;
  height: 100%;
}

.sidebar-card {
  border-radius: 18px;
  height: 100%;
}

.sidebar-card :deep(.el-card__body) {
  padding: 20px;
}

.sidebar-header {
  text-align: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--meme-border);
}

.sidebar-avatar {
  cursor: zoom-in;
  border: 3px solid var(--el-color-primary-light-9);
  background: linear-gradient(135deg, var(--meme-primary), var(--meme-primary-dark));
}

.sidebar-name {
  margin: 14px 0 8px;
  font-size: 30px;
  line-height: 1.25;
  color: var(--meme-text);
  word-break: break-all;
}

.sidebar-signature {
  margin: 0;
  color: var(--meme-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.sidebar-stats {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 8px;
  margin-top: 16px;
  padding: 4px 0 2px;
}

.sidebar-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  color: inherit;
  transition: background 0.15s ease;
}

.sidebar-stat:hover:not(.sidebar-stat--static) {
  background: var(--meme-primary-soft);
}

.sidebar-stat strong {
  font-size: 18px;
  font-weight: 700;
  color: var(--meme-text);
  line-height: 1.2;
}

.sidebar-stat span {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.sidebar-stat--static {
  cursor: default;
}

.sidebar-stat--static strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text-secondary);
}

.sidebar-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.sidebar-edit-btn,
.sidebar-follow-btn {
  width: 100%;
}

.profile-main-content {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.overview-row {
  margin-bottom: 14px;
}

.overview-card {
  border-radius: 14px;
}

.overview-card :deep(.el-card__body) {
  padding: 14px 12px;
}

.overview-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--meme-text);
  line-height: 1.2;
  text-align: center;
}

.overview-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--meme-text-secondary);
  text-align: center;
}

.content-card {
  border-radius: 16px;
  flex: 1;
  min-height: 0;
}

.content-card :deep(.el-card__body) {
  padding: 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.content-card :deep(.el-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.content-card :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}

.meme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
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
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.published-panel-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  max-width: 520px;
}

.published-create-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--meme-text-inverse);
  background: linear-gradient(135deg, var(--meme-primary) 0%, var(--meme-primary-dark) 100%);
  box-shadow: 0 4px 14px var(--meme-focus-ring);
  text-decoration: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  flex-shrink: 0;
}

.published-create-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px var(--meme-focus-ring);
  color: var(--meme-text-inverse);
}

.published-create-btn-icon {
  font-size: 16px;
  line-height: 1;
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
  border: 1px solid var(--el-border-color-lighter);
  background: var(--meme-bg-card);
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.15s ease;
}

.published-filter-chip:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.published-filter-chip.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
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
  color: var(--el-color-danger);
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
  color: var(--el-text-color-primary);
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
  color: var(--el-text-color-regular);
}

.delete-published-notes {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.delete-published-preview {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
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

.profile-card :deep(.el-card__body) {
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
  border: 3px solid var(--el-color-primary-light-9);
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

.stats-card :deep(.el-card__body) {
  padding: 18px 10px;
}

.edit-form {
  margin-top: 8px;
}

.edit-echo-skeleton {
  padding-top: 8px;
}

.avatar-edit-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-edit-preview {
  border: 2px solid var(--el-color-primary-light-9);
}

.avatar-edit-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-edit-tip {
  font-size: 12px;
  color: var(--meme-text-muted);
}

.avatar-file-input {
  display: none;
}

.field-full {
  width: 100%;
}

.cropper-panel {
  width: 100%;
  height: 420px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg);
}

.avatar-cropper {
  width: 100%;
  height: 100%;
}

.cropper-toolbar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cropper-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cropper-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cropper-step-label {
  font-size: 12px;
  color: var(--meme-text-secondary);
}

.cropper-preview-wrap {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.cropper-preview-card {
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  padding: 10px;
  background: var(--meme-bg-card);
}

.cropper-preview-title {
  font-size: 12px;
  color: var(--meme-text-secondary);
  margin-bottom: 8px;
}

.cropper-preview-img {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  background: var(--meme-bg-muted);
}

.cropper-preview-empty {
  height: 140px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--meme-text-muted);
  background: var(--meme-bg);
  font-size: 12px;
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

  .profile-layout {
    min-height: auto;
  }

  .profile-layout {
    grid-template-columns: 1fr;
  }

  .sidebar-name {
    font-size: 24px;
  }

  .profile-card :deep(.el-card__body) {
    padding: 16px;
  }

  .profile-content {
    flex-direction: column;
  }

  .profile-main {
    align-items: flex-start;
    gap: 14px;
  }

  .profile-avatar {
    width: 88px;
    height: 88px;
  }

  .profile-nickname {
    font-size: 22px;
  }

  .cropper-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .cropper-preview-wrap {
    grid-template-columns: 1fr;
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
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}
.favorite-create-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 10px;
  background: var(--el-color-primary-light-7);
  color: var(--meme-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s ease, box-shadow 0.18s ease;
  box-shadow: 0 1px 2px var(--meme-focus-ring);
}
.favorite-create-btn:hover {
  background: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px var(--meme-focus-ring);
}
.favorite-create-btn-icon {
  font-size: 16px;
  line-height: 1;
  font-weight: 700;
}
.folder-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 120px;
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
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  color: var(--meme-text-secondary);
}
.folder-card-name {
  flex: 1;
  min-width: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
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
  color: var(--el-text-color-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease;
}
.folder-card-more:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}
.folder-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.folder-card-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
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
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.folder-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: none;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.folder-back-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}
.folder-detail-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
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
  border: 1px solid var(--el-border-color-lighter);
  background: var(--meme-bg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.folder-batch-bar.is-active {
  border-color: var(--meme-border-accent);
  box-shadow: 0 2px 10px var(--meme-focus-ring);
}
.folder-batch-check :deep(.el-checkbox__label) {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.folder-batch-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
}
.folder-batch-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
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
  border: 1px solid var(--el-border-color-lighter);
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
  color: var(--el-text-color-primary);
}
.folder-batch-dock-clear {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: color 0.15s ease;
}
.folder-batch-dock-clear:hover {
  color: var(--el-color-primary);
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
  background: var(--el-color-primary-light-9);
  color: var(--meme-primary);
  border-color: var(--el-color-primary-light-7);
}
.folder-batch-dock-btn-move:hover {
  background: var(--el-color-primary-light-7);
  border-color: var(--el-color-primary-light-5);
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
  box-shadow: 0 0 0 2px var(--el-color-primary);
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
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
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
.favorite-folder-picker .folder-meta {
  flex: 1;
  min-width: 0;
}
.favorite-folder-picker .folder-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.favorite-folder-picker .folder-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.favorite-folder-check {
  color: var(--el-color-primary);
  font-weight: 700;
  font-size: 16px;
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
/* 收藏梗图卡片操作菜单（挂载在 body） */
.meme-action-popper.el-popper {
  border-radius: 12px !important;
  border: none !important;
  box-shadow: var(--meme-shadow-soft) !important;
  padding: 6px 0 !important;
  min-width: 120px;
}
.meme-action-popper .el-dropdown-menu__item {
  padding: 10px 20px;
  font-size: 14px;
  color: var(--meme-text-secondary);
  line-height: 1.2;
}
.meme-action-popper .el-dropdown-menu__item:not(.is-disabled):hover {
  background: var(--meme-bg-muted);
  color: var(--meme-text);
}
</style>
