<template>
  <div class="feedback-page">
    <div class="feedback-shell">
      <header class="feedback-hero">
        <div class="feedback-hero__mark" aria-hidden="true">
          <i class="ri-chat-smile-2-line" />
        </div>
        <div class="feedback-hero__copy">
          <p class="feedback-kicker">FEEDBACK</p>
          <h1 class="feedback-title">提交反馈</h1>
          <p class="feedback-lead">
            {{ meta.notice || '描述你遇到的问题或建议，我们会尽快查阅。' }}
          </p>
        </div>
      </header>

      <div class="feedback-layout">
        <section class="feedback-panel">
          <div v-if="pageLoading" class="feedback-loading">
            <span class="feedback-loading__spinner" />
            <span>加载中...</span>
          </div>

          <ui-form
            v-else
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="feedback-form"
          >
            <div class="feedback-section">
              <div class="feedback-section__head">
                <h2 class="feedback-section__title">反馈类型</h2>
                <p class="feedback-section__hint">选择最贴近的一类，方便我们更快分拣</p>
              </div>
              <ui-form-item prop="category" class="feedback-form-item--bare">
                <div class="feedback-cats" role="radiogroup" aria-label="反馈类型">
                  <button
                    v-for="item in meta.categories"
                    :key="item.value"
                    type="button"
                    class="feedback-cat"
                    :class="[
                      `is-${item.value}`,
                      { 'is-active': form.category === item.value },
                    ]"
                    role="radio"
                    :aria-checked="form.category === item.value"
                    @click="selectCategory(item.value)"
                  >
                    <span class="feedback-cat__icon" aria-hidden="true">
                      <i :class="categoryIcon(item.value)" />
                    </span>
                    <span class="feedback-cat__label">{{ item.label }}</span>
                  </button>
                </div>
              </ui-form-item>
            </div>

            <div class="feedback-section">
              <div class="feedback-section__head">
                <h2 class="feedback-section__title">详细说明</h2>
                <p class="feedback-section__hint">现象、复现步骤或期望效果，写清楚会更好处理</p>
              </div>
              <ui-form-item prop="content" class="feedback-form-item--bare">
                <vs-input
                  v-model="form.content"
                  type="textarea"
                  :rows="7"
                  maxlength="2000"
                  block
                  placeholder="例如：在发布页上传封面后偶发失败，提示网络错误。刷新后偶现。"
                  class="feedback-input feedback-input--area"
                />
                <div class="feedback-counter" aria-live="polite">
                  {{ contentLength }} / 2000
                </div>
              </ui-form-item>
            </div>

            <div class="feedback-section feedback-section--last">
              <div class="feedback-section__head">
                <h2 class="feedback-section__title">联系邮箱</h2>
                <p class="feedback-section__hint">可选；留空将使用当前账号邮箱</p>
              </div>
              <ui-form-item prop="contactEmail" class="feedback-form-item--bare">
                <vs-input
                  v-model="form.contactEmail"
                  maxlength="128"
                  clearable
                  block
                  placeholder="name@example.com"
                  class="feedback-input"
                />
              </ui-form-item>
            </div>

            <div class="feedback-actions">
              <vs-button
                type="primary"
                class="feedback-submit"
                :loading="submitting"
                @click="onSubmit"
              >
                <i v-if="!submitting" class="ri-send-plane-2-line" aria-hidden="true" />
                提交反馈
              </vs-button>
            </div>
          </ui-form>
        </section>

        <aside class="feedback-aside" aria-label="提交说明">
          <div class="feedback-aside__card">
            <p class="feedback-aside__eyebrow">我们会这样处理</p>
            <ul class="feedback-aside__list">
              <li>
                <i class="ri-flashlight-line" aria-hidden="true" />
                <span>问题类反馈优先排查线上异常</span>
              </li>
              <li>
                <i class="ri-lightbulb-line" aria-hidden="true" />
                <span>建议类会进入产品需求池评估</span>
              </li>
              <li>
                <i class="ri-shield-check-line" aria-hidden="true" />
                <span>举报类会尽快核查相关内容</span>
              </li>
            </ul>
          </div>
          <div class="feedback-aside__note">
            <i class="ri-information-line" aria-hidden="true" />
            <p>请勿在反馈中填写密码等敏感信息。需要紧急联系时，请留下常用邮箱。</p>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<script>
