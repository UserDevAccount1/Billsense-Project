<template>
  <div>
    <div class="page-header">
      <h1>Cases</h1>
      <p>Counterfeit incident reports filed by users · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div class="filters" v-if="rows.length">
        <button v-for="f in filters" :key="f" :class="{ on: active === f }" @click="active = f">{{ f }}</button>
      </div>
      <div v-if="loading" class="state">Loading cases…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filtered.length" class="state">No cases.</div>
      <div v-else class="cards">
        <div v-for="c in filtered" :key="c._key" class="ccard">
          <img v-if="c.image" :src="c.image" class="cimg" @error="e => e.target.style.display='none'" />
          <div class="cbody">
            <div class="chead">
              <span class="ctitle">{{ c.title || '(untitled case)' }}</span>
              <span class="badge" :class="badge(c.status)">{{ c.status || 'open' }}</span>
            </div>
            <p class="cdesc">{{ c.description || '—' }}</p>
            <div class="cmeta">
              <span v-if="c.userName"><span class="material-icons">person</span>{{ c.userName }}</span>
              <span v-if="c.address"><span class="material-icons">place</span>{{ c.address }}</span>
              <span v-if="c.caseDate || c.date"><span class="material-icons">event</span>{{ c.caseDate || c.date }}</span>
              <span v-if="c.isArchived"><span class="material-icons">inventory_2</span>archived</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { list } from '../services/db.js'
export default {
  name: 'Cases',
  data() { return { rows: [], loading: true, error: '', active: 'All' } },
  computed: {
    filters() {
      const s = new Set(['All'])
      this.rows.forEach(r => s.add((r.status || 'open')))
      return [...s]
    },
    filtered() {
      return this.active === 'All' ? this.rows
        : this.rows.filter(r => (r.status || 'open') === this.active)
    }
  },
  async mounted() {
    try {
      const r = await list('Cases')
      this.rows = r.sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
    } catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    badge(s) {
      const v = (s || 'open').toLowerCase()
      if (v.includes('resolved') || v.includes('closed') || v.includes('verified')) return 'ok'
      if (v.includes('review') || v.includes('pending') || v.includes('progress')) return 'warn'
      if (v.includes('reject') || v.includes('invalid')) return 'err'
      return 'neutral'
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.filters { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; }
.filters button { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .35rem .85rem; border-radius: 999px; font-size: .8rem; cursor: pointer; text-transform: capitalize; }
.filters button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; align-items: center; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 1rem; }
.ccard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; overflow: hidden; }
.cimg { width: 100%; height: 160px; object-fit: cover; display: block; }
.cbody { padding: 1rem 1.15rem; }
.chead { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .5rem; }
.ctitle { font-weight: 600; }
.cdesc { margin: 0 0 .65rem; font-size: .85rem; color: var(--text-muted); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.cmeta { display: flex; flex-wrap: wrap; gap: .85rem; font-size: .78rem; color: var(--text-muted); }
.cmeta span { display: flex; align-items: center; gap: .3rem; }
.cmeta .material-icons { font-size: .9rem; }
.badge { font-size: .68rem; padding: .12rem .5rem; border-radius: 999px; text-transform: capitalize; flex-shrink: 0; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.warn { background: rgba(251,191,36,.15); color: #fbbf24; }
.badge.err { background: rgba(248,113,113,.15); color: #f87171; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
