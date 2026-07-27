/**
 * 帮助文档 Markdown → 安全 HTML（内容来自站内受控文稿）。
 * 支持：标题、段落、列表、表格、引用、分隔线、粗体、行内代码、链接、目录锚点。
 */
import { sanitizeExternalUrl } from './safeUrl'

function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function slugify(text) {
  return String(text || '')
    .trim()
    .toLowerCase()
    .replace(/[^\u4e00-\u9fa5a-z0-9\s-]/gi, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

function inlineFormat(text) {
  let out = escapeHtml(text)
  out = out.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, label, href) => {
    const safe = sanitizeExternalUrl(href) || (String(href || '').startsWith('#') ? String(href).trim() : '')
    if (!safe) return escapeHtml(label)
    const isHash = safe.startsWith('#')
    const attrs = isHash
      ? `href="${escapeHtml(safe)}"`
      : `href="${escapeHtml(safe)}" target="_blank" rel="noopener noreferrer"`
    return `<a ${attrs}>${escapeHtml(label)}</a>`
  })
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>')
  return out
}

function isTableSeparator(line) {
  return /^\|?[\s:-]+\|[\s|:-]+$/.test(line.trim())
}

function splitTableRow(line) {
  let raw = line.trim()
  if (raw.startsWith('|')) raw = raw.slice(1)
  if (raw.endsWith('|')) raw = raw.slice(0, -1)
  return raw.split('|').map((cell) => cell.trim())
}

function renderTable(rows) {
  if (!rows.length) return ''
  const head = rows[0]
  const body = rows.slice(1)
  let html = '<div class="help-table-wrap"><table class="help-table"><thead><tr>'
  head.forEach((cell) => {
    html += `<th>${inlineFormat(cell)}</th>`
  })
  html += '</tr></thead><tbody>'
  body.forEach((row) => {
    html += '<tr>'
    row.forEach((cell) => {
      html += `<td>${inlineFormat(cell)}</td>`
    })
    html += '</tr>'
  })
  html += '</tbody></table></div>'
  return html
}

/**
 * @param {string} markdown
 * @returns {{ html: string, toc: Array<{ id: string, title: string, level: number }> }}
 */
export function renderHelpMarkdown(markdown) {
  const lines = String(markdown || '').replace(/\r\n/g, '\n').split('\n')
  const toc = []
  const parts = []
  let i = 0
  let paragraph = []
  let listItems = null
  let listOrdered = false
  let quoteLines = null

  const flushParagraph = () => {
    if (!paragraph.length) return
    parts.push(`<p>${inlineFormat(paragraph.join(' '))}</p>`)
    paragraph = []
  }

  const flushList = () => {
    if (!listItems) return
    const tag = listOrdered ? 'ol' : 'ul'
    parts.push(
      `<${tag}>${listItems.map((item) => `<li>${inlineFormat(item)}</li>`).join('')}</${tag}>`
    )
    listItems = null
  }

  const flushQuote = () => {
    if (!quoteLines) return
    parts.push(`<blockquote><p>${inlineFormat(quoteLines.join(' '))}</p></blockquote>`)
    quoteLines = null
  }

  const flushBlocks = () => {
    flushParagraph()
    flushList()
    flushQuote()
  }

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    if (!trimmed) {
      flushBlocks()
      i += 1
      continue
    }

    if (trimmed === '---' || trimmed === '***') {
      flushBlocks()
      parts.push('<hr />')
      i += 1
      continue
    }

    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushBlocks()
      const level = heading[1].length
      const title = heading[2].replace(/\s*\{#[^}]+\}\s*$/, '').trim()
      let id = ''
      const explicit = /\{#([^}]+)\}\s*$/.exec(heading[2])
      if (explicit) {
        id = explicit[1]
      } else {
        // 兼容「1. 标题」与目录锚点
        id = slugify(title)
      }
      if (level <= 2) {
        toc.push({ id, title, level })
      }
      parts.push(`<h${level} id="${escapeHtml(id)}">${inlineFormat(title)}</h${level}>`)
      i += 1
      continue
    }

    if (trimmed.startsWith('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      flushBlocks()
      const rows = [splitTableRow(trimmed)]
      i += 2
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(splitTableRow(lines[i]))
        i += 1
      }
      parts.push(renderTable(rows))
      continue
    }

    if (trimmed.startsWith('> ')) {
      flushParagraph()
      flushList()
      if (!quoteLines) quoteLines = []
      quoteLines.push(trimmed.slice(2))
      i += 1
      continue
    }

    const ul = /^[-*]\s+(.+)$/.exec(trimmed)
    if (ul) {
      flushParagraph()
      flushQuote()
      if (!listItems || listOrdered) {
        flushList()
        listItems = []
        listOrdered = false
      }
      listItems.push(ul[1])
      i += 1
      continue
    }

    const ol = /^\d+\.\s+(.+)$/.exec(trimmed)
    if (ol) {
      flushParagraph()
      flushQuote()
      if (!listItems || !listOrdered) {
        flushList()
        listItems = []
        listOrdered = true
      }
      listItems.push(ol[1])
      i += 1
      continue
    }

    flushList()
    flushQuote()
    paragraph.push(trimmed)
    i += 1
  }

  flushBlocks()
  return { html: parts.join('\n'), toc }
}
