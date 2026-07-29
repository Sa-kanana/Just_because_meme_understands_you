import { defineStore } from 'pinia'
import { logout as apiLogout, renewLogin as apiRenewLogin } from '@/api/auth'

const STORAGE_KEY = 'meme_admin_auth'
const STORAGE_MODE_KEY = 'meme_admin_auth_mode'

function persistAuth({ token, user, rememberMe }) {
  const raw = JSON.stringify({ token, user })
  try {
    if (rememberMe) {
      localStorage.setItem(STORAGE_KEY, raw)
      localStorage.setItem(STORAGE_MODE_KEY, 'local')
      sessionStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_MODE_KEY)
    } else {
      sessionStorage.setItem(STORAGE_KEY, raw)
      sessionStorage.setItem(STORAGE_MODE_KEY, 'session')
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

function loadPersistedAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY) || sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch (_) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const saved = loadPersistedAuth()
    return {
      user: saved && saved.user ? saved.user : null,
      token: saved && saved.token ? saved.token : null,
      rememberMe: true,
      _renewingPromise: null,
    }
  },
  getters: {
    isLoggedIn: (state) => !!state.user && !!state.token,
    isAdmin: (state) =>
      !!(state.user && String(state.user.role || '') === 'ROLE_ADMIN'),
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
    clearAuthState() {
      this.user = null
      this.token = null
      this.rememberMe = true
      this._renewingPromise = null
      clearPersistedAuth()
    },
    async renewAccessToken() {
      if (this._renewingPromise) return this._renewingPromise
      this._renewingPromise = apiRenewLogin()
        .then((data) => {
          if (data && data.token) {
            this.token = data.token
            if (data.user) this.user = data.user
            persistAuth({
              token: this.token,
              user: this.user,
              rememberMe: this.rememberMe,
            })
          }
          return this.token
        })
        .finally(() => {
          this._renewingPromise = null
        })
      return this._renewingPromise
    },
    async logout() {
      try {
        await apiLogout()
      } catch (_) {
        // ignore
      }
      this.clearAuthState()
    },
  },
})
