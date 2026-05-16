<template>
  <div>
    <div class="page-header">
      <h1>Cases — Management</h1>
      <p>Review and manage counterfeit incident reports · {{ rows.length }} total</p>
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

      <div v-if="loading" class="state">Loading cases…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filtered.length" class="state">No cases.</div>

      <div v-else class="tablewrap">
        <table>
          <thead>
            <tr>
              <th></th><th>Title</th><th>Reporter</th><th>Date</th>
              <th>Status</th><th>Archived</th><th class="ac">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in filtered" :key="c._key" :class="{ busy: pending[c._key] }">
              <td><img v-if="c.image" :src="c.image" class="thumb" @click="preview = c.image"
                       @error="e => e.target.style.visibility='hidden'" /></td>
              <td>
                <div class="tt">{{ c.title || '(untitled)' }}</div>
                <div class="td">{{ (c.description || '').slice(0, 90) }}</div>
              </td>
              <td>{{ c.userName || '—' }}</td>
              <td class="nw">{{ c.caseDate || c.date || '—' }}</td>
              <td>
                <select :value="c.status || 'open'" @change="setStatus(c, $event.target.value)"
                        class="sel" :class="badge(c.status)">
                  <option>open</option><option>reviewing</option>
                  <option>resolved</option><option>rejected</option>
                </select>
              </td>
              <td>
                <button class="toggle" :class="{ on: c.isArchived }" @click="toggleArchive(c)">
                  {{ c.isArchived ? 'Yes' : 'No' }}
                </button>
              </td>
              <td class="ac">
                <button class="ico" title="View image" :disabled="!c.image" @click="preview = c.image">
                  <span class="material-icons">visibility</span>
                </button>
                <button class="ico danger" title="Delete case" @click="del(c)">
                  <span class="material-icons">delete</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="preview" class="lightbox" @click="preview = null">
      <img :src="preview" />
    </div>
    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type === 'err' ? 'error' : 'check_circle' }}</span>
      {{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { list, patch, remove } from '../services/db.js'
export default {
  name: 'Cases',
  data() {
    return { rows: [], loading: true, error: '', active: 'All',
             pending: {}, preview: null, toast: null }
  },
  computed: {
    filters() {
      const s = new Set(['All'])
      this.rows.forEach(r => s.add(r.status || 'open'))
      return [...s]
    },
    filtered() {
      return this.active === 'All' ? this.rows
        : this.rows.filter(r => (r.status || 'open') === this.active)
    }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try {
        const r = await list('Cases')
        this.rows = r.sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
      } catch (e) { this.error = e.message }
      finally { this.loading = false }
    },
    notify(msg, type = 'ok') {
      this.toast = { msg, type }
      setTimeout(() => { this.toast = null }, 3000)
    },
    badge(s) {
      const v = (s || 'open').toLowerCase()
      if (v.includes('resolved')) return 'ok'
      if (v.includes('review')) return 'warn'
      if (v.includes('reject')) return 'err'
      return 'neutral'
    },
    async setStatus(c, status) {
      this.pending = { ...this.pending, [c._key]: true }
      try {
        await patch(`Cases/${c._key}`, { status })
        c.status = status
        this.notify(`Case "${c.title || c._key}" → ${status}`)
      } catch (e) { this.notify(e.message, 'err') }
      finally { this.pending = { ...this.pending, [c._key]: false } }
    },
    async toggleArchive(c) {
      const next = !c.isArchived
      this.pending = { ...this.pending, [c._key]: true }
      try {
        await patch(`Cases/${c._key}`, { isArchived: next })
        c.isArchived = next
        this.notify(`Case ${next ? 'archived' : 'unarchived'}`)
      } catch (e) { this.notify(e.message, 'err') }
      finally { this.pending = { ...this.pending, [c._key]: false } }
    },
    async del(c) {
      if (!confirm(`Delete case "${c.title || c._key}"? This cannot be undone.`)) return
      this.pending = { ...this.pending, [c._key]: true }
      try {
        await remove(`Cases/${c._key}`)
        this.rows = this.rows.filter(r => r._key !== c._key)
        this.notify('Case deleted')
      } catch (e) { this.notify(e.message, 'err') }
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem;
  flex-wrap: wrap; margin-bottom: 1rem; }
.filters { display: flex; gap: .5rem; flex-wrap: wrap; }
.filters button { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08);
  color: var(--text-muted); padding: .35rem .85rem; border-radius: 999px; font-size: .8rem;
  cursor: pointer; text-transform: capitalize; }
.filters button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.refresh { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .4rem .85rem; border-radius: 8px; font-size: .82rem; cursor: pointer; display: flex;
  align-items: center; gap: .35rem; }
.refresh .spin { animation: s 1s linear infinite; }
@keyframes s { to { transform: rotate(360deg); } }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; }
.tablewrap { overflow-x: auto; background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; }
table { width: 100%; border-collapse: collapse; font-size: .85rem; }
th { text-align: left; padding: .8rem 1rem; color: var(--text-muted); font-weight: 600;
  border-bottom: 1px solid rgba(255,255,255,.08); white-space: nowrap; }
td { padding: .7rem 1rem; border-bottom: 1px solid rgba(255,255,255,.04); vertical-align: middle; }
tr.busy { opacity: .5; pointer-events: none; }
.thumb { width: 42px; height: 42px; border-radius: 6px; object-fit: cover; cursor: pointer; }
.tt { font-weight: 600; }
.td { color: var(--text-muted); font-size: .78rem; margin-top: .15rem; }
.nw { white-space: nowrap; }
.sel { background: rgba(0,0,0,.25); border: 1px solid rgba(255,255,255,.1); border-radius: 6px;
  padding: .3rem .5rem; color: inherit; font-size: .8rem; text-transform: capitalize; cursor: pointer; }
.sel.ok { color: #4ade80; } .sel.warn { color: #fbbf24; } .sel.err { color: #f87171; }
.sel.neutral { color: #94a3b8; }
.toggle { background: rgba(148,163,184,.15); color: #94a3b8; border: 0; border-radius: 999px;
  padding: .2rem .7rem; font-size: .76rem; cursor: pointer; }
.toggle.on { background: rgba(255,163,26,.18); color: #ffa31a; }
.ac { text-align: right; white-space: nowrap; }
.ico { background: none; border: 0; color: var(--text-muted); cursor: pointer; padding: .25rem; }
.ico:hover:not(:disabled) { color: #fff; }
.ico:disabled { opacity: .3; cursor: not-allowed; }
.ico.danger:hover { color: #f87171; }
.ico .material-icons { font-size: 1.15rem; }
.lightbox { position: fixed; inset: 0; background: rgba(0,0,0,.85); display: flex;
  align-items: center; justify-content: center; z-index: 999; padding: 2rem; cursor: zoom-out; }
.lightbox img { max-width: 90vw; max-height: 90vh; border-radius: 8px; }
.toast { position: fixed; bottom: 1.5rem; right: 1.5rem; display: flex; align-items: center;
  gap: .5rem; padding: .7rem 1.1rem; border-radius: 8px; font-size: .87rem; z-index: 1000; }
.toast.ok { background: rgba(34,197,94,.18); color: #4ade80; border: 1px solid rgba(34,197,94,.3); }
.toast.err { background: rgba(248,113,113,.18); color: #f87171; border: 1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size: 1.05rem; }
</style>
