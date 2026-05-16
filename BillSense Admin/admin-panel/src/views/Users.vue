<template>
  <div>
    <div class="page-header">
      <h1>Users</h1>
      <p>Registered BillSense app users · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div v-if="loading" class="state">Loading users…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!rows.length" class="state">No users found.</div>
      <div v-else class="cards">
        <div v-for="u in rows" :key="u._key" class="ucard">
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
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { list } from '../services/db.js'
export default {
  name: 'Users',
  data() { return { rows: [], loading: true, error: '' } },
  async mounted() {
    try { this.rows = await list('Users') }
    catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    statusClass(s) {
      const v = (s || '').toLowerCase()
      if (v.includes('verified') || v === 'active') return 'ok'
      if (v.includes('pending')) return 'warn'
      if (v.includes('block') || v.includes('ban')) return 'err'
      return 'neutral'
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; align-items: center; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 1rem; }
.ucard { display: flex; gap: 1rem; background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; padding: 1rem; }
.avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.avatar.ph { display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.05); }
.avatar.ph .material-icons { color: var(--text-muted); }
.udata { min-width: 0; flex: 1; }
.uname { font-weight: 600; display: flex; align-items: center; gap: .5rem; margin-bottom: .35rem; }
.urow { display: flex; align-items: center; gap: .4rem; font-size: .85rem; color: var(--text-muted);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.urow .material-icons { font-size: .95rem; }
.urow.muted { opacity: .6; font-size: .75rem; }
.badge { font-size: .68rem; padding: .1rem .5rem; border-radius: 999px; text-transform: capitalize; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.warn { background: rgba(251,191,36,.15); color: #fbbf24; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
