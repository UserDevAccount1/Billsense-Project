<template>
  <div>
    <div class="page-header">
      <h1>Settings</h1>
      <p>App configuration, announcements &amp; environment</p>
    </div>
    <div class="content">
      <div class="panel">
        <h3><span class="material-icons">campaign</span> Announcements</h3>
        <div v-if="ann.loading" class="muted">Loading…</div>
        <div v-else-if="ann.error" class="muted err">{{ ann.error }}</div>
        <div v-else-if="!ann.rows.length" class="muted">No announcements.</div>
        <div v-else class="alist">
          <div v-for="a in ann.rows" :key="a._key" class="aitem">
            <div class="ahead">
              <b>{{ a.title || '(untitled)' }}</b>
              <span class="badge" :class="a.active ? 'ok' : 'neutral'">{{ a.active ? 'active' : 'inactive' }}</span>
              <span v-if="a.priority" class="badge prio">{{ a.priority }}</span>
            </div>
            <p>{{ a.message }}</p>
            <small v-if="a.date">{{ a.date }}</small>
          </div>
        </div>
      </div>

      <div class="panel">
        <h3><span class="material-icons">dns</span> Environment</h3>
        <div class="kv"><span>Dashboard origin</span><b>{{ origin }}</b></div>
        <div class="kv"><span>Firebase project</span><b>bill-sense-aec6b</b></div>
        <div class="kv"><span>RTDB</span><b>bill-sense-aec6b-default-rtdb</b></div>
        <div class="kv"><span>Cloud Run API</span><b>asia-southeast2.run.app</b></div>
        <div class="kv"><span>Gemini proxy</span><b :class="proxyOk ? 'g' : 'r'">{{ proxyState }}</b></div>
        <div class="kv"><span>Auth gate</span><b>client-side soft gate</b></div>
      </div>

      <div class="panel danger">
        <h3><span class="material-icons">logout</span> Session</h3>
        <p class="muted">End your dashboard session on this device.</p>
        <button class="signout" @click="signOut">
          <span class="material-icons">logout</span> Sign out
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { list } from '../services/db.js'
import { logout } from '../services/auth.js'

export default {
  name: 'Settings',
  data() {
    return {
      ann: { rows: [], loading: true, error: '' },
      origin: typeof window !== 'undefined' ? window.location.origin : '',
      proxyState: 'checking…', proxyOk: false
    }
  },
  async mounted() {
    try {
      const r = await list('Announcements')
      this.ann.rows = r.sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
    } catch (e) { this.ann.error = e.message }
    finally { this.ann.loading = false }

    const base = window.location.hostname === 'billsense.dev-environment.site'
      ? '' : 'https://billsense.dev-environment.site'
    try {
      const h = await fetch(base + '/api/gemini/health', { signal: AbortSignal.timeout(8000) }).then(r => r.json())
      this.proxyOk = !!h.keyConfigured
      this.proxyState = h.keyConfigured ? 'connected · key configured' : 'reachable · no key'
    } catch {
      this.proxyState = 'unreachable'
    }
  },
  methods: {
    signOut() { logout(); this.$router.replace('/login') }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; display: flex; flex-direction: column; gap: 1rem; max-width: 760px; }
.panel { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1.1rem 1.25rem; }
.panel.danger { border-color: rgba(248,113,113,.25); }
.panel h3 { margin: 0 0 .85rem; font-size: 1rem; display: flex; align-items: center; gap: .5rem; }
.panel h3 .material-icons { color: #ffa31a; font-size: 1.15rem; }
.panel.danger h3 .material-icons { color: #f87171; }
.kv { display: flex; justify-content: space-between; padding: .4rem 0; font-size: .87rem;
  border-bottom: 1px solid rgba(255,255,255,.04); }
.kv span { color: var(--text-muted); }
.kv b.g { color: #4ade80; } .kv b.r { color: #f87171; }
.muted { color: var(--text-muted); font-size: .87rem; }
.muted.err { color: #f87171; }
.alist { display: flex; flex-direction: column; gap: .75rem; }
.aitem { background: rgba(0,0,0,.18); border-radius: 8px; padding: .75rem .9rem; }
.ahead { display: flex; align-items: center; gap: .5rem; margin-bottom: .35rem; }
.aitem p { margin: .25rem 0; font-size: .87rem; color: var(--text); }
.aitem small { color: var(--text-muted); font-size: .73rem; }
.badge { font-size: .66rem; padding: .1rem .45rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
.badge.prio { background: rgba(99,102,241,.15); color: #a5b4fc; }
.signout { background: rgba(248,113,113,.12); color: #f87171; border: 1px solid rgba(248,113,113,.3);
  border-radius: 8px; padding: .55rem 1rem; font-size: .9rem; cursor: pointer; display: flex;
  align-items: center; gap: .4rem; }
.signout:hover { background: rgba(248,113,113,.2); }
.signout .material-icons { font-size: 1.05rem; }
</style>
