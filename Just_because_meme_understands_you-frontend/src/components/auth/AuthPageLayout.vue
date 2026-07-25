<template>
  <div class="auth-layout">
    <div class="auth-layout__glow auth-layout__glow--a" aria-hidden="true" />
    <div class="auth-layout__glow auth-layout__glow--b" aria-hidden="true" />

    <div class="auth-layout__shell">
      <aside class="auth-layout__brand">
        <span class="auth-layout__eyebrow">互联网梗文化社区</span>
        <h1 class="auth-layout__brand-title">
          只因<span class="auth-layout__accent">“梗”</span>懂你
        </h1>
        <p class="auth-layout__tagline">万物皆可梗，只有我懂你。</p>
        <p class="auth-layout__lead">{{ lead }}</p>
        <ul class="auth-layout__points" aria-hidden="true">
          <li>
            <i class="ri-sparkling-2-line" />
            <span>AI 搜梗，秒懂语境</span>
          </li>
          <li>
            <i class="ri-bookmark-3-line" />
            <span>收藏夹，灵感随手存</span>
          </li>
          <li>
            <i class="ri-community-line" />
            <span>和梗友一起冲浪</span>
          </li>
        </ul>
      </aside>

      <section class="auth-layout__panel">
        <header class="auth-layout__panel-head">
          <h2 class="auth-layout__panel-title">{{ title }}</h2>
          <p v-if="desc" class="auth-layout__panel-desc">{{ desc }}</p>
        </header>
        <div class="auth-layout__panel-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="auth-layout__panel-foot">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AuthPageLayout',
  props: {
    title: { type: String, required: true },
    desc: { type: String, default: '' },
    lead: {
      type: String,
      default: '登录后解锁收藏、发布与个性化推荐，让快乐常伴左右。',
    },
  },
}
</script>

<style scoped>
.auth-layout {
  position: relative;
  min-height: calc(100dvh - 120px);
  padding: 12px 0 36px;
  display: flex;
  align-items: stretch;
  justify-content: center;
  overflow: hidden;
}

.auth-layout__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(40px);
  opacity: 0.55;
}

.auth-layout__glow--a {
  width: 280px;
  height: 280px;
  top: -60px;
  left: -40px;
  background: color-mix(in srgb, var(--meme-primary) 28%, transparent);
}

.auth-layout__glow--b {
  width: 320px;
  height: 320px;
  right: -80px;
  bottom: 10%;
  background: color-mix(in srgb, #52c7b8 22%, transparent);
}

.auth-layout__shell {
  position: relative;
  z-index: 1;
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  border-radius: 20px;
  border: 1px solid var(--meme-border);
  background: var(--meme-bg-card);
  box-shadow: var(--meme-shadow-soft);
  overflow: hidden;
  animation: auth-rise 0.45s ease both;
}

.auth-layout__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 36px 36px;
  background: var(--meme-gradient-hero);
  overflow: hidden;
}

.auth-layout__brand::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--meme-hero-fade);
  pointer-events: none;
}

.auth-layout__eyebrow,
.auth-layout__brand-title,
.auth-layout__tagline,
.auth-layout__lead,
.auth-layout__points {
  position: relative;
  z-index: 1;
}

.auth-layout__eyebrow {
  display: block;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--meme-text-muted);
}

.auth-layout__brand-title {
  margin: 0 0 10px;
  font-size: clamp(28px, 3.2vw, 34px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: var(--meme-text);
}

.auth-layout__accent {
  background: linear-gradient(120deg, var(--meme-primary-dark) 0%, var(--meme-primary) 45%, #52c7b8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.auth-layout__tagline {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 650;
  color: var(--meme-primary);
}

.auth-layout__lead {
  margin: 0 0 28px;
  max-width: 34em;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
}

.auth-layout__points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.auth-layout__points li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--meme-text-secondary);
}

.auth-layout__points i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  font-size: 15px;
  color: var(--meme-primary);
  background: var(--meme-primary-soft);
}

.auth-layout__panel {
  display: flex;
  flex-direction: column;
  padding: 36px 32px 28px;
  background: var(--meme-bg-elevated);
}

.auth-layout__panel-head {
  margin-bottom: 22px;
}

.auth-layout__panel-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--meme-text);
}

.auth-layout__panel-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--meme-text-secondary);
}

.auth-layout__panel-body {
  flex: 1;
}

.auth-layout__panel-foot {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--meme-border);
  text-align: center;
  font-size: 13px;
  color: var(--meme-text-secondary);
}

@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 820px) {
  .auth-layout {
    padding: 4px 0 28px;
  }

  .auth-layout__shell {
    grid-template-columns: 1fr;
  }

  .auth-layout__brand {
    padding: 28px 24px 24px;
  }

  .auth-layout__lead {
    margin-bottom: 0;
  }

  .auth-layout__points {
    display: none;
  }

  .auth-layout__panel {
    padding: 28px 22px 22px;
  }
}
</style>
