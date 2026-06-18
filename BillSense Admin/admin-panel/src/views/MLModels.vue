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

      <h3 class="sec">Model Ensemble — versions &amp; training</h3>
      <p class="docnote">
        Latest retrain <b>2026-06-18</b>: denomination <b>v2</b> (mAP@50 0.93, ₱50 recall 38%→97%)
        + <b>securitycf v1</b> (real counterfeit detection — false_* markers → COUNTERFEIT).
        Full update history &amp; training docs in the repo:
        <code>docs/MODEL_REGISTRY.md</code> · <code>docs/HOW_COUNTERFEIT_DETECTION_WORKS.md</code>.
      </p>
      <div class="models">
        <div v-for="m in ensemble" :key="m.file" class="mcard">
          <div class="mfile">{{ m.file }} <span class="ver">{{ m.version }}</span></div>
          <div class="mrole">{{ m.role }}</div>
          <div class="mout">{{ m.out }}</div>
          <div class="mmeta">
            <span>Trained: {{ m.trained }}</span>
            <span v-if="m.metric && m.metric !== '—'">· {{ m.metric }}</span>
          </div>
          <div v-if="m.note" class="mnote">{{ m.note }}</div>
        </div>
      </div>

      <!-- DOCUMENTATION -->
      <h3 class="sec">Documentation — what's improved &amp; how it works</h3>
      <div class="docgrid">

        <div class="doccard">
          <h4>✅ What's improved (2026-06-18)</h4>
          <ul class="chk">
            <li>Denomination model <b>retrained (v2)</b> — held-out mAP@50 <b>0.73 → 0.93</b>; ₱50 recall <b>38% → 97%</b>, ₱200 38% → 83%.</li>
            <li><b>Real counterfeit detection</b> — new <code>securitycf</code> model flags forgeries via <code>false_*</code> markers → <b>COUNTERFEIT</b> verdict.</li>
            <li><b>Full security-feature checklist</b> — surfaced all detectable features (was capped at 6 → now 9 base / 13–14 incl. high-denom).</li>
            <li><b>Tagged annotation</b> — each detected feature is boxed &amp; labelled on the scanned bill.</li>
            <li><b>Pre-scan overview</b> for Standard / Multi / Video modes.</li>
            <li><b>Billy</b> — answers directly (no canned intro), credits the researcher (Joy Canutab et al.), clear-chat &amp; answer-persistence fixed.</li>
          </ul>
        </div>

        <div class="doccard">
          <h4>🔍 How a scan works</h4>
          <ol class="steps">
            <li>Capture the bill (front-lit photo, or tilt/angles for Multi-Scan).</li>
            <li><b>Denomination</b> detection (<code>denomination2.pt</code>). If UNKNOWN → ask for a clearer re-scan.</li>
            <li><b>Security features</b> detected in parallel (securitycf + security/counterfeit/OVI/OVD/EVP models) → coverage = detected ÷ expected.</li>
            <li><b>Capture quality</b> (blur/brightness/contrast) + <b>feature geometry</b> (placement vs reference).</li>
            <li><code>evaluate_counterfeit()</code> → authenticity score (0–100) + verdict + an annotated image with tagged features.</li>
          </ol>
        </div>

        <div class="doccard">
          <h4>🧠 How the models are trained</h4>
          <ul>
            <li>YOLOv8 (Ultralytics) on a <b>Google Colab T4 GPU</b>; datasets staged via GCS / Roboflow.</li>
            <li><b>Denomination v2</b>: 3 full-res PH-banknote datasets, auto-canonicalised class names, leakage-safe 80/20 split, ~100 epochs, imgsz 640.</li>
            <li><b>securitycf v1</b>: 3 merged security datasets → <b>16 classes</b> (8 genuine + 8 <code>false_*</code>) — genuine security features <i>and</i> their counterfeit versions.</li>
            <li>Models are <b>drop-in</b>: class-name contract must match the server. Deploy = rebuild Cloud Run image + <code>gcloud run deploy</code>. Pipelines: <code>training/retrain.py</code>, <code>colab_train_v2.py</code>, <code>colab_train_security.py</code>.</li>
          </ul>
        </div>

        <div class="doccard">
          <h4>⚖️ How GENUINE vs COUNTERFEIT is measured</h4>
          <p>There is <b>no single "real/fake" model</b> — the verdict is <b>computed</b> from evidence:</p>
          <ul>
            <li><b>COUNTERFEIT</b> when a positive forgery marker is detected: any securitycf <code>false_*</code> (false watermark, false thread, false OVI, false bill…) or a false enhanced value panel.</li>
            <li>Otherwise an <b>authenticity score (0–100)</b> is computed:<br>
              <code>score = (0.60·coverage + 0.40·detection-confidence + 0.15·geometry-bonus) × capture-quality</code></li>
            <li>Tiers: <b>≥75</b> GENUINE (high) · <b>≥50</b> GENUINE (medium) · <b>&lt;50</b> LIKELY GENUINE · quality&lt;30 → NEEDS_RESCAN · no denomination → UNKNOWN.</li>
            <li><b>Relaxed rule:</b> missing features ≠ counterfeit — a single front-lit photo can't see the watermark/see-through (need backlight) or OVI/OVD (need tilt), so their absence only lowers confidence; only a <i>positive</i> forgery signal condemns a note.</li>
          </ul>
          <p class="mt"><b>Security features measured (14):</b> value, serial number, security thread, concealed value, watermark, value watermark, see-through mark, UV thread, symbol of nature, shadow thread (+ optically variable ink, OV thread, OVD, enhanced value panel on ₱500/₱1000).</p>
        </div>

      </div>
      <p class="docnote">Source docs in the repo: <code>docs/HOW_COUNTERFEIT_DETECTION_WORKS.md</code> · <code>docs/MODEL_REGISTRY.md</code> · <code>docs/SESSION_REPORT_2026-06-18.md</code>.</p>
    </div>
  </div>
