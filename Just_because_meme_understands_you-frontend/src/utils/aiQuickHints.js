import { getMemeList } from '@/api/meme'

const FALLBACK_HINTS = [
  { text: '上班摸鱼被发现怎么回' },
  { text: '考试周的自我安慰' },
  { text: '朋友说下次一定' },
]

/** @returns {Array<{ text: string }>} */
export function getFallbackHints() {
  return FALLBACK_HINTS.map((item) => ({ ...item }))
}

const QUESTION_TEMPLATES = [
  (name) => `${name}是什么意思`,
  (name) => `${name}适合什么场合`,
  (name) => `怎么用「${name}」接话`,
  (name) => `${name}的出处是什么`,
  (name) => `什么场景会用到「${name}」`,
]

/**
 * @param {unknown[]} list
 * @returns {unknown[]}
 */
function shuffle(list) {
  const arr = Array.isArray(list) ? [...list] : []
  for (let i = arr.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    const tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
  }
  return arr
}

/**
 * 从梗名生成用户可读的搜梗问句。
 * @param {string} name
 * @returns {string}
 */
function buildQuestion(name) {
  const title = String(name || '').trim()
  if (!title) return ''
  const template = QUESTION_TEMPLATES[Math.floor(Math.random() * QUESTION_TEMPLATES.length)]
  return template(title)
}

/**
 * 随机拉取若干站内梗，拼成快捷问答。
 * @param {number} [count=3]
 * @returns {Promise<Array<{ text: string }>>}
 */
export async function loadRandomMemeHints(count = 3) {
  const size = Math.max(3, Math.min(Number(count) || 3, 6))
  try {
    const page = 1 + Math.floor(Math.random() * 3)
    const sort = Math.random() < 0.5 ? 'hot' : 'new'
    const data = await getMemeList({ page, sort, size: 16 })
    const names = [
      ...new Set(
        (data.list || [])
          .map((item) => (item && item.name != null ? String(item.name).trim() : ''))
          .filter(Boolean)
      ),
    ]
    if (!names.length) {
      return shuffle(FALLBACK_HINTS).slice(0, size)
    }

    const picked = shuffle(names).slice(0, size)
    const hints = picked
      .map((name) => ({ text: buildQuestion(name) }))
      .filter((item) => item.text)

    if (hints.length >= size) return hints.slice(0, size)
    if (hints.length) return hints

    return shuffle(FALLBACK_HINTS).slice(0, size)
  } catch {
    return shuffle(FALLBACK_HINTS).slice(0, size)
  }
}
