<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-left">
        <div class="error-tag">只因“梗”懂你 · 异常页面</div>
        <div class="error-code-wrap">
          <span class="error-code-main">{{ displayCode }}</span>
          <span v-if="codeLabel" class="error-code-label">{{ codeLabel }}</span>
        </div>
        <h1 class="error-title">
          {{ displayTitle }}
        </h1>
        <p class="error-subtitle">
          {{ displaySubtitle }}
        </p>

        <div v-if="displayTips.length" class="error-tips">
          <p
            v-for="(item, index) in displayTips"
            :key="index"
            class="error-tip-item"
          >
            · {{ item }}
          </p>
        </div>

        <div class="error-actions">
          <el-button
            type="primary"
            round
            @click="goPrimary"
          >
            {{ primaryText }}
          </el-button>
          <el-button
            v-if="secondaryText"
            round
            @click="goSecondary"
          >
            {{ secondaryText }}
          </el-button>
        </div>
      </div>

      <div class="error-right">
        <div class="meme-badge">梗图宇宙出现了一个小 Bug</div>
        <div class="meme-illustration">
          <div class="meme-face">
            <span class="meme-eye left"></span>
            <span class="meme-eye right"></span>
            <span class="meme-mouth">_</span>
          </div>
          <div class="meme-text-bubble">
            <p>
              {{ bubbleText }}
            </p>
          </div>
          <div class="meme-glow"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ErrorPage',
  props: {
    /**
     * HTTP 状态码或业务错误编码
     * 例如：404 / 403 / 401 / 500 / 'biz'
     */
    code: {
      type: [Number, String],
      default: 500,
    },
    /**
     * 自定义主标题（不传则按 code 自动生成）
     */
    title: {
      type: String,
      default: '',
    },
    /**
     * 自定义副标题 / 错误原因（可从业务逻辑中透传）
     */
    message: {
      type: String,
      default: '',
    },
    /**
     * 业务中断时，是否允许返回上一页（默认 true）
     */
    allowBack: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    normalizedCode() {
      if (this.code === 'biz') return 'biz'
      const num = Number(this.code)
      return Number.isFinite(num) ? num : 500
    },
    displayCode() {
      if (this.normalizedCode === 'biz') return 'Oops'
      return this.normalizedCode
    },
    codeLabel() {
      if (this.normalizedCode === 404) return '资源不见了'
      if (this.normalizedCode === 403) return '你还没解锁这个梗'
      if (this.normalizedCode === 401) return '请先登录再玩梗'
      if (this.normalizedCode === 500) return '服务器脑子短路了'
      if (this.normalizedCode === 'biz') return '业务被打断了'
      return ''
    },
    displayTitle() {
      if (this.title) return this.title
      if (this.normalizedCode === 404) return '梗图跑丢了，找不到这个页面'
      if (this.normalizedCode === 403) return '这个梗只有特定人群才能看'
      if (this.normalizedCode === 401) return '登录一下，我们好继续玩梗'
      if (this.normalizedCode === 500) return '服务器刚刚整了个冷笑话（出错了）'
      if (this.normalizedCode === 'biz') return '剧情被打断，业务流程走不下去了'
      return '出现了一点小问题'
    },
    displaySubtitle() {
      if (this.message) return this.message
      if (this.normalizedCode === 404)
        return '你访问的梗图可能被下架、被移走，或者根本就没诞生过。'
      if (this.normalizedCode === 403)
        return '你当前账号暂无查看权限，可以尝试切换账号或联系管理员。'
      if (this.normalizedCode === 401)
        return '登录后才能保存、点赞和收藏你的专属梗图宇宙。'
      if (this.normalizedCode === 500)
        return '后台服务开了个不那么好笑的玩笑，请稍后再试一次。'
      if (this.normalizedCode === 'biz')
        return '当前操作因为业务规则被中断，可以尝试返回上一步或刷新页面。'
      return '别担心，我们会尽快修好它。'
    },
    displayTips() {
      if (this.normalizedCode === 404) {
        return [
          '检查一下链接是否输错了，或者返回首页重新打开看看。',
          '也可以在顶部搜索栏里，试试用关键词找找看。',
        ]
      }
      if (this.normalizedCode === 403 || this.normalizedCode === 401) {
        return [
          '确认当前登录账号是否正确，是否已经完成必要的权限开通。',
          '如果你觉得这是个误会，可以截图本页联系站点管理员。',
        ]
      }
      if (this.normalizedCode === 500) {
        return [
          '稍等几秒后尝试刷新当前页面。',
          '如果多次出现，可以把时间和操作步骤反馈给我们。',
        ]
      }
      if (this.normalizedCode === 'biz') {
        return [
          '有可能是表单校验未通过、状态过期或后端业务规则限制。',
          '可以根据提示调整输入，或返回上一步重新发起操作。',
        ]
      }
      return []
    },
    primaryText() {
      if (this.normalizedCode === 401) return '去登录'
      if (this.normalizedCode === 403) return '回到首页'
      if (this.normalizedCode === 404) return '回到首页'
      if (this.normalizedCode === 500) return '刷新试试'
      if (this.normalizedCode === 'biz')
        return this.allowBack ? '返回上一页' : '回到首页'
      return '回到首页'
    },
    secondaryText() {
      if (this.normalizedCode === 401) return '回到首页'
      if (this.normalizedCode === 403) return '重新登录'
      if (this.normalizedCode === 404) return ''
      if (this.normalizedCode === 500) return '回到首页'
      if (this.normalizedCode === 'biz' && this.allowBack) return '回到首页'
      return ''
    },
    bubbleText() {
      if (this.normalizedCode === 404)
        return '“我发誓刚才还在这的……” —— 走丢的梗图'
      if (this.normalizedCode === 403)
        return '“你还没解锁这个等级的笑点。”'
      if (this.normalizedCode === 401)
        return '“先登录一下，我们才能对上梗。”'
      if (this.normalizedCode === 500)
        return '“别慌，我只是短路了一下。” —— 服务器'
      if (this.normalizedCode === 'biz')
        return '“剧情被打断，等我把逻辑理顺再继续。”'
      return '“一点小小的异常，也挡不住玩梗的心。”'
    },
  },
  methods: {
    goPrimary() {
      if (this.normalizedCode === 500) {
        this.reloadPage()
        return
      }
      if (this.normalizedCode === 401) {
        this.goLogin()
        return
      }
      if (this.normalizedCode === 'biz' && this.allowBack) {
        this.goBack()
        return
      }
      this.goHome()
    },
    goSecondary() {
      if (this.normalizedCode === 401) {
        this.goHome()
        return
      }
      if (this.normalizedCode === 403) {
        this.goLogin()
        return
      }
      if (this.normalizedCode === 500) {
        this.goHome()
        return
      }
      if (this.normalizedCode === 'biz' && this.allowBack) {
        this.goHome()
        return
      }
    },
    goHome() {
      this.$router.replace({ path: '/' })
    },
    goLogin() {
      this.$router.replace({ path: '/login' })
    },
    goBack() {
      if (window.history.length > 1) {
        this.$router.back()
      } else {
        this.goHome()
      }
    },
    reloadPage() {
      if (typeof window !== 'undefined') {
        window.location.reload()
      }
    },
  },
}
</script>

