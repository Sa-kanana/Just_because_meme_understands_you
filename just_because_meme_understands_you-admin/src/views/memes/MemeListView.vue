<template>
  <div>
    <div class="toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索名称/介绍"
        allow-clear
        style="width: 220px"
        @press-enter="reload"
      />
      <a-select
        v-model="status"
        placeholder="状态"
        allow-clear
        style="width: 160px"
        :options="statusOptions"
      />
      <a-button type="primary" @click="reload">查询</a-button>
      <a-button @click="openCreateModal">新增梗</a-button>
    </div>
    <div v-if="selectedKeys.length" class="batch-toolbar">
      <span class="batch-selection">已选 {{ selectedKeys.length }} 条</span>
      <a-popconfirm :content="batchConfirmText('通过', eligibleRecords('approve').length)" @ok="runMemeBatch('approve')">
        <a-button size="small" type="primary" :disabled="!eligibleRecords('approve').length || batching">
          批量通过 {{ eligibleRecords('approve').length }}
        </a-button>
      </a-popconfirm>
      <a-popconfirm :content="batchConfirmText('拒绝或驳回申诉', eligibleRecords('reject').length)" @ok="runMemeBatch('reject')">
        <a-button size="small" status="danger" :disabled="!eligibleRecords('reject').length || batching">
          批量拒绝 {{ eligibleRecords('reject').length }}
        </a-button>
      </a-popconfirm>
      <a-popconfirm :content="batchConfirmText('风控下架', eligibleRecords('offline').length)" @ok="runMemeBatch('offline')">
        <a-button size="small" status="warning" :disabled="!eligibleRecords('offline').length || batching">
          批量下架 {{ eligibleRecords('offline').length }}
        </a-button>
      </a-popconfirm>
      <a-popconfirm :content="batchConfirmText('强制上架', eligibleRecords('online').length)" @ok="runMemeBatch('online')">
        <a-button size="small" :disabled="!eligibleRecords('online').length || batching">
          批量上架 {{ eligibleRecords('online').length }}
        </a-button>
      </a-popconfirm>
      <a-popconfirm
        :content="batchConfirmText('永久删除', eligibleRecords('delete').length)"
        @ok="runMemeBatch('delete')"
      >
        <a-button size="small" status="danger" :disabled="!eligibleRecords('delete').length || batching">
          批量删除 {{ eligibleRecords('delete').length }}
        </a-button>
      </a-popconfirm>
      <a-button size="small" type="text" :disabled="batching" @click="selectedKeys = []">清除选择</a-button>
    </div>
    <a-table
      row-key="id"
      v-model:selectedKeys="selectedKeys"
      :row-selection="rowSelection"
      :loading="loading"
      :columns="columns"
      :data="list"
      :pagination="pagination"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
    >
      <template #image="{ record }">
        <a-image v-if="record.image" :src="record.image" width="48" height="48" />
        <span v-else class="muted">无</span>
      </template>
      <template #status="{ record }">
        <div>
          <div>{{ record.statusDesc }}</div>
          <div v-if="record.offlineReason" class="muted">{{ record.offlineReason }}</div>
          <div v-if="record.appealRejectCount" class="muted">驳回 {{ record.appealRejectCount }} 次</div>
        </div>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button type="text" size="mini" @click="openDetail(record)">查看详情</a-button>
          <a-button
            v-if="record.status === 2 || record.status === 6"
            type="text"
            size="mini"
            status="success"
            @click="doApprove(record)"
          >
            通过
          </a-button>
          <a-button
            v-if="record.status === 2 || record.status === 6"
            type="text"
            size="mini"
            status="danger"
            @click="doReject(record)"
          >
            {{ record.status === 6 ? '驳回申诉' : '拒绝' }}
          </a-button>
          <a-button
            v-if="record.status === 1 || record.status === 2 || record.status === 3"
            type="text"
            size="mini"
            status="warning"
            @click="doOffline(record)"
          >
            风控下架
          </a-button>
          <a-button
            v-if="record.status === 3 || record.status === 5"
            type="text"
            size="mini"
            @click="doOnline(record)"
          >
            强制上架
          </a-button>
          <a-popconfirm
            content="将永久删除该梗，删除后无法恢复。确认继续？"
            @ok="doDelete(record)"
          >
            <a-button type="text" size="mini" status="danger" :disabled="batching">
              删除
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-drawer
      v-model:visible="detailVisible"
      class="meme-detail-drawer"
      placement="right"
      width="min(780px, 100vw)"
      :footer="false"
      unmount-on-close
    >
      <template #title>
        <div class="detail-drawer-heading">
          <span class="detail-drawer-eyebrow">MEME ARCHIVE</span>
          <div class="detail-drawer-title-row">
            <strong>{{ detail ? detail.name : '梗详情' }}</strong>
            <a-tag v-if="detail" :color="memeStatusColors[detail.status] || 'gray'">
              {{ detail.statusDesc || '未知状态' }}
            </a-tag>
          </div>
        </div>
      </template>

      <div v-if="detailLoading" class="detail-state">
        <a-spin :size="32" />
        <span>正在整理梗的详细信息…</span>
      </div>
      <div v-else-if="detailError" class="detail-state detail-state--error">
        <a-empty :description="detailError" />
        <a-button type="primary" @click="reloadDetail">重新加载</a-button>
      </div>
      <div v-else-if="detail" class="meme-detail-content">
        <section class="detail-hero">
          <div class="detail-cover-frame">
            <a-image
              v-if="detail.image"
              class="detail-cover-image"
              :src="detail.image"
              fit="cover"
              alt="梗图封面"
            />
            <div v-else class="detail-cover-placeholder"><icon-image /><span>暂无封面</span></div>
            <span class="detail-cover-label"><icon-image /> 内容封面</span>
          </div>
          <div class="detail-summary">
            <span class="detail-summary-kicker">梗图档案 · #{{ detail.id }}</span>
            <h2>{{ detail.name || '未命名梗' }}</h2>
            <div class="detail-author">
              <a-avatar :size="34" class="detail-author-avatar">
                {{ (detail.authorNickname || '匿').slice(0, 1) }}
              </a-avatar>
              <div class="detail-author-copy">
                <strong>{{ detail.authorNickname || '匿名用户' }}</strong>
                <span>用户 ID {{ detail.userId || '—' }}</span>
              </div>
            </div>
            <div class="detail-dates">
              <span><icon-calendar /> 发布 {{ detail.releaseTime || '—' }}</span>
              <span><icon-calendar /> 更新 {{ detail.updateTime || '—' }}</span>
            </div>
          </div>
        </section>

        <section class="detail-metrics" aria-label="梗图数据">
          <div class="detail-metric">
            <span class="detail-metric-icon"><icon-eye /></span>
            <strong>{{ detail.pageViews || 0 }}</strong>
            <span>浏览</span>
          </div>
          <div class="detail-metric">
            <span class="detail-metric-icon"><icon-heart /></span>
            <strong>{{ detail.likes || 0 }}</strong>
            <span>点赞</span>
          </div>
          <div class="detail-metric">
            <span class="detail-metric-icon"><icon-message /></span>
            <strong>{{ detail.comments || 0 }}</strong>
            <span>评论</span>
          </div>
        </section>

        <section class="detail-panel">
          <div class="detail-section-heading">
            <div><span class="detail-section-kicker">INTRODUCTION</span><h3>梗的介绍</h3></div>
          </div>
          <p v-if="detail.introduction" class="detail-introduction">{{ detail.introduction }}</p>
          <p v-else class="detail-empty-note">暂未填写介绍</p>
        </section>

        <section class="detail-panel">
          <div class="detail-section-heading">
            <div><span class="detail-section-kicker">TAGS</span><h3>关联标签</h3></div>
            <span class="detail-section-count">{{ (detail.tags || []).length }} 个</span>
          </div>
          <a-space v-if="detail.tags && detail.tags.length" wrap class="detail-tag-list">
            <a-tag v-for="tag in detail.tags" :key="tag.id" class="detail-tag">
              {{ tag.isPrimary ? '主 · ' : '' }}{{ tagCategoryNames[tag.category] || '' }} {{ tag.name }}
            </a-tag>
          </a-space>
          <p v-else class="detail-empty-note">暂无关联标签</p>
        </section>

        <section class="detail-panel">
          <div class="detail-section-heading">
            <div><span class="detail-section-kicker">ATTACHMENTS</span><h3>关联资源</h3></div>
            <span class="detail-section-count">{{ (detail.resources || []).length }} 项</span>
          </div>
          <div v-if="detail.resources && detail.resources.length" class="detail-resource-grid">
            <article v-for="resource in detail.resources" :key="resource.id" class="detail-resource-card">
              <div class="detail-resource-preview">
                <a-image
                  v-if="resource.type === 'image' || resource.type === 'media'"
                  :src="resource.url"
                  width="100%"
                  height="156"
                  fit="cover"
                  alt="关联图片资源"
                />
                <video
                  v-else-if="resource.type === 'video' && resource.url"
                  :src="resource.url"
                  controls
                  preload="metadata"
                  class="detail-resource-video"
                />
                <div v-else class="detail-resource-placeholder">
                  <component :is="resourceTypeMeta[resource.type] ? resourceTypeMeta[resource.type].icon : IconLink" />
                  <span>{{ resourceTypeMeta[resource.type] ? resourceTypeMeta[resource.type].label : '关联资源' }}</span>
                </div>
              </div>
              <div class="detail-resource-info">
                <div class="detail-resource-title-row">
                  <strong>{{ resource.title || '未命名资源' }}</strong>
                  <a-tag size="small" :color="resourceStatusMeta[resource.status] ? resourceStatusMeta[resource.status].color : 'gray'">
                    {{ resourceStatusMeta[resource.status] ? resourceStatusMeta[resource.status].label : '未知状态' }}
                  </a-tag>
                </div>
                <div class="detail-resource-bottom">
                  <span>{{ resourceTypeMeta[resource.type] ? resourceTypeMeta[resource.type].label : '关联资源' }}</span>
                  <a
                    v-if="resource.url"
                    :href="resource.url"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="detail-resource-link"
                  >查看原资源 <icon-link /></a>
                  <span v-else class="detail-empty-note">资源地址为空</span>
                </div>
              </div>
            </article>
          </div>
          <p v-else class="detail-empty-note">暂无关联资源</p>
        </section>

        <section class="detail-panel detail-moderation-panel">
          <div class="detail-section-heading">
            <div><span class="detail-section-kicker">MODERATION</span><h3>审核与风控</h3></div>
            <a-tag :color="memeStatusColors[detail.status] || 'gray'">{{ detail.statusDesc || '未知状态' }}</a-tag>
          </div>
          <div class="detail-moderation-grid">
            <div>
              <span>下架 / 风控原因</span>
              <strong>{{ detail.offlineReason || '暂无记录' }}</strong>
            </div>
            <div>
              <span>申诉驳回次数</span>
              <strong>{{ detail.appealRejectCount || 0 }} 次</strong>
            </div>
          </div>
        </section>
      </div>
    </a-drawer>

    <a-modal
      v-model:visible="createModalVisible"
      title="新增梗"
      :ok-loading="saving"
      :ok-button-props="{ disabled: coverUploading }"
      :cancel-button-props="{ disabled: saving || coverUploading }"
      :mask-closable="false"
      @ok="submitCreate"
      @cancel="closeCreateModal"
    >
      <a-form :model="createForm" layout="vertical" class="create-meme-form">
        <a-form-item label="梗名称" required>
          <a-input
            v-model="createForm.name"
            maxlength="50"
            show-word-limit
            placeholder="输入梗名称"
          />
        </a-form-item>
        <a-form-item label="梗介绍">
          <a-textarea
            v-model="createForm.introduction"
            :max-length="255"
            show-word-limit
            :auto-size="{ minRows: 3, maxRows: 6 }"
            placeholder="介绍梗的来源、含义或使用场景"
          />
        </a-form-item>
        <a-form-item label="封面图" required>
          <div class="create-meme-cover">
            <img
              v-if="createForm.image"
              class="create-meme-preview"
              :src="createForm.image"
              alt="梗封面预览"
            />
            <div class="create-meme-cover-actions">
              <input
                ref="coverFileInput"
                class="create-meme-file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                :disabled="coverUploading || saving"
                @change="onCoverFileChange"
              />
              <a-button :loading="coverUploading" :disabled="saving" @click="chooseCoverFile">
                {{ createForm.image ? '更换封面' : '上传封面到 OSS' }}
              </a-button>
              <a-button
                v-if="createForm.image"
                status="danger"
                :disabled="coverUploading || saving"
                @click="createForm.image = ''"
              >
                移除
              </a-button>
              <span class="create-meme-upload-tip">JPG、PNG、WebP 或 GIF，最大 10MB</span>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="主分类标签" required>
          <a-select
            v-model="createForm.primaryTagId"
            allow-search
            allow-clear
            :loading="tagsLoading"
            :options="tagOptions"
            placeholder="选择最能代表这个梗的标签"
          />
        </a-form-item>
        <a-form-item label="补充标签" required>
          <a-select
            v-model="createForm.tagIds"
            multiple
            allow-search
            allow-clear
            :loading="tagsLoading"
            :options="secondaryTagOptions"
            placeholder="选择 3～7 个标签"
          />
          <p class="create-meme-review-hint">已选 {{ createForm.tagIds.length }} 个；主标签不会计入数量。</p>
        </a-form-item>
        <p class="create-meme-review-hint">提交后进入审核中，审核通过后会展示在网站上。</p>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { IconFile, IconFileVideo, IconImage, IconLink } from '@arco-design/web-vue/es/icon'
