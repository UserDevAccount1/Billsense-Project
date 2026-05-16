<template>
  <div>
    <div class="page-header">
      <h1>Scan Reports</h1>
      <p>Processed bill scans from the mobile app + development session reports</p>
    </div>
    <div class="content">
      <div class="stats">
        <div class="stat"><div class="n">{{ scans.length }}</div><div class="l">Total scans</div></div>
        <div class="stat"><div class="n">{{ counts.standard }}</div><div class="l">Standard</div></div>
        <div class="stat"><div class="n">{{ counts.multi }}</div><div class="l">Multi</div></div>
        <div class="stat"><div class="n">{{ counts.video }}</div><div class="l">Video</div></div>
        <div class="stat"><div class="n">{{ counts.bills }}</div><div class="l">Bills logged</div></div>
      </div>

      <div class="bar">
        <h3 class="sec">Recent Scans</h3>
        <div class="filters">
          <button v-for="f in scanFilters" :key="f" :class="{ on: scanFilter === f }" @click="scanFilter = f">{{ f }}</button>
        </div>
      </div>

      <div v-if="loading" class="state">Loading scans…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filteredScans.length" class="state">No scans found.</div>

      <div v-else class="gallery">
        <div v-for="s in filteredScans" :key="s._id" class="scard">
          <div class="imgwrap" @click="preview = s.annotatedImageUrl" :class="{ noimg: !s.annotatedImageUrl }">
            <img v-if="s.annotatedImageUrl" :src="s.annotatedImageUrl" loading="lazy"
                 @error="e => { e.target.style.display='none'; e.target.parentElement.classList.add('noimg') }" />
            <span v-else class="material-icons ph">image_not_supported</span>
            <span class="verdict" :class="vClass(s)">{{ vLabel(s) }}</span>
            <span class="typ">{{ s.type }}</span>
          </div>
          <div class="sbody">
            <div class="srow">
              <b>₱{{ s.denomination || '?' }}</b>
              <span class="conf" :class="confClass(s.confidence)">{{ s.confidence || '—' }}</span>
            </div>
            <div class="feat">
              <span class="material-icons">verified</span>
              {{ s.detectedFeaturesCount != null ? s.detectedFeaturesCount : '?' }}/{{ s.totalExpectedFeatures || 6 }} features
              <span class="cov" v-if="s.coveragePercentage != null">· {{ Math.round(s.coveragePercentage) }}%</span>
            </div>
            <div class="meta">
              <span><span class="material-icons">schedule</span>{{ fmt(s.timestamp) }}</span>
              <span v-if="s.processingTime"><span class="material-icons">speed</span>{{ s.processingTime }}s</span>
            </div>
          </div>
        </div>
      </div>

      <h3 class="sec mt">Development Session Reports</h3>
      <div v-if="repLoading" class="state">Loading…</div>
      <div v-else-if="!reports.length" class="state">No session reports.</div>
      <div v-else class="rlist">
        <div v-for="r in reports" :key="r._key" class="rcard">
          <div class="rhead">
            <span class="rtitle">{{ r.title || '(untitled)' }}</span>
            <span class="badge" :class="rBadge(r.status)">{{ r.status || 'n/a' }}</span>
          </div>
          <div class="rmeta">
            <span v-if="r.author"><span class="material-icons">person</span>{{ r.author }}</span>
            <span v-if="r.date"><span class="material-icons">schedule</span>{{ fmt(r.date) }}</span>
            <span><span class="material-icons">bug_report</span>{{ len(r.issuesFound) }} issues</span>
            <span><span class="material-icons">build</span>{{ len(r.fixesApplied) }} fixes</span>
          </div>
          <p v-if="r.summary" class="rsum">{{ r.summary }}</p>
          <div class="tabs">
            <button v-for="t in tabsFor(r)" :key="t.key" :class="{ on: openTab[r._key] === t.key }"
                    @click="setTab(r._key, t.key)">{{ t.label }} <em>{{ t.n }}</em></button>
          </div>
          <div class="panel" v-if="openTab[r._key]">
            <ul class="ilist">
              <li v-for="(it, i) in arr(r[openTab[r._key]])" :key="i">
                <span v-if="it.severity" class="sev" :class="sevClass(it.severity)">{{ it.severity }}</span>
                <span v-if="it.file" class="file">{{ it.file }}</span>
                <span v-if="it.category" class="cat">{{ it.category }}</span>
                <span v-if="it.done !== undefined" class="material-icons chk" :class="{ done: it.done }">{{ it.done ? 'check_circle' : 'radio_button_unchecked' }}</span>
                <span class="itext">{{ it.item || it.task || it.description || it.name || stringify(it) }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <div v-if="preview" class="lightbox" @click="preview = null"><img :src="preview" /></div>
  </div>
</template>

<script>
import { value, count } from '../services/db.js'

const SCAN_COLLECTIONS = [
  { path: 'Standard Scan', type: 'Standard' },
  { path: 'Multi Scan',    type: 'Multi' },
  { path: 'Video Scan',    type: 'Video' }
]

export default {
  name: 'ScanReports',
  data() {
    return {
      scans: [], reports: [], counts: { standard: 0, multi: 0, video: 0, bills: 0 },
      loading: true, repLoading: true, error: '',
      scanFilter: 'All', preview: null, openTab: {}
    }
  },
  computed: {
    scanFilters() { return ['All', 'Standard', 'Multi', 'Video', 'Genuine', 'Counterfeit'] },
    filteredScans() {
      const f = this.scanFilter
      if (f === 'All') return this.scans
      if (f === 'Genuine') return this.scans.filter(s => this.isGenuine(s))
      if (f === 'Counterfeit') return this.scans.filter(s => !this.isGenuine(s))
      return this.scans.filter(s => s.type === f)
    }
  },
  async mounted() {
    // Scan images (nested userId -> scanId -> record)
    try {
      const all = []
      for (const c of SCAN_COLLECTIONS) {
        const tree = await value(c.path)
        if (tree && typeof tree === 'object') {
          for (const [uid, userScans] of Object.entries(tree)) {
            if (!userScans || typeof userScans !== 'object') continue
            for (const [sid, rec] of Object.entries(userScans)) {
              if (!rec || typeof rec !== 'object') continue
              all.push({ ...rec, _id: `${c.type}:${uid}:${sid}`, type: c.type })
            }
          }
        }
      }
      all.sort((a, b) => String(b.timestamp || '').localeCompare(String(a.timestamp || '')))
      this.scans = all
    } catch (e) { this.error = e.message }
    finally { this.loading = false }

    try {
      const [s, m, v, b] = await Promise.all([
        count('Standard Scan'), count('Multi Scan'), count('Video Scan'), count('Bills')
      ])
      this.counts = { standard: s, multi: m, video: v, bills: b }
    } catch (_) {}

    // Session reports
    try {
      const sr = await value('session_reports')
      this.reports = sr && typeof sr === 'object'
        ? Object.entries(sr).map(([_key, r]) => ({ _key, ...r }))
            .sort((a, x) => String(x.date || '').localeCompare(String(a.date || '')))
        : []
    } catch (_) {}
    finally { this.repLoading = false }
  },
  methods: {
    isGenuine(s) {
      const a = s.authenticity
      const str = (typeof a === 'string' ? a : (a && a.status) || s.isGenuine || '').toString().toUpperCase()
      if (s.isGenuine === true || s.genuine === true) return true
      if (s.isGenuine === false || s.genuine === false) return false
      return !str.includes('COUNTERFEIT') && !str.includes('FAKE')
    },
    vLabel(s) { return this.isGenuine(s) ? 'GENUINE' : 'COUNTERFEIT' },
    vClass(s) { return this.isGenuine(s) ? 'ok' : 'bad' },
    confClass(c) {
      const v = (c || '').toString().toUpperCase()
      if (v === 'HIGH') return 'ok'
      if (v === 'MEDIUM') return 'mid'
      return 'low'
    },
    fmt(d) { if (!d) return '—'; const x = new Date(d); return isNaN(x) ? String(d) : x.toLocaleString() },
    arr(v) { return Array.isArray(v) ? v : (v && typeof v === 'object' ? Object.values(v) : []) },
    len(v) { return this.arr(v).length },
    stringify(o) { try { return typeof o === 'string' ? o : JSON.stringify(o) } catch { return String(o) } },
    setTab(k, t) { this.openTab = { ...this.openTab, [k]: this.openTab[k] === t ? null : t } },
    tabsFor(r) {
      const t = []
      for (const [key, label] of [['issuesFound','Issues'],['fixesApplied','Fixes'],['checklist','Checklist'],['actionsDone','Actions'],['features','Features']])
        if (this.len(r[key])) t.push({ key, label, n: this.len(r[key]) })
      return t
    },
    rBadge(s) { const v = (s||'').toLowerCase(); return v.includes('complete')||v.includes('pass')?'ok':v.includes('fail')?'err':'neutral' },
    sevClass(s) { const v=(s||'').toLowerCase(); return v.includes('crit')?'crit':v.includes('high')?'high':v.includes('med')?'med':'low' }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
.stat { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1.1rem; text-align: center; }
.stat .n { font-size: 1.8rem; font-weight: 700; color: #ffa31a; }
.stat .l { font-size: .8rem; color: var(--text-muted); margin-top: .25rem; }
.bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; }
.sec { margin: 0 0 .85rem; font-size: 1.05rem; }
.sec.mt { margin-top: 2rem; }
.filters { display: flex; gap: .4rem; flex-wrap: wrap; }
.filters button { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .3rem .75rem; border-radius: 999px; font-size: .78rem; cursor: pointer; }
.filters button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; }
.gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1rem; }
.scard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; overflow: hidden; }
.imgwrap { position: relative; height: 200px; background: #0b1019; cursor: zoom-in;
  display: flex; align-items: center; justify-content: center; }
