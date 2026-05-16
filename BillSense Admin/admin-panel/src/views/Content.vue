<template>
  <div>
    <div class="page-header">
      <h1>Content</h1>
      <p>Educational trivia &amp; tutorials shown in the mobile app</p>
    </div>
    <div class="content">
      <div class="tabbar">
        <button :class="{ on: tab==='trivia' }" @click="tab='trivia'">
          <span class="material-icons">lightbulb</span> Trivia <em>{{ trivia.length }}</em>
        </button>
        <button :class="{ on: tab==='tut' }" @click="tab='tut'">
          <span class="material-icons">school</span> Tutorials <em>{{ tutorials.length }}</em>
        </button>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span>
        </button>
      </div>

      <div v-if="loading" class="state">Loading…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!list.length" class="state">No {{ tab==='trivia'?'trivia':'tutorials' }}.</div>

      <div v-else class="cards">
        <div v-for="it in list" :key="it._key" class="ccard" :class="{ busy: pending[it._key] }">
          <div class="chead">
            <span class="ctitle">{{ it.title || '(untitled)' }}</span>
            <span class="mtype">{{ it.mediaType && it.mediaType!=='none' ? it.mediaType : 'text' }}</span>
          </div>
          <p class="cdesc">{{ it.description || '—' }}</p>
          <img v-if="it.mediaUrl || it.downloadImageUrl" :src="it.mediaUrl || it.downloadImageUrl"
               class="cmedia" @error="e=>e.target.style.display='none'" />
          <div class="cfoot">
            <span class="cdate">{{ it.date || '' }} {{ it.time || '' }}</span>
            <button class="del" @click="del(it)" title="Delete"><span class="material-icons">delete</span></button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type==='err'?'error':'check_circle' }}</span>{{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, remove } from '../services/db.js'
export default {
  name: 'Content',
  data() { return { tab:'trivia', loading:true, error:'', trivia:[], tutorials:[], pending:{}, toast:null } },
  computed: { list() { return this.tab==='trivia'?this.trivia:this.tutorials } },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading=true; this.error=''
      try {
        const [tr, tu] = await Promise.all([ value('Trivia'), value('Tutorials') ])
        const norm = d => d && typeof d==='object'
          ? Object.entries(d).map(([_key,v])=>({_key,...v})).sort((a,b)=>String(b.date||'').localeCompare(String(a.date||''))) : []
        this.trivia = norm(tr); this.tutorials = norm(tu)
      } catch(e){ this.error=e.message } finally { this.loading=false }
    },
    notify(m,t='ok'){ this.toast={msg:m,type:t}; setTimeout(()=>this.toast=null,3000) },
    async del(it) {
      if (!confirm(`Delete "${it.title || it._key}"?`)) return
      const root = this.tab==='trivia' ? 'Trivia' : 'Tutorials'
      this.pending={...this.pending,[it._key]:true}
      try {
        await remove(`${root}/${it._key}`)
        this[this.tab==='trivia'?'trivia':'tutorials'] =
          this[this.tab==='trivia'?'trivia':'tutorials'].filter(x=>x._key!==it._key)
        this.notify('Deleted')
      } catch(e){ this.notify(e.message,'err') }
    }
  }
}
</script>

<style scoped>
.content { padding:1.5rem 2rem; }
.tabbar { display:flex; gap:.5rem; margin-bottom:1.25rem; align-items:center; }
.tabbar button { display:flex; align-items:center; gap:.45rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.55rem 1rem; border-radius:10px; font-size:.88rem; cursor:pointer; }
.tabbar button.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.tabbar button .material-icons { font-size:1.05rem; } .tabbar button em { font-style:normal; opacity:.7; font-size:.78rem; }
.refresh { margin-left:auto; padding:.5rem .7rem !important; }
.refresh .spin { animation:s 1s linear infinite; } @keyframes s { to { transform:rotate(360deg);} }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.cards { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:1rem; }
.ccard { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem 1.15rem; }
.ccard.busy { opacity:.5; pointer-events:none; }
.chead { display:flex; justify-content:space-between; align-items:flex-start; gap:.75rem; margin-bottom:.5rem; }
.ctitle { font-weight:600; }
.mtype { font-size:.66rem; background:rgba(99,102,241,.18); color:#a5b4fc; padding:.12rem .45rem; border-radius:999px; white-space:nowrap; }
.cdesc { margin:0 0 .65rem; font-size:.85rem; color:var(--text-muted); line-height:1.5; }
.cmedia { width:100%; border-radius:8px; margin-bottom:.65rem; }
.cfoot { display:flex; justify-content:space-between; align-items:center; }
.cdate { font-size:.74rem; color:var(--text-muted); }
.del { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.del:hover { color:#f87171; } .del .material-icons { font-size:1.1rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }
</style>