import {
  approveAdminMeme,
  createAdminMeme,
  deleteAdminMeme,
  fetchAdminMemeDetail,
  fetchAdminMemes,
  offlineAdminMeme,
  onlineAdminMeme,
  rejectAdminMeme,
} from '@/api/adminMeme'
import { uploadToOss } from '@/api/oss'
import { fetchAdminTags } from '@/api/adminTag'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toastError, toastSuccess } from '@/utils/uiFeedback'
import { runBatchOperations } from '@/utils/batchOperations.mjs'

const route = useRoute()
const loading = ref(false)
const list = ref([])
const selectedKeys = ref([])
const batching = ref(false)
const createModalVisible = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const coverFileInput = ref(null)
const tagsLoading = ref(false)
const tagsLoaded = ref(false)
const tagOptions = ref([])
const tagCategoryNames = {
  SOURCE: '来源', // 中文注释：标签来源分类显示名称。
  FORMAT: '形式', // 中文注释：标签形式分类显示名称。
  EMOTION: '情绪', // 中文注释：标签情绪分类显示名称。
  SCENARIO: '场景', // 中文注释：标签场景分类显示名称。
  CONTENT: '内容', // 中文注释：标签内容分类显示名称。
  ERA: '时代', // 中文注释：标签时代分类显示名称。
  PROPAGATION: '传播特征', // 中文注释：标签传播分类显示名称。
  TONE: '使用语气', // 中文注释：标签语气分类显示名称。
  UNCATEGORIZED: '待归类', // 中文注释：历史标签分类显示名称。
}
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const detailMemeId = ref('')
const detailRequestVersion = ref(0)
const createForm = reactive({
  name: '', // 梗名称，提交前去除首尾空格。
  introduction: '', // 梗介绍，最多 255 个字符。
  image: '', // OSS 临时封面地址，提交后由后端转入正式目录。
  primaryTagId: null, // 主分类标签 ID。
  tagIds: [], // 3～7 个普通标签 ID。
})
const secondaryTagOptions = computed(() => tagOptions.value.filter((option) => option.value !== Number(createForm.primaryTagId)))
const keyword = ref('')
const status = ref(undefined)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
})

