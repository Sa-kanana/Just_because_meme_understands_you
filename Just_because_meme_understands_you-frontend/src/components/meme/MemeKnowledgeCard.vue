<template>
  <section class="knowledge-card" aria-label="梗资料卡">
    <div class="knowledge-head">
      <div><h3>梗资料卡</h3><p>出处、流行语境与延伸脉络</p></div>
      <vs-button size="small" type="border" @click="openCorrection">提交纠错</vs-button>
    </div>
    <p v-if="loading" class="muted">资料加载中…</p>
    <p v-else-if="error" class="muted">{{ error }}</p>
    <template v-else-if="card">
      <dl class="knowledge-fields">
        <div v-if="card.sourceUrl"><dt>出处</dt><dd><a :href="card.sourceUrl" target="_blank" rel="noopener noreferrer">{{ card.sourceUrl }}</a></dd></div>
        <div v-if="card.firstSeenDate"><dt>首次出现</dt><dd>{{ card.firstSeenDate }}</dd></div>
        <div v-if="card.popularityContext"><dt>流行语境</dt><dd>{{ card.popularityContext }}</dd></div>
        <div v-if="card.usageExamples"><dt>用法示例</dt><dd>{{ card.usageExamples }}</dd></div>
        <div v-if="card.evolution"><dt>演变</dt><dd>{{ card.evolution }}</dd></div>
        <div v-if="card.relatedMemes && card.relatedMemes.length"><dt>相关梗</dt><dd>
          <router-link v-for="item in card.relatedMemes" :key="item.id" :to="{ name: 'memeDetail', params: { id: item.id } }" class="related-link">{{ item.name }}</router-link>
        </dd></div>
      </dl>
      <p v-if="!hasDetails" class="muted">暂无资料，欢迎补充。</p>
    </template>

    <vs-dialog v-model="dialogVisible" width="720px" class="knowledge-dialog">
      <template #header><h3>提交资料纠错</h3></template>
      <p class="muted">只提交修改过的字段，审核通过后会更新资料卡。</p>
      <div class="correction-fields">
        <label>出处 URL<input v-model="form.sourceUrl" maxlength="500" placeholder="https://..." /></label>
        <label>首次出现日期<input v-model="form.firstSeenDate" type="date" /></label>
        <label>流行语境<textarea v-model="form.popularityContext" maxlength="2000" rows="3" /></label>
        <label>用法示例<textarea v-model="form.usageExamples" maxlength="2000" rows="3" /></label>
        <label>演变<textarea v-model="form.evolution" maxlength="2000" rows="3" /></label>
        <label>相关梗 ID（逗号分隔，最多 12 个）<input v-model="form.relatedMemeIds" placeholder="例如：12, 35" /></label>
      </div>
      <template #footer>
        <vs-button size="small" type="border" class="correction-cancel" @click="dialogVisible = false">取消</vs-button>
        <vs-button size="small" class="correction-submit" :loading="submitting" :disabled="submitting" @click="submitCorrection">提交审核</vs-button>
      </template>
    </vs-dialog>
  </section>
</template>

<script setup>
/* global defineProps */
import { computed, reactive, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/uiFeedback'
import { isAuthErrorHandled } from '@/utils/authSession'
import { getMemeKnowledge, submitMemeKnowledgeCorrection } from '@/api/memeKnowledge'

const props = defineProps({ memeId: { type: [String, Number], required: true } })
const authStore = useAuthStore()
const card = ref(null)
const loading = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ sourceUrl: '', firstSeenDate: '', popularityContext: '', usageExamples: '', evolution: '', relatedMemeIds: '' })
const original = ref({})
const hasDetails = computed(() => Boolean(card.value && (card.value.sourceUrl || card.value.firstSeenDate ||
  card.value.popularityContext || card.value.usageExamples || card.value.evolution || card.value.relatedMemes?.length)))

async function load() {
  card.value = null
  error.value = ''
  loading.value = true
  try { card.value = await getMemeKnowledge(props.memeId) }
  catch (e) { if (!isAuthErrorHandled(e)) error.value = e.message || '资料加载失败' }
  finally { loading.value = false }
}

function openCorrection() {
  if (!authStore.isLoggedIn) { toast.warning('请先登录后提交纠错'); return }
  if (!card.value) { toast.warning('资料尚未加载完成'); return }
  Object.assign(form, {
    sourceUrl: card.value.sourceUrl || '', firstSeenDate: card.value.firstSeenDate || '',
    popularityContext: card.value.popularityContext || '', usageExamples: card.value.usageExamples || '',
    evolution: card.value.evolution || '',
    relatedMemeIds: (card.value.relatedMemes || []).map((item) => item.id).join(', '),
  })
  original.value = { ...form }
  dialogVisible.value = true
}

