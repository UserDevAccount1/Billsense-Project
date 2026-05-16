<template>
  <div>
    <div class="page-header">
      <h1>ML Models</h1>
      <p>YOLOv8 OBB ensemble — config &amp; Cloud Run inference status</p>
    </div>
    <div class="content">
      <div class="grid">
        <div class="panel">
          <h3><span class="material-icons">cloud</span> Cloud Run Inference API</h3>
          <div v-if="api.loading" class="muted">Checking…</div>
          <div v-else-if="api.error" class="muted err">Offline: {{ api.error }}</div>
          <template v-else>
            <div class="kv"><span>Status</span><b :class="api.data.status === 'healthy' ? 'g' : 'r'">{{ api.data.status }}</b></div>
            <div class="kv"><span>API version</span><b>{{ api.data.api_version || '—' }}</b></div>
            <div class="kv"><span>Models loaded</span><b :class="api.data.models_loaded ? 'g' : 'y'">{{ api.data.models_loaded }}</b></div>
            <div class="kv"><span>Firebase</span><b :class="api.data.firebase_available ? 'g' : 'r'">{{ api.data.firebase_available }}</b></div>
            <div class="kv"><span>Scan types</span><b>{{ (api.data.scan_types || []).join(', ') || '—' }}</b></div>
          </template>
        </div>

        <div class="panel">
          <h3><span class="material-icons">tune</span> ml_config (Firebase)</h3>
          <div v-if="cfg.loading" class="muted">Loading…</div>
          <div v-else-if="cfg.error" class="muted err">{{ cfg.error }}</div>
          <pre v-else class="json">{{ pretty(cfg.data) }}</pre>
        </div>
      </div>

      <h3 class="sec">Model Ensemble</h3>
      <div class="models">
        <div v-for="m in ensemble" :key="m.file" class="mcard">
          <div class="mfile">{{ m.file }}</div>
          <div class="mrole">{{ m.role }}</div>
          <div class="mout">{{ m.out }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { value } from '../services/db.js'

const API = 'https://billsense-api-340624938055.asia-southeast2.run.app'
const ENSEMBLE = [
  { file: 'denomination2.pt', role: 'Denomination classifier', out: '20 · 50 · 100 · 200 · 500 · 1000' },
  { file: 'security_best.pt', role: 'Security features', out: 'watermark · thread · serial · concealed value · see-through' },
  { file: 'ovi.pt', role: 'Optically Variable Ink', out: 'present / absent / suspicious' },
  { file: 'ovd.pt', role: 'Optically Variable Device', out: 'foil region polygons' },
  { file: 'evp.pt', role: 'Enhanced Value Panel', out: '500 / 1000 EVP variants' },
  { file: 'counterfeit_best.pt', role: 'Direct counterfeit classifier', out: 'UV-thread · anomalies' }
]

export default {
  name: 'MLModels',
  data() {
    return {
      ensemble: ENSEMBLE,
      api: { loading: true, error: '', data: {} },
      cfg: { loading: true, error: '', data: null }
    }
  },
  async mounted() {
    fetch(`${API}/api/health`, { signal: AbortSignal.timeout(30000) })
      .then(r => r.json())
      .then(d => { this.api.data = d })
      .catch(e => { this.api.error = e.message })
      .finally(() => { this.api.loading = false })

    try { this.cfg.data = await value('ml_config') }
    catch (e) { this.cfg.error = e.message }
    finally { this.cfg.loading = false }
  },
  methods: {
    pretty(o) { try { return JSON.stringify(o, null, 2) } catch { return String(o) } }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1.5rem; }
@media (max-width: 800px) { .grid { grid-template-columns: 1fr; } }
.panel { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1.1rem 1.25rem; }
.panel h3 { margin: 0 0 .85rem; font-size: 1rem; display: flex; align-items: center; gap: .5rem; }
.panel h3 .material-icons { color: #ffa31a; font-size: 1.15rem; }
.kv { display: flex; justify-content: space-between; padding: .35rem 0; font-size: .87rem;
  border-bottom: 1px solid rgba(255,255,255,.04); }
.kv span { color: var(--text-muted); }
.kv b.g { color: #4ade80; } .kv b.y { color: #fbbf24; } .kv b.r { color: #f87171; }
.muted { color: var(--text-muted); font-size: .87rem; }
.muted.err { color: #f87171; }
.json { font-size: .78rem; background: rgba(0,0,0,.25); padding: .75rem; border-radius: 8px;
  overflow: auto; max-height: 240px; margin: 0; color: #cbd5e1; }
.sec { margin: 0 0 .85rem; font-size: 1.05rem; }
.models { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
.mcard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1rem 1.15rem; }
.mfile { font-family: monospace; color: #ffa31a; font-size: .9rem; }
.mrole { font-weight: 600; margin: .3rem 0; font-size: .92rem; }
.mout { font-size: .8rem; color: var(--text-muted); }
</style>
