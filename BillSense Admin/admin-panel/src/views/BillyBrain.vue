<template>
  <div>
    <div class="page-header">
      <h1>Billy Brain</h1>
      <p>Billy's AI infrastructure, knowledge sources &amp; prompts — and the bits you can update live.</p>
    </div>

    <div class="content">
      <!-- INFRASTRUCTURE (documented + live status) -->
      <div class="bcard">
        <div class="bhead">
          <span class="material-icons">hub</span> Infrastructure
          <button class="refresh" @click="loadHealth" :disabled="hLoading">
            <span class="material-icons" :class="{ spin: hLoading }">refresh</span>
          </button>
        </div>

        <div class="flow">
          App (BillyAIService) &nbsp;→&nbsp; <b>/api/billy/chat</b> (Cloud Run) &nbsp;→&nbsp;
          FAISS retrieval over the docs &nbsp;→&nbsp; guardrailed prompt &nbsp;→&nbsp;
          Gemini (via cPanel proxy) &nbsp;→&nbsp; <b>{ answer, sources }</b>
        </div>

        <div v-if="hError" class="state err"><span class="material-icons">error</span> {{ hError }}</div>
        <div v-else class="grid">
          <div class="kv"><span>Endpoint</span><b>{{ health.endpoint || '—' }}</b></div>
          <div class="kv"><span>Vector DB</span><b>{{ health.vector_db || 'FAISS' }}</b></div>
          <div class="kv"><span>Embeddings</span><b>{{ health.embedding_model || '—' }}</b></div>
          <div class="kv"><span>Indexed chunks</span><b>{{ health.chunks ?? '—' }}</b></div>
          <div class="kv"><span>FAISS ready</span>
            <b :class="health.faiss_ready ? 'ok' : 'warn'">{{ health.faiss_ready ? 'yes' : 'lazy / fallback' }}</b>
          </div>
          <div class="kv"><span>Default model</span><b>{{ health.model_default || '—' }}</b></div>
          <div class="kv"><span>Active model</span><b>{{ health.model_active || '—' }}</b></div>
          <div class="kv"><span>API version</span><b>{{ health.logic_version || '—' }}</b></div>
        </div>

        <div class="cols">
          <div>
            <h4>Knowledge sources (RAG corpus)</h4>
            <ul class="chips">
              <li v-for="d in (health.documents || [])" :key="d"><span class="material-icons">description</span>{{ d }}</li>
            </ul>
          </div>
          <div>
            <h4>Guardrails</h4>
            <ul class="chips">
              <li v-for="g in (health.guardrails || [])" :key="g" class="guard"><span class="material-icons">shield</span>{{ g }}</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- EDITABLE CONFIG (saved to RTDB, read live by the server) -->
      <div class="bcard">
        <div class="bhead">
          <span class="material-icons">tune</span> Live configuration
          <span class="hint">Saved to <code>billy_analytics/config</code> — picked up by the server within ~60s, no redeploy.</span>
        </div>

        <div v-if="cLoading" class="state">Loading config…</div>
        <template v-else>
          <label class="fld">
            <span>Model</span>
            <select v-model="cfg.model">
              <option value="">(use default — {{ health.model_default || 'gemini-3.1-flash-lite' }})</option>
              <option value="gemini-3.1-flash-lite">gemini-3.1-flash-lite</option>
              <option value="gemini-2.5-flash">gemini-2.5-flash</option>
              <option value="gemini-flash-latest">gemini-flash-latest</option>
            </select>
          </label>

          <label class="fld">
            <span>System prompt override <em>(leave blank to use the built-in guardrailed prompt)</em></span>
            <textarea v-model="cfg.systemPrompt" rows="6"
              placeholder="Optional. If set, replaces Billy's default system prompt. Keep the guardrails (no code, no off-topic, educational-only law, no forgery help, never invent)."></textarea>
          </label>

          <div class="notes">
            <div class="nhead">
              <h4>Knowledge notes <em>(injected as authoritative context)</em></h4>
              <button class="add" @click="addNote"><span class="material-icons">add</span> Add note</button>
            </div>
            <div v-if="!cfg.knowledge.length" class="state sm">No notes yet — add a quick fact, FAQ answer, or correction.</div>
            <div v-for="(n, i) in cfg.knowledge" :key="i" class="note">
              <input v-model="n.title" placeholder="Title (e.g. 'Court admissibility')" />
              <input v-model="n.keywords" placeholder="Trigger keywords, comma-separated (blank = always)" />
              <textarea v-model="n.content" rows="3" placeholder="The answer / fact Billy should use."></textarea>
              <button class="del" @click="cfg.knowledge.splice(i, 1)"><span class="material-icons">delete</span></button>
            </div>
          </div>

          <div class="actions">
            <button class="save" @click="save" :disabled="saving">
              <span class="material-icons">{{ saving ? 'hourglass_top' : 'save' }}</span>
              {{ saving ? 'Saving…' : 'Save configuration' }}
            </button>
            <span v-if="savedMsg" class="saved"><span class="material-icons">check_circle</span> {{ savedMsg }}</span>
            <span v-if="cError" class="state err sm"><span class="material-icons">error</span> {{ cError }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { value as dbValue, patch as dbPatch } from '../services/db.js'

const API_BASE = 'https://billsense-api-340624938055.asia-southeast2.run.app'

const health = ref({})
const hLoading = ref(false)
const hError = ref('')

const cfg = reactive({ model: '', systemPrompt: '', knowledge: [] })
const cLoading = ref(true)
const saving = ref(false)
const cError = ref('')
const savedMsg = ref('')

async function loadHealth() {
  hLoading.value = true; hError.value = ''
  try {
    const r = await fetch(`${API_BASE}/api/billy/health`, { signal: AbortSignal.timeout(30000) })
    if (!r.ok) throw new Error('HTTP ' + r.status)
    health.value = await r.json()
  } catch (e) {
    hError.value = 'Could not reach the Billy service: ' + (e.message || e)
  } finally {
    hLoading.value = false
  }
}

async function loadConfig() {
  cLoading.value = true; cError.value = ''
  try {
    const data = await dbValue('billy_analytics/config')
    if (data && typeof data === 'object') {
      cfg.model = data.model || ''
      cfg.systemPrompt = data.systemPrompt || ''
      cfg.knowledge = Array.isArray(data.knowledge) ? data.knowledge.map(n => ({
        title: n.title || '', content: n.content || '', keywords: n.keywords || ''
      })) : []
    }
  } catch (e) {
    cError.value = 'Could not load config: ' + (e.message || e)
  } finally {
    cLoading.value = false
  }
}

function addNote() {
  cfg.knowledge.push({ title: '', content: '', keywords: '' })
}

async function save() {
  saving.value = true; cError.value = ''; savedMsg.value = ''
  try {
    const knowledge = cfg.knowledge
      .filter(n => (n.content || '').trim())
      .map(n => ({ title: (n.title || '').trim(), content: n.content.trim(), keywords: (n.keywords || '').trim() }))
    await dbPatch('billy_analytics/config', {
      model: cfg.model || '',
      systemPrompt: cfg.systemPrompt || '',
      knowledge,
      updatedAt: new Date().toISOString()
    })
    savedMsg.value = 'Saved — live within ~60s'
    setTimeout(() => { savedMsg.value = '' }, 4000)
    loadHealth()
  } catch (e) {
    cError.value = 'Save failed: ' + (e.message || e)
  } finally {
    saving.value = false
  }
}

onMounted(() => { loadHealth(); loadConfig() })
</script>

<style scoped>
.content { display: flex; flex-direction: column; gap: 18px; }
.bcard { background: #fff; border: 1px solid #e7e9ef; border-radius: 14px; padding: 18px; }
.bhead { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 16px; margin-bottom: 12px; }
.bhead .material-icons { color: #5b6bef; }
.bhead .refresh { margin-left: auto; border: 0; background: #f1f3f9; border-radius: 8px; padding: 6px; cursor: pointer; }
.bhead .hint { margin-left: auto; font-weight: 400; font-size: 12px; color: #8a90a6; }
.bhead .hint code { background: #f1f3f9; padding: 1px 5px; border-radius: 5px; }
.flow { background: #f7f8fc; border: 1px dashed #d7dbe9; border-radius: 10px; padding: 10px 12px; font-size: 13px; color: #4a5070; margin-bottom: 14px; line-height: 1.7; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; margin-bottom: 14px; }
.kv { background: #f7f8fc; border-radius: 10px; padding: 9px 12px; display: flex; flex-direction: column; gap: 2px; }
.kv span { font-size: 11px; color: #8a90a6; text-transform: uppercase; letter-spacing: .4px; }
.kv b { font-size: 13px; color: #2a2f45; word-break: break-word; }
.kv b.ok { color: #1f9d57; } .kv b.warn { color: #c98a00; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.cols h4 { margin: 0 0 8px; font-size: 13px; color: #2a2f45; }
.chips { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.chips li { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #4a5070; background: #f7f8fc; border-radius: 8px; padding: 6px 10px; }
.chips li .material-icons { font-size: 16px; color: #5b6bef; }
.chips li.guard .material-icons { color: #1f9d57; }
.fld { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.fld > span { font-size: 13px; font-weight: 600; color: #2a2f45; }
.fld em { font-weight: 400; color: #8a90a6; }
.fld select, .fld textarea, .note input, .note textarea { border: 1px solid #d7dbe9; border-radius: 9px; padding: 9px 11px; font: inherit; width: 100%; }
.notes { margin-bottom: 14px; }
.nhead { display: flex; align-items: center; margin-bottom: 8px; }
.nhead h4 { margin: 0; font-size: 13px; } .nhead em { font-weight: 400; color: #8a90a6; }
.nhead .add { margin-left: auto; }
.note { display: grid; grid-template-columns: 1fr 1fr auto; grid-template-areas: 'title kw del' 'content content del'; gap: 8px; background: #f7f8fc; border-radius: 10px; padding: 10px; margin-bottom: 8px; }
.note input:first-child { grid-area: title; } .note input:nth-child(2) { grid-area: kw; }
.note textarea { grid-area: content; } .note .del { grid-area: del; align-self: start; }
button.add, button.save { display: inline-flex; align-items: center; gap: 6px; border: 0; border-radius: 9px; padding: 9px 14px; font-weight: 600; cursor: pointer; }
button.add { background: #eef0fb; color: #4250c8; }
button.save { background: #5b6bef; color: #fff; }
button.save:disabled { opacity: .6; cursor: default; }
button.del { border: 0; background: #fdecec; color: #c0392b; border-radius: 8px; padding: 6px; cursor: pointer; }
.actions { display: flex; align-items: center; gap: 12px; }
.saved { display: inline-flex; align-items: center; gap: 5px; color: #1f9d57; font-size: 13px; }
.state { color: #8a90a6; font-size: 13px; padding: 8px 0; }
.state.sm { padding: 4px 0; } .state.err { color: #c0392b; display: flex; align-items: center; gap: 6px; }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 720px) { .cols { grid-template-columns: 1fr; } }
</style>