import { getFeedbackMeta, submitFeedback } from '@/api/feedback'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toast } from '@/utils/uiFeedback'

const DEFAULT_CATEGORIES = [
  { value: 'bug', label: '问题反馈' },
  { value: 'suggestion', label: '功能建议' },
  { value: 'report', label: '内容举报' },
  { value: 'other', label: '其他' },
]

const CATEGORY_ICONS = {
  bug: 'ri-bug-line',
  suggestion: 'ri-lightbulb-line',
  report: 'ri-alarm-warning-line',
  other: 'ri-chat-3-line',
}

export default {
  name: 'FeedbackPage',
  data() {
    return {
      pageLoading: true,
      submitting: false,
      meta: {
        categories: DEFAULT_CATEGORIES,
        notice: '',
      },
      form: {
        category: 'suggestion',
        content: '',
        contactEmail: '',
      },
      rules: {
        category: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
        content: [
          { required: true, message: '请填写反馈内容', trigger: 'blur' },
          {
            validator: (_rule, value, callback) => {
              const text = value != null ? String(value).trim() : ''
              if (text.length < 5) {
                callback(new Error('反馈内容至少 5 个字'))
                return
              }
              if (text.length > 2000) {
                callback(new Error('反馈内容最多 2000 字'))
                return
              }
              callback()
            },
            trigger: 'blur',
          },
        ],
        contactEmail: [
          {
            validator: (_rule, value, callback) => {
              const text = value != null ? String(value).trim() : ''
              if (!text) {
                callback()
                return
              }
              if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) {
                callback(new Error('邮箱格式不正确'))
                return
              }
              callback()
            },
            trigger: 'blur',
          },
        ],
      },
    }
  },
  computed: {
    contentLength() {
      return String(this.form.content || '').length
    },
  },
  async mounted() {
    try {
      const data = await getFeedbackMeta()
      if (data.categories && data.categories.length) {
        this.meta.categories = data.categories
      }
      this.meta.notice = data.notice || ''
      if (!this.form.category && this.meta.categories[0]) {
        this.form.category = this.meta.categories[0].value
      }
    } catch (e) {
      if (!isAuthErrorHandled(e)) {
        toast.error(e.message || '加载失败')
      }
    } finally {
      this.pageLoading = false
    }
  },
  methods: {
    categoryIcon(value) {
      return CATEGORY_ICONS[value] || 'ri-chat-3-line'
    },
    selectCategory(value) {
      this.form.category = value
      this.$nextTick(() => {
        this.$refs.formRef?.clearValidate?.(['category'])
      })
    },
    async onSubmit() {
      const formRef = this.$refs.formRef
      if (!formRef || typeof formRef.validate !== 'function') return
      try {
        await formRef.validate()
      } catch {
        return
      }
      if (this.submitting) return
      this.submitting = true
      try {
        const result = await submitFeedback({
          category: this.form.category,
          content: this.form.content,
          contactEmail: this.form.contactEmail,
          pageUrl: this.$route.fullPath,
        })
        toast.success(result.message || '反馈已提交')
        this.form.content = ''
        this.form.contactEmail = ''
      } catch (e) {
        if (!isAuthErrorHandled(e)) {
          toast.error(e.message || '提交失败')
        }
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style scoped>
.feedback-page {
  padding: 20px 16px 48px;
  background:
    radial-gradient(ellipse 70% 45% at 0% 0%, var(--meme-primary-soft), transparent 55%),
    radial-gradient(ellipse 50% 40% at 100% 8%, rgba(82, 199, 184, 0.08), transparent 50%);
}

.feedback-shell {
  max-width: 980px;
  margin: 0 auto;
}

.feedback-hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 22px;
}

.feedback-hero__mark {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 22px;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
  border: 1px solid var(--meme-border-accent);
}

.feedback-hero__copy {
  min-width: 0;
}

.feedback-kicker {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--meme-text-muted);
}

.feedback-title {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.feedback-lead {
  margin: 0;
  max-width: 560px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
}

.feedback-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
  align-items: start;
}

.feedback-panel {
  padding: 22px 22px 20px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-md);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.feedback-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 220px;
  justify-content: center;
  color: var(--meme-text-muted);
  font-size: 13px;
}