</template>

<script>
import { value } from '../services/db.js'

const API = 'https://billsense-api-340624938055.asia-southeast2.run.app'
const ENSEMBLE = [
  { file: 'denomination2.pt', role: 'Denomination classifier', out: '20 · 50 · 100 · 200 · 500 · 1000',
    version: 'v2', trained: '2026-06-18', metric: 'mAP@50 0.93', note: 'Retrained on 3 full-res datasets; ₱50 recall 38%→97%' },
  { file: 'securitycf.pt', role: 'Security features + counterfeit', out: 'watermark · see-through · shadow · thread · concealed · OVI · EVP + false_* markers',
    version: 'v1', trained: '2026-06-18', metric: '16 classes', note: 'NEW — false_* detections → COUNTERFEIT verdict (real fake detection)' },
  { file: 'security_best.pt', role: 'Security features (legacy)', out: 'watermark · thread · serial · concealed value · see-through',
    version: 'v1', trained: 'original', metric: '—', note: 'Supplemented by securitycf' },
  { file: 'counterfeit_best.pt', role: 'Security feature detector', out: 'UV-thread · concealed · thread · serial · value',
    version: 'v1', trained: 'original', metric: '—', note: 'Feature detector (not real/fake)' },
  { file: 'ovi.pt', role: 'Optically Variable Ink', out: 'present / absent / suspicious',
    version: 'v1', trained: 'original', metric: '—', note: 'High-denomination tilt feature' },
  { file: 'ovd.pt', role: 'Optically Variable Device', out: 'foil region polygons',
    version: 'v1', trained: 'original', metric: '—', note: 'High-denomination tilt feature' },
  { file: 'evp.pt', role: 'Enhanced Value Panel', out: '500 / 1000 EVP + false EVP',
    version: 'v1', trained: 'original', metric: '—', note: 'Original forgery marker' }
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
.ver { background: #1f9d57; color: #fff; border-radius: 6px; padding: 1px 7px; font-size: .72rem; font-weight: 700; margin-left: 4px; }
.mmeta { font-size: .76rem; color: var(--text-muted); margin-top: .45rem; }
.mnote { font-size: .78rem; color: #cbd5e1; margin-top: .35rem; font-style: italic; }
.docnote { font-size: .82rem; color: var(--text-muted); margin: 1rem 0 0; line-height: 1.6; }
.docnote code { background: rgba(0,0,0,.25); padding: 1px 6px; border-radius: 5px; color: #ffa31a; }
.docgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
@media (max-width: 900px) { .docgrid { grid-template-columns: 1fr; } }
.doccard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1.1rem 1.25rem; }
.doccard h4 { margin: 0 0 .6rem; font-size: .95rem; }
.doccard ul, .doccard ol { margin: 0; padding-left: 1.2rem; }
.doccard li { font-size: .84rem; line-height: 1.65; color: #cbd5e1; margin-bottom: .35rem; }
.doccard p { font-size: .84rem; line-height: 1.6; color: #cbd5e1; margin: 0 0 .5rem; }
.doccard p.mt { margin-top: .6rem; }
.doccard code { background: rgba(0,0,0,.25); padding: 1px 6px; border-radius: 5px; color: #ffa31a; font-size: .8rem; }
.chk { list-style: none; padding-left: 0; }
.chk li { padding-left: 1.4rem; position: relative; }
.chk li::before { content: "✓"; position: absolute; left: 0; color: #1f9d57; font-weight: 700; }
</style>