<style scoped>
.error-page {
  min-height: calc(100vh - 56px);
  padding: 40px 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at top left, #e0f2fe 0, #eff6ff 35%, #f9fafb 100%);
}

.error-card {
  width: 100%;
  max-width: 980px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.12);
  padding: 32px 36px;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: 32px;
  position: relative;
  overflow: hidden;
}

.error-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 10% 0%, rgba(56, 189, 248, 0.15), transparent 60%),
    radial-gradient(circle at 90% 100%, rgba(129, 140, 248, 0.18), transparent 60%);
  opacity: 0.85;
  pointer-events: none;
}

.error-left,
.error-right {
  position: relative;
  z-index: 1;
}

.error-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.04);
  color: #4b5563;
  font-size: 12px;
  margin-bottom: 12px;
  backdrop-filter: blur(6px);
}

.error-code-wrap {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 6px;
}

.error-code-main {
  font-size: 44px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #111827;
}

.error-code-label {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(49, 138, 239, 0.08);
  color: #2563eb;
}

.error-title {
  margin: 8px 0 12px;
  font-size: 26px;
  line-height: 1.32;
  color: #111827;
}

.error-subtitle {
  margin: 0 0 16px;
  font-size: 14px;
  color: #4b5563;
}

.error-tips {
  margin-bottom: 24px;
}

.error-tip-item {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.error-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
}

.meme-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: #374151;
  margin-bottom: 10px;
  backdrop-filter: blur(6px);
}

.meme-illustration {
  width: 100%;
  max-width: 320px;
  aspect-ratio: 4 / 3;
  border-radius: 24px;
  background: radial-gradient(circle at 20% 0%, #38bdf8, #1d4ed8 45%, #020617 100%);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.6);
  position: relative;
  overflow: hidden;
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.meme-face {
  position: relative;
  align-self: center;
  width: 112px;
  height: 72px;
  border-radius: 999px;
  background: radial-gradient(circle at top, #fefce8, #fbbf24);
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.meme-eye {
  position: absolute;
  top: 24px;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #020617;
  box-shadow: 0 0 0 2px rgba(248, 250, 252, 0.7);
}

.meme-eye.left {
  left: 26px;
}

.meme-eye.right {
  right: 26px;
}

.meme-mouth {
  position: absolute;
  bottom: 18px;
  font-size: 20px;
  color: #111827;
}

.meme-text-bubble {
  align-self: flex-end;
  max-width: 180px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.86);
  color: #e5e7eb;
  font-size: 12px;
  padding: 8px 10px;
  line-height: 1.5;
  position: relative;
}

.meme-text-bubble::after {
  content: '';
  position: absolute;
  bottom: -8px;
  right: 24px;
  border-width: 8px 8px 0 0;
  border-style: solid;
  border-color: rgba(15, 23, 42, 0.86) transparent transparent transparent;
}

.meme-glow {
  position: absolute;
  inset: auto 0 0;
  height: 60px;
  background: radial-gradient(circle at 50% 0%, rgba(248, 250, 252, 0.3), transparent 70%);
  pointer-events: none;
}

@media (max-width: 900px) {
  .error-card {
    grid-template-columns: minmax(0, 1fr);
    padding: 24px 20px;
  }

  .error-right {
    align-items: flex-start;
  }

  .error-page {
    padding: 24px 16px;
  }
}
</style>

