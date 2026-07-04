<template>
  <div>
    <div class="page-header">
      <h1>Documentation</h1>
      <p>System documentation library — mobile app, web app, infrastructure &amp; ML models</p>
    </div>
    <div class="content">
      <div class="tabbar">
        <button v-for="c in categories" :key="c.key" :class="{ on: filter===c.key }" @click="filter=c.key">
          <span class="material-icons">{{ c.icon }}</span> {{ c.label }} <em>{{ countFor(c.key) }}</em>
        </button>
        <button class="add" @click="openAdd" :disabled="loading">
          <span class="material-icons">upload_file</span> Add document
        </button>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span>
        </button>
      </div>

      <div v-if="loading" class="state">Loading…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!list.length" class="state">No documents yet — click "Add document".</div>

      <table v-else class="doc-table">
        <thead>
          <tr>
            <th>Document</th>
            <th>Category</th>
            <th>Version</th>
            <th>Size</th>
            <th>Updated</th>
            <th class="right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in list" :key="d._key" :class="{ busy: pending[d._key] }">
            <td>
              <div class="dtitle"><span class="material-icons pdficon">picture_as_pdf</span> {{ d.title }}</div>
              <div class="ddesc">{{ d.description }}</div>
            </td>
            <td><span class="cat" :class="d.category">{{ catLabel(d.category) }}</span></td>
            <td>{{ d.version || '—' }}</td>
            <td>{{ d.sizeLabel || '—' }}</td>
            <td>{{ d.updatedAt ? d.updatedAt.slice(0, 10) : '—' }}</td>
            <td class="right">
              <button class="act" title="View / download" @click="openDoc(d)"><span class="material-icons">open_in_new</span></button>
              <button class="act" title="Edit details" @click="openEdit(d)"><span class="material-icons">edit</span></button>
              <button class="act del" title="Delete" @click="del(d)"><span class="material-icons">delete</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add / Edit modal -->
    <div v-if="form" class="modal-backdrop" @click.self="closeForm">
      <div class="modal">
        <div class="mhead">
          <h2>{{ form.key ? 'Edit document' : 'Add document' }}</h2>
          <button class="x" @click="closeForm"><span class="material-icons">close</span></button>
        </div>
        <label>Title
          <input v-model="form.title" type="text" placeholder="e.g. BillSense Mobile App — User Manual" />
        </label>
        <label>Category
          <select v-model="form.category">
            <option value="mobile">Mobile App</option>
            <option value="webapp">Web App (Admin Panel)</option>
            <option value="infrastructure">Infrastructure</option>
            <option value="ml-model">ML Models &amp; Scanning</option>
            <option value="other">Other</option>
          </select>
        </label>
        <label>Version
          <input v-model="form.version" type="text" placeholder="e.g. 1.0 / app 1.5.18" />
        </label>
        <label>Description
          <textarea v-model="form.description" rows="3" placeholder="What this document covers…"></textarea>
        </label>
        <label v-if="!form.key">PDF file
          <input type="file" accept="application/pdf" @change="onFilePicked" :disabled="saving" />
          <span class="hint">Max ~700 KB per upload (text PDFs are fine). Larger files: upload to Storage manually and use Edit to set the URL.</span>
        </label>
        <label v-if="form.key">File URL
          <input v-model="form.fileUrl" type="url" placeholder="https://firebasestorage.googleapis.com/…" />
        </label>
        <div class="mfoot">
          <button class="ghost" @click="closeForm" :disabled="saving">Cancel</button>
          <button class="primary" @click="save" :disabled="saving || !form.title.trim()">
            <span v-if="saving" class="material-icons spin">progress_activity</span>
            {{ saving ? saveState : (form.key ? 'Save changes' : 'Upload & add') }}
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
import { value, remove, update, pushId, proxyBase } from '../services/db.js'

const CATS = [
  { key: 'all', label: 'All', icon: 'folder' },
  { key: 'mobile', label: 'Mobile App', icon: 'smartphone' },
  { key: 'webapp', label: 'Web App', icon: 'web' },
  { key: 'infrastructure', label: 'Infrastructure', icon: 'dns' },
  { key: 'ml-model', label: 'ML & Scanning', icon: 'model_training' }
]