const statusOptions = [
  { label: '正常', value: 1 },
  { label: '审核中', value: 2 },
  { label: '主动下架', value: 3 },
  { label: '风控锁定', value: 5 },
  { label: '恢复审核中', value: 6 },
]

const memeStatusColors = { 1: 'green', 2: 'orange', 3: 'gray', 5: 'red', 6: 'purple' }
const resourceStatusMeta = {
  1: { label: '可用', color: 'green' },
  0: { label: '已下架', color: 'orange' },
  2: { label: '已归档', color: 'gray' },
}
const resourceTypeMeta = {
  link: { label: '外部链接', icon: IconLink },
  media: { label: '媒体', icon: IconImage },
  video: { label: '视频', icon: IconFileVideo },
  article: { label: '文章', icon: IconFile },
  image: { label: '图片', icon: IconImage },
}

const rowSelection = { type: 'checkbox', showCheckedAll: true, onlyCurrent: true }
const selectedRecords = computed(() => {
  const selectedIds = new Set(selectedKeys.value.map(String))
  return list.value.filter((record) => selectedIds.has(String(record.id)))
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 100 },
  { title: '封面', slotName: 'image', width: 72 },
  { title: '名称', dataIndex: 'name', ellipsis: true, tooltip: true },
  { title: '作者', dataIndex: 'authorNickname', width: 120 },
  { title: '状态', slotName: 'status', width: 140 },
  { title: '浏览', dataIndex: 'pageViews', width: 80 },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
  { title: '操作', slotName: 'actions', width: 330 },
]

