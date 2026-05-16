<template>
  <div>
    <div class="page-header">
      <h1>Thesis Validator</h1>
      <p>Document version control &amp; defense-panel comment management</p>
    </div>

    <div class="content">
      <div class="tabbar">
        <button :class="{ on: tab === 'doc' }" @click="tab = 'doc'">
          <span class="material-icons">description</span> Document Versions
          <em>{{ versions.length }}</em>
        </button>
        <button :class="{ on: tab === 'panel' }" @click="tab = 'panel'">
          <span class="material-icons">rate_review</span> Panel Comments
          <em>{{ openComments }}/{{ totalComments }}</em>
        </button>
      </div>

      <div v-if="loading" class="state">Loading thesis data…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>

      <!-- ===== DOCUMENT VERSIONS ===== -->
      <div v-else-if="tab === 'doc'" class="doc">
        <div class="doc-side">
          <label class="lbl">Version</label>
          <select v-model="selVer" class="sel">
            <option v-for="v in versions" :key="v._key" :value="v._key">
              v{{ v.versionNumber }} — {{ (v.date || '').slice(0,10) }} — {{ v.author }}
            </option>
          </select>
          <p class="chg" v-if="curVersion">{{ curVersion.changesSummary }}</p>
          <label class="lbl">Sections</label>
          <ul class="seclist">
            <li v-for="(s, key) in (curVersion && curVersion.sections) || {}" :key="key"
                :class="{ on: selSec === key }" @click="selSec = key">
              {{ s.title || prettyKey(key) }}
            </li>
          </ul>
        </div>
        <div class="doc-main">
          <template v-if="curSection">
            <h3>{{ curSection.title || prettyKey(selSec) }}</h3>
            <div class="secmeta">
              {{ wordCount(curSection.content) }} words ·
              v{{ curVersion.versionNumber }} · {{ curVersion.author }}
            </div>
            <div class="seccontent">{{ stripHtml(curSection.content) }}</div>
          </template>
          <div v-else class="state">Select a section.</div>
        </div>
      </div>

      <!-- ===== PANEL COMMENTS ===== -->
      <div v-else class="panels">
        <div v-if="!panelRequests.length" class="state">No panel requests.</div>
        <div v-for="req in panelRequests" :key="req._key" class="preq">
          <div class="preq-head">
            <div>
              <strong>{{ req.title || '(untitled request)' }}</strong>
              <span class="src" v-if="req.source">· {{ req.source }}</span>
            </div>
            <select :value="req.status || 'pending'" class="sel sm"
                    @change="setReqStatus(req, $event.target.value)">
              <option>pending</option><option>in-review</option>
              <option>addressed</option><option>closed</option>
            </select>
          </div>
          <div class="preq-meta">
            <span><span class="material-icons">event</span>{{ (req.date || '').slice(0,10) }}</span>
            <span><span class="material-icons">groups</span>{{ (req.panelists || []).length }} panelists</span>
            <span><span class="material-icons">comment</span>{{ reqCommentStats(req) }}</span>
          </div>

          <div v-for="(p, pi) in (req.panelists || [])" :key="pi" class="panelist">
            <div class="pl-name"><span class="material-icons">person</span>{{ p.name || ('Panelist ' + (pi+1)) }}</div>
            <div v-if="!(p.comments || []).length" class="nocmt">No comments.</div>
            <div v-for="(c, ci) in (p.comments || [])" :key="c.id || ci" class="cmt">
              <span class="sec-tag">{{ prettyKey(c.section) }}</span>
              <span class="cmt-text">{{ c.text }}</span>
              <select :value="c.status || 'pending'" class="sel xs" :class="cStatusClass(c.status)"
                      @change="setCommentStatus(req, pi, ci, $event.target.value)">
                <option>pending</option><option>addressed</option>
                <option>resolved</option><option>rejected</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type === 'err' ? 'error' : 'check_circle' }}</span>
      {{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, patch } from '../services/db.js'

export default {
  name: 'Thesis',
  data() {
    return {
      tab: 'doc', loading: true, error: '', toast: null,
      versions: [], panelRequests: [],
      selVer: '', selSec: ''
    }
  },
  computed: {
    curVersion() { return this.versions.find(v => v._key === this.selVer) || null },
    curSection() {
      const s = this.curVersion && this.curVersion.sections
      return s ? s[this.selSec] : null
    },
    totalComments() {
      return this.panelRequests.reduce((n, r) =>
        n + (r.panelists || []).reduce((m, p) => m + (p.comments || []).length, 0), 0)
    },
    openComments() {
      return this.panelRequests.reduce((n, r) =>
        n + (r.panelists || []).reduce((m, p) =>
          m + (p.comments || []).filter(c => !['resolved', 'rejected'].includes(c.status)).length, 0), 0)
    }
  },
  async mounted() {
    try {
      const [tv, pr] = await Promise.all([
        value('thesis_versions'), value('thesis_panel_requests')
      ])
      this.versions = tv && typeof tv === 'object'
        ? Object.entries(tv).map(([_key, v]) => ({ _key, ...v }))
            .sort((a, b) => (a.versionNumber || 0) - (b.versionNumber || 0))
        : []
      this.panelRequests = pr && typeof pr === 'object'
        ? Object.entries(pr).map(([_key, r]) => ({ _key, ...r }))
        : []
      if (this.versions.length) {
        this.selVer = this.versions[this.versions.length - 1]._key  // latest
        const secs = this.curVersion && this.curVersion.sections
        if (secs) this.selSec = Object.keys(secs)[0]
      }
    } catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    prettyKey(k) {
      if (!k) return '—'
      return String(k).replace(/_/g, ' ')
        .replace(/\b(\w)/g, c => c.toUpperCase())
        .replace(/Chapter(\d)/, 'Chapter $1 —')
    },
    stripHtml(s) {
      if (!s) return ''
      const txt = String(s).replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
      return txt.replace(/[ \t]{2,}/g, ' ').replace(/\n{3,}/g, '\n\n').trim()
    },
    wordCount(s) { return this.stripHtml(s).split(/\s+/).filter(Boolean).length },
    reqCommentStats(req) {
      const all = (req.panelists || []).flatMap(p => p.comments || [])
      const done = all.filter(c => ['resolved', 'rejected'].includes(c.status)).length
      return `${done}/${all.length} resolved`
    },
    cStatusClass(s) {
      const v = (s || 'pending').toLowerCase()
      if (v === 'resolved') return 'ok'
      if (v === 'addressed') return 'warn'
      if (v === 'rejected') return 'mut'
      return 'pend'
    },
    notify(msg, type = 'ok') {
      this.toast = { msg, type }; setTimeout(() => { this.toast = null }, 3000)
    },
    async setReqStatus(req, status) {
      try {
        await patch(`thesis_panel_requests/${req._key}`, { status })
        req.status = status
        this.notify(`Request → ${status}`)
      } catch (e) { this.notify(e.message, 'err') }
    },
    async setCommentStatus(req, pi, ci, status) {
      // Rewrite the whole panelists array (RTDB PATCH replaces the key).
      const panelists = JSON.parse(JSON.stringify(req.panelists || []))
      if (!panelists[pi] || !panelists[pi].comments || !panelists[pi].comments[ci]) return
      panelists[pi].comments[ci].status = status
      try {
        await patch(`thesis_panel_requests/${req._key}`, { panelists })
        req.panelists = panelists
        this.notify(`Comment → ${status}`)
      } catch (e) { this.notify(e.message, 'err') }
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.tabbar { display: flex; gap: .5rem; margin-bottom: 1.25rem; }
.tabbar button { display: flex; align-items: center; gap: .45rem; background: var(--bg-card);
  border: 1px solid rgba(255,255,255,.08); color: var(--text-muted); padding: .55rem 1rem;
  border-radius: 10px; font-size: .88rem; cursor: pointer; }
.tabbar button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.tabbar button .material-icons { font-size: 1.05rem; }
.tabbar button em { font-style: normal; opacity: .7; font-size: .78rem; }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; }

.doc { display: grid; grid-template-columns: 280px 1fr; gap: 1.25rem; }
@media (max-width: 820px) { .doc { grid-template-columns: 1fr; } }
.doc-side { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; padding: 1rem; align-self: start; }
.lbl { display: block; font-size: .72rem; text-transform: uppercase; letter-spacing: .04em;
  color: var(--text-muted); margin: .5rem 0 .35rem; }
.sel { width: 100%; background: rgba(0,0,0,.25); border: 1px solid rgba(255,255,255,.1);
  border-radius: 8px; padding: .5rem .6rem; color: inherit; font-size: .85rem; cursor: pointer; }
.sel.sm { width: auto; font-size: .8rem; }
.sel.xs { width: auto; font-size: .74rem; padding: .2rem .4rem; }
.sel.xs.ok { color: #4ade80; } .sel.xs.warn { color: #fbbf24; }
.sel.xs.mut { color: #94a3b8; } .sel.xs.pend { color: #f87171; }
.chg { font-size: .8rem; color: var(--text-muted); margin: .6rem 0; line-height: 1.45; }
.seclist { list-style: none; margin: .25rem 0 0; padding: 0; }
.seclist li { padding: .45rem .6rem; border-radius: 7px; font-size: .82rem; cursor: pointer;
  color: var(--text-muted); }
.seclist li:hover { background: rgba(255,255,255,.04); }
.seclist li.on { background: rgba(255,163,26,.15); color: #ffa31a; }
.doc-main { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; padding: 1.25rem 1.5rem; min-width: 0; }
.doc-main h3 { margin: 0 0 .3rem; }
.secmeta { font-size: .78rem; color: var(--text-muted); margin-bottom: 1rem; }
.seccontent { white-space: pre-wrap; font-size: .9rem; line-height: 1.7; color: var(--text);
  max-height: 65vh; overflow-y: auto; background: rgba(0,0,0,.18); padding: 1rem 1.25rem;
  border-radius: 8px; }

.panels { display: flex; flex-direction: column; gap: 1rem; }
.preq { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; padding: 1rem 1.25rem; }
.preq-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
.preq-head strong { font-size: .98rem; }
.src { color: var(--text-muted); font-size: .78rem; }
.preq-meta { display: flex; gap: 1.25rem; font-size: .78rem; color: var(--text-muted);
  margin: .5rem 0 .85rem; flex-wrap: wrap; }
.preq-meta span { display: flex; align-items: center; gap: .3rem; }
.preq-meta .material-icons { font-size: .9rem; }
.panelist { border-top: 1px solid rgba(255,255,255,.05); padding-top: .75rem; margin-top: .75rem; }
.pl-name { display: flex; align-items: center; gap: .4rem; font-weight: 600; font-size: .87rem;
  margin-bottom: .5rem; }
.pl-name .material-icons { font-size: 1rem; color: #ffa31a; }
.nocmt { font-size: .8rem; color: var(--text-muted); padding-left: 1.4rem; }
.cmt { display: flex; align-items: flex-start; gap: .6rem; padding: .5rem 0;
  border-bottom: 1px solid rgba(255,255,255,.03); }
.sec-tag { font-size: .68rem; background: rgba(99,102,241,.18); color: #a5b4fc;
  padding: .12rem .45rem; border-radius: 999px; white-space: nowrap; flex-shrink: 0; }
.cmt-text { flex: 1; font-size: .85rem; line-height: 1.45; }
.toast { position: fixed; bottom: 1.5rem; right: 1.5rem; display: flex; align-items: center;
  gap: .5rem; padding: .7rem 1.1rem; border-radius: 8px; font-size: .87rem; z-index: 1000; }
.toast.ok { background: rgba(34,197,94,.18); color: #4ade80; border: 1px solid rgba(34,197,94,.3); }
.toast.err { background: rgba(248,113,113,.18); color: #f87171; border: 1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size: 1.05rem; }
</style>