export default {
  name: 'Documentation',
  data() {
    return {
      loading: true, error: '', docs: [], filter: 'all', pending: {},
      toast: null, form: null, saving: false, saveState: '', pendingFile: null,
      categories: CATS
    }
  },
  computed: {
    list() {
      const d = this.filter === 'all' ? this.docs : this.docs.filter(x => x.category === this.filter)
      return [...d].sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')))
    }
  },
  mounted() { this.load() },
  methods: {
    catLabel(k) { const c = CATS.find(c => c.key === k); return c ? c.label : (k || 'Other') },
    countFor(k) { return k === 'all' ? this.docs.length : this.docs.filter(d => d.category === k).length },
    notify(m, t = 'ok', ms = 3500) { this.toast = { msg: m, type: t }; setTimeout(() => this.toast = null, ms) },
    async load() {
      this.loading = true; this.error = ''
      try {
        const obj = await value('Documentation')
        this.docs = obj && typeof obj === 'object'
          ? Object.entries(obj).map(([_key, v]) => ({ _key, ...v })) : []
      } catch (e) { this.error = e.message } finally { this.loading = false }
    },
    openDoc(d) {
      if (!d.fileUrl) return this.notify('No file URL on this record', 'err')
      window.open(d.fileUrl, '_blank', 'noopener')
    },
    openAdd() {
      this.pendingFile = null
      this.form = { key: null, _newKey: pushId(), title: '', category: 'mobile', version: '', description: '', fileUrl: '' }
    },
    openEdit(d) {
      this.pendingFile = null
      this.form = { key: d._key, title: d.title || '', category: d.category || 'other', version: d.version || '', description: d.description || '', fileUrl: d.fileUrl || '' }
    },
    closeForm() { if (!this.saving) { this.form = null; this.pendingFile = null } },
    onFilePicked(e) {
      const f = e.target.files && e.target.files[0]
      if (!f) return
      if (f.type !== 'application/pdf') { this.notify('Please pick a PDF file', 'err'); return }
      this.pendingFile = f
      if (!this.form.title.trim()) this.form.title = f.name.replace(/\.pdf$/i, '')
    },
    async save() {
      const f = this.form
      if (!f.title.trim()) return
      if (!f.key && !this.pendingFile) { this.notify('Pick a PDF file to upload', 'err'); return }
      this.saving = true
      try {
        let sizeLabel = null
        if (this.pendingFile) {
          this.saveState = 'Uploading…'
          const buf = await this.pendingFile.arrayBuffer()
          let bin = ''
          const bytes = new Uint8Array(buf)
          for (let i = 0; i < bytes.length; i += 0x8000) {
            bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000))
          }
          const dataBase64 = btoa(bin)
          if (dataBase64.length > 950000) throw new Error('PDF too large for panel upload (~700 KB max)')
          const r = await fetch(`${proxyBase()}/api/storage/upload`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ folder: 'docs', key: f.key || f._newKey, contentType: 'application/pdf', dataBase64 })
          })
          const j = await r.json().catch(() => ({}))
          if (!r.ok || j.ok === false || !j.data?.url) throw new Error(j.error || `upload failed: HTTP ${r.status}`)
          f.fileUrl = j.data.url
          sizeLabel = `${(bytes.length / 1024).toFixed(0)} KB`
        }
        this.saveState = 'Saving…'
        const key = f.key || f._newKey
        const fields = {
          id: key,
          title: f.title.trim(),
          category: f.category,
          version: (f.version || '').trim(),
          description: (f.description || '').trim(),
          fileUrl: (f.fileUrl || '').trim(),
          updatedAt: new Date().toISOString()
        }
        if (sizeLabel) fields.sizeLabel = sizeLabel
        await update(`Documentation/${key}`, fields)
        this.notify(f.key ? 'Saved' : 'Document added')
        this.form = null; this.pendingFile = null
        await this.load()
      } catch (e) { this.notify(e.message, 'err', 6000) } finally { this.saving = false; this.saveState = '' }
    },
    async del(d) {
      if (!confirm(`Delete "${d.title}"? (The record is removed; the stored file stays in Firebase Storage.)`)) return
      this.pending = { ...this.pending, [d._key]: true }
      try {
        await remove(`Documentation/${d._key}`)
        this.docs = this.docs.filter(x => x._key !== d._key)
        this.notify('Deleted')
      } catch (e) { this.notify(e.message, 'err') }
      finally { const p = { ...this.pending }; delete p[d._key]; this.pending = p }
    }
  }
}
</script>