function applyRouteQuery() {
  const q = route.query || {}
  if (q.keyword != null && String(q.keyword).trim()) {
    keyword.value = String(q.keyword).trim()
  }
  if (q.status != null && String(q.status).trim() !== '') {
    const n = Number(q.status)
    status.value = Number.isFinite(n) ? n : undefined
  }
}

async function load() {
  selectedKeys.value = []
  loading.value = true
  try {
    const data = await fetchAdminMemes({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: keyword.value,
      status: status.value,
    })
    list.value = data.list
    pagination.total = data.total
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(record) {
  detailMemeId.value = String(record.id)
  detailVisible.value = true
  await loadDetail(detailMemeId.value)
}

async function reloadDetail() {
  if (!detailMemeId.value) return
  await loadDetail(detailMemeId.value)
}

async function loadDetail(memeId) {
  const requestVersion = ++detailRequestVersion.value
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const result = await fetchAdminMemeDetail(memeId)
    if (requestVersion === detailRequestVersion.value) detail.value = result
  } catch (error) {
    if (requestVersion === detailRequestVersion.value) {
      detailError.value = error.message || '梗详情加载失败，请重试'
    }
  } finally {
    if (requestVersion === detailRequestVersion.value) detailLoading.value = false
  }
}

function resetCreateForm() {
  createForm.name = ''
  createForm.introduction = ''
  createForm.image = ''
  createForm.primaryTagId = null
  createForm.tagIds = []
}

async function loadTagOptions() {
  if (tagsLoaded.value || tagsLoading.value) return
  tagsLoading.value = true
  try {
    const allTags = []
    let page = 1
    let hasMore = true
    while (hasMore) {
      const result = await fetchAdminTags({ page, size: 50 })
      allTags.push(...result.list)
      hasMore = result.hasMore
      page += 1
    }
    tagOptions.value = allTags.map((tag) => ({
      label: `${tagCategoryNames[tag.category] || tagCategoryNames.UNCATEGORIZED} · ${tag.name}`,
      value: Number(tag.id),
    }))
    tagsLoaded.value = true
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '获取标签失败')
  } finally {
    tagsLoading.value = false
  }
}

