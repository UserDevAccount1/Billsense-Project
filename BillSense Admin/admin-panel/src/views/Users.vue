<template>
  <div>
    <div class="page-header">
      <h1>Users</h1>
      <p>Registered BillSense app users · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div class="toolbar" v-if="!loading && !error">
        <div class="search-box">
          <span class="material-icons">search</span>
          <input v-model="q" type="text" placeholder="Search name, email, phone…" />
          <button v-if="q" class="clear" @click="q=''"><span class="material-icons">close</span></button>
        </div>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span>
        </button>
      </div>

      <div v-if="loading" class="state">Loading users…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filtered.length" class="state">No users found.</div>
      <div v-else class="cards">
        <div v-for="u in filtered" :key="u._key" class="ucard" :class="{ busy: pending[u._key] }">
          <img v-if="u.image" :src="u.image" class="avatar" @error="e => e.target.style.display='none'" />
          <div v-else class="avatar ph"><span class="material-icons">person</span></div>
          <div class="udata">
            <div class="uname">{{ u.name || '(no name)' }}
              <span class="badge" :class="statusClass(u.status)">{{ u.status || 'unknown' }}</span>
            </div>
            <div class="urow"><span class="material-icons">mail</span>{{ u.email || '—' }}</div>
            <div class="urow"><span class="material-icons">phone</span>{{ u.phone || '—' }}</div>
            <div class="urow muted">id: {{ u.id || u._key }}</div>
          </div>
          <div class="uactions">
            <button class="act" @click="openEdit(u)" title="Edit user"><span class="material-icons">edit</span></button>
            <button class="act del" @click="askDelete(u)" title="Delete user"><span class="material-icons">delete</span></button>
          </div>
        </div>
      </div>
    </div>

    <!-- Edit modal -->
    <div v-if="form" class="modal-backdrop" @click.self="closeForm">
      <div class="modal">
        <div class="mhead">
          <h2>Edit user</h2>
          <button class="x" @click="closeForm"><span class="material-icons">close</span></button>
        </div>
        <label>Name<input v-model="form.name" type="text" /></label>
        <label>Email<input v-model="form.email" type="email" /></label>
        <label>Phone<input v-model="form.phone" type="text" /></label>
        <label>Status
          <select v-model="form.status">
            <option value="verified">verified</option>
            <option value="active">active</option>
            <option value="pending">pending</option>
            <option value="blocked">blocked</option>
          </select>
        </label>
        <p class="hint">Only these fields are updated — password, tokens and other data are left untouched.</p>
        <div class="mfoot">
          <button class="ghost" @click="closeForm" :disabled="saving">Cancel</button>
          <button class="primary" @click="save" :disabled="saving">
            <span v-if="saving" class="material-icons spin">progress_activity</span> Save changes
          </button>
        </div>
      </div>
    </div>

    <!-- Delete confirm -->
    <div v-if="delTarget" class="modal-backdrop" @click.self="delTarget=null">
      <div class="modal">
        <div class="mhead"><h2><span class="material-icons" style="color:#f87171;vertical-align:middle">warning</span> Delete user</h2>
          <button class="x" @click="delTarget=null"><span class="material-icons">close</span></button></div>
        <p style="font-size:.9rem;line-height:1.6;">Delete <strong>{{ delTarget.name || delTarget._key }}</strong>
          ({{ delTarget.email || 'no email' }})? This permanently removes the user from the database and cannot be undone.</p>
        <div class="mfoot">
          <button class="ghost" @click="delTarget=null" :disabled="saving">Cancel</button>
          <button class="danger" @click="doDelete" :disabled="saving">
            <span v-if="saving" class="material-icons spin">progress_activity</span> Delete
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
import { list, update, remove } from '../services/db.js'
export default {
  name: 'Users',
  data() { return { rows: [], loading: true, error: '', q: '', form: null, delTarget: null, saving: false, pending: {}, toast: null } },
  computed: {
    filtered() {
      const q = this.q.trim().toLowerCase()
      if (!q) return this.rows
      return this.rows.filter(u =>
        (u.name || '').toLowerCase().includes(q) ||
        (u.email || '').toLowerCase().includes(q) ||
        (u.phone || '').toLowerCase().includes(q))
    }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try { this.rows = await list('Users') }
      catch (e) { this.error = e.message }
      finally { this.loading = false }
    },
    statusClass(s) {
      const v = (s || '').toLowerCase()
      if (v.includes('verified') || v === 'active') return 'ok'
      if (v.includes('pending')) return 'warn'
      if (v.includes('block') || v.includes('ban')) return 'err'
      return 'neutral'
    },
    notify(m, t = 'ok') { this.toast = { msg: m, type: t }; setTimeout(() => this.toast = null, 3000) },
    openEdit(u) {
      this.form = { _key: u._key, name: u.name || '', email: u.email || '', phone: u.phone || '', status: u.status || 'pending' }
    },
    closeForm() { if (!this.saving) this.form = null },
    async save() {
      const f = this.form
      this.saving = true
      try {
        const fields = { name: f.name.trim(), email: f.email.trim(), phone: f.phone.trim(), status: f.status }
        await update(`Users/${f._key}`, fields)
        const u = this.rows.find(x => x._key === f._key)
        if (u) Object.assign(u, fields)
        this.notify('User updated')
        this.form = null
      } catch (e) { this.notify(e.message, 'err') } finally { this.saving = false }
    },
    askDelete(u) { this.delTarget = u },
    async doDelete() {
      const u = this.delTarget
      this.saving = true
      this.pending = { ...this.pending, [u._key]: true }
      try {
        await remove(`Users/${u._key}`)
        this.rows = this.rows.filter(x => x._key !== u._key)
        this.notify('User deleted')
        this.delTarget = null
      } catch (e) { this.notify(e.message, 'err') }
      finally { this.saving = false; const p = { ...this.pending }; delete p[u._key]; this.pending = p }
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.toolbar { display: flex; gap: .6rem; align-items: center; margin-bottom: 1.1rem; }
.search-box { display: flex; align-items: center; gap: .5rem; flex: 1; background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); border-radius: 8px; padding: .5rem .8rem; }
.search-box input { flex: 1; background: none; border: 0; color: var(--text); outline: none; font-size: .9rem; }
.search-box .material-icons { color: var(--text-muted); font-size: 1.1rem; }
.search-box .clear { background: none; border: 0; color: var(--text-muted); cursor: pointer; padding: 0; }
.refresh { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted); border-radius: 8px; padding: .5rem .65rem; cursor: pointer; }
.spin { animation: s 1s linear infinite; } @keyframes s { to { transform: rotate(360deg); } }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; align-items: center; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 1rem; }
.ucard { display: flex; gap: 1rem; background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 1rem; position: relative; }
.ucard.busy { opacity: .5; pointer-events: none; }
.avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.avatar.ph { display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.05); }
.avatar.ph .material-icons { color: var(--text-muted); }
.udata { min-width: 0; flex: 1; }
.uname { font-weight: 600; display: flex; align-items: center; gap: .5rem; margin-bottom: .35rem; }
.urow { display: flex; align-items: center; gap: .4rem; font-size: .85rem; color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.urow .material-icons { font-size: .95rem; }
.urow.muted { opacity: .6; font-size: .75rem; }
.uactions { display: flex; flex-direction: column; gap: .35rem; }
.act { background: none; border: 1px solid rgba(255,255,255,.1); border-radius: 6px; color: var(--text-muted); cursor: pointer; padding: .25rem; }
.act:hover { color: #a5b4fc; border-color: #a5b4fc; } .act.del:hover { color: #f87171; border-color: #f87171; }
.act .material-icons { font-size: 1.05rem; }
.badge { font-size: .68rem; padding: .1rem .5rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.warn { background: rgba(251,191,36,.15); color: #fbbf24; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
.modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,.6); display: flex; align-items: center; justify-content: center; z-index: 1100; padding: 1rem; }
.modal { background: var(--bg-card,#1a1d29); border: 1px solid rgba(255,255,255,.1); border-radius: 14px; width: 100%; max-width: 460px; padding: 1.5rem; }
.mhead { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.mhead h2 { margin: 0; font-size: 1.1rem; }
.x { background: none; border: 0; color: var(--text-muted); cursor: pointer; }
.modal label { display: block; font-size: .8rem; color: var(--text-muted); margin-bottom: .85rem; }
.modal input, .modal select { width: 100%; margin-top: .35rem; background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.12); border-radius: 8px; color: var(--text,#fff); padding: .55rem .7rem; font-size: .9rem; box-sizing: border-box; font-family: inherit; }
.hint { font-size: .72rem; color: var(--text-muted); margin: 0 0 .5rem; }
.mfoot { display: flex; justify-content: flex-end; gap: .6rem; margin-top: .5rem; }
.mfoot button { display: flex; align-items: center; gap: .35rem; padding: .55rem 1.1rem; border-radius: 8px; font-size: .88rem; cursor: pointer; border: 1px solid transparent; }
.mfoot .ghost { background: none; border-color: rgba(255,255,255,.15); color: var(--text-muted); }
.mfoot .primary { background: #ffa31a; color: #1a1d29; font-weight: 600; }
.mfoot .danger { background: #ef4444; color: #fff; font-weight: 600; }
.mfoot button:disabled { opacity: .6; cursor: not-allowed; }
.mfoot .material-icons { font-size: 1rem; }
.toast { position: fixed; bottom: 1.5rem; right: 1.5rem; display: flex; align-items: center; gap: .5rem; padding: .7rem 1.1rem; border-radius: 8px; font-size: .87rem; z-index: 1200; }
.toast.ok { background: rgba(34,197,94,.18); color: #4ade80; border: 1px solid rgba(34,197,94,.3); }
.toast.err { background: rgba(248,113,113,.18); color: #f87171; border: 1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size: 1.05rem; }
</style>
