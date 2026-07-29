<template>
  <div class="publish-page">
    <header class="publish-hero">
      <p class="publish-hero-kicker">PUBLISH</p>
      <h1 class="publish-hero-title">发布梗</h1>
      <p class="publish-hero-sub">
        分享你知道的梗，填写基础信息并附上延伸阅读，帮助更多人快速 get 到点。
      </p>
    </header>

    <vs-card class="publish-card">
      <template #text>
        <ui-form :model="form" label-position="top" class="publish-form">
          <section class="form-section">
            <div class="section-head">
              <span class="section-icon section-icon--primary" aria-hidden="true">
                <i class="ri-edit-2-line" />
              </span>
              <div>
                <h3 class="section-title">基础信息</h3>
                <p class="section-hint">名称与介绍会展示在梗详情页顶部</p>
              </div>
            </div>

            <ui-form-item label="梗名称" required>
              <vs-input
                v-model="form.name"
                block
                clearable
                maxlength="50"
                placeholder="给这个梗起个名字"
                class="publish-field"
              />
            </ui-form-item>

            <ui-form-item label="梗介绍">
              <vs-input
                v-model="form.introduction"
                type="textarea"
                block
                :rows="4"
                maxlength="500"
                placeholder="介绍一下这个梗的来源、出处、怎么用…（选填）"
                class="publish-field publish-field--textarea"
              />
            </ui-form-item>
          </section>

          <section class="form-section">
            <div class="section-head">
              <span class="section-icon" aria-hidden="true">
                <i class="ri-image-2-line" />
              </span>
              <div>
                <h3 class="section-title">封面与标签</h3>
                <p class="section-hint">封面用于列表展示，标签帮助用户发现你的梗</p>
              </div>
            </div>

            <ui-form-item label="封面图" required>
              <div class="cover-uploader">
                <div
                  v-if="form.image"
                  class="upload-tile upload-tile--cover upload-tile--filled upload-tile--previewable"
                  role="button"
                  tabindex="0"
                  aria-label="点击放大查看封面"
                  @click="openImagePreview(form.image)"
                  @keydown.enter.prevent="openImagePreview(form.image)"
                >
                  <ui-image :src="form.image" fit="cover" class="upload-preview" />
                  <button
                    type="button"
                    class="upload-remove"
                    aria-label="移除封面"
                    @click.stop="form.image = ''"
                  >
                    <i class="ri-close-line" />
                  </button>
                  <span class="upload-badge">封面</span>
                  <span class="upload-zoom-hint" aria-hidden="true">
                    <i class="ri-zoom-in-line" />
                  </span>
                </div>
                <label v-else class="upload-tile upload-tile--cover upload-tile--empty">
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    class="upload-input"
                    :disabled="coverUploading"
                    @change="onCoverChange"
                  />
                  <i class="ri-image-add-line upload-icon" aria-hidden="true" />
                  <span class="upload-label">{{ coverUploading ? '上传中…' : '点击上传封面' }}</span>
                  <span class="upload-tip">JPG / PNG / WebP / GIF，≤ 10MB</span>
                </label>
              </div>
            </ui-form-item>

            <ui-form-item label="标签">
              <vs-select
                v-model="form.tagIds"
                multiple
                filter
                block
                placeholder="选择标签（选填）"
                class="tag-select"
              >
                <vs-option
                  v-for="tag in tags"
                  :key="tag.id"
                  :label="tag.name"
                  :value="tag.id"
                >
                  {{ tag.name }}
                </vs-option>
              </vs-select>
            </ui-form-item>
          </section>

          <section class="form-section">
            <div class="section-head section-head--with-action">
              <div class="section-head-main">
                <span class="section-icon" aria-hidden="true">
                  <i class="ri-links-line" />
                </span>
                <div>
                  <h3 class="section-title">相关链接</h3>
                  <p class="section-hint">B 站、百科、原文报道等外链，最多 10 条</p>
                </div>
              </div>
              <vs-button
                v-if="form.resources.length < 10"
                type="border"
                color="primary"
                size="small"
                class="section-add-btn"
                @click="addLink"
              >
                <i class="ri-add-line" aria-hidden="true" />
                添加链接
              </vs-button>
            </div>

            <div v-if="form.resources.length" class="link-list">
              <div
                v-for="(item, idx) in form.resources"
                :key="`link-${idx}`"
                class="link-card"
              >
                <div class="link-card-index">{{ idx + 1 }}</div>
                <div class="link-card-body">
                  <div class="link-card-row">
                    <vs-select v-model="item.type" class="link-type-select" placeholder="类型">
                      <vs-option label="通用链接" value="link" />
                      <vs-option label="文章" value="article" />
                      <vs-option label="视频" value="video" />
                    </vs-select>
                    <vs-input
                      v-model="item.title"
                      block
                      maxlength="128"
                      placeholder="给链接起个标题，如「原视频出处」"
                      class="link-title-input"
                    />
                  </div>
                  <vs-input
                    v-model="item.url"
                    block
                    maxlength="500"
                    placeholder="https://..."
                    class="link-url-input"
                  >
                    <template #icon>
                      <i class="ri-link" aria-hidden="true" />
                    </template>
                  </vs-input>
                </div>
                <button
                  type="button"
                  class="link-card-remove"
                  aria-label="删除链接"
                  @click="removeLink(idx)"
                >
                  <i class="ri-delete-bin-line" />
                </button>
              </div>
            </div>

            <button v-else type="button" class="link-empty" @click="addLink">
              <i class="ri-link link-empty-icon" aria-hidden="true" />
              <span class="link-empty-title">还没有添加相关链接</span>
              <span class="link-empty-desc">点击此处或上方按钮，添加 B 站、百科等延伸阅读</span>
            </button>
          </section>

          <section class="form-section form-section--last">
            <div class="section-head">
              <span class="section-icon" aria-hidden="true">
                <i class="ri-attachment-2" />
              </span>
              <div>
                <h3 class="section-title">相关资源</h3>
                <p class="section-hint">补充图片 / GIF，最多 6 张，与外链分开存储</p>
              </div>
            </div>

            <div class="resource-uploader">
              <div
                v-for="(url, idx) in form.resourceUrls"
                :key="`res-${idx}`"
                class="upload-tile upload-tile--resource upload-tile--filled upload-tile--previewable"
                role="button"
                tabindex="0"
                aria-label="点击放大查看资源图"
                @click="openImagePreview(url)"
                @keydown.enter.prevent="openImagePreview(url)"
              >
                <ui-image :src="url" fit="cover" class="upload-preview" />
                <button
                  type="button"
                  class="upload-remove"
                  aria-label="移除资源"
                  @click.stop="removeResource(idx)"
                >
                  <i class="ri-close-line" />
                </button>
                <span class="upload-zoom-hint" aria-hidden="true">
                  <i class="ri-zoom-in-line" />
                </span>
              </div>
              <label v-if="form.resourceUrls.length < 6" class="upload-tile upload-tile--resource upload-tile--empty">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  class="upload-input"
                  :disabled="resourceUploading"
                  @change="onResourceChange"
                />
                <i class="ri-add-line upload-icon" aria-hidden="true" />
                <span class="upload-label">{{ resourceUploading ? '上传中…' : '添加资源' }}</span>
              </label>
            </div>
          </section>

          <footer class="publish-footer">
            <p class="publish-footer-hint">
              <i class="ri-shield-check-line" aria-hidden="true" />
              提交后将进入审核，通过后会在首页展示
            </p>
            <div class="publish-actions">
              <vs-button
                type="border"
                color="primary"
                class="publish-cancel-btn"
                :disabled="submitting"
                @click="goBack"
              >
                取消
              </vs-button>
              <vs-button
                color="primary"
                :loading="submitting"
                :disabled="!canSubmit || submitting"
                class="publish-submit-btn"
                @click="handleSubmit"
              >
                <span class="publish-submit-inner">
                  <i v-if="!submitting" class="ri-send-plane-2-line" aria-hidden="true" />
                  发布梗
                </span>
              </vs-button>
            </div>
          </footer>
        </ui-form>
      </template>
    </vs-card>

    <vs-dialog
      v-model="imagePreviewVisible"
      full-screen
      not-padding
      class="publish-image-viewer"
    >
      <div class="publish-image-viewer__body" @click="closeImagePreview">
        <button
          type="button"
          class="publish-image-viewer__close"
          aria-label="关闭预览"
          @click.stop="closeImagePreview"
        >
          <i class="ri-close-line" />
        </button>
        <ui-image
          v-if="imagePreviewSrc"
          :src="imagePreviewSrc"
          fit="contain"
          class="publish-image-viewer__img"
          @click.stop
        />
        <p class="publish-image-viewer__tip">点击空白处关闭</p>
      </div>
    </vs-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from '@/utils/uiFeedback'