<style scoped>
.content { padding:1.5rem 2rem; }
.tabbar { display:flex; gap:.5rem; margin-bottom:1.25rem; align-items:center; flex-wrap:wrap; }
.tabbar button { display:flex; align-items:center; gap:.45rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.55rem 1rem; border-radius:10px; font-size:.88rem; cursor:pointer; }
.tabbar button.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.tabbar button .material-icons { font-size:1.05rem; } .tabbar button em { font-style:normal; opacity:.7; font-size:.78rem; }
.tabbar .add { margin-left:auto; background:rgba(34,197,94,.15); color:#4ade80; border-color:rgba(34,197,94,.3); }
.refresh { padding:.5rem .7rem !important; }
.spin { animation:s 1s linear infinite; } @keyframes s { to { transform:rotate(360deg);} }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.doc-table { width:100%; border-collapse:collapse; background:var(--bg-card); border-radius:12px; overflow:hidden; }
.doc-table th { text-align:left; font-size:.72rem; text-transform:uppercase; letter-spacing:.05em; color:var(--text-muted); padding:.8rem 1rem; border-bottom:1px solid rgba(255,255,255,.08); }
.doc-table td { padding:.85rem 1rem; border-bottom:1px solid rgba(255,255,255,.05); font-size:.87rem; vertical-align:top; }
.doc-table tr.busy { opacity:.5; pointer-events:none; }
.right { text-align:right; white-space:nowrap; }
.dtitle { font-weight:600; display:flex; align-items:center; gap:.45rem; }
.pdficon { font-size:1.1rem; color:#f87171; }
.ddesc { font-size:.78rem; color:var(--text-muted); margin-top:.25rem; max-width:420px; }
.cat { font-size:.68rem; padding:.16rem .55rem; border-radius:999px; white-space:nowrap; background:rgba(99,102,241,.18); color:#a5b4fc; }
.cat.mobile { background:rgba(34,197,94,.15); color:#4ade80; }
.cat.webapp { background:rgba(59,130,246,.18); color:#93c5fd; }
.cat.infrastructure { background:rgba(249,115,22,.18); color:#fdba74; }
.cat.ml-model { background:rgba(236,72,153,.18); color:#f9a8d4; }
.act { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem .3rem; }
.act:hover { color:#a5b4fc; } .act.del:hover { color:#f87171; } .act .material-icons { font-size:1.1rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }
.modal-backdrop { position:fixed; inset:0; background:rgba(0,0,0,.6); display:flex; align-items:center; justify-content:center; z-index:1100; padding:1rem; }
.modal { background:var(--bg-card,#1a1d29); border:1px solid rgba(255,255,255,.1); border-radius:14px; width:100%; max-width:520px; padding:1.5rem; max-height:90vh; overflow:auto; }
.mhead { display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem; }
.mhead h2 { margin:0; font-size:1.1rem; }
.x { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.modal label { display:block; font-size:.8rem; color:var(--text-muted); margin-bottom:.9rem; }
.modal input, .modal textarea, .modal select { width:100%; margin-top:.35rem; background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.12); border-radius:8px; color:var(--text,#fff); padding:.6rem .7rem; font-size:.9rem; font-family:inherit; box-sizing:border-box; }
.modal input[type=file] { padding:.45rem .5rem; cursor:pointer; }
.hint { display:block; margin-top:.3rem; font-size:.72rem; color:var(--text-muted); opacity:.8; }
.modal textarea { resize:vertical; }
.mfoot { display:flex; justify-content:flex-end; gap:.6rem; margin-top:.5rem; }
.mfoot button { display:flex; align-items:center; gap:.35rem; padding:.55rem 1.1rem; border-radius:8px; font-size:.88rem; cursor:pointer; border:1px solid transparent; }
.mfoot .ghost { background:none; border-color:rgba(255,255,255,.15); color:var(--text-muted); }
.mfoot .primary { background:#ffa31a; color:#1a1d29; font-weight:600; }
.mfoot .primary:disabled { opacity:.5; cursor:not-allowed; }
.mfoot .primary .material-icons { font-size:1rem; }
</style>
