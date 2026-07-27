<template>
  <div class="captcha-field">
    <vs-input
      :model-value="captchaCode"
      block
      clearable
      maxlength="8"
      placeholder="请输入图中验证码"
      autocomplete="off"
      class="auth-field captcha-field__input"
      @update:model-value="onCodeInput"
    >
      <template #icon>
        <i class="ri-shield-check-line" aria-hidden="true" />
      </template>
    </vs-input>
    <button
      type="button"
      class="captcha-field__image-btn"
      :disabled="loading"
      :title="loading ? '加载中' : '点击刷新验证码'"
      aria-label="刷新验证码"
      @click="refresh"
    >
      <i
        v-if="loading"
        class="ri-loader-4-line captcha-field__spin"
        aria-hidden="true"
      />
      <img
        v-else-if="imageBase64"
        :src="imageBase64"
        alt="图形验证码"
        class="captcha-field__img"
        draggable="false"
      />
      <span v-else class="captcha-field__fallback">点击获取</span>
    </button>
  </div>
</template>

<script>
import { fetchCaptcha } from '@/api/auth'
import { toast } from '@/utils/uiFeedback'

export default {
  name: 'CaptchaField',
  props: {
    captchaId: {
      type: String,
      default: '',
    },
    captchaCode: {
      type: String,
      default: '',
    },
    /** 挂载时自动拉取 */
    autoLoad: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['update:captchaId', 'update:captchaCode'],
  data() {
    return {
      imageBase64: '',
      loading: false,
    }
  },
  mounted() {
    if (this.autoLoad) {
      this.refresh()
    }
  },
  methods: {
    onCodeInput(value) {
      const next = value != null ? String(value).toUpperCase() : ''
      this.$emit('update:captchaCode', next)
    },
    /**
     * 刷新验证码；失败返回 false。
     * @returns {Promise<boolean>}
     */
    refresh() {
      if (this.loading) return Promise.resolve(false)
      this.loading = true
      this.$emit('update:captchaCode', '')
      this.$emit('update:captchaId', '')
      return fetchCaptcha()
        .then(({ captchaId, imageBase64 }) => {
          this.imageBase64 = imageBase64 || ''
          this.$emit('update:captchaId', captchaId || '')
          return true
        })
        .catch((err) => {
          this.imageBase64 = ''
          const msg =
            (err && (err.message || err.msg)) || '验证码加载失败，请重试'
          toast.error(msg)
          return false
        })
        .finally(() => {
          this.loading = false
        })
    },
    /** 提交前本地校验 */
    validateLocal() {
      if (!this.captchaId || !(this.captchaCode || '').trim()) {
        return '请完成人机验证'
      }
      return ''
    },
  },
}
</script>

<style scoped>
.captcha-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 10px;
  align-items: stretch;
  width: 100%;
}

.captcha-field__image-btn {
  height: 46px;
  padding: 0;
  border-radius: 10px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-muted);
  cursor: pointer;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.captcha-field__image-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--meme-primary) 40%, var(--meme-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--meme-primary) 18%, transparent);
}

.captcha-field__image-btn:disabled {
  cursor: wait;
  opacity: 0.85;
}

.captcha-field__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  user-select: none;
}

.captcha-field__fallback {
  font-size: 12px;
  color: var(--meme-text-muted);
  font-weight: 600;
}

.captcha-field__spin {
  display: inline-block;
  font-size: 18px;
  color: var(--meme-primary);
  animation: captcha-spin 0.8s linear infinite;
}

@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 520px) {
  .captcha-field {
    grid-template-columns: 1fr;
  }

  .captcha-field__image-btn {
    width: 100%;
    height: 48px;
  }
}
</style>