import { getMemeTags, publishMeme } from '@/api/meme'
import { uploadToOss } from '@/api/oss'
import { useAuthStore } from '@/stores/auth'
import { isAuthErrorHandled } from '@/utils/authSession'

const router = useRouter()
const authStore = useAuthStore()

const tags = ref([])
const submitting = ref(false)
const coverUploading = ref(false)
const resourceUploading = ref(false)
const imagePreviewVisible = ref(false)
const imagePreviewSrc = ref('')

const form = reactive({
  name: '',
  introduction: '',
  image: '',
  tagIds: [],
  resources: [],
  resourceUrls: [],
})

const canSubmit = computed(
  () =>
    form.name.trim() &&
    form.image
)

function openImagePreview(url) {
  const src = String(url || '').trim()
  if (!src) return
  imagePreviewSrc.value = src
  imagePreviewVisible.value = true
}

function closeImagePreview() {
  imagePreviewVisible.value = false
  imagePreviewSrc.value = ''
}

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    router.replace({ name: 'login', query: { redirect: '/publish' } })
    return
  }
  try {
    tags.value = await getMemeTags()
  } catch (e) {
    toast.error(e.message || '加载标签失败')
  }
})

async function onCoverChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    toast.warning('封面不能超过 10MB')
    return
  }
  coverUploading.value = true
  try {
    form.image = await uploadToOss(file, 'meme')
  } catch (e) {
    toast.error(e.message || '封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

function addLink() {
  if (form.resources.length >= 10) {
    toast.warning('最多 10 条相关链接')
    return
  }
  form.resources.push({
    type: 'link',
    title: '',
    url: '',
    sortOrder: form.resources.length,
  })
}

function removeLink(idx) {
  form.resources.splice(idx, 1)
  form.resources.forEach((item, index) => {
    item.sortOrder = index
  })
}

function validateLinks() {
  for (let i = 0; i < form.resources.length; i += 1) {
    const item = form.resources[i]
    const title = String(item.title || '').trim()
    const url = String(item.url || '').trim()
    if (!title && !url) continue
    if (!title) {
      toast.warning(`第 ${i + 1} 条链接请填写标题`)
      return false
    }
    if (!url) {
      toast.warning(`第 ${i + 1} 条链接请填写 URL`)
      return false
    }
    if (!/^https?:\/\//i.test(url)) {
      toast.warning(`第 ${i + 1} 条链接须以 http:// 或 https:// 开头`)
      return false
    }
  }
  return true
}

async function onResourceChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (form.resourceUrls.length >= 6) {
    toast.warning('最多 6 个资源')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    toast.warning('单个资源不能超过 10MB')
    return
  }
  resourceUploading.value = true
  try {
    const url = await uploadToOss(file, 'meme')
    form.resourceUrls.push(url)
  } catch (e) {
    toast.error(e.message || '资源上传失败')
  } finally {
    resourceUploading.value = false
  }
}

function removeResource(idx) {
  form.resourceUrls.splice(idx, 1)
}

async function handleSubmit() {
  if (!canSubmit.value) return
  if (!validateLinks()) return
  submitting.value = true
  try {
    const resources = form.resources
      .map((item, index) => ({
        type: item.type || 'link',
        title: String(item.title || '').trim(),
        url: String(item.url || '').trim(),
        sortOrder: index,
      }))
      .filter((item) => item.title && item.url)

    const data = await publishMeme({
      name: form.name,
      introduction: form.introduction,
      image: form.image,
      tagIds: form.tagIds,
      resources,
      resourceUrls: form.resourceUrls,
    })
    toast.success(`发布成功，当前状态：${data.statusDesc || '审核中'}`)
    const user = authStore.currentUser
    const rawId = user?.id ?? user?.userId
    const userId = rawId != null ? String(rawId).trim() : ''
    if (userId && /^\d+$/.test(userId)) {
      router.push({
        name: 'userProfile',
        params: { userId },
        query: { tab: 'published' },
      })
    } else {
      router.push('/')
    }
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    toast.error(e.message || '发布失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.publish-page {
  max-width: 820px;
  margin: 0 auto;
  padding: 12px 8px 40px;
}

.publish-hero {
  margin-bottom: 18px;
  text-align: center;
}

.publish-hero-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--meme-primary);
}

.publish-hero-title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.publish-hero-sub {
  margin: 0 auto;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--meme-text-secondary);
}