.feedback-loading__spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: feedback-spin 0.8s linear infinite;
}

.feedback-section {
  margin-bottom: 22px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--meme-border);
}

.feedback-section--last {
  margin-bottom: 8px;
  padding-bottom: 0;
  border-bottom: none;
}

.feedback-section__head {
  margin-bottom: 12px;
}

.feedback-section__title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meme-text);
}

.feedback-section__hint {
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.feedback-form-item--bare :deep(.ui-form-item__label),
.feedback-form-item--bare :deep(.vs-form-item__label) {
  display: none;
}

.feedback-cats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.feedback-cat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-height: 78px;
  padding: 12px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-sm);
  background: var(--meme-bg-elevated);
  color: var(--meme-text);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.feedback-cat:hover {
  border-color: var(--meme-border-strong);
}

.feedback-cat.is-active {
  border-color: var(--meme-primary);
  background: var(--meme-primary-soft);
  box-shadow: 0 0 0 1px var(--meme-border-accent);
}

.feedback-cat__icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 15px;
  background: var(--meme-bg-muted);
  color: var(--meme-text-secondary);
}

.feedback-cat.is-bug .feedback-cat__icon {
  color: var(--meme-danger);
  background: var(--meme-danger-soft);
}

.feedback-cat.is-suggestion .feedback-cat__icon {
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.feedback-cat.is-report .feedback-cat__icon {
  color: var(--meme-warning);
  background: var(--meme-warning-soft);
}

.feedback-cat.is-other .feedback-cat__icon {
  color: var(--meme-text-secondary);
}

.feedback-cat.is-active .feedback-cat__icon {
  background: var(--meme-bg-card);
}

.feedback-cat__label {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}

.feedback-counter {
  margin-top: 6px;
  text-align: right;
  font-size: 11px;
  color: var(--meme-text-muted);
}

.feedback-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

.feedback-submit :deep(span) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.feedback-form :deep(.feedback-input .vs-input__wrapper),
.feedback-form :deep(.feedback-input .vs-input__original) {
  width: 100%;
}

.feedback-aside {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feedback-aside__card {
  padding: 16px;
  border: 1px solid var(--meme-border);
  border-radius: var(--meme-radius-md);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
}

.feedback-aside__eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--meme-text);
}

.feedback-aside__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback-aside__list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--meme-text-secondary);
}

.feedback-aside__list i {
  margin-top: 1px;
  color: var(--meme-primary);
  font-size: 14px;
}

.feedback-aside__note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: var(--meme-radius-sm);
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  color: var(--meme-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.feedback-aside__note i {
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 14px;
  color: var(--meme-text-secondary);
}

.feedback-aside__note p {
  margin: 0;
}

@keyframes feedback-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 860px) {
  .feedback-layout {
    grid-template-columns: 1fr;
  }

  .feedback-aside {
    order: -1;
  }

  .feedback-cats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .feedback-hero {
    gap: 12px;
  }

  .feedback-title {
    font-size: 22px;
  }

  .feedback-panel {
    padding: 16px;
  }

  .feedback-cats {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