function openCreateModal() {
  resetCreateForm()
  createModalVisible.value = true
  loadTagOptions()
}

function closeCreateModal() {
  if (saving.value || coverUploading.value) return
  createModalVisible.value = false
  resetCreateForm()
}

function chooseCoverFile() {
  if (coverUploading.value || saving.value) return
  coverFileInput.value?.click()
}

async function onCoverFileChange(event) {
  const input = event.target
  const file = input.files && input.files[0]
  if (!file) return

  coverUploading.value = true
  try {
    createForm.image = await uploadToOss(file, 'meme')
    toastSuccess('封面已上传到 OSS')
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '封面上传失败')
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

async function submitCreate() {
  if (saving.value || coverUploading.value) return
  if (!createForm.name.trim()) {
    toastError('请输入梗名称')
    return
  }
  if (!createForm.image) {
    toastError('请先上传封面图')
    return
  }
  if (!createForm.primaryTagId) {
    toastError('请选择主分类标签')
    return
  }
  if (createForm.tagIds.length < 3 || createForm.tagIds.length > 7) {
    toastError('请选择 3～7 个补充标签')
    return
  }

  saving.value = true
  try {
    const created = await createAdminMeme({
      name: createForm.name.trim(),
      introduction: createForm.introduction.trim(),
      image: createForm.image,
      primaryTagId: Number(createForm.primaryTagId),
      tagIds: [...new Set(createForm.tagIds.map(Number).filter((id) => Number.isInteger(id) && id > 0))],
    })
    toastSuccess(`梗已提交审核${created && created.memeId ? `，ID：${created.memeId}` : ''}`)
    createModalVisible.value = false
    resetCreateForm()
    status.value = 2
    pagination.current = 1
    await load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '新增梗失败')
  } finally {
    saving.value = false
  }
}

