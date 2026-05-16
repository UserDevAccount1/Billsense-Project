<template>
  <div>
    <div class="page-header">
      <h1>Scan Reports</h1>
      <p>Scan activity and development session reports</p>
    </div>
    <div class="content">
      <div class="stats">
        <div class="stat"><div class="n">{{ counts.standard }}</div><div class="l">Standard Scan users</div></div>
        <div class="stat"><div class="n">{{ counts.multi }}</div><div class="l">Multi Scan users</div></div>
        <div class="stat"><div class="n">{{ counts.video }}</div><div class="l">Video Scan users</div></div>
        <div class="stat"><div class="n">{{ counts.bills }}</div><div class="l">Bills logged</div></div>
        <div class="stat"><div class="n">{{ counts.detections }}</div><div class="l">Detections</div></div>
      </div>

      <h3 class="sec">Session Reports</h3>
      <div v-if="loading" class="state">Loading…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!reports.length" class="state">No session reports.</div>

      <div v-else class="rlist">
        <div v-for="r in reports" :key="r._key" class="rcard">
          <div class="rhead">
            <span class="rtitle">{{ r.title || '(untitled report)' }}</span>
            <span class="badge" :class="badge(r.status)">{{ r.status || 'n/a' }}</span>
          </div>
          <div class="rmeta">
            <span v-if="r.author"><span class="material-icons">person</span>{{ r.author }}</span>
            <span v-if="r.date"><span class="material-icons">schedule</span>{{ fmtDate(r.date) }}</span>
            <span><span class="material-icons">bug_report</span>{{ len(r.issuesFound) }} issues</span>
            <span><span class="material-icons">build</span>{{ len(r.fixesApplied) }} fixes</span>
            <span><span class="material-icons">checklist</span>{{ doneCount(r.checklist) }}/{{ len(r.checklist) }} tasks</span>
          </div>
          <p v-if="r.summary" class="rsum">{{ r.summary }}</p>

          <div class="tabs">
            <button v-for="t in tabsFor(r)" :key="t.key"
                    :class="{ on: openTab[r._key] === t.key }"
                    @click="setTab(r._key, t.key)">
              {{ t.label }} <em>{{ t.n }}</em>
            </button>
          </div>

          <div class="panel" v-if="openTab[r._key]">
            <!-- Issues -->
            <ul v-if="openTab[r._key] === 'issues'" class="ilist">
              <li v-for="(it, i) in arr(r.issuesFound)" :key="i">
                <span class="sev" :class="sevClass(it.severity)">{{ it.severity || '—' }}</span>
                <span class="itext">{{ it.item || it.issue || stringify(it) }}</span>
                <span v-if="it.status" class="st">{{ it.status }}</span>
              </li>
            </ul>
            <!-- Fixes -->
            <ul v-else-if="openTab[r._key] === 'fixes'" class="ilist">
              <li v-for="(it, i) in arr(r.fixesApplied)" :key="i">
                <span v-if="it.file" class="file">{{ it.file }}</span>
                <span class="itext">{{ it.item || it.fix || stringify(it) }}</span>
              </li>
            </ul>
            <!-- Checklist -->
            <ul v-else-if="openTab[r._key] === 'checklist'" class="ilist">
              <li v-for="(it, i) in arr(r.checklist)" :key="i">
                <span class="material-icons chk" :class="{ done: it.done }">
                  {{ it.done ? 'check_circle' : 'radio_button_unchecked' }}
                </span>
                <span class="itext">{{ it.task || it.item || stringify(it) }}</span>
              </li>
            </ul>
            <!-- Actions -->
            <ul v-else-if="openTab[r._key] === 'actions'" class="ilist">
              <li v-for="(it, i) in arr(r.actionsDone)" :key="i">
                <span v-if="it.category" class="cat">{{ it.category }}</span>
                <span class="itext">{{ it.item || stringify(it) }}</span>
              </li>
            </ul>
            <!-- Features -->
            <ul v-else-if="openTab[r._key] === 'features'" class="ilist col">
              <li v-for="(it, i) in arr(r.features)" :key="i">
                <b v-if="it.name || it.title">{{ it.name || it.title }}</b>
                <span class="itext">{{ it.description || stringify(it) }}</span>
                <small v-if="it.controls">{{ it.controls }}</small>
              </li>
            </ul>
            <!-- User guide -->
            <div v-else-if="openTab[r._key] === 'guide'" class="guide">
              <div v-for="(v, k) in (r.userGuide || {})" :key="k">
                <b>{{ k }}</b><span>{{ v }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { list, count } from '../services/db.js'
export default {
  name: 'ScanReports',
  data() {
    return {
      counts: { standard: 0, multi: 0, video: 0, bills: 0, detections: 0 },
      reports: [], loading: true, error: '', openTab: {}
    }
  },
  async mounted() {
    try {
      const [s, m, v, b, d, reps] = await Promise.all([
        count('Standard Scan'), count('Multi Scan'), count('Video Scan'),
        count('Bills'), count('Detections'), list('session_reports')
      ])
      this.counts = { standard: s, multi: m, video: v, bills: b, detections: d }
      this.reports = reps.sort((a, x) => String(x.date || '').localeCompare(String(a.date || '')))
    } catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    arr(v) { return Array.isArray(v) ? v : (v && typeof v === 'object' ? Object.values(v) : []) },
    len(v) { return this.arr(v).length },
    doneCount(v) { return this.arr(v).filter(x => x && x.done).length },
    stringify(o) { try { return typeof o === 'string' ? o : JSON.stringify(o) } catch { return String(o) } },
    fmtDate(d) { const x = new Date(d); return isNaN(x) ? d : x.toLocaleString() },
    setTab(k, t) { this.openTab = { ...this.openTab, [k]: this.openTab[k] === t ? null : t } },
    tabsFor(r) {
      const t = []
      if (this.len(r.issuesFound)) t.push({ key: 'issues', label: 'Issues', n: this.len(r.issuesFound) })
      if (this.len(r.fixesApplied)) t.push({ key: 'fixes', label: 'Fixes', n: this.len(r.fixesApplied) })
      if (this.len(r.checklist)) t.push({ key: 'checklist', label: 'Checklist', n: this.len(r.checklist) })
      if (this.len(r.actionsDone)) t.push({ key: 'actions', label: 'Actions', n: this.len(r.actionsDone) })
      if (this.len(r.features)) t.push({ key: 'features', label: 'Features', n: this.len(r.features) })
      if (r.userGuide && typeof r.userGuide === 'object')
        t.push({ key: 'guide', label: 'Guide', n: Object.keys(r.userGuide).length })
      return t
    },
    badge(s) {
      const v = (s || '').toLowerCase()
      if (v.includes('complete') || v.includes('done') || v.includes('pass')) return 'ok'
      if (v.includes('progress') || v.includes('pending')) return 'warn'
      if (v.includes('fail') || v.includes('error')) return 'err'
      return 'neutral'
    },
    sevClass(s) {
      const v = (s || '').toLowerCase()
      if (v.includes('critical')) return 'crit'
      if (v.includes('high')) return 'high'
      if (v.includes('med')) return 'med'
      return 'low'
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
.stat { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1.1rem; text-align: center; }
.stat .n { font-size: 1.8rem; font-weight: 700; color: #ffa31a; }
.stat .l { font-size: .8rem; color: var(--text-muted); margin-top: .25rem; }
.sec { margin: 0 0 .85rem; font-size: 1.05rem; }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; align-items: center; }
.rlist { display: flex; flex-direction: column; gap: .85rem; }
.rcard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1rem 1.15rem; }
.rhead { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .5rem; }
.rtitle { font-weight: 600; }
.rmeta { display: flex; flex-wrap: wrap; gap: 1rem; font-size: .8rem; color: var(--text-muted); }
.rmeta span { display: flex; align-items: center; gap: .35rem; }
.rmeta .material-icons { font-size: .95rem; }
.rsum { margin: .65rem 0 .5rem; font-size: .88rem; line-height: 1.5; color: var(--text); }
.tabs { display: flex; flex-wrap: wrap; gap: .4rem; margin: .65rem 0 0; }
.tabs button { background: rgba(0,0,0,.2); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .3rem .7rem; border-radius: 7px; font-size: .78rem; cursor: pointer; }
.tabs button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.tabs button em { opacity: .65; font-style: normal; }
.panel { margin-top: .75rem; background: rgba(0,0,0,.18); border-radius: 8px; padding: .75rem 1rem; }
.ilist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .5rem; font-size: .84rem; }
.ilist.col li { flex-direction: column; align-items: flex-start; gap: .2rem; }
.ilist li { display: flex; align-items: flex-start; gap: .6rem; }
.itext { flex: 1; line-height: 1.45; }
.sev { font-size: .66rem; padding: .1rem .45rem; border-radius: 999px; flex-shrink: 0; text-transform: uppercase; }
.sev.crit { background: rgba(248,113,113,.2); color: #f87171; }
.sev.high { background: rgba(251,146,60,.2); color: #fb923c; }
.sev.med { background: rgba(251,191,36,.18); color: #fbbf24; }
.sev.low { background: rgba(148,163,184,.18); color: #94a3b8; }
.st { font-size: .68rem; color: #4ade80; flex-shrink: 0; }
.file { font-family: monospace; font-size: .72rem; background: rgba(99,102,241,.18); color: #a5b4fc;
  padding: .1rem .4rem; border-radius: 4px; flex-shrink: 0; }
.cat { font-size: .68rem; background: rgba(255,163,26,.15); color: #ffa31a; padding: .1rem .45rem;
  border-radius: 999px; flex-shrink: 0; }
.chk { font-size: 1rem; color: #64748b; flex-shrink: 0; }
.chk.done { color: #4ade80; }
.ilist small { color: var(--text-muted); font-size: .76rem; }
.guide { display: flex; flex-direction: column; gap: .6rem; font-size: .84rem; }
.guide div { display: flex; flex-direction: column; gap: .2rem; }
.guide b { color: #ffa31a; text-transform: capitalize; }
.guide span { color: var(--text-muted); line-height: 1.45; }
.badge { font-size: .68rem; padding: .12rem .5rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.warn { background: rgba(251,191,36,.15); color: #fbbf24; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
