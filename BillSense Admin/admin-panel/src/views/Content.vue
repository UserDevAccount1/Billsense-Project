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
        <button class="add" @click="openAdd" :disabled="loading">
          <span class="material-icons">add</span> Add {{ tab==='trivia'?'trivia':'tutorial' }}
        </button>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span>
        </button>
      </div>

      <div v-if="loading" class="state">Loading…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!list.length" class="state">No {{ tab==='trivia'?'trivia':'tutorials' }} yet — click “Add”.</div>

      <div v-else class="cards">
        <div v-for="it in list" :key="it._key" class="ccard" :class="{ busy: pending[it._key] }">
          <div class="chead">
            <span class="ctitle">{{ it.title || '(untitled)' }}</span>
            <span class="mtype">{{ it.mediaType && it.mediaType!=='none' ? it.mediaType : 'text' }}</span>
          </div>
          <p class="cdesc">{{ it.description || '—' }}</p>
          <img v-if="it.downloadImageUrl || it.mediaUrl" :src="it.downloadImageUrl || it.mediaUrl"
               class="cmedia" @error="e=>e.target.style.display='none'" />
          <div class="cfoot">
            <span class="cdate">{{ it.date || '' }} {{ it.time || '' }}</span>
            <div class="actions">
              <button class="act" @click="openEdit(it)" title="Edit"><span class="material-icons">edit</span></button>
              <button class="act del" @click="del(it)" title="Delete"><span class="material-icons">delete</span></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Add / Edit modal -->
    <div v-if="form" class="modal-backdrop" @click.self="closeForm">
      <div class="modal">
        <div class="mhead">
          <h2>{{ form.key ? 'Edit' : 'Add' }} {{ tab==='trivia'?'trivia':'tutorial' }}</h2>
          <button class="x" @click="closeForm"><span class="material-icons">close</span></button>
        </div>
        <label>Title
          <input v-model="form.title" type="text" placeholder="e.g. The 20-peso bill is all about heroes!" />
        </label>
        <label>Description
          <textarea v-model="form.description" rows="5" placeholder="The text shown in the mobile app…"></textarea>
        </label>
        <label>Media type
          <select v-model="form.mediaType">
            <option value="text">Text only</option>
            <option value="image">Image</option>
            <option value="video">Video</option>
          </select>
        </label>
        <label v-if="form.mediaType==='image'">Image URL
          <input v-model="form.downloadImageUrl" type="url" placeholder="https://…/image.jpg" />
        </label>
        <label v-if="form.mediaType==='video'">Video URL
          <input v-model="form.downloadVideoUrl" type="url" placeholder="https://…/video.mp4" />
        </label>
        <div class="mfoot">
          <button class="ghost" @click="closeForm" :disabled="saving">Cancel</button>
          <button class="primary" @click="save" :disabled="saving || !form.title.trim()">
            <span v-if="saving" class="material-icons spin">progress_activity</span>
            {{ form.key ? 'Save changes' : 'Add' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type==='err'?'error':'check_circle' }}</span>{{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, remove, update, pushId } from '../services/db.js'

function nowParts() {
  const d = new Date(), p = n => String(n).padStart(2, '0')
  return {
    date: `${p(d.getDate())}-${p(d.getMonth() + 1)}-${d.getFullYear()}`,
    time: `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }
}

export default {
  name: 'Content',
  data() { return { tab: 'trivia', loading: true, error: '', trivia: [], tutorials: [], pending: {}, toast: null, form: null, saving: false } },
  computed: {
    list() { return this.tab === 'trivia' ? this.trivia : this.tutorials },
    rootPath() { return this.tab === 'trivia' ? 'Trivia' : 'Tutorials' }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try {
        const [tr, tu] = await Promise.all([value('Trivia'), value('Tutorials')])
        const norm = d => d && typeof d === 'object'
          ? Object.entries(d).map(([_key, v]) => ({ _key, ...v })).sort((a, b) => String(b.date || '').localeCompare(String(a.date || ''))) : []
        this.trivia = norm(tr); this.tutorials = norm(tu)
      } catch (e) { this.error = e.message } finally { this.loading = false }
    },
    notify(m, t = 'ok') { this.toast = { msg: m, type: t }; setTimeout(() => this.toast = null, 3000) },
    openAdd() { this.form = { key: null, title: '', description: '', mediaType: 'text', downloadImageUrl: '', downloadVideoUrl: '' } },
    openEdit(it) {
      this.form = {
        key: it._key,
        title: it.title || '',
        description: it.description || '',
        mediaType: (it.mediaType && it.mediaType !== 'none') ? it.mediaType : 'text',
        downloadImageUrl: it.downloadImageUrl || it.mediaUrl || '',
        downloadVideoUrl: it.downloadVideoUrl || ''
      }
    },
    closeForm() { if (!this.saving) this.form = null },
    async save() {
      const f = this.form
      if (!f.title.trim()) return
      this.saving = true
      const root = this.rootPath
      // Match the mobile app's model fields exactly (Trivia.java / Tutorials.java).
      const fields = {
        title: f.title.trim(),
        description: f.description.trim(),
        mediaType: f.mediaType,
        downloadImageUrl: f.mediaType === 'image' ? (f.downloadImageUrl || '').trim() : '',
        downloadVideoUrl: f.mediaType === 'video' ? (f.downloadVideoUrl || '').trim() : ''
      }
      try {
        if (f.key) {
          // Edit existing — merge changed fields, keep id/date/time.
          await update(`${root}/${f.key}`, fields)
          this.notify('Saved')
        } else {
          // Create — generate a Firebase-style key, store id=key + timestamp.
          const key = pushId()
          const { date, time } = nowParts()
          await update(`${root}/${key}`, { id: key, ...fields, date, time })
          this.notify('Added')
        }
        this.form = null
        await this.load()
      } catch (e) { this.notify(e.message, 'err') } finally { this.saving = false }
    },
    async del(it) {
      if (!confirm(`Delete "${it.title || it._key}"?`)) return
      const root = this.rootPath
      this.pending = { ...this.pending, [it._key]: true }
      try {
        await remove(`${root}/${it._key}`)
        const arr = this.tab === 'trivia' ? 'trivia' : 'tutorials'
        this[arr] = this[arr].filter(x => x._key !== it._key)
        this.notify('Deleted')
      } catch (e) { this.notify(e.message, 'err') }
      finally { const p = { ...this.pending }; delete p[it._key]; this.pending = p }
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
.tabbar .add { margin-left:auto; background:rgba(34,197,94,.15); color:#4ade80; border-color:rgba(34,197,94,.3); }
.refresh { padding:.5rem .7rem !important; }
.refresh .spin, .spin { animation:s 1s linear infinite; } @keyframes s { to { transform:rotate(360deg);} }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.cards { display:grid; grid-template-columns:repeat(auto-fill,minmax(320px,1fr)); gap:1rem; }
.ccard { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem 1.15rem; }
.ccard.busy { opacity:.5; pointer-events:none; }
.chead { display:flex; justify-content:space-between; align-items:flex-start; gap:.75rem; margin-bottom:.5rem; }
.ctitle { font-weight:600; }
.mtype { font-size:.66rem; background:rgba(99,102,241,.18); color:#a5b4fc; padding:.12rem .45rem; border-radius:999px; white-space:nowrap; }
.cdesc { margin:0 0 .65rem; font-size:.85rem; color:var(--text-muted); line-height:1.5; white-space:pre-wrap; }
.cmedia { width:100%; border-radius:8px; margin-bottom:.65rem; }
.cfoot { display:flex; justify-content:space-between; align-items:center; }
.cdate { font-size:.74rem; color:var(--text-muted); }
.actions { display:flex; gap:.25rem; }
.act { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.act:hover { color:#a5b4fc; } .act.del:hover { color:#f87171; } .act .material-icons { font-size:1.1rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }
/* modal */
.modal-backdrop { position:fixed; inset:0; background:rgba(0,0,0,.6); display:flex; align-items:center; justify-content:center; z-index:1100; padding:1rem; }
.modal { background:var(--bg-card,#1a1d29); border:1px solid rgba(255,255,255,.1); border-radius:14px; width:100%; max-width:520px; padding:1.5rem; max-height:90vh; overflow:auto; }
.mhead { display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem; }
.mhead h2 { margin:0; font-size:1.1rem; text-transform:capitalize; }
.x { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.modal label { display:block; font-size:.8rem; color:var(--text-muted); margin-bottom:.9rem; }
.modal input, .modal textarea, .modal select { width:100%; margin-top:.35rem; background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.12); border-radius:8px; color:var(--text,#fff); padding:.6rem .7rem; font-size:.9rem; font-family:inherit; box-sizing:border-box; }
.modal textarea { resize:vertical; }
.mfoot { display:flex; justify-content:flex-end; gap:.6rem; margin-top:.5rem; }
.mfoot button { display:flex; align-items:center; gap:.35rem; padding:.55rem 1.1rem; border-radius:8px; font-size:.88rem; cursor:pointer; border:1px solid transparent; }
.mfoot .ghost { background:none; border-color:rgba(255,255,255,.15); color:var(--text-muted); }
.mfoot .primary { background:#ffa31a; color:#1a1d29; font-weight:600; }
.mfoot .primary:disabled { opacity:.5; cursor:not-allowed; }
.mfoot .primary .material-icons { font-size:1rem; }
</style>
