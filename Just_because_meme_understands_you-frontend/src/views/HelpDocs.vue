<template>
  <div class="help-page">
    <div class="help-shell">
      <header class="help-hero">
        <div class="help-hero__mark" aria-hidden="true">
          <i class="ri-book-open-line" />
        </div>
        <div class="help-hero__copy">
          <p class="help-kicker">HELP</p>
          <h1 class="help-title">{{ docTitle }}</h1>
          <p class="help-lead">
            用大白话说明怎么逛、怎么搜、怎么收藏、怎么发梗。找不到答案时，可翻文末常见问题，或去提交反馈。
          </p>
        </div>
      </header>

      <div v-if="pageLoading" class="help-loading">
        <span class="help-loading__spinner" />
        <span>加载帮助文档…</span>
      </div>

      <div v-else-if="loadError" class="help-error">
        <p>{{ loadError }}</p>
        <button type="button" class="help-retry" @click="loadDoc">重新加载</button>
      </div>

      <div v-else class="help-layout">
        <aside v-if="toc.length" class="help-toc" aria-label="目录">
          <p class="help-toc__title">目录</p>
          <nav class="help-toc__nav">
            <a
              v-for="item in toc"
              :key="item.id"
              :href="`#${item.id}`"
              class="help-toc__link"
              :class="{ 'is-h2': item.level === 2 }"
              @click.prevent="scrollToSection(item.id)"
            >
              {{ item.title }}
            </a>
          </nav>
        </aside>

        <article class="help-article">
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="help-markdown" v-html="html" />
          <footer v-if="version" class="help-meta">文档版本 {{ version }}</footer>
        </article>
      </div>
    </div>
  </div>
</template>

<script>
import { getHelpDoc } from '@/api/help'
import { renderHelpMarkdown } from '@/utils/helpMarkdown'
import { isAuthErrorHandled } from '@/utils/authSession'
import { toast } from '@/utils/uiFeedback'

export default {
  name: 'HelpDocsPage',
  data() {
    return {
      pageLoading: true,
      loadError: '',
      title: '帮助中心',
      html: '',
      toc: [],
      version: '',
    }
  },
  computed: {
    docTitle() {
      return this.title || '帮助中心'
    },
  },
  mounted() {
    this.loadDoc()
  },
  methods: {
    async loadDoc() {
      this.pageLoading = true
      this.loadError = ''
      try {
        const data = await getHelpDoc()
        this.title = data.title
        this.version = data.version
        const rendered = renderHelpMarkdown(data.content)
        this.html = rendered.html
        this.toc = (rendered.toc || []).filter((item) => item.level === 2)
        this.$nextTick(() => {
          const hash = String(this.$route.hash || '').replace(/^#/, '')
          if (hash) this.scrollToSection(hash)
        })
      } catch (e) {
        if (!isAuthErrorHandled(e)) {
          this.loadError = e.message || '加载失败'
          toast.error(this.loadError)
        }
      } finally {
        this.pageLoading = false
      }
    },
    scrollToSection(id) {
      const el = document.getElementById(id)
      if (!el) return
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      if (this.$router) {
        this.$router.replace({ hash: `#${id}` }).catch(() => {})
      }
    },
  },
}
</script>

<style scoped>
.help-page {
  padding: 20px 16px 56px;
  background:
    radial-gradient(ellipse 70% 45% at 0% 0%, var(--meme-primary-soft), transparent 55%),
    radial-gradient(ellipse 50% 40% at 100% 8%, rgba(82, 199, 184, 0.08), transparent 50%);
}

.help-shell {
  max-width: 1080px;
  margin: 0 auto;
}

.help-hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 22px;
}

.help-hero__mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(
    145deg,
    var(--meme-primary),
    color-mix(in srgb, var(--meme-primary) 55%, #52c7b8)
  );
  color: #fff;
  font-size: 24px;
  box-shadow: 0 10px 24px var(--meme-focus-ring);
}

.help-kicker {
  margin: 0 0 4px;
  font-size: 12px;
  letter-spacing: 0.14em;
  font-weight: 700;
  color: var(--meme-text-muted);
}

.help-title {
  margin: 0 0 8px;
  font-size: clamp(22px, 3vw, 28px);
  font-weight: 800;
  color: var(--meme-text);
  line-height: 1.25;
}

.help-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--meme-text-secondary);
  max-width: 52em;
}