.imgwrap img { width: 100%; height: 100%; object-fit: contain; }
.imgwrap.noimg { cursor: default; }
.imgwrap .ph { font-size: 2.5rem; color: #334155; }
.verdict { position: absolute; top: 8px; left: 8px; font-size: .68rem; font-weight: 700;
  padding: .15rem .5rem; border-radius: 999px; letter-spacing: .03em; }
.verdict.ok { background: rgba(34,197,94,.85); color: #04210f; }
.verdict.bad { background: rgba(248,113,113,.9); color: #2a0606; }
.typ { position: absolute; top: 8px; right: 8px; font-size: .66rem; background: rgba(0,0,0,.55);
  color: #cbd5e1; padding: .15rem .5rem; border-radius: 999px; }
.sbody { padding: .8rem 1rem; }
.srow { display: flex; justify-content: space-between; align-items: center; }
.srow b { font-size: 1.1rem; }
.conf { font-size: .7rem; padding: .12rem .5rem; border-radius: 999px; }
.conf.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.conf.mid { background: rgba(251,191,36,.15); color: #fbbf24; }
.conf.low { background: rgba(148,163,184,.15); color: #94a3b8; }
.feat { display: flex; align-items: center; gap: .35rem; font-size: .8rem; color: var(--text-muted); margin: .4rem 0; }
.feat .material-icons { font-size: .95rem; }
.cov { opacity: .8; }
.meta { display: flex; gap: 1rem; font-size: .75rem; color: var(--text-muted); }
.meta span { display: flex; align-items: center; gap: .3rem; }
.meta .material-icons { font-size: .85rem; }
.lightbox { position: fixed; inset: 0; background: rgba(0,0,0,.9); display: flex; align-items: center;
  justify-content: center; z-index: 999; padding: 2rem; cursor: zoom-out; }
.lightbox img { max-width: 92vw; max-height: 92vh; border-radius: 8px; }
.rlist { display: flex; flex-direction: column; gap: .85rem; }
.rcard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1rem 1.15rem; }
.rhead { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .5rem; }
.rtitle { font-weight: 600; }
.rmeta { display: flex; flex-wrap: wrap; gap: 1rem; font-size: .8rem; color: var(--text-muted); }
.rmeta span { display: flex; align-items: center; gap: .35rem; }
.rmeta .material-icons { font-size: .95rem; }
.rsum { margin: .65rem 0 .5rem; font-size: .88rem; line-height: 1.5; }
.tabs { display: flex; flex-wrap: wrap; gap: .4rem; margin-top: .5rem; }
.tabs button { background: rgba(0,0,0,.2); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .3rem .7rem; border-radius: 7px; font-size: .76rem; cursor: pointer; }
.tabs button.on { background: rgba(255,163,26,.15); color: #ffa31a; }
.tabs button em { opacity: .65; font-style: normal; }
.panel { margin-top: .7rem; background: rgba(0,0,0,.18); border-radius: 8px; padding: .7rem 1rem; }
.ilist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .45rem; font-size: .83rem; }
.ilist li { display: flex; align-items: flex-start; gap: .55rem; }
.itext { flex: 1; line-height: 1.4; }
.sev { font-size: .64rem; padding: .1rem .4rem; border-radius: 999px; text-transform: uppercase; flex-shrink: 0; }
.sev.crit { background: rgba(248,113,113,.2); color: #f87171; }
.sev.high { background: rgba(251,146,60,.2); color: #fb923c; }
.sev.med { background: rgba(251,191,36,.18); color: #fbbf24; }
.sev.low { background: rgba(148,163,184,.18); color: #94a3b8; }
.file { font-family: monospace; font-size: .7rem; background: rgba(99,102,241,.18); color: #a5b4fc; padding: .1rem .4rem; border-radius: 4px; flex-shrink: 0; }
.cat { font-size: .66rem; background: rgba(255,163,26,.15); color: #ffa31a; padding: .1rem .4rem; border-radius: 999px; flex-shrink: 0; }
.chk { font-size: 1rem; color: #64748b; flex-shrink: 0; }
.chk.done { color: #4ade80; }
.badge { font-size: .68rem; padding: .12rem .5rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
