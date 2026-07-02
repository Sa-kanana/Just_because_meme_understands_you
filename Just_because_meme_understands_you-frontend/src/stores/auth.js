/**
 * 认证状态：登录 → 个人中心（Vue Router + Pinia + Storage）
 */
import { defineStore } from 'pinia'
import { logout as apiLogout, renewLogin as apiRenewLogin } from '@/api/auth'
import { useMemeDetailStore } from './memeDetail'

const STORAGE_KEY = 'meme_auth'
const STORAGE_MODE_KEY = 'meme_auth_mode'
const STORAGE_MODE = {
  LOCAL: 'local',
  SESSION: 'session',
}

function persistAuth({ token, user, rememberMe }) {
  const raw = JSON.stringify({ token, user })
  const mode = rememberMe ? STORAGE_MODE.LOCAL : STORAGE_MODE.SESSION
  try {
    if (mode === STORAGE_MODE.LOCAL) {
      localStorage.setItem(STORAGE_KEY, raw)
      localStorage.setItem(STORAGE_MODE_KEY, STORAGE_MODE.LOCAL)
      sessionStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_MODE_KEY)
    } else {
      sessionStorage.setItem(STORAGE_KEY, raw)
      sessionStorage.setItem(STORAGE_MODE_KEY, STORAGE_MODE.SESSION)
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem(STORAGE_MODE_KEY)
    }
  } catch (_) {
    // ignore
  }
}

function clearPersistedAuth() {
  try {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(STORAGE_MODE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_MODE_KEY)
  } catch (_) {
    // ignore
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    token: null,
    rememberMe: true,
    _renewingPromise: null,
  }),

  getters: {
    isLoggedIn: (state) => !!state.user && !!state.token,
    currentUser: (state) => state.user,
  },

  actions: {
    setAuth(payload) {
      if (!payload || !payload.token || !payload.user) return
      const token = String(payload.token).trim()
      if (!token) return
      this.token = token
      this.user = payload.user
      this.rememberMe = payload.rememberMe !== false
      persistAuth({
        token: this.token,
        user: this.user,
        rememberMe: this.rememberMe,
      })
    },

    updateCurrentUser(patch = {}) {
      if (!this.user || typeof this.user !== 'object') return
      this.user = {
        ...this.user,
        ...(patch && typeof patch === 'object' ? patch : {}),
      }
      if (this.token) {
        persistAuth({
          token: this.token,
          user: this.user,
          rememberMe: this.rememberMe,
        })
      }
    },

    clearAuthState() {
      this.user = null
      this.token = null
      this.rememberMe = true
      this._renewingPromise = null
      clearPersistedAuth()

      // 清空梗详情等可能包含上一位用户浏览痕迹的状态
      try {
        const memeDetailStore = useMemeDetailStore()
        if (memeDetailStore.clearDetail) {
          memeDetailStore.clearDetail()
        }
      } catch (_) {
        // ignore
      }
    },

    async renewLogin() {
      if (!this.token) {
        return Promise.reject(new Error('当前未登录，无法续航'))
      }
      if (this._renewingPromise) {
        return this._renewingPromise
      }

      this._renewingPromise = apiRenewLogin()
        .then(({ token, user }) => {
          const nextToken = token != null ? String(token).trim() : ''
          if (!nextToken) {
            throw new Error('登录续航失败：未获取到新 token')
          }
          this.token = nextToken
          if (user && typeof user === 'object') {
            this.user = {
              ...(this.user || {}),
              ...user,
            }
          }
          if (this.user) {
            persistAuth({
              token: this.token,
              user: this.user,
              rememberMe: this.rememberMe,
            })
          }
          return nextToken
        })
        .finally(() => {
          this._renewingPromise = null
        })

      return this._renewingPromise
    },

    /**
     * 退出登录：先请求后端使服务端会话/令牌失效，再清理前端现场，防止下一位使用者看到上一位的敏感信息。
     * - 调用 POST /logout（携带当前 token）
     * - 无论接口成功与否，都会清空本地 token/user、Storage 及梗详情等状态
     */
    async logout() {
      const token = this.token

      // 有 token 时通知后端退出（失败也继续清理前端）
      if (token) {
        try {
          await apiLogout(token)
        } catch (_) {
          // 网络或后端错误不影响本地清理
        }
      }

      this.clearAuthState()
    },

    initFromStorage() {
      try {
        const localRaw = localStorage.getItem(STORAGE_KEY)
        const sessionRaw = sessionStorage.getItem(STORAGE_KEY)
        const raw = localRaw || sessionRaw
        if (!raw) return
        const data = JSON.parse(raw)
        if (data && data.user && data.token) {
          this.user = data.user
          this.token = String(data.token)
          const mode =
            localStorage.getItem(STORAGE_MODE_KEY) ||
            sessionStorage.getItem(STORAGE_MODE_KEY)
          this.rememberMe = mode !== STORAGE_MODE.SESSION
        }
      } catch (e) {
        // ignore
      }
    },
  },
})