.help-loading,
.help-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
  border-radius: 16px;
  border: 1px solid var(--meme-border);
  background: color-mix(in srgb, var(--meme-bg-elevated) 92%, transparent);
  color: var(--meme-text-secondary);
  font-size: 14px;
}

.help-loading__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--meme-border);
  border-top-color: var(--meme-primary);
  border-radius: 50%;
  animation: help-spin 0.8s linear infinite;
}

@keyframes help-spin {
  to {
    transform: rotate(360deg);
  }
}

.help-retry {
  border: 1px solid var(--meme-primary);
  background: transparent;
  color: var(--meme-primary);
  border-radius: 999px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
}

.help-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.help-toc {
  position: sticky;
  top: 88px;
  padding: 16px 14px;
  border-radius: 16px;
  border: 1px solid var(--meme-border);
  background: color-mix(in srgb, var(--meme-bg-elevated) 92%, transparent);
}

.help-toc__title {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--meme-text-muted);
}

.help-toc__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.help-toc__link {
  display: block;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--meme-text-secondary);
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.help-toc__link:hover {
  background: var(--meme-primary-soft);
  color: var(--meme-primary);
}

.help-article {
  padding: 22px 24px 28px;
  border-radius: 16px;
  border: 1px solid var(--meme-border);
  background: color-mix(in srgb, var(--meme-bg-elevated) 94%, transparent);
  min-width: 0;
}

.help-meta {
  margin-top: 28px;
  padding-top: 14px;
  border-top: 1px solid var(--meme-border);
  font-size: 12px;
  color: var(--meme-text-muted);
}

.help-markdown :deep(h1) {
  display: none;
}

.help-markdown :deep(h2) {
  margin: 1.6em 0 0.7em;
  padding-top: 0.35em;
  font-size: 1.35rem;
  font-weight: 800;
  color: var(--meme-text);
  scroll-margin-top: 96px;
}

.help-markdown :deep(h2:first-of-type) {
  margin-top: 0.2em;
}

.help-markdown :deep(h3) {
  margin: 1.25em 0 0.55em;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--meme-text);
  scroll-margin-top: 96px;
}

.help-markdown :deep(p),
.help-markdown :deep(li) {
  font-size: 14px;
  line-height: 1.75;
  color: var(--meme-text-secondary);
}

.help-markdown :deep(p) {
  margin: 0 0 0.85em;
}

.help-markdown :deep(ul),
.help-markdown :deep(ol) {
  margin: 0 0 1em;
  padding-left: 1.35em;
}

.help-markdown :deep(li + li) {
  margin-top: 0.35em;
}

.help-markdown :deep(strong) {
  color: var(--meme-text);
  font-weight: 700;
}

.help-markdown :deep(code) {
  padding: 0.1em 0.35em;
  border-radius: 6px;
  background: var(--meme-bg-muted);
  font-size: 0.92em;
  color: var(--meme-text);
}

.help-markdown :deep(a) {
  color: var(--meme-primary);
  text-decoration: none;
}

.help-markdown :deep(a:hover) {
  text-decoration: underline;
}

.help-markdown :deep(blockquote) {
  margin: 0 0 1em;
  padding: 10px 14px;
  border-left: 3px solid var(--meme-primary);
  border-radius: 0 10px 10px 0;
  background: var(--meme-primary-soft);
}

.help-markdown :deep(blockquote p) {
  margin: 0;
  color: var(--meme-text);
}

.help-markdown :deep(hr) {
  margin: 1.6em 0;
  border: none;
  border-top: 1px solid var(--meme-border);
}

.help-markdown :deep(.help-table-wrap) {
  overflow-x: auto;
  margin: 0 0 1.1em;
  border-radius: 12px;
  border: 1px solid var(--meme-border);
}

.help-markdown :deep(.help-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.help-markdown :deep(.help-table th),
.help-markdown :deep(.help-table td) {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--meme-border);
  color: var(--meme-text-secondary);
  vertical-align: top;
}

.help-markdown :deep(.help-table th) {
  background: color-mix(in srgb, var(--meme-bg-muted) 80%, transparent);
  color: var(--meme-text);
  font-weight: 700;
  white-space: nowrap;
}

.help-markdown :deep(.help-table tr:last-child td) {
  border-bottom: none;
}

@media (max-width: 860px) {
  .help-layout {
    grid-template-columns: 1fr;
  }

  .help-toc {
    position: static;
  }

  .help-toc__nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 6px;
  }

  .help-article {
    padding: 18px 16px 24px;
  }
}
</style>