.publish-card {
  border-radius: var(--meme-radius-lg) !important;
}

.publish-card :deep(.vs-card__text) {
  padding: 8px 28px 24px;
}

.publish-form :deep(.ui-form-item) {
  margin-bottom: 16px;
}

.publish-form :deep(.ui-form-item__label) {
  font-weight: 600;
  color: var(--meme-text-secondary);
  padding-bottom: 6px;
}

.publish-field {
  width: 100%;
}

.publish-field :deep(.vs-input__wrapper),
.publish-field :deep(.vs-input__original),
.tag-select :deep(.vs-select),
.link-title-input :deep(.vs-input__wrapper),
.link-url-input :deep(.vs-input__wrapper) {
  width: 100%;
}

.publish-field--textarea :deep(textarea),
.publish-field--textarea :deep(.vs-input__original) {
  min-height: 108px;
  line-height: 1.55;
  resize: vertical;
}

.form-section {
  padding: 20px 0;
  border-bottom: 1px solid var(--meme-border);
}

.form-section--last {
  border-bottom: none;
  padding-bottom: 8px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.section-head--with-action {
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.section-head-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.section-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 18px;
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
}

.section-icon--primary {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.section-title {
  margin: 0 0 2px;
  font-size: 16px;
  font-weight: 650;
  color: var(--meme-text);
}

.section-hint {
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
  line-height: 1.5;
}

.section-add-btn {
  flex-shrink: 0;
}

.cover-uploader,
.resource-uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.upload-tile {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
}

.upload-tile--cover {
  width: 200px;
  height: 132px;
}

.upload-tile--resource {
  width: 108px;
  height: 108px;
}

.upload-tile--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1.5px dashed var(--meme-border-strong);
  background: var(--meme-bg-muted);
  cursor: pointer;
  color: var(--meme-text-muted);
}

.upload-tile--empty:hover {
  border-color: var(--meme-primary);
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
  box-shadow: 0 4px 12px var(--meme-focus-ring);
  transform: translateY(-1px);
}

.upload-tile--filled {
  border: 1px solid var(--meme-border);
  box-shadow: var(--meme-shadow-soft);
}

.upload-tile--previewable {
  cursor: zoom-in;
}

.upload-tile--previewable:hover .upload-zoom-hint {
  opacity: 1;
  transform: translateY(0);
}

.upload-zoom-hint {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translate(-50%, 4px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.55);
  color: #fff;
  font-size: 15px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease, transform 0.15s ease;
  backdrop-filter: blur(4px);
}

.upload-preview {
  width: 100%;
  height: 100%;
  display: block;
}

.upload-icon {
  font-size: 28px;
  opacity: 0.9;
  line-height: 1;
}

.upload-label {
  font-size: 13px;
  font-weight: 550;
}

.upload-tip {
  font-size: 11px;
  color: var(--meme-text-muted);
  text-align: center;
  padding: 0 8px;
  line-height: 1.3;
}

.upload-badge {
  position: absolute;
  left: 8px;
  bottom: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 550;
  color: var(--meme-text-inverse);
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
}

.upload-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: var(--meme-text-inverse);
  cursor: pointer;
  transition: background 0.2s;
}

