<template>
  <div>
    <div class="page-header">
      <h1>Support Tickets</h1>
      <p>User support requests &amp; conversations · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div class="toolbar">
        <div class="filters">
          <button v-for="f in filters" :key="f" :class="{ on: active === f }" @click="active = f">{{ f }}</button>
        </div>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span> Refresh
        </button>
      </div>

      <div v-if="loading" class="state">Loading tickets…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filtered.length" class="state">No tickets.</div>

      <div v-else class="tablewrap">
        <table>
          <thead><tr>
            <th>#</th><th>Concern</th><th>User</th><th>Date</th>
            <th>Status</th><th>Archived</th><th class="ac">Chat</th>
          </tr></thead>
          <tbody>
            <tr v-for="t in filtered" :key="t._key" :class="{ busy: pending[t._key] }">
              <td class="nw">#{{ t.ticketNo || '—' }}</td>
              <td><div class="tt">{{ t.concern || '(no concern)' }}</div></td>
              <td>{{ t.userName || '—' }}</td>
              <td class="nw">{{ t.date || '—' }}</td>
              <td>
                <select :value="t.status || 'Active'" class="sel" :class="badge(t.status)"
                        @change="setStatus(t, $event.target.value)">
                  <option>Active</option><option>In Progress</option>
                  <option>Resolved</option><option>Closed</option>
                </select>
              </td>
              <td>
                <button class="toggle" :class="{ on: t.isArchived }" @click="toggleArchive(t)">
                  {{ t.isArchived ? 'Yes' : 'No' }}
                </button>
              </td>
              <td class="ac">
                <button class="ico" :disabled="!chatCount(t)" @click="openChat(t)" title="View conversation">
                  <span class="material-icons">forum</span>
                  <em v-if="chatCount(t)">{{ chatCount(t) }}</em>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="chatFor" class="modal" @click.self="chatFor = null">
      <div class="modal-box">
        <div class="modal-head">
          <strong>Ticket #{{ chatFor.ticketNo }} — {{ chatFor.userName }}</strong>
          <button class="ico" @click="chatFor = null"><span class="material-icons">close</span></button>
        </div>
        <div class="chatlist">
          <div v-for="m in chatMessages(chatFor)" :key="m.messageId"
               class="bubble" :class="{ mine: m.senderId !== chatFor.userId }">
            <div class="btext">{{ m.message }}</div>
            <div class="bmeta">{{ m.senderId === chatFor.userId ? (chatFor.userName||'User') : 'Support' }}</div>
          </div>
          <div v-if="!chatMessages(chatFor).length" class="state">No messages.</div>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type === 'err' ? 'error' : 'check_circle' }}</span>{{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, patch } from '../services/db.js'
export default {
  name: 'SupportTickets',
  data() { return { rows: [], loading: true, error: '', active: 'All', pending: {}, chatFor: null, toast: null } },
  computed: {
    filters() { const s=new Set(['All']); this.rows.forEach(r=>s.add(r.status||'Active')); return [...s] },
    filtered() { return this.active==='All'?this.rows:this.rows.filter(r=>(r.status||'Active')===this.active) }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try {
        const d = await value('Support')
        this.rows = d && typeof d === 'object'
          ? Object.entries(d).map(([_key, v]) => ({ _key, ...v }))
              .sort((a,b)=>String(b.date||'').localeCompare(String(a.date||''))) : []
      } catch (e) { this.error = e.message } finally { this.loading = false }
    },
    notify(m,t='ok'){ this.toast={msg:m,type:t}; setTimeout(()=>this.toast=null,3000) },
    badge(s){ const v=(s||'').toLowerCase(); return v.includes('resolv')||v.includes('clos')?'ok':v.includes('progress')?'warn':'pend' },
    chatCount(t){ if(!t.Chats||typeof t.Chats!=='object')return 0; return Object.values(t.Chats).reduce((n,th)=>n+(th&&typeof th==='object'?Object.keys(th).length:0),0) },
    chatMessages(t){
      if(!t.Chats||typeof t.Chats!=='object')return []
      const out=[]
      for(const thread of Object.values(t.Chats))
        if(thread&&typeof thread==='object') for(const m of Object.values(thread)) if(m&&m.message) out.push(m)
      return out.sort((a,b)=>String(a.messageId||'').localeCompare(String(b.messageId||'')))
    },
    openChat(t){ this.chatFor=t },
    async setStatus(t,status){
      this.pending={...this.pending,[t._key]:true}
      try { await patch(`Support/${t._key}`,{status}); t.status=status; this.notify(`Ticket #${t.ticketNo} → ${status}`) }
      catch(e){ this.notify(e.message,'err') } finally { this.pending={...this.pending,[t._key]:false} }
    },
    async toggleArchive(t){
      const next=!t.isArchived
      this.pending={...this.pending,[t._key]:true}
      try { await patch(`Support/${t._key}`,{isArchived:next}); t.isArchived=next; this.notify(next?'Archived':'Unarchived') }
      catch(e){ this.notify(e.message,'err') } finally { this.pending={...this.pending,[t._key]:false} }
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.toolbar { display:flex; justify-content:space-between; align-items:center; gap:1rem; flex-wrap:wrap; margin-bottom:1rem; }
.filters { display:flex; gap:.5rem; flex-wrap:wrap; }
.filters button { background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.35rem .85rem; border-radius:999px; font-size:.8rem; cursor:pointer; }
.filters button.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.refresh { background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.4rem .85rem; border-radius:8px; font-size:.82rem; cursor:pointer; display:flex; align-items:center; gap:.35rem; }
.refresh .spin { animation:s 1s linear infinite; } @keyframes s { to { transform:rotate(360deg);} }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.tablewrap { overflow-x:auto; background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; }
table { width:100%; border-collapse:collapse; font-size:.85rem; }
th { text-align:left; padding:.8rem 1rem; color:var(--text-muted); font-weight:600; border-bottom:1px solid rgba(255,255,255,.08); white-space:nowrap; }
td { padding:.7rem 1rem; border-bottom:1px solid rgba(255,255,255,.04); vertical-align:middle; }
tr.busy { opacity:.5; pointer-events:none; }
.nw { white-space:nowrap; } .tt { font-weight:600; }
.sel { background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:6px; padding:.3rem .5rem; color:inherit; font-size:.8rem; cursor:pointer; }
.sel.ok{color:#4ade80;} .sel.warn{color:#fbbf24;} .sel.pend{color:#f87171;}
.toggle { background:rgba(148,163,184,.15); color:#94a3b8; border:0; border-radius:999px; padding:.2rem .7rem; font-size:.76rem; cursor:pointer; }
.toggle.on { background:rgba(255,163,26,.18); color:#ffa31a; }
.ac { text-align:right; }
.ico { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.25rem; display:inline-flex; align-items:center; gap:.2rem; }
.ico:hover:not(:disabled){ color:#fff; } .ico:disabled{ opacity:.3; cursor:not-allowed; }
.ico em { font-style:normal; font-size:.7rem; }
.modal { position:fixed; inset:0; background:rgba(0,0,0,.7); display:flex; align-items:center; justify-content:center; z-index:999; padding:2rem; }
.modal-box { background:var(--bg-card); border:1px solid rgba(255,255,255,.1); border-radius:12px; width:100%; max-width:560px; max-height:80vh; display:flex; flex-direction:column; }
.modal-head { display:flex; justify-content:space-between; align-items:center; padding:1rem 1.25rem; border-bottom:1px solid rgba(255,255,255,.08); }
.chatlist { overflow-y:auto; padding:1rem 1.25rem; display:flex; flex-direction:column; gap:.6rem; }
.bubble { max-width:75%; padding:.55rem .8rem; border-radius:12px; background:rgba(255,255,255,.05); align-self:flex-start; }
.bubble.mine { align-self:flex-end; background:rgba(255,163,26,.15); }
.btext { font-size:.87rem; line-height:1.4; }
.bmeta { font-size:.68rem; color:var(--text-muted); margin-top:.2rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }
</style>
