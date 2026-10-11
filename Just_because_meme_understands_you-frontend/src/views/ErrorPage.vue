<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-copy">
        <div class="error-brand">
          <img src="/icon.svg" alt="" />
          <span class="brand-name">只因“梗”懂你</span>
          <span class="brand-divider" aria-hidden="true"></span>
          <span class="brand-context">页面状态提示</span>
        </div>
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

        <ul v-if="displayTips.length" class="error-tips">
          <li
            v-for="(item, index) in displayTips"
            :key="index"
            class="error-tip-item"
          >
            <span class="tip-index">0{{ index + 1 }}</span>
            <span>{{ item }}</span>
          </li>
        </ul>

        <div class="error-actions">
          <vs-button
            color="primary"
            @click="goPrimary"
          >
            {{ primaryText }}
          </vs-button>
          <vs-button
            v-if="secondaryText"
            @click="goSecondary"
          >
            {{ secondaryText }}
          </vs-button>
        </div>
      </div>

      <div class="error-visual" aria-hidden="true">
        <div class="meme-illustration">
          <div class="illustration-header">
            <span class="illustration-kicker">梗图宇宙</span>
            <span class="illustration-status"><i></i>暂时离线</span>
          </div>

          <div class="illustration-orbit illustration-orbit--outer"></div>
          <div class="illustration-orbit illustration-orbit--inner"></div>
          <div class="illustration-code">{{ displayCode }}</div>
          <div class="illustration-logo">
            <img src="/icon.svg" alt="" />
          </div>

          <div class="meme-text-bubble">
            <span class="bubble-quote">“</span>
            <p>{{ bubbleText }}</p>
          </div>
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
  box-sizing: border-box;
  padding: clamp(24px, 5vw, 72px);
  display: grid;
  place-items: center;
  background: var(--meme-gradient-page);
}

.error-card {
  width: min(100%, 1160px);
  box-sizing: border-box;
  border: 1px solid var(--meme-border);
  border-radius: 30px;
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
  padding: clamp(28px, 5vw, 64px);
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  align-items: center;
  gap: clamp(32px, 5vw, 72px);
}

.error-copy {
  min-width: 0;
}

.error-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
  color: var(--meme-text-secondary);
}

.error-brand img {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
}

.brand-name {
  color: var(--meme-text);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.brand-divider {
  width: 1px;
  height: 16px;
  margin: 0 2px;
  background: var(--meme-border-strong);
}

.brand-context {
  font-size: 13px;
}

.error-code-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 18px;
}

.error-code-main {
  font-size: clamp(68px, 8vw, 92px);
  line-height: 0.92;
  font-weight: 780;
  letter-spacing: -0.075em;
  color: var(--meme-text);
}

.error-code-label {
  font-size: 13px;
  padding: 8px 13px;
  border-radius: 999px;
  background: var(--meme-primary-soft);
  color: var(--meme-accent-text);
  font-weight: 600;
}

.error-title {
  max-width: 600px;
  margin: 0 0 14px;
  font-size: clamp(26px, 3vw, 38px);
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.035em;
  color: var(--meme-text);
}

.error-subtitle {
  max-width: 560px;
  margin: 0 0 26px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--meme-text-secondary);
}

.error-tips {
  display: grid;
  gap: 12px;
  max-width: 580px;
  padding: 0;
  margin: 0 0 30px;
  list-style: none;
}

.error-tip-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--meme-text-secondary);
}

.tip-index {
  padding-top: 1px;
  color: var(--meme-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.error-visual {
  min-width: 0;
}

.meme-illustration {
  width: 100%;
  min-height: 390px;
  box-sizing: border-box;
  padding: 26px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 28px;
  background:
    radial-gradient(circle at 50% 48%, rgba(108, 196, 255, 0.42), transparent 38%),
    linear-gradient(145deg, #279edb 0%, #286ce0 48%, #172a70 100%);
  box-shadow: 0 26px 56px rgba(30, 83, 180, 0.23);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.meme-illustration::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 36px 36px;
  opacity: 0.3;
  mask-image: linear-gradient(180deg, #000 0%, transparent 88%);
  pointer-events: none;
}

.illustration-header,
.illustration-logo,
.meme-text-bubble {
  position: relative;
  z-index: 1;
}

.illustration-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.illustration-kicker {
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.illustration-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  background: rgba(9, 26, 78, 0.18);
  color: rgba(255, 255, 255, 0.88);
  font-size: 11px;
}

.illustration-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ae6c7;
  box-shadow: 0 0 10px rgba(154, 230, 199, 0.8);
}

.illustration-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.illustration-orbit--outer {
  width: min(76%, 286px);
  aspect-ratio: 1;
  border-style: dashed;
  transform: translate(-50%, -43%) rotate(-16deg);
}

.illustration-orbit--inner {
  width: min(58%, 218px);
  aspect-ratio: 1;
  opacity: 0.55;
}

.illustration-code {
  position: absolute;
  z-index: 0;
  left: 50%;
  top: 49%;
  color: rgba(255, 255, 255, 0.14);
  font-size: clamp(94px, 11vw, 138px);
  font-weight: 800;
  letter-spacing: -0.09em;
  line-height: 0.85;
  transform: translate(-50%, -40%);
  white-space: nowrap;
}

.illustration-logo {
  width: 124px;
  height: 124px;
  margin: auto;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 36px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 22px 42px rgba(10, 30, 85, 0.28);
  transform: rotate(-5deg);
}

.illustration-logo img {
  width: 94px;
  height: 94px;
}

.meme-text-bubble {
  width: min(100%, 300px);
  box-sizing: border-box;
  align-self: flex-end;
  padding: 13px 16px 13px 42px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 17px;
  background: rgba(10, 24, 68, 0.42);
  color: #fff;
  backdrop-filter: blur(12px);
}

.meme-text-bubble p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
}

.bubble-quote {
  position: absolute;
  top: 7px;
  left: 15px;
  color: rgba(255, 255, 255, 0.55);
  font-family: Georgia, serif;
  font-size: 30px;
  line-height: 1;
}

@media (max-width: 900px) {
  .error-card {
    grid-template-columns: minmax(0, 1fr);
    gap: 34px;
  }

  .meme-illustration {
    min-height: 340px;
  }
}

@media (max-width: 560px) {
  .error-page {
    min-height: calc(100vh - 56px);
    padding: 18px 14px 28px;
  }

  .error-card {
    padding: 26px 21px;
    border-radius: 23px;
    gap: 28px;
  }

  .error-brand {
    gap: 8px;
    margin-bottom: 27px;
  }

  .error-brand img {
    width: 30px;
    height: 30px;
  }

  .brand-name {
    font-size: 14px;
  }

  .brand-context {
    font-size: 12px;
  }

  .error-code-wrap {
    gap: 12px;
    margin-bottom: 16px;
  }

  .error-code-main {
    font-size: 66px;
  }

  .error-title {
    font-size: 27px;
  }

  .error-subtitle {
    font-size: 14px;
    margin-bottom: 22px;
  }

  .error-tip-item {
    font-size: 13px;
  }

  .error-actions {
    gap: 9px;
  }

  .meme-illustration {
    min-height: 286px;
    padding: 20px;
    border-radius: 22px;
  }

  .illustration-logo {
    width: 100px;
    height: 100px;
    border-radius: 29px;
  }

  .illustration-logo img {
    width: 76px;
    height: 76px;
  }

  .meme-text-bubble {
    width: 100%;
  }

  .illustration-code {
    font-size: 96px;
  }
}
</style>
