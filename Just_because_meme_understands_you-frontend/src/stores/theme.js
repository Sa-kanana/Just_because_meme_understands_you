/**
 * 主题模式：仅管偏好（light / dark / system），
 * 真正生效靠 html.dark + CSS 变量，不在各页面写暗色分支。
 */
import { defineStore } from 'pinia'

export const THEME_MODE = Object.freeze({
  LIGHT: 'light',
  DARK: 'dark',
  SYSTEM: 'system',
})

export const THEME_MODE_OPTIONS = [
  { value: THEME_MODE.SYSTEM, label: '跟随系统', icon: 'ri-computer-line', desc: '自动匹配系统外观' },
  { value: THEME_MODE.LIGHT, label: '浅色', icon: 'ri-sun-line', desc: '始终使用浅色主题' },
  { value: THEME_MODE.DARK, label: '深色', icon: 'ri-moon-line', desc: '始终使用深色主题' },
]

const STORAGE_KEY = 'meme_theme_mode'
const VALID_MODES = new Set(Object.values(THEME_MODE))

function readStoredMode() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw && VALID_MODES.has(raw)) return raw
  } catch (_) {
    // ignore
  }
  return THEME_MODE.SYSTEM
}

function persistMode(mode) {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch (_) {
    // ignore
  }
}

function getSystemPrefersDark() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function resolveIsDark(mode) {
  if (mode === THEME_MODE.DARK) return true
  if (mode === THEME_MODE.LIGHT) return false
  return getSystemPrefersDark()
}

/**
 * 同步 DOM：Element Plus 官方暗色依赖 html.dark；
 * color-scheme 影响原生控件滚动条/表单底色。
 */
export function applyThemeToDocument(isDark) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.toggle('dark', isDark)
  root.setAttribute('data-theme', isDark ? 'dark' : 'light')
  root.style.colorScheme = isDark ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: THEME_MODE.SYSTEM,
    /** 解析后的实际外观，供图标/文案使用 */
    resolvedDark: false,
    _mediaQuery: null,
    _mediaHandler: null,
  }),

  getters: {
    isDark: (state) => state.resolvedDark,
    isSystem: (state) => state.mode === THEME_MODE.SYSTEM,
    currentOption: (state) =>
      THEME_MODE_OPTIONS.find((item) => item.value === state.mode) || THEME_MODE_OPTIONS[0],
  },

  actions: {
    init() {
      this.mode = readStoredMode()
      this.syncResolved()
      this.bindSystemListener()
    },

    setMode(mode) {
      if (!VALID_MODES.has(mode)) return
      this.mode = mode
      persistMode(mode)
      this.syncResolved()
      this.bindSystemListener()
    },

    /** 在浅色 / 深色之间快速切换（保留：若当前是 system，则按解析结果取反成固定偏好） */
    toggleLightDark() {
      this.setMode(this.resolvedDark ? THEME_MODE.LIGHT : THEME_MODE.DARK)
    },

    syncResolved() {
      const next = resolveIsDark(this.mode)
      this.resolvedDark = next
      applyThemeToDocument(next)
    },

    bindSystemListener() {
      this.unbindSystemListener()
      if (typeof window === 'undefined' || !window.matchMedia) return
      if (this.mode !== THEME_MODE.SYSTEM) return

      const mq = window.matchMedia('(prefers-color-scheme: dark)')
      const handler = () => {
        if (this.mode === THEME_MODE.SYSTEM) {
          this.syncResolved()
        }
      }
      if (typeof mq.addEventListener === 'function') {
        mq.addEventListener('change', handler)
      } else if (typeof mq.addListener === 'function') {
        mq.addListener(handler)
      }
      this._mediaQuery = mq
      this._mediaHandler = handler
    },

    unbindSystemListener() {
      const mq = this._mediaQuery
      const handler = this._mediaHandler
      if (!mq || !handler) return
      if (typeof mq.removeEventListener === 'function') {
        mq.removeEventListener('change', handler)
      } else if (typeof mq.removeListener === 'function') {
        mq.removeListener(handler)
      }
      this._mediaQuery = null
      this._mediaHandler = null
    },
  },
})
