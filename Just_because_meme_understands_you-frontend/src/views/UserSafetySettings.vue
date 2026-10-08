<template>
  <section class="safety-page">
    <header class="safety-hero">
      <div class="safety-icon"><i class="ri-shield-check-line" /></div>
      <div><p class="meme-section-kicker">COMMUNITY SAFETY</p><h1>社区安全</h1>
        <p>静音只影响你看到的内容；屏蔽还会阻止双方新增关注和评论。</p></div>
    </header>
    <section class="safety-card">
      <header class="card-heading"><div><h2>安全关系</h2><p>你可以随时撤销自己设置的关系。</p></div><span>{{ safety.items.length }} 项</span></header>
      <div v-if="safety.loading" class="empty">正在读取安全关系…</div>
      <div v-else-if="error" class="empty error">{{ error }} <button @click="load">重试</button></div>
      <div v-else-if="!safety.items.length" class="empty"><i class="ri-user-follow-line" /><strong>暂时没有安全关系</strong><span>你可以在用户主页静音或屏蔽账号。</span></div>
      <ul v-else>
        <li v-for="item in safety.items" :key="item.direction + item.type + item.targetUserId">
          <ui-avatar :size="42" :src="item.avatar" :fallback="item.nickname || 'U'" />
          <div class="identity"><strong>{{ item.nickname || ('用户 ' + item.targetUserId) }}</strong>
            <small>{{ item.direction === 'INCOMING' ? '对方已屏蔽你' : item.type === 'BLOCK' ? '已屏蔽 · 双方无法新增关注或评论' : '已静音 · 仅对你隐藏对方内容' }}</small></div>
          <span class="badge">{{ item.direction === 'INCOMING' ? '对方设置' : item.type === 'BLOCK' ? '屏蔽中' : '静音中' }}</span>
          <button v-if="item.direction !== 'INCOMING'" :disabled="busy === item.type + item.targetUserId" @click="remove(item)">
            {{ busy === item.type + item.targetUserId ? '处理中…' : item.type === 'BLOCK' ? '取消屏蔽' : '取消静音' }}
          </button>
        </li>
      </ul>
    </section>
  </section>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useUserSafetyStore } from '@/stores/userSafety'
import { toast } from '@/utils/uiFeedback'
const auth = useAuthStore()
const safety = useUserSafetyStore()
const error = ref('')
const busy = ref('')
const id = () => auth.currentUser?.id ?? auth.currentUser?.userId
async function load() { error.value = ''; try { await safety.load(id()) } catch (cause) { error.value = cause?.message || '安全关系读取失败' } }
async function remove(item) {
  busy.value = item.type + item.targetUserId
  try { await safety.remove(item.targetUserId, item.type); toast.success(item.type === 'BLOCK' ? '已取消屏蔽' : '已取消静音') }
  catch (cause) { toast.error(cause?.message || '操作失败，请稍后重试') }
  finally { busy.value = '' }
}
onMounted(load)
</script>
<style scoped>
.safety-page{max-width:1000px;margin:auto;padding:12px 0 48px;color:var(--meme-text)}
.safety-hero,.safety-card{padding:26px;border:1px solid var(--meme-border);border-radius:20px;background:var(--meme-bg-card);box-shadow:var(--meme-shadow-card)}
.safety-hero{display:flex;align-items:center;gap:18px;background:linear-gradient(120deg,var(--meme-primary-soft),var(--meme-bg-card) 70%)}
.safety-icon{display:grid;place-items:center;width:56px;height:56px;flex:none;border-radius:16px;background:var(--meme-bg-card);color:var(--meme-primary);font-size:27px}
h1{margin:4px 0 8px;font-size:30px}.safety-hero p:last-child,.card-heading p{margin:0;color:var(--meme-text-secondary);line-height:1.7}
.safety-card{margin-top:20px}.card-heading{display:flex;justify-content:space-between;align-items:center;padding-bottom:17px;border-bottom:1px solid var(--meme-border)}
.card-heading h2{margin:0 0 4px;font-size:19px}.card-heading>span{padding:6px 10px;border-radius:20px;background:var(--meme-primary-soft);color:var(--meme-primary);font-size:12px}
ul{list-style:none;margin:0;padding:0}li{display:flex;align-items:center;gap:14px;padding:15px 0;border-bottom:1px solid var(--meme-border)}li:last-child{border:0}
.identity{display:grid;gap:5px;min-width:0;flex:1}.identity strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}small{color:var(--meme-text-secondary)}
.badge{font-size:11px;color:var(--meme-primary);white-space:nowrap}button{padding:7px 11px;border:1px solid var(--meme-border);border-radius:9px;background:transparent;color:var(--meme-text-secondary);cursor:pointer;white-space:nowrap}
button:hover{color:#c74455;border-color:#c74455}button:disabled{opacity:.6;cursor:wait}.empty{display:grid;place-items:center;gap:8px;min-height:130px;color:var(--meme-text-secondary)}.empty i{font-size:28px;color:var(--meme-primary)}.error{color:#c74455}
@media(max-width:620px){.safety-hero{align-items:flex-start;padding:20px 16px}.safety-card{padding:18px 14px}li{flex-wrap:wrap;gap:10px}.identity{flex-basis:calc(100% - 60px)}}
</style>