function reload() {
  pagination.current = 1
  load()
}

function onPageChange(page) {
  pagination.current = page
  load()
}

function onPageSizeChange(size) {
  pagination.pageSize = size
  pagination.current = 1
  load()
}

async function doApprove(record) {
  try {
    await approveAdminMeme(record.id)
    toastSuccess('已通过')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doReject(record) {
  try {
    await rejectAdminMeme(record.id)
    toastSuccess(record.status === 6 ? '已驳回申诉' : '已拒绝')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doOffline(record) {
  try {
    await offlineAdminMeme(record.id)
    toastSuccess('已风控下架锁定')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doOnline(record) {
  try {
    await onlineAdminMeme(record.id)
    toastSuccess('已强制上架')
    load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '操作失败')
  }
}

async function doDelete(record) {
  try {
    await deleteAdminMeme(record.id)
    toastSuccess('梗已永久删除')
    await load()
  } catch (e) {
    if (!isAuthErrorHandled(e)) toastError(e.message || '删除失败')
  }
}

function eligibleRecords(action) {
  if (action === 'delete') return selectedRecords.value
  const allowedStatuses = {
    approve: [2, 6],
    reject: [2, 6],
    offline: [1, 2, 3],
    online: [3, 5],
  }
  return selectedRecords.value.filter((record) => allowedStatuses[action].includes(Number(record.status)))
}

function batchConfirmText(actionName, count) {
  if (actionName === '永久删除') {
    return `将永久删除选中的 ${count} 条梗，删除后无法恢复。确认继续？`
  }
  return `将对 ${count} 条符合状态的梗执行${actionName}，其他选中项会跳过。确认继续？`
}

function reportBatchResult(actionName, result) {
  if (result.failed.length === 0) {
    toastSuccess(`${actionName} ${result.succeeded.length} 条`)
    return
  }
  const reason = result.failed[0].error && result.failed[0].error.message
  const message = `${actionName}完成：成功 ${result.succeeded.length} 条，失败 ${result.failed.length} 条${reason ? `（${reason}）` : ''}`
  if (!isAuthErrorHandled(result.failed[0].error)) toastError(message)
}

async function runMemeBatch(action) {
  if (batching.value) return
  const targets = eligibleRecords(action)
  if (!targets.length) return
  const operations = {
    approve: { run: (record) => approveAdminMeme(record.id), label: '批量通过' },
    reject: { run: (record) => rejectAdminMeme(record.id), label: '批量拒绝/驳回申诉' },
    offline: { run: (record) => offlineAdminMeme(record.id), label: '批量风控下架' },
    online: { run: (record) => onlineAdminMeme(record.id), label: '批量强制上架' },
    delete: { run: (record) => deleteAdminMeme(record.id), label: '批量永久删除' },
  }
  batching.value = true
  try {
    const result = await runBatchOperations(targets, operations[action].run)
    reportBatchResult(operations[action].label, result)
    await load()
  } finally {
    batching.value = false
  }
}

watch(
  () => route.query,
  () => {
    applyRouteQuery()
    reload()
  }
)

onMounted(() => {
  applyRouteQuery()
  load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.batch-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}
.batch-selection {
  margin-right: 4px;
  color: var(--color-text-2);
  font-size: 13px;
}
.muted {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-3);
}
.create-meme-form {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.create-meme-cover {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.create-meme-preview {
  width: 128px;
  height: 128px;
  flex: 0 0 128px;
  object-fit: cover;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}
.create-meme-cover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.create-meme-file-input {
  display: none;
}
.create-meme-upload-tip,
.create-meme-review-hint {
  color: var(--color-text-3);
  font-size: 12px;
}
.create-meme-review-hint {
  margin: 0;
}
.meme-detail-drawer :deep(.arco-drawer-header) {
  padding: 18px 24px;
  border-bottom: 1px solid #e8edf5;
  background: #fff;
}
.meme-detail-drawer :deep(.arco-drawer-body) {
  padding: 0;
  background: #f5f7fb;
}
.detail-drawer-heading {
  display: grid;
  gap: 4px;
}
.detail-drawer-eyebrow,
.detail-summary-kicker,
.detail-section-kicker {
  color: #8795aa;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.detail-drawer-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.detail-drawer-title-row strong {
  overflow: hidden;
  color: #263650;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-state {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #74839a;
}
.detail-state--error {
  padding: 24px;
}
.meme-detail-content {
  display: grid;
  gap: 14px;
  padding: 20px 24px 28px;
}
.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 20px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid #e6ebf3;
  border-radius: 16px;
  background: linear-gradient(135deg, #fff 0%, #f9fbff 100%);
  box-shadow: 0 8px 24px rgb(37 59 94 / 5%);
}
.detail-cover-frame {
  position: relative;
  min-height: 210px;
  overflow: hidden;
  border-radius: 12px;
  background: linear-gradient(145deg, #e6efff, #eef5f8 60%, #d9eced);
}
.detail-cover-frame :deep(.arco-image) {
  display: block;
  width: 100%;
  height: 100%;
}
.detail-cover-frame :deep(.arco-image-img) {
  width: 100%;
  height: 100%;
  min-height: 210px;
  object-fit: cover;
}
.detail-cover-placeholder {
  display: flex;
  min-height: 210px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #8a9cb5;
  font-size: 13px;
}
.detail-cover-placeholder :deep(.arco-icon) {
  font-size: 28px;
}
.detail-cover-label {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border: 1px solid rgb(255 255 255 / 25%);
  border-radius: 999px;
  background: rgb(19 34 55 / 68%);
  color: #fff;
  font-size: 11px;
  backdrop-filter: blur(8px);
}
.detail-summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: 8px 4px;
}
.detail-summary-kicker {
  color: #3975d8;
  letter-spacing: 0.03em;
}
.detail-summary h2 {
  margin: 9px 0 18px;
  color: #24334b;
  font-size: 23px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.detail-author {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-author-avatar {
  flex: 0 0 auto;
  background: linear-gradient(135deg, #4b83ef, #285fce);
  color: #fff;
  font-weight: 700;
}
.detail-author-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}
.detail-author-copy strong {
  overflow: hidden;
  color: #34445e;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-author-copy span,
.detail-dates {
  color: #8996a9;
  font-size: 11px;
}
.detail-dates {
  display: grid;
  gap: 7px;
  margin-top: 18px;
}
.detail-dates span {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  overflow-wrap: anywhere;
}
.detail-dates :deep(.arco-icon) {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #7d94b6;
}
.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.detail-metric {
  display: grid;
  justify-items: center;
  gap: 4px;
  padding: 14px 10px 12px;
  border: 1px solid #e7ecf4;
  border-radius: 13px;
  background: #fff;
}
.detail-metric-icon {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 10px;
  background: #eef4ff;
  color: #3975d8;
  font-size: 15px;
}
.detail-metric:nth-child(2) .detail-metric-icon {
  background: #fff0f0;
  color: #df6a72;
}
.detail-metric:nth-child(3) .detail-metric-icon {
  background: #edf8f5;
  color: #3b9a83;
}
.detail-metric strong {
  color: #263650;
  font-size: 19px;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.detail-metric > span:last-child {
  color: #8794a8;
  font-size: 11px;
}
.detail-panel {
  padding: 17px 18px;
  border: 1px solid #e7ecf4;
  border-radius: 14px;
  background: #fff;
}
.detail-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.detail-section-heading h3 {
  margin: 3px 0 0;
  color: #2b3a52;
  font-size: 15px;
}
.detail-section-kicker {
  font-size: 9px;
  letter-spacing: 0.1em;
}
.detail-section-count {
  color: #8291a7;
  font-size: 12px;
}
.detail-introduction {
  margin: 0;
  color: #52627a;
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.detail-empty-note {
  margin: 0;
  color: #9aa6b7;
  font-size: 12px;
}
.detail-tag-list {
  max-width: 100%;
}
.detail-tag {
  margin: 0;
  padding: 4px 10px;
  border: 1px solid #d9e7ff;
  border-radius: 999px;
  background: #f2f6ff;
  color: #3f6fb9;
}
.detail-resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.detail-resource-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e7ecf3;
  border-radius: 12px;
  background: #fbfcfe;
}
.detail-resource-preview {
  display: grid;
  height: 156px;
  overflow: hidden;
  place-items: center;
  background: linear-gradient(135deg, #edf2f8, #e4ebf4);
}
.detail-resource-preview :deep(.arco-image) {
  display: block;
  width: 100%;
  height: 100%;
}
.detail-resource-preview :deep(.arco-image-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.detail-resource-video {
  width: 100%;
  height: 100%;
  background: #17243a;
  object-fit: contain;
}
.detail-resource-placeholder {
  display: grid;
  justify-items: center;
  gap: 8px;
  color: #8494aa;
  font-size: 12px;
}
.detail-resource-placeholder :deep(.arco-icon) {
  font-size: 26px;
}
.detail-resource-info {
  display: grid;
  gap: 9px;
  padding: 11px 12px 12px;
}
.detail-resource-title-row,
.detail-resource-bottom {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.detail-resource-title-row strong {
  overflow: hidden;
  color: #3c4b62;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-resource-bottom {
  color: #8a98aa;
  font-size: 11px;
}
.detail-resource-link {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  color: #3975d8;
  text-decoration: none;
}
.detail-resource-link:hover {
  color: #245cb8;
}
.detail-moderation-panel {
  border-color: #e3e9f2;
  background: linear-gradient(145deg, #fff, #fafcff);
}
.detail-moderation-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 170px;
  gap: 14px;
}
.detail-moderation-grid > div {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 11px 12px;
  border-radius: 10px;
  background: #f5f7fb;
}
.detail-moderation-grid span {
  color: #8b98aa;
  font-size: 11px;
}
.detail-moderation-grid strong {
  color: #495a72;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
@media (max-width: 640px) {
  .meme-detail-content { padding: 14px; }
  .detail-hero { grid-template-columns: 1fr; gap: 13px; }
  .detail-cover-frame { min-height: 190px; }
  .detail-cover-frame :deep(.arco-image-img),
  .detail-cover-placeholder { min-height: 190px; }
  .detail-summary { padding: 3px 2px 6px; }
  .detail-summary h2 { margin-bottom: 13px; font-size: 20px; }
  .detail-dates { margin-top: 13px; }
  .detail-resource-grid { grid-template-columns: 1fr; }
  .detail-moderation-grid { grid-template-columns: 1fr; }
}
</style>
