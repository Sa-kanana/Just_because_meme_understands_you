<template>
  <button
    v-if="!hidden"
    type="button"
    class="follow-btn"
    :class="[
      `follow-btn--${size}`,
      {
        'is-followed': followed,
        'is-mutual': followed && isMutual,
        'is-loading': loading,
        'is-hover-cancel': followed && hoverCancel,
      },
    ]"
    :disabled="loading || disabled"
    :aria-pressed="followed"
    @mouseenter="hoverCancel = followed"
    @mouseleave="hoverCancel = false"
    @click.stop="handleClick"
  >
    <span v-if="loading" class="follow-btn__spinner" aria-hidden="true" />
    <span class="follow-btn__label">{{ label }}</span>
  </button>
</template>

<script>
import { ElMessage } from 'element-plus'
import { followUser, unfollowUser, getFollowStatus } from '@/api/follow'
import { useAuthStore } from '@/stores/auth'

export default {
  name: 'FollowButton',
  props: {
    userId: {
      type: [String, Number],
      required: true,
    },
    /** 初始是否已关注（可由父层批量状态 / 个人页 isFollow 注入） */
    modelValue: {
      type: Boolean,
      default: false,
    },
    mutual: {
      type: Boolean,
      default: false,
    },
    size: {
      type: String,
      default: 'md', // sm | md | lg
      validator: (v) => ['sm', 'md', 'lg'].includes(v),
    },
    /** 挂载时是否主动拉一次状态（无 modelValue 来源时用） */
    fetchOnMount: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    /** 自己的主页不展示 */
    hideSelf: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['update:modelValue', 'update:mutual', 'change'],
  data() {
    return {
      followed: Boolean(this.modelValue),
      isMutual: Boolean(this.mutual),
      loading: false,
      hoverCancel: false,
      /** 递增序号：忽略过期的状态查询，避免覆盖刚完成的关注/取消 */
      statusEpoch: 0,
    }
  },
  computed: {
    authStore() {
      return useAuthStore()
    },
    targetId() {
      // 雪花 ID 必须保持字符串，禁止 Number()
      return this.userId != null ? String(this.userId).trim() : ''
    },
    currentUserId() {
      const u = this.authStore.currentUser
      if (!u) return ''
      if (u.id != null) return String(u.id).trim()
      if (u.userId != null) return String(u.userId).trim()
      return ''
    },
    isSelf() {
      return !!this.currentUserId && this.currentUserId === this.targetId
    },
    hidden() {
      return this.hideSelf && this.isSelf
    },
    label() {
      if (this.loading) return this.followed ? '处理中' : '关注中'
      if (!this.followed) return '+ 关注'
      if (this.hoverCancel) return '取消关注'
      if (this.isMutual) return '已互关'
      return '已关注'
    },
  },
  watch: {
    modelValue(v) {
      this.followed = Boolean(v)
    },
    mutual(v) {
      this.isMutual = Boolean(v)
    },
    targetId() {
      this.statusEpoch += 1
      this.followed = Boolean(this.modelValue)
      this.isMutual = Boolean(this.mutual)
      if (this.fetchOnMount && this.authStore.isLoggedIn && !this.hidden) {
        this.refreshStatus()
      }
    },
  },
  mounted() {
    if (this.fetchOnMount && this.authStore.isLoggedIn && !this.hidden) {
      this.refreshStatus()
    }
  },
  methods: {
    requireLogin() {
      if (this.authStore.isLoggedIn) return true
      this.$router.push({
        name: 'login',
        query: { redirect: this.$route.fullPath },
      })
      return false
    },
    async refreshStatus() {
      if (!this.targetId || !this.authStore.isLoggedIn || this.isSelf) return
      const epoch = ++this.statusEpoch
      try {
        const data = await getFollowStatus(this.targetId, this.authStore.token)
        if (epoch !== this.statusEpoch) return
        this.applyResult(data, { emitChange: true })
      } catch (_) {
        // 静默：状态刷新失败不打扰用户
      }
    },
    applyResult(data, { emitChange = true } = {}) {
      if (!data) return
      this.followed = Boolean(data.followed)
      this.isMutual = Boolean(data.mutual)
      this.$emit('update:modelValue', this.followed)
      this.$emit('update:mutual', this.isMutual)
      if (emitChange) {
        this.$emit('change', {
          userId: this.targetId,
          followed: this.followed,
          mutual: this.isMutual,
          followCount: data.followCount,
          fansCount: data.fansCount,
        })
      }
    },
    async handleClick() {
      if (this.loading || this.disabled || this.hidden) return
      if (!this.requireLogin()) return
      if (!/^\d+$/.test(this.targetId)) {
        ElMessage.warning('用户无效')
        return
      }

      const nextFollow = !this.followed
      // 作废在途的 status 查询，防止把刚关注的结果冲掉
      this.statusEpoch += 1
      const epoch = this.statusEpoch
      this.loading = true
      try {
        const data = nextFollow
          ? await followUser(this.targetId, this.authStore.token)
          : await unfollowUser(this.targetId, this.authStore.token)
        if (epoch !== this.statusEpoch) return
        this.applyResult(data)
        this.hoverCancel = false
        ElMessage.success(nextFollow ? '关注成功' : '已取消关注')
      } catch (err) {
        if (epoch === this.statusEpoch) {
          ElMessage.error((err && err.message) || (nextFollow ? '关注失败' : '取消关注失败'))
        }
      } finally {
        if (epoch === this.statusEpoch) {
          this.loading = false
        }
      }
    },
  },
}
</script>

<style scoped>
.follow-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid transparent;
  border-radius: var(--meme-radius-sm);
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  user-select: none;
  transition:
    background 0.18s ease,
    color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.15s ease;
  white-space: nowrap;
  line-height: 1;
  color: var(--meme-text-inverse);
  background: var(--meme-primary);
}

.follow-btn:hover:not(:disabled):not(.is-followed) {
  background: var(--meme-primary-dark);
  box-shadow: 0 4px 12px var(--meme-primary-soft);
}

.follow-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.follow-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.follow-btn--sm {
  min-width: 72px;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  border-radius: 4px;
}

.follow-btn--md {
  min-width: 96px;
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
}

.follow-btn--lg {
  min-width: 120px;
  height: 40px;
  padding: 0 18px;
  font-size: 14px;
  border-radius: var(--meme-radius-md);
}

.follow-btn.is-followed {
  color: var(--meme-text-secondary);
  background: var(--meme-bg-muted);
  border-color: var(--meme-border);
  box-shadow: none;
}

.follow-btn.is-followed.is-hover-cancel {
  color: var(--meme-text-inverse);
  background: var(--meme-danger);
  border-color: transparent;
}

.follow-btn__spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: follow-spin 0.7s linear infinite;
}

.follow-btn.is-followed .follow-btn__spinner {
  border-color: rgba(100, 116, 139, 0.35);
  border-top-color: var(--meme-text-secondary);
}

.follow-btn.is-followed.is-hover-cancel .follow-btn__spinner {
  border-color: rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
}

@keyframes follow-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