.upload-remove:hover {
  background: rgba(220, 38, 38, 0.85);
}

.upload-input {
  display: none;
}

.tag-select {
  width: 100%;
}

.link-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.link-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px 14px 12px;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.link-card:hover {
  border-color: color-mix(in srgb, var(--meme-primary) 35%, var(--meme-border));
  box-shadow: 0 4px 14px var(--meme-focus-ring);
}

.link-card-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
  margin-top: 4px;
}

.link-card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.link-card-row {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 10px;
}

.link-type-select,
.link-title-input,
.link-url-input {
  width: 100%;
}

.link-card-remove {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin-top: 2px;
  border: none;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--meme-text-muted);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.link-card-remove:hover {
  background: var(--meme-danger-soft);
  color: var(--meme-danger);
}

.link-empty {
  width: 100%;
  padding: 28px 20px;
  border-radius: 12px;
  border: 1.5px dashed var(--meme-border-strong);
  background: var(--meme-bg-muted);
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.link-empty:hover {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.link-empty-icon {
  display: block;
  font-size: 28px;
  color: var(--meme-text-muted);
  margin: 0 auto 8px;
  line-height: 1;
}

.link-empty-title {
  display: block;
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 550;
  color: var(--meme-text-secondary);
}

.link-empty-desc {
  display: block;
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.publish-footer {
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid var(--meme-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.publish-footer-hint {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.publish-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.publish-cancel-btn {
  min-width: 88px;
  height: 40px !important;
  padding: 0 18px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  color: var(--meme-text-secondary) !important;
  background: var(--meme-bg-elevated) !important;
  border: 1px solid var(--meme-border-strong) !important;
  box-shadow: none !important;
}

.publish-cancel-btn:hover:not(:disabled) {
  color: var(--meme-text) !important;
  border-color: var(--meme-text-muted) !important;
  background: var(--meme-bg-muted) !important;
  filter: none !important;
}

.publish-cancel-btn :deep(.vs-button__content) {
  color: inherit !important;
}

.publish-submit-btn {
  min-width: 124px;
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

.publish-submit-btn:hover:not(:disabled) {
  background: var(--meme-primary-dark) !important;
  color: #fff !important;
  filter: none !important;
  transform: translateY(-1px);
}

.publish-submit-btn:disabled,
.publish-submit-btn.is-disabled {
  color: #fff !important;
  background: color-mix(in srgb, var(--meme-primary) 45%, var(--meme-border)) !important;
  box-shadow: none !important;
  opacity: 1 !important;
  cursor: not-allowed;
}

.publish-submit-btn:disabled :deep(.vs-button__content),
.publish-submit-btn:disabled :deep(.vs-button__content *) {
  color: #fff !important;
  opacity: 1 !important;
}

.publish-submit-btn :deep(.vs-button__content) {
  color: inherit !important;
}

.publish-submit-inner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit;
}

@media (max-width: 640px) {
  .publish-page {
    padding: 8px 0 28px;
  }

  .publish-card :deep(.vs-card__text) {
    padding: 4px 16px 20px;
  }

  .publish-hero-title {
    font-size: 22px;
  }

  .link-card-row {
    grid-template-columns: 1fr;
  }

  .section-head--with-action {
    align-items: flex-start;
  }

  .publish-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .publish-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

.publish-image-viewer :deep(.vs-dialog__content) {
  background: transparent !important;
  box-shadow: none !important;
}

.publish-image-viewer__body {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 48px 24px 32px;
  background: rgba(15, 23, 42, 0.88);
  cursor: zoom-out;
}

.publish-image-viewer__close {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 22px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.publish-image-viewer__close:hover {
  background: rgba(255, 255, 255, 0.22);
}

.publish-image-viewer__img {
  max-width: min(92vw, 960px);
  max-height: 82vh;
  border-radius: 8px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  cursor: default;
}

.publish-image-viewer__tip {
  margin: 16px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}
</style>
