<template>
  <div>
    <div class="page-header">
      <h1>Notifications &amp; Billy Chats</h1>
      <p>Push notification log and AI conversation review</p>
    </div>
    <div class="content">
      <div class="tabbar">
        <button :class="{ on: tab==='notif' }" @click="tab='notif'">
          <span class="material-icons">notifications</span> Notifications <em>{{ notifs.length }}</em>
        </button>
        <button :class="{ on: tab==='chats' }" @click="tab='chats'">
          <span class="material-icons">smart_toy</span> Billy Chats <em>{{ chats.length }}</em>
        </button>
      </div>

      <div v-if="loading" class="state">Loading…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>

      <div v-else-if="tab==='notif'">
        <div v-if="!notifs.length" class="state">No notifications.</div>
        <div v-else class="tablewrap">
          <table>
            <thead><tr><th>Title</th><th>Body</th><th>Topic</th><th>Status</th><th>Time</th></tr></thead>
            <tbody>
              <tr v-for="n in notifs" :key="n._key">
                <td class="tt">{{ n.title || '—' }}</td>
                <td class="bd">{{ n.body || '—' }}</td>
                <td><span class="chip">{{ n.topic || n.type || '—' }}</span></td>
                <td><span class="chip" :class="n.status==='read'?'ok':'mut'">{{ n.status || '—' }}</span></td>
                <td class="nw">{{ n.time || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else>
        <div v-if="!chats.length" class="state">No Billy chats.</div>
        <div v-else class="chatgrid">
          <div class="chatusers">
            <div v-for="c in chats" :key="c._key" class="cu" :class="{ on: selChat===c._key }" @click="selChat=c._key">
              <span class="material-icons">person</span>
              <div>
                <div class="cuid">{{ c._key.slice(0,18) }}</div>
                <div class="cucount">{{ c.msgs.length }} messages</div>
              </div>
            </div>
          </div>
          <div class="chatview">
            <div v-if="!curChat" class="state">Select a conversation.</div>
            <template v-else>
              <div v-for="(m,i) in curChat.msgs" :key="i" class="bubble" :class="{ ai: m.isAI }">
                <div class="btext">{{ m.text }}</div>
                <div class="bmeta">{{ m.isAI ? 'Billy' : 'User' }}<span v-if="m.time"> · {{ m.time }}</span></div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { value } from '../services/db.js'
export default {
  name: 'Notifications',
  data() { return { tab:'notif', loading:true, error:'', notifs:[], chats:[], selChat:'' } },
  computed: { curChat() { return this.chats.find(c=>c._key===this.selChat) || null } },
  async mounted() {
    try {
      const [nf, bc] = await Promise.all([ value('Notifications'), value('Billy Chats') ])
      this.notifs = nf && typeof nf==='object'
        ? Object.entries(nf).map(([_key,v])=>({_key,...v})).sort((a,b)=>String(b.time||'').localeCompare(String(a.time||''))) : []
      // Billy Chats: userKey -> { msgKey -> {message/text, sender, time} }
      this.chats = bc && typeof bc==='object'
        ? Object.entries(bc).map(([_key,thread]) => {
            const msgs = thread && typeof thread==='object'
              ? Object.values(thread).filter(m=>m&&typeof m==='object').map(m=>({
                  text: m.message || m.text || m.content || JSON.stringify(m),
                  isAI: /bot|ai|billy|assistant|model/i.test(String(m.senderId||m.sender||m.role||'')),
                  time: m.time || ''
                })) : []
            return { _key, msgs }
          }).filter(c=>c.msgs.length) : []
      if (this.chats.length) this.selChat = this.chats[0]._key
    } catch (e) { this.error = e.message } finally { this.loading = false }
  }
}
</script>

<style scoped>
.content { padding:1.5rem 2rem; }
.tabbar { display:flex; gap:.5rem; margin-bottom:1.25rem; }
.tabbar button { display:flex; align-items:center; gap:.45rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.55rem 1rem; border-radius:10px; font-size:.88rem; cursor:pointer; }
.tabbar button.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.tabbar button .material-icons { font-size:1.05rem; } .tabbar button em { font-style:normal; opacity:.7; font-size:.78rem; }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.tablewrap { overflow-x:auto; background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; }
table { width:100%; border-collapse:collapse; font-size:.85rem; }
th { text-align:left; padding:.8rem 1rem; color:var(--text-muted); font-weight:600; border-bottom:1px solid rgba(255,255,255,.08); white-space:nowrap; }
td { padding:.7rem 1rem; border-bottom:1px solid rgba(255,255,255,.04); vertical-align:top; }
.tt { font-weight:600; } .bd { color:var(--text-muted); max-width:360px; } .nw { white-space:nowrap; }
.chip { font-size:.7rem; padding:.12rem .5rem; border-radius:999px; background:rgba(148,163,184,.15); color:#94a3b8; }
.chip.ok { background:rgba(34,197,94,.15); color:#4ade80; } .chip.mut { background:rgba(148,163,184,.15); color:#94a3b8; }
.chatgrid { display:grid; grid-template-columns:260px 1fr; gap:1rem; }
@media (max-width:820px){ .chatgrid { grid-template-columns:1fr; } }
.chatusers { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:.5rem; align-self:start; max-height:70vh; overflow-y:auto; }
.cu { display:flex; gap:.5rem; align-items:center; padding:.6rem; border-radius:8px; cursor:pointer; }
.cu:hover { background:rgba(255,255,255,.04); } .cu.on { background:rgba(255,163,26,.15); }
.cu .material-icons { color:#ffa31a; font-size:1.1rem; }
.cuid { font-size:.78rem; font-family:monospace; } .cucount { font-size:.72rem; color:var(--text-muted); }
.chatview { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem 1.25rem; max-height:70vh; overflow-y:auto; display:flex; flex-direction:column; gap:.55rem; }
.bubble { max-width:78%; padding:.55rem .8rem; border-radius:12px; background:rgba(255,255,255,.05); align-self:flex-start; }
.bubble.ai { align-self:flex-end; background:rgba(255,163,26,.13); }
.btext { font-size:.87rem; line-height:1.45; white-space:pre-wrap; }
.bmeta { font-size:.68rem; color:var(--text-muted); margin-top:.2rem; }
</style>
