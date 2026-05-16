<template>
  <div>
    <div class="page-header">
      <h1>Scan Reports</h1>
      <p>Scan activity and session reports</p>
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
            <span v-if="r.date"><span class="material-icons">schedule</span>{{ ago(r.date) }}</span>
            <span v-if="r.issuesFound != null"><span class="material-icons">bug_report</span>{{ r.issuesFound }} issues</span>
            <span v-if="r.fixesApplied != null"><span class="material-icons">build</span>{{ r.fixesApplied }} fixes</span>
          </div>
          <p v-if="r.summary" class="rsum">{{ r.summary }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { list, count, timeAgo } from '../services/db.js'
export default {
  name: 'ScanReports',
  data() {
    return {
      counts: { standard: 0, multi: 0, video: 0, bills: 0, detections: 0 },
      reports: [], loading: true, error: ''
    }
  },
  async mounted() {
    try {
      const [s, m, v, b, d, reps] = await Promise.all([
        count('Standard Scan'), count('Multi Scan'), count('Video Scan'),
        count('Bills'), count('Detections'), list('session_reports')
      ])
      this.counts = { standard: s, multi: m, video: v, bills: b, detections: d }
      this.reports = reps.sort((a, b2) => String(b2.date || '').localeCompare(String(a.date || '')))
    } catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    ago: timeAgo,
    badge(s) {
      const v = (s || '').toLowerCase()
      if (v.includes('complete') || v.includes('done') || v.includes('pass')) return 'ok'
      if (v.includes('progress') || v.includes('pending')) return 'warn'
      if (v.includes('fail') || v.includes('error')) return 'err'
      return 'neutral'
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
.rsum { margin: .65rem 0 0; font-size: .88rem; line-height: 1.5; color: var(--text); }
.badge { font-size: .68rem; padding: .12rem .5rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.warn { background: rgba(251,191,36,.15); color: #fbbf24; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
