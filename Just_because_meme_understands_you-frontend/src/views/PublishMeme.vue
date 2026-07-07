<template>
  <div class="publish-page">
    <header class="publish-hero">
      <h1 class="publish-hero-title">发布梗</h1>
      <p class="publish-hero-sub">
        分享你知道的梗，填写基础信息并附上延伸阅读，帮助更多人快速 get 到点。
      </p>
    </header>

    <el-card class="publish-card" shadow="never">
      <el-form :model="form" label-position="top" class="publish-form" size="large">
        <!-- 基础信息 -->
        <section class="form-section">
          <div class="section-head">
            <span class="section-icon section-icon--primary">✦</span>
            <div>
              <h3 class="section-title">基础信息</h3>
              <p class="section-hint">名称与介绍会展示在梗详情页顶部</p>
            </div>
          </div>

          <el-form-item label="梗名称" required>
            <el-input
              v-model="form.name"
              maxlength="50"
              show-word-limit
              placeholder="给这个梗起个名字"
            />
          </el-form-item>

          <el-form-item label="梗介绍" required>
            <el-input
              v-model="form.introduction"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="介绍一下这个梗的来源、出处、怎么用…"
            />
          </el-form-item>
        </section>

        <!-- 封面与标签 -->
        <section class="form-section">
          <div class="section-head">
            <span class="section-icon section-icon--cover">🖼</span>
            <div>
              <h3 class="section-title">封面与标签</h3>
              <p class="section-hint">封面用于列表展示，标签帮助用户发现你的梗</p>
            </div>
          </div>

          <el-form-item label="封面图" required>
            <div class="cover-uploader">
              <div v-if="form.image" class="upload-tile upload-tile--cover upload-tile--filled">
                <el-image :src="form.image" fit="cover" class="upload-preview" />
                <button type="button" class="upload-remove" aria-label="移除封面" @click="form.image = ''">
                  <el-icon><Close /></el-icon>
                </button>
                <span class="upload-badge">封面</span>
              </div>
              <label v-else class="upload-tile upload-tile--cover upload-tile--empty">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  class="upload-input"
                  @change="onCoverChange"
                />
                <el-icon class="upload-icon" :size="28"><Picture /></el-icon>
                <span class="upload-label">{{ coverUploading ? '上传中…' : '点击上传封面' }}</span>
                <span class="upload-tip">JPG / PNG / WebP / GIF，≤ 10MB</span>
              </label>
            </div>
          </el-form-item>

          <el-form-item label="标签" required>
            <el-select
              v-model="form.tagIds"
              multiple
              filterable
              placeholder="选择至少一个标签"
              class="tag-select"
            >
              <el-option
                v-for="tag in tags"
                :key="tag.id"
                :label="tag.name"
                :value="tag.id"
              />
            </el-select>
          </el-form-item>
        </section>

        <!-- 相关链接 -->
        <section class="form-section">
          <div class="section-head section-head--with-action">
            <div class="section-head-main">
              <span class="section-icon section-icon--link">🔗</span>
              <div>
                <h3 class="section-title">相关链接</h3>
                <p class="section-hint">B 站、百科、原文报道等外链，最多 10 条</p>
              </div>
            </div>
            <el-button
              v-if="form.resources.length < 10"
              class="section-add-btn"
              round
              @click="addLink"
            >
              <el-icon><Plus /></el-icon>
              添加链接
            </el-button>
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
                  <el-select v-model="item.type" class="link-type-select" placeholder="类型">
                    <el-option label="通用链接" value="link">
                      <span class="link-type-option"><el-icon><Link /></el-icon> 通用链接</span>
                    </el-option>
                    <el-option label="文章" value="article">
                      <span class="link-type-option"><el-icon><Document /></el-icon> 文章</span>
                    </el-option>
                    <el-option label="视频" value="video">
                      <span class="link-type-option"><el-icon><VideoCamera /></el-icon> 视频</span>
                    </el-option>
                  </el-select>
                  <el-input
                    v-model="item.title"
                    maxlength="128"
                    show-word-limit
                    placeholder="给链接起个标题，如「原视频出处」"
                    class="link-title-input"
                  />
                </div>
                <el-input
                  v-model="item.url"
                  maxlength="500"
                  placeholder="https://..."
                  class="link-url-input"
                >
                  <template #prefix>
                    <el-icon class="link-url-prefix"><Link /></el-icon>
                  </template>
                </el-input>
              </div>
              <button
                type="button"
                class="link-card-remove"
                aria-label="删除链接"
                @click="removeLink(idx)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </div>

          <div v-else class="link-empty" @click="addLink">
            <el-icon class="link-empty-icon" :size="32"><Link /></el-icon>
            <p class="link-empty-title">还没有添加相关链接</p>
            <p class="link-empty-desc">点击此处或上方按钮，添加 B 站、百科等延伸阅读</p>
          </div>
        </section>

        <!-- 相关资源 -->
        <section class="form-section form-section--last">
          <div class="section-head">
            <span class="section-icon section-icon--media">📎</span>
            <div>
              <h3 class="section-title">相关资源</h3>
              <p class="section-hint">补充图片 / GIF，最多 6 张，与外链分开存储</p>
            </div>
          </div>

          <div class="resource-uploader">
            <div
              v-for="(url, idx) in form.resourceUrls"
              :key="`res-${idx}`"
              class="upload-tile upload-tile--resource upload-tile--filled"
            >
              <el-image :src="url" fit="cover" class="upload-preview" />
              <button type="button" class="upload-remove" aria-label="移除资源" @click="removeResource(idx)">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <label v-if="form.resourceUrls.length < 6" class="upload-tile upload-tile--resource upload-tile--empty">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                class="upload-input"
                @change="onResourceChange"
              />
              <el-icon class="upload-icon" :size="24"><Plus /></el-icon>
              <span class="upload-label">{{ resourceUploading ? '上传中…' : '添加资源' }}</span>
            </label>
          </div>
        </section>

        <footer class="publish-footer">
          <p class="publish-footer-hint">提交后将进入审核，通过后会在首页展示</p>
          <div class="publish-actions">
            <el-button size="large" round @click="goBack">取消</el-button>
            <el-button
              type="primary"
              size="large"
              round
              :loading="submitting"
              :disabled="!canSubmit"
              class="publish-submit-btn"
              @click="handleSubmit"
            >
              发布梗
            </el-button>
          </div>
        </footer>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Close,
  Delete,
  Document,
  Link,
  Picture,
  Plus,
  VideoCamera,
} from '@element-plus/icons-vue'
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
    form.introduction.trim() &&
    form.image &&
    form.tagIds.length > 0
)

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    router.replace({ name: 'login', query: { redirect: '/publish' } })
    return
  }
  try {
    tags.value = await getMemeTags()
  } catch (e) {
    ElMessage.error(e.message || '加载标签失败')
  }
})

