<template>
  <AuthPageLayout
    title="正在完成 GitHub 登录"
    desc="请稍候，正在安全地换取登录凭证。"
    lead="授权成功后会自动跳转；若长时间无响应，请返回登录页重试。"
  >
    <div class="oauth-callback">
      <span class="oauth-callback__spinner" aria-hidden="true" />
      <p class="oauth-callback__text">{{ statusText }}</p>
      <router-link v-if="failed" to="/login" class="oauth-callback__link">返回登录</router-link>
    </div>
  </AuthPageLayout>
</template>

<script>
import { toast } from '@/utils/uiFeedback'
import { exchangeOauthTicket } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { POST_LOGIN_REDIRECT_KEY, resolveSafeRedirectPath } from '@/utils/authSession'
import AuthPageLayout from '@/components/auth/AuthPageLayout.vue'

export default {
  name: 'OauthCallbackPage',
  components: { AuthPageLayout },
  data() {
    return {
      statusText: '正在登录…',
      failed: false,
    }
  },
  mounted() {
    this.completeLogin()
  },
  methods: {
    async completeLogin() {
      const error = this.$route.query.error
      if (error) {
        this.failed = true
        this.statusText = String(error)
        toast.error(String(error))
        return
      }
      const ticket = this.$route.query.ticket
      if (!ticket) {
        this.failed = true
        this.statusText = '缺少登录凭证，请重新授权'
        toast.error(this.statusText)
        return
      }
      try {
        const { token, user } = await exchangeOauthTicket(String(ticket))
        useAuthStore().setAuth({ token, user })
        toast.success('GitHub 登录成功')
        const redirectFromQuery = this.$route.query.redirect
        let redirectFromStorage = ''
        try {
          redirectFromStorage = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY) || ''
          sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
        } catch (_) {
          // ignore
        }
        const redirect = resolveSafeRedirectPath(redirectFromQuery || redirectFromStorage)
        this.$router.replace(redirect)
      } catch (e) {
        this.failed = true
        this.statusText = e?.message || 'GitHub 登录失败'
        toast.error(this.statusText)
      }
    },
  },
}
</script>

<style scoped>
.oauth-callback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 12px 0 8px;
  min-height: 120px;
}

.oauth-callback__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: oauth-spin 0.8s linear infinite;
}

@keyframes oauth-spin {
  to {
    transform: rotate(360deg);
  }
}

.oauth-callback__text {
  margin: 0;
  font-size: 14px;
  color: var(--meme-text-secondary);
  text-align: center;
}

.oauth-callback__link {
  color: var(--meme-primary);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.oauth-callback__link:hover {
  text-decoration: underline;
}
</style>
