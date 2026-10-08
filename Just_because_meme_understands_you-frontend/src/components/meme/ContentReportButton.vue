<template>
  <div class="comment-report">
    <button v-if="!submitted" type="button" class="report-trigger" @click="openDialog">
      <i class="ri-flag-line" aria-hidden="true" /> 举报
    </button>
    <span v-else class="report-submitted">已举报</span>

    <vs-dialog v-model="visible" width="440px" class="report-dialog">
      <template #header><h3 class="report-title">举报这条评论</h3></template>
      <div class="report-form">
        <label for="comment-report-reason">举报原因</label>
        <select id="comment-report-reason" v-model="reason">
          <option value="SPAM">垃圾广告</option>
          <option value="ABUSE">辱骂骚扰</option>
          <option value="COPYRIGHT">版权侵权</option>
          <option value="PORNOGRAPHY">色情低俗</option>
          <option value="VIOLENCE">暴力血腥</option>
          <option value="OTHER">其他</option>
        </select>
        <label for="comment-report-detail">补充说明 <span>选填，最多 500 字</span></label>
        <textarea id="comment-report-detail" v-model="detail" maxlength="500" rows="4" placeholder="描述需要处理的问题" />
        <p v-if="reason === 'OTHER' && !detail.trim()" class="report-hint">选择“其他”时请填写说明。</p>
        <p v-if="error" class="report-error">{{ error }}</p>
        <div class="report-actions">
          <vs-button type="border" @click="visible = false">取消</vs-button>
          <vs-button color="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">提交举报</vs-button>
        </div>
      </div>
    </vs-dialog>
  </div>
</template>

<script setup>
/* global defineProps */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { submitCommentReport } from '@/api/memeReport'
import { toast } from '@/utils/uiFeedback'

const props = defineProps({ commentId: { type: [String, Number], required: true } })
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const visible = ref(false)
const reason = ref('SPAM')
const detail = ref('')
const submitting = ref(false)
const submitted = ref(false)
const error = ref('')
const canSubmit = computed(() => !submitting.value && (reason.value !== 'OTHER' || !!detail.value.trim()))

function openDialog() {
  error.value = ''
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  visible.value = true
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    await submitCommentReport(props.commentId, { reason: reason.value, detail: detail.value })
    submitted.value = true
    visible.value = false
    toast.success('举报已提交，感谢帮助维护社区')
  } catch (cause) {
    error.value = cause?.message || '举报提交失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.report-trigger { display: inline-flex; align-items: center; gap: 5px; padding: 5px 8px; border: 0; color: #8390a4; background: transparent; font-size: 12px; cursor: pointer; }
.report-trigger:hover { color: #d34d5e; }
.report-submitted { color: #748298; font-size: 12px; }
.report-title { margin: 0; color: #1e2b42; font-size: 19px; }
.report-form { display: grid; gap: 10px; padding: 8px 0 2px; }
.report-form label { display: flex; justify-content: space-between; color: #39465b; font-size: 13px; font-weight: 650; }
.report-form label span { color: #939daf; font-weight: 400; }
.report-form select, .report-form textarea { width: 100%; box-sizing: border-box; border: 1px solid #dfe5ef; border-radius: 10px; padding: 10px 12px; color: #2d3a50; background: #fff; font: inherit; }
.report-form textarea { resize: vertical; }
.report-form select:focus, .report-form textarea:focus { outline: 2px solid #b6d1ff; border-color: #6b9af0; }
.report-hint, .report-error { margin: 0; color: #c74455; font-size: 12px; }
.report-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 4px; }
</style>
