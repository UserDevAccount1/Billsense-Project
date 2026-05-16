<template>
  <div>
    <div class="page-header">
      <h1>Agents</h1>
      <p>Field agent accounts · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div v-if="loading" class="state">Loading agents…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!rows.length" class="state">No agents.</div>
      <div v-else class="cards">
        <div v-for="a in rows" :key="a._key" class="acard" :class="{ busy: pending[a._key] }">
          <div class="avatar"><span class="material-icons">support_agent</span></div>
          <div class="adata">
            <div class="aname">{{ a.name || '(no name)' }}</div>
            <div class="arow"><span class="material-icons">mail</span>{{ a.email || '—' }}</div>
            <div class="arow"><span class="material-icons">phone</span>{{ a.phone || '—' }}</div>
            <div class="arow muted">id: {{ a.id || a._key }}</div>
            <select :value="a.status || 'unverified'" class="sel" :class="badge(a.status)"
                    @change="setStatus(a, $event.target.value)">
              <option>unverified</option><option>verified</option>
              <option>active</option><option>suspended</option>
            </select>
          </div>
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
  name: 'Agents',
  data() { return { rows: [], loading: true, error: '', pending: {}, toast: null } },
  async mounted() {
    try {
      const d = await value('Agents')
      this.rows = d && typeof d === 'object'
        ? Object.entries(d).map(([_key, v]) => ({ _key, ...v })) : []
    } catch (e) { this.error = e.message } finally { this.loading = false }
  },
  methods: {
    notify(m,t='ok'){ this.toast={msg:m,type:t}; setTimeout(()=>this.toast=null,3000) },
    badge(s){ const v=(s||'').toLowerCase(); return v==='verified'||v==='active'?'ok':v==='suspended'?'err':'warn' },
    async setStatus(a,status){
      this.pending={...this.pending,[a._key]:true}
      try { await patch(`Agents/${a._key}`,{status}); a.status=status; this.notify(`${a.name||'Agent'} → ${status}`) }
      catch(e){ this.notify(e.message,'err') } finally { this.pending={...this.pending,[a._key]:false} }
    }
  }
}
</script>

<style scoped>
.content { padding:1.5rem 2rem; }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.cards { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:1rem; }
.acard { display:flex; gap:1rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem; }
.acard.busy { opacity:.5; pointer-events:none; }
.avatar { width:52px; height:52px; border-radius:50%; background:rgba(255,163,26,.15); display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.avatar .material-icons { color:#ffa31a; }
.adata { min-width:0; flex:1; }
.aname { font-weight:600; margin-bottom:.35rem; }
.arow { display:flex; align-items:center; gap:.4rem; font-size:.84rem; color:var(--text-muted); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.arow .material-icons { font-size:.95rem; }
.arow.muted { opacity:.6; font-size:.74rem; }
.sel { margin-top:.5rem; background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:6px; padding:.3rem .5rem; color:inherit; font-size:.8rem; cursor:pointer; }
.sel.ok{color:#4ade80;} .sel.warn{color:#fbbf24;} .sel.err{color:#f87171;}
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }
</style>
