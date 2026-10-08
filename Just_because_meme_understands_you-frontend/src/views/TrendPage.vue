<template>
  <main class="page trend-page">
    <header class="trend-heading">
      <p class="trend-kicker">MEME PULSE</p>
      <h1>热梗趋势</h1>
      <p>看看最近哪些梗正在升温，也发现本周的共同话题。</p>
    </header>

    <section v-if="data.weeklyTopic" class="weekly-topic">
      <div>
        <span class="topic-label">{{ data.weeklyTopic.weekLabel }} · 本周主题</span>
        <h2>#{{ data.weeklyTopic.tagName }}</h2>
        <p v-if="data.weeklyTopic.provisional">
          近期热度数据仍在积累，本周主题暂按仍有关联公开梗的标签活跃度选取。
        </p>
        <p v-else>根据近 7 天公开梗的互动增长选出，来看看大家最近在聊什么。</p>
      </div>
      <vs-button color="primary" @click="searchTopic">浏览本周主题</vs-button>
    </section>

    <section class="trend-panel">
      <div class="panel-heading">
        <div>
          <h2>互动热度</h2>
          <p>浏览 + 点赞 × 4 + 评论 × 6</p>
        </div>
        <div class="range-switch" role="group" aria-label="趋势范围">
          <button :class="{ active: days === 7 }" @click="changeRange(7)">近 7 天</button>
          <button :class="{ active: days === 30 }" @click="changeRange(30)">近 30 天</button>
        </div>
      </div>
      <p v-if="!data.historyReady" class="history-note">
        趋势记录刚开始累积。至少有两个日期的快照后，这里会显示每日互动变化。
      </p>
      <div v-else class="daily-bars" aria-label="每日热度变化">
        <div v-for="day in data.daily" :key="day.date" class="daily-bar-item">
          <div class="bar-track"><span :style="{ height: barHeight(day.score) }" /></div>
          <strong>{{ formatScore(day.score) }}</strong>
          <time>{{ shortDate(day.date) }}</time>
        </div>
      </div>
    </section>

    <section class="trend-panel list-panel">
      <div class="panel-heading">
        <div>
          <h2>正在升温</h2>
          <p>按所选时间段的互动增长排序</p>
        </div>
      </div>
      <div v-if="loading" class="trend-loading">正在整理热度数据…</div>
      <ui-empty v-else-if="!data.topMemes.length" description="还没有足够的趋势数据" />
      <div v-else class="trend-list">
        <button
          v-for="(item, index) in data.topMemes"
          :key="item.memeId"
          class="trend-item"
          @click="$router.push(`/detail/${item.memeId}`)"
        >
          <span class="trend-rank">{{ String(index + 1).padStart(2, '0') }}</span>
          <ui-image :src="item.image" :alt="item.name" fit="cover" class="trend-cover" />
          <span class="trend-copy">
            <strong>{{ item.name }}</strong>
            <small>{{ item.tags.map((tag) => `#${tag}`).join(' · ') || '梗图社区' }}</small>
          </span>
          <span class="trend-score">+{{ formatScore(item.score) }}<small>热度</small></span>
        </button>
      </div>
      <p v-if="error" class="trend-error">{{ error }}</p>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMemeTrends } from '@/api/trends'

const router = useRouter()
const days = ref(7)
const loading = ref(false)
const error = ref('')
const data = ref({ topMemes: [], daily: [], weeklyTopic: null, historyReady: false })
const maxScore = computed(() => Math.max(1, ...data.value.daily.map((day) => Number(day.score) || 0)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getMemeTrends(days.value)
  } catch (cause) {
    error.value = cause?.message || '热度趋势暂时不可用'
  } finally {
    loading.value = false
  }
}

function changeRange(value) {
  if (days.value === value) return
  days.value = value
  load()
}

function searchTopic() {
  const topic = data.value.weeklyTopic
  if (topic) router.push({ name: 'search', query: { keyword: topic.tagName } })
}

function barHeight(score) {
  const value = Math.max(0, Number(score) || 0)
  return `${value ? Math.max(8, (value / maxScore.value) * 100) : 2}%`
}