async function onCoverChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('封面不能超过 10MB')
    return
  }
  coverUploading.value = true
  try {
    form.image = await uploadToOss(file, 'meme')
  } catch (e) {
    ElMessage.error(e.message || '封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

function addLink() {
  if (form.resources.length >= 10) {
    ElMessage.warning('最多 10 条相关链接')
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
      ElMessage.warning(`第 ${i + 1} 条链接请填写标题`)
      return false
    }
    if (!url) {
      ElMessage.warning(`第 ${i + 1} 条链接请填写 URL`)
      return false
    }
    if (!/^https?:\/\//i.test(url)) {
      ElMessage.warning(`第 ${i + 1} 条链接须以 http:// 或 https:// 开头`)
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
    ElMessage.warning('最多 6 个资源')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单个资源不能超过 10MB')
    return
  }
  resourceUploading.value = true
  try {
    const url = await uploadToOss(file, 'meme')
    form.resourceUrls.push(url)
  } catch (e) {
    ElMessage.error(e.message || '资源上传失败')
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
    ElMessage.success(`发布成功，当前状态：${data.statusDesc || '审核中'}`)
    router.push('/')
  } catch (e) {
    if (isAuthErrorHandled(e)) return
    ElMessage.error(e.message || '发布失败')
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
  padding: 28px 20px 48px;
}

.publish-hero {
  margin-bottom: 20px;
  text-align: center;
}

.publish-hero-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--meme-text, #111827);
}

.publish-hero-sub {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #6b7280;
  max-width: 520px;
  margin-inline: auto;
}

.publish-card {
  border-radius: var(--meme-radius-xl, 20px);
  border: 1px solid var(--meme-border, #e5e7eb);
  box-shadow:
    0 4px 16px rgba(49, 138, 239, 0.06),
    0 8px 32px rgba(15, 23, 42, 0.06);
}

.publish-card :deep(.el-card__body) {
  padding: 8px 28px 24px;
}

.publish-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #374151;
  padding-bottom: 6px;
}

.publish-form :deep(.el-input__wrapper),
.publish-form :deep(.el-textarea__inner),
.publish-form :deep(.el-select__wrapper) {
  border-radius: 10px;
}

.form-section {
  padding: 20px 0;
  border-bottom: 1px solid #f0f2f5;
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
  font-size: 16px;
  background: #f3f4f6;
}

.section-icon--primary {
  background: rgba(49, 138, 239, 0.12);
  color: var(--meme-primary, #318aef);
  font-size: 14px;
}

.section-icon--cover,
.section-icon--link,
.section-icon--media {
  background: #f9fafb;
}

.section-title {
  margin: 0 0 2px;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.section-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}

.section-add-btn {
  flex-shrink: 0;
  border-color: var(--meme-primary, #318aef);
  color: var(--meme-primary, #318aef);
  background: rgba(49, 138, 239, 0.06);
}

.section-add-btn:hover {
  background: rgba(49, 138, 239, 0.12);
  border-color: var(--meme-primary-dark, #2872d4);
  color: var(--meme-primary-dark, #2872d4);
}

/* 上传区域 */
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
  border: 1.5px dashed #d1d5db;
  background: linear-gradient(180deg, #fafbfc 0%, #f5f7fa 100%);
  cursor: pointer;
  color: #9ca3af;
}

.upload-tile--empty:hover {
  border-color: var(--meme-primary, #318aef);
  color: var(--meme-primary, #318aef);
  box-shadow: 0 4px 12px rgba(49, 138, 239, 0.12);
  transform: translateY(-1px);
}

.upload-tile--filled {
  border: 1px solid #e5e7eb;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.upload-preview {
  width: 100%;
  height: 100%;
  display: block;
}

.upload-icon {
  opacity: 0.85;
}

.upload-label {
  font-size: 13px;
  font-weight: 500;
}

.upload-tip {
  font-size: 11px;
  color: #b0b8c4;
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
  font-weight: 500;
  color: #fff;
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
  color: #fff;
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

/* 链接列表 */
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
  border: 1px solid #e8ecf1;
  background: linear-gradient(180deg, #ffffff 0%, #fafbfc 100%);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.link-card:hover {
  border-color: rgba(49, 138, 239, 0.35);
  box-shadow: 0 4px 14px rgba(49, 138, 239, 0.08);
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
  color: var(--meme-primary, #318aef);
  background: rgba(49, 138, 239, 0.1);
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

.link-type-select {
  width: 100%;
}

.link-type-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.link-url-prefix {
  color: #9ca3af;
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
  color: #9ca3af;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.link-card-remove:hover {
  background: #fef2f2;
  color: #ef4444;
}

.link-empty {
  padding: 28px 20px;
  border-radius: 12px;
  border: 1.5px dashed #e0e4ea;
  background: #fafbfc;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.link-empty:hover {
  border-color: rgba(49, 138, 239, 0.4);
  background: rgba(49, 138, 239, 0.04);
}

.link-empty-icon {
  color: #c4cad4;
  margin-bottom: 8px;
}

.link-empty-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 500;
  color: #6b7280;
}

.link-empty-desc {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

/* 底部操作 */
.publish-footer {
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.publish-footer-hint {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

.publish-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.publish-submit-btn {
  min-width: 120px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(49, 138, 239, 0.35);
}

@media (max-width: 640px) {
  .publish-page {
    padding: 16px 12px 32px;
  }

  .publish-card :deep(.el-card__body) {
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
</style>
