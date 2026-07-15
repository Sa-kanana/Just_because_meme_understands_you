<template>
  <el-drawer
    :model-value="visible"
    class="follow-relation-drawer"
    direction="rtl"
    size="420px"
    :with-header="true"
    destroy-on-close
    @close="onClose"
  >
    <template #header>
      <div class="relation-drawer-head">
        <h3 class="relation-drawer-title">{{ ownerNickname || '用户' }}的关系</h3>
        <p class="relation-drawer-sub">#{{ ownerUserId }}</p>
      </div>
    </template>

    <div class="relation-tabs" role="tablist">
      <button
        type="button"
        role="tab"
        class="relation-tab"
        :class="{ 'is-active': activeTab === 'following' }"
        :aria-selected="activeTab === 'following'"
        @click="switchTab('following')"
      >
        关注
        <span class="relation-tab-count">{{ formatCount(followCount) }}</span>
      </button>
      <button
        type="button"
        role="tab"
        class="relation-tab"
        :class="{ 'is-active': activeTab === 'followers' }"
        :aria-selected="activeTab === 'followers'"
        @click="switchTab('followers')"
      >
        粉丝
        <span class="relation-tab-count">{{ formatCount(fansCount) }}</span>
      </button>
    </div>

    <div v-if="loading && !list.length" class="relation-empty">
      <span class="meme-loading-spinner" />
      <span>加载中...</span>
    </div>
    <div v-else-if="errorMessage" class="relation-empty relation-empty--error">
      {{ errorMessage }}
    </div>
    <div v-else-if="!list.length" class="relation-empty">
      {{ activeTab === 'following' ? '还没有关注任何人' : '还没有粉丝' }}
    </div>
    <ul v-else class="relation-list">
      <li
        v-for="item in list"
        :key="`rel-${activeTab}-${item.userId}`"
        class="relation-item"
      >
        <button type="button" class="relation-user" @click="goProfile(item.userId)">
          <el-avatar :size="48" :src="item.avatar" class="relation-avatar">
            {{ (item.nickname || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="relation-user-meta">
            <div class="relation-name-row">
              <span class="relation-name">{{ item.nickname }}</span>
              <span v-if="item.mutual" class="relation-mutual-tag">互关</span>
            </div>
            <p class="relation-signature">
              {{ item.signature || '这个人很懒，什么都没留下~' }}
            </p>
          </div>
        </button>
        <FollowButton
          v-model="item.followedByMe"
          :user-id="item.userId"
          :mutual="item.mutual"
          size="sm"
          @change="(payload) => onItemFollowChange(item, payload)"
        />
      </li>
    </ul>

    <div v-if="list.length" class="relation-footer">
      <el-button
        v-if="hasMore"
        text
        type="primary"
        :loading="loadingMore"
        @click="loadMore"
      >
        加载更多
      </el-button>
      <span v-else class="relation-footer-end">没有更多了</span>
    </div>
  </el-drawer>
</template>

<script>
import { pageFollowing, pageFollowers } from '@/api/follow'
import { useAuthStore } from '@/stores/auth'
import { buildUserProfileLocation } from '@/utils/pageBreadcrumb'
import FollowButton from '@/components/user/FollowButton.vue'

export default {
  name: 'FollowRelationDrawer',
  components: { FollowButton },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    ownerUserId: {
      type: [String, Number],
      required: true,
    },
    ownerNickname: {
      type: String,
      default: '',
    },
    followCount: {
      type: Number,
      default: 0,
    },
    fansCount: {
      type: Number,
      default: 0,
    },
    /** following | followers */
    initialTab: {
      type: String,
      default: 'following',
    },
  },
  emits: ['update:visible', 'counts-change'],
  data() {
    return {
      activeTab: 'following',
      list: [],
      page: 1,
      hasMore: false,
      loading: false,
      loadingMore: false,
      errorMessage: '',
    }
  },
  computed: {
    authToken() {
      return useAuthStore().token
    },
  },
  watch: {
    visible(v) {
      if (v) {
        this.activeTab = this.initialTab === 'followers' ? 'followers' : 'following'
        this.reload()
      }
    },
  },
  methods: {
    formatCount(n) {
      const num = Number(n)
      if (!Number.isFinite(num) || num < 0) return '0'
      if (num >= 10000) return `${(num / 10000).toFixed(1).replace(/\.0$/, '')}万`
      return String(Math.floor(num))
    },
    onClose() {
      this.$emit('update:visible', false)
    },
    switchTab(tab) {
      if (this.activeTab === tab) return
      this.activeTab = tab
      this.reload()
    },
    async reload() {
      this.list = []
      this.page = 1
      this.hasMore = false
      this.errorMessage = ''
      this.loading = true
      try {
        await this.fetchPage(1, false)
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (!this.hasMore || this.loadingMore) return
      this.loadingMore = true
      try {
        await this.fetchPage(this.page + 1, true)
      } finally {
        this.loadingMore = false
      }
    },
    async fetchPage(pageNo, append) {
      const id = String(this.ownerUserId || '').trim()
      if (!/^\d+$/.test(id)) {
        this.errorMessage = '用户无效'
        return
      }
      const api = this.activeTab === 'followers' ? pageFollowers : pageFollowing
      try {
        const data = await api(id, { page: pageNo, size: 20 }, this.authToken)
        const next = Array.isArray(data.list) ? data.list : []
        this.list = append ? this.list.concat(next) : next
        this.page = data.page || pageNo
        this.hasMore = Boolean(data.hasMore)
        this.errorMessage = ''
      } catch (err) {
        if (!append) {
          this.list = []
          this.errorMessage = (err && err.message) || '加载失败'
        }
      }
    },
    goProfile(userId) {
      const location = buildUserProfileLocation(userId, { fromRoute: this.$route })
      this.onClose()
      this.$router.push(location)
    },
    onItemFollowChange(item, payload) {
      if (!item || !payload) return
      item.followedByMe = Boolean(payload.followed)
      item.mutual = Boolean(payload.mutual)
      this.$emit('counts-change', {
        followCount: payload.followCount,
        fansCount: payload.fansCount,
        targetUserId: payload.userId,
        followed: payload.followed,
        mutual: payload.mutual,
        tab: this.activeTab,
      })
    },
  },
}
</script>

<style scoped>
.relation-drawer-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.relation-drawer-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--meme-text);
}

.relation-drawer-sub {
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
}

.relation-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 4px;
  border-radius: 10px;
  background: var(--meme-bg-muted);
}

.relation-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--meme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.relation-tab.is-active {
  background: var(--meme-bg-card);
  color: var(--meme-primary);
  box-shadow: var(--meme-shadow-soft);
}

.relation-tab-count {
  font-weight: 500;
  color: var(--meme-text-muted);
  font-size: 12px;
}

.relation-tab.is-active .relation-tab-count {
  color: var(--meme-primary);
}

.relation-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.relation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 12px;
  transition: background 0.15s ease;
}

.relation-item:hover {
  background: var(--meme-bg-muted);
}

.relation-user {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.relation-avatar {
  flex-shrink: 0;
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
  font-weight: 700;
}

.relation-user-meta {
  min-width: 0;
  flex: 1;
}

.relation-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.relation-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--meme-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-mutual-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.relation-signature {
  margin: 0;
  font-size: 12px;
  color: var(--meme-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 220px;
  color: var(--meme-text-muted);
  font-size: 13px;
}

.relation-empty--error {
  color: var(--meme-danger);
}

.relation-footer {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}

.relation-footer-end {
  font-size: 12px;
  color: var(--meme-text-muted);
}
</style>