function formatScore(value) {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(Number(value) || 0)
}

function shortDate(value) {
  return value ? value.slice(5).replace('-', '/') : ''
}

onMounted(load)
</script>

<style scoped>
.trend-page { max-width: 1180px; margin: 0 auto; padding: 12px 18px 48px; color: #19243a; }
.trend-heading { margin: 16px 0 24px; }
.trend-kicker { margin: 0 0 7px; color: #3772e8; font-size: 12px; font-weight: 800; letter-spacing: .16em; }
.trend-heading h1 { margin: 0; font-size: clamp(28px, 4vw, 38px); }
.trend-heading > p:last-child { color: #77839a; }
.weekly-topic, .trend-panel { border: 1px solid #e8edf6; border-radius: 22px; background: #fff; box-shadow: 0 12px 35px rgba(43, 69, 112, .06); }
.weekly-topic { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; padding: 24px 28px; background: linear-gradient(115deg, #edf5ff, #f8f4ff 70%, #fff); }
.topic-label { color: #5574b7; font-size: 13px; font-weight: 700; }
.weekly-topic h2 { margin: 8px 0 4px; color: #245fda; font-size: 28px; }
.weekly-topic p { margin: 0; color: #71809a; }
.trend-panel { margin-bottom: 18px; padding: 22px 24px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.panel-heading h2 { margin: 0; font-size: 20px; }
.panel-heading p { margin: 5px 0 0; color: #8791a5; font-size: 13px; }
.range-switch { display: flex; padding: 4px; border-radius: 12px; background: #f2f5fa; }
.range-switch button { padding: 8px 13px; border: 0; border-radius: 9px; color: #6e7b90; background: transparent; cursor: pointer; }
.range-switch button.active { color: #2364df; background: #fff; box-shadow: 0 2px 8px #203d6818; }
.history-note { padding: 16px; border-radius: 12px; color: #758198; background: #f7f9fc; text-align: center; }
.daily-bars { display: flex; align-items: end; gap: 8px; min-height: 190px; overflow-x: auto; padding: 12px 0 0; }
.daily-bar-item { display: flex; flex: 1 0 28px; flex-direction: column; align-items: center; gap: 6px; min-width: 28px; color: #7e8aa0; font-size: 11px; }
.bar-track { display: flex; align-items: end; width: min(28px, 80%); height: 126px; border-radius: 8px; background: #f2f5fb; overflow: hidden; }
.bar-track span { width: 100%; min-height: 2px; border-radius: 8px 8px 0 0; background: linear-gradient(180deg, #6fa5ff, #2868e7); }
.daily-bar-item strong { color: #53617a; font-size: 10px; }
.trend-list { display: grid; gap: 8px; }
.trend-item { display: flex; align-items: center; gap: 14px; width: 100%; padding: 10px; border: 0; border-radius: 14px; color: inherit; background: transparent; text-align: left; cursor: pointer; }
.trend-item:hover { background: #f5f8fd; }
.trend-rank { width: 28px; color: #8390a8; font-size: 13px; font-weight: 800; }
.trend-cover { flex: 0 0 58px; width: 58px; height: 58px; border-radius: 11px; background: #eff2f7; }
.trend-copy { display: grid; flex: 1; gap: 5px; min-width: 0; }
.trend-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.trend-copy small { overflow: hidden; color: #8a95a8; text-overflow: ellipsis; white-space: nowrap; }
.trend-score { color: #2768e7; font-size: 17px; font-weight: 800; text-align: right; }
.trend-score small { display: block; color: #98a2b2; font-size: 11px; font-weight: 500; }
.trend-loading, .trend-error { padding: 18px; color: #7d899b; text-align: center; }
.trend-error { color: #ca5050; }
@media (max-width: 560px) { .trend-page { padding: 8px 14px 32px; } .weekly-topic { align-items: flex-start; flex-direction: column; padding: 20px; } .trend-panel { padding: 18px 14px; border-radius: 17px; } .panel-heading { align-items: flex-start; flex-direction: column; } .trend-score { font-size: 14px; } }
</style>