async function submitCorrection() {
  const changes = {}
  for (const key of ['sourceUrl', 'firstSeenDate', 'popularityContext', 'usageExamples', 'evolution']) {
    if (form[key] !== original.value[key]) changes[key] = form[key].trim()
  }
  if (form.relatedMemeIds !== original.value.relatedMemeIds) {
    const ids = form.relatedMemeIds.split(',').map((item) => item.trim()).filter(Boolean)
    if (ids.length > 12 || ids.some((id) => !/^\d+$/.test(id))) {
      toast.warning('相关梗请填写最多 12 个数字 ID')
      return
    }
    changes.relatedMemeIds = ids
  }
  if (!Object.keys(changes).length) { toast.warning('请先修改需要纠错的内容'); return }
  if (changes.sourceUrl && !/^https?:\/\/[^\s/]+/i.test(changes.sourceUrl)) {
    toast.warning('出处 URL 仅支持 http/https')
    return
  }
  submitting.value = true
  try {
    await submitMemeKnowledgeCorrection(props.memeId, changes)
    toast.success('纠错已提交，等待审核')
    dialogVisible.value = false
  } catch (e) { if (!isAuthErrorHandled(e)) toast.error(e.message || '提交失败') }
  finally { submitting.value = false }
}

watch(() => props.memeId, load, { immediate: true })
</script>

<style scoped>
.knowledge-card { padding: 20px; border: 1px solid var(--meme-border); border-radius: 16px; background: var(--meme-bg-elevated); }
.knowledge-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.knowledge-head h3 { margin: 0; color: var(--meme-text); }
.knowledge-head p, .muted { margin: 6px 0 0; color: var(--meme-text-muted); font-size: 13px; }
.knowledge-fields { margin: 18px 0 0; display: grid; gap: 14px; }
.knowledge-fields > div { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 10px; }
.knowledge-fields dt { color: var(--meme-text-muted); }
.knowledge-fields dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--meme-text); }
.related-link { margin-right: 12px; }
/* 纠错弹窗：桌面双列填写，操作按钮统一靠右 */
.correction-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 14px;
  margin-top: 16px;
}

.correction-fields label {
  display: grid;
  min-width: 0;
  gap: 7px;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.correction-fields input,
.correction-fields textarea {
  width: 100%;
  min-height: 44px;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--meme-border);
  border-radius: 10px;
  background: var(--meme-bg-muted);
  color: var(--meme-text);
  font: inherit;
  font-weight: 400;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.correction-fields textarea {
  min-height: 112px;
  line-height: 1.55;
  resize: vertical;
}

.correction-fields input:focus,
.correction-fields textarea:focus {
  border-color: var(--meme-primary);
  background: var(--meme-bg-elevated);
  box-shadow: 0 0 0 3px var(--meme-focus-ring);
}

.knowledge-dialog :deep(.vs-dialog__header) {
  padding: 20px 24px 8px;
  border-bottom: none;
}

.knowledge-dialog :deep(.vs-dialog__header h3) {
  color: var(--meme-text);
  font-size: 18px;
  font-weight: 750;
}

.knowledge-dialog :deep(.vs-dialog__content) {
  padding: 8px 24px 12px;
}

.knowledge-dialog :deep(.vs-dialog__footer) {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 24px 20px;
  border-top: 1px solid var(--meme-border);
  background: transparent;
}

.knowledge-dialog :deep(.correction-cancel),
.knowledge-dialog :deep(.correction-submit) {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 96px;
  height: 40px !important;
  margin: 0 !important;
  padding: 0 18px !important;
  border-radius: 10px !important;
  font-size: 14px !important;
  font-weight: 650 !important;
}

.knowledge-dialog :deep(.correction-cancel) {
  color: var(--meme-text-secondary) !important;
  border: 1px solid var(--meme-border-strong, var(--meme-border)) !important;
  background: var(--meme-bg-elevated) !important;
  box-shadow: none !important;
}

.knowledge-dialog :deep(.correction-submit) {
  color: #fff !important;
  border: none !important;
  background: var(--meme-primary) !important;
  box-shadow: 0 4px 14px var(--meme-focus-ring) !important;
}

@media (max-width: 600px) {
  .correction-fields {
    grid-template-columns: 1fr;
    row-gap: 12px;
  }

  .knowledge-dialog :deep(.vs-dialog__header) {
    padding: 18px 18px 8px;
  }

  .knowledge-dialog :deep(.vs-dialog__content) {
    padding: 8px 18px 12px;
  }

  .knowledge-dialog :deep(.vs-dialog__footer) {
    padding: 12px 18px 18px;
  }
}
</style>
