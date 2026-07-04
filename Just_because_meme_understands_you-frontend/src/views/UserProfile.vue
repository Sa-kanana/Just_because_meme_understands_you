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

          <div class="sidebar-meta">
            <div class="sidebar-meta-item">
              <span>用户ID</span>
              <span>#{{ profile.userId || '-' }}</span>
            </div>
            <div class="sidebar-meta-item">
              <span>关注</span>
              <span>{{ formatNum(profile.stats.followCount) }}</span>
            </div>
            <div class="sidebar-meta-item">
              <span>粉丝</span>
              <span>{{ formatNum(profile.stats.fansCount) }}</span>
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
            <el-tab-pane :label="`发布梗图 (${publishedList.length})`" name="published">
              <div v-if="publishedList.length" class="meme-grid">
                <div
                  v-for="item in publishedList"
                  :key="`published-${item.id}`"
                  class="meme-card-item"
                  @click="goMemeDetail(item.id)"
                >
                  <el-image :src="item.image" fit="cover" class="meme-cover">
                    <template #error>
                      <div class="meme-cover-error">图片加载失败</div>
                    </template>
                  </el-image>
                  <div class="meme-info">
                    <div class="meme-title" :title="item.name">{{ item.name || '未命名梗图' }}</div>
                    <div class="meme-meta">
                      <span>👁 {{ formatNum(item.pageViews) }}</span>
                      <span>👍 {{ formatNum(item.likes) }}</span>
                      <span>💬 {{ formatNum(item.comments) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="这个用户还没有发布梗图" />
            </el-tab-pane>

            <el-tab-pane :label="`收藏梗图 (${favoriteList.length})`" name="favorite">
              <div v-if="favoriteList.length" class="meme-grid">
                <div
                  v-for="item in favoriteList"
                  :key="`favorite-${item.id}`"
                  class="meme-card-item"
                  @click="goMemeDetail(item.id)"
                >
                  <el-image :src="item.image" fit="cover" class="meme-cover">
                    <template #error>
                      <div class="meme-cover-error">图片加载失败</div>
                    </template>
                  </el-image>
                  <div class="meme-info">
                    <div class="meme-title" :title="item.name">{{ item.name || '未命名梗图' }}</div>
                    <div class="meme-meta">
                      <span>👁 {{ formatNum(item.pageViews) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="这个用户还没有收藏梗图" />
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
  </div>
</template>

<script>
import { Cropper } from 'vue-advanced-cropper'
import { ElImageViewer, ElMessage } from 'element-plus'
import { getEditProfileEcho, getUserProfile, updateUserProfile } from '@/api/user'
import { uploadToOss } from '@/api/oss'
import { useAuthStore } from '@/stores/auth'

export default {
  name: 'UserProfilePage',
  components: {
    Cropper,
    ElImageViewer,
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
    }
  },
  computed: {
    currentUser() {
      return useAuthStore().currentUser
    },
    authToken() {
      return useAuthStore().token
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
      return this.currentUserId
    },
    isRequestUserIdValid() {
      return /^\d+$/.test(this.requestUserId)
    },
    showEditButton() {
      if (this.profile.isSelf) return true
      return !!this.currentUserId && this.currentUserId === String(this.profile.userId)
    },
    avatarFallback() {
      const nickname = this.profile.nickname || ''
      return nickname ? nickname.charAt(0).toUpperCase() : 'U'
    },
    avatarViewerList() {
      return this.profile.avatar ? [this.profile.avatar] : []
    },
    publishedList() {
      return Array.isArray(this.profile.memes) ? this.profile.memes : []
    },
    favoriteList() {
      return Array.isArray(this.profile.favorites) ? this.profile.favorites : []
    },
  },
  watch: {
    '$route.params.userId': {
      immediate: true,
      handler() {
        this.syncRouteUserId()
        this.loadProfile()
      },
    },
    currentUserId() {
      this.syncRouteUserId()
      this.loadProfile()
    },
  },
  methods: {
    syncRouteUserId() {
      if (!this.currentUserId || !/^\d+$/.test(this.currentUserId)) return
      if (this.routeUserId === this.currentUserId) return
      if (this.$route.name !== 'userProfile') return
      this.$router.replace({
        name: 'userProfile',
        params: { userId: this.currentUserId },
        query: this.$route.query,
      })
    },
    async loadProfile() {
      if (!this.requestUserId) {
        this.errorMessage = '未获取到当前用户ID，请先登录后重试'
        return
      }
      if (!this.isRequestUserIdValid) {
        this.errorMessage = '当前用户ID格式错误，请重新登录后重试'
        return
      }
      this.loading = true
      this.errorMessage = ''
      try {
        const data = await getUserProfile(this.requestUserId, this.authToken)
        this.profile = {
          ...this.profile,
          ...data,
          stats: {
            ...this.profile.stats,
            ...(data.stats || {}),
          },
        }
      } catch (error) {
        this.errorMessage = error && error.message ? error.message : '个人主页加载失败'
      } finally {
        this.loading = false
      }
    },
    openAvatarViewer() {
      if (!this.profile.avatar) return
      this.avatarViewerVisible = true
    },
    goMemeDetail(id) {
      if (id == null || id === '') return
      this.$router.push({ name: 'memeDetail', params: { id } })
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
  },
}
</script>

<style scoped>
.profile-page {
  margin: -16px -32px -32px;
  padding: 16px 24px 24px;
  min-height: calc(100vh - 56px);
  background: var(--meme-bg, #f9fafb);
  width: calc(100% + 64px);
}

.profile-loading-page {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #6b7280;
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
  border-bottom: 1px solid #eef2f7;
}

.sidebar-avatar {
  cursor: zoom-in;
  border: 3px solid #e8f2fd;
  background: linear-gradient(135deg, var(--meme-primary, #318aef), var(--meme-primary-dark, #2872d4));
}

.sidebar-name {
  margin: 14px 0 8px;
  font-size: 30px;
  line-height: 1.25;
  color: #111827;
  word-break: break-all;
}

.sidebar-signature {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.sidebar-meta {
  margin-top: 14px;
}

.sidebar-meta-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed #edf2f7;
  font-size: 14px;
  color: #4b5563;
}

.sidebar-meta-item:last-child {
  border-bottom: none;
}

.sidebar-actions {
  margin-top: 12px;
}

.sidebar-edit-btn {
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
  color: #111827;
  line-height: 1.2;
  text-align: center;
}

.overview-label {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
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
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.meme-card-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.meme-cover {
  width: 100%;
  height: 140px;
  background: #f3f4f6;
}

.meme-cover-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 12px;
}

.meme-info {
  padding: 10px;
}

.meme-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
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
  color: #6b7280;
}

.profile-card {
  border-radius: var(--meme-radius-xl, 24px);
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
  color: #6b7280;
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
  border: 3px solid #e8f2fd;
  background: linear-gradient(135deg, var(--meme-primary, #318aef), var(--meme-primary-dark, #2872d4));
  color: #fff;
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
  color: #111827;
}

.profile-signature {
  margin: 0;
  color: #6b7280;
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
  border: 2px solid #e8f2fd;
}

.avatar-edit-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-edit-tip {
  font-size: 12px;
  color: #9ca3af;
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
  border: 1px solid #e5e7eb;
  background: #f9fafb;
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
  color: #6b7280;
}

.cropper-preview-wrap {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.cropper-preview-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px;
  background: #fff;
}

.cropper-preview-title {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}

.cropper-preview-img {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  background: #f3f4f6;
}

.cropper-preview-empty {
  height: 140px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  background: #f9fafb;
  font-size: 12px;
}

.stats-value {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stats-label {
  margin-top: 6px;
  color: #6b7280;
  font-size: 14px;
}

.meme-loading-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid #e5e7eb;
  border-top-color: var(--meme-primary, #318aef);
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
</style>
