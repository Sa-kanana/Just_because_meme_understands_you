<template>
  <div class="publish-page">
    <el-card class="publish-card" shadow="never">
      <template #header>
        <div class="publish-header">
          <h2 class="publish-title">发布梗</h2>
        </div>
      </template>

      <el-form :model="form" label-position="top" class="publish-form">
        <el-form-item label="梗名称" required>
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="给这个梗起个名字" />
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

        <el-form-item label="封面图" required>
          <div class="cover-uploader">
            <div v-if="form.image" class="cover-preview">
              <el-image :src="form.image" fit="cover" class="cover-img" />
              <span class="cover-remove" @click="form.image = ''">×</span>
            </div>
            <label v-else class="cover-add">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                class="cover-file-input"
                @change="onCoverChange"
              />
              <span class="cover-add-inner">
                <span v-if="coverUploading">上传中…</span>
                <span v-else>＋ 点击上传封面</span>
              </span>
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

        <el-form-item label="相关资源（图片/视频/GIF）">
          <div class="resource-uploader">
            <div
              v-for="(url, idx) in form.resourceUrls"
              :key="`res-${idx}`"
              class="resource-item"
            >
              <el-image :src="url" fit="cover" class="resource-thumb" />
              <span class="resource-remove" @click="removeResource(idx)">×</span>
            </div>
            <label v-if="form.resourceUrls.length < 6" class="resource-add">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                class="resource-file-input"
                @change="onResourceChange"
              />
              <span class="resource-add-inner">
                <span v-if="resourceUploading">上传中…</span>
                <span v-else>＋ 添加资源</span>
              </span>
            </label>
          </div>
        </el-form-item>

        <div class="publish-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="handleSubmit"
          >
            发布
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  submitting.value = true
  try {
    const data = await publishMeme({
      name: form.name,
      introduction: form.introduction,
      image: form.image,
      tagIds: form.tagIds,
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
  max-width: 760px;
  margin: 24px auto;
  padding: 0 16px;
}

.publish-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.publish-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.publish-sub {
  font-size: 12px;
  color: #9ca3af;
}

.publish-form {
  margin-top: 8px;
}

.cover-uploader,
.resource-uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.cover-preview,
.cover-add,
.resource-item,
.resource-add {
  width: 180px;
  height: 120px;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
}

.cover-preview,
.resource-item {
  border: 1px solid #e5e7eb;
}

.cover-img,
.resource-thumb {
  width: 100%;
  height: 100%;
}

.cover-add,
.resource-add {
  border: 1px dashed #d1d5db;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #9ca3af;
  font-size: 13px;
  transition: border-color 0.2s, color 0.2s;
}

.cover-add:hover,
.resource-add:hover {
  border-color: #409eff;
  color: #409eff;
}

.resource-add {
  width: 120px;
  height: 120px;
}

.cover-add-inner,
.resource-add-inner,
.cover-file-input,
.resource-file-input {
  pointer-events: none;
}

.cover-file-input,
.resource-file-input {
  display: none;
}

.cover-remove,
.resource-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  border-bottom-left-radius: 6px;
}

.tag-select {
  width: 100%;
}

.publish-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}
</style>
