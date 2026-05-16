<template>
  <div>
    <div class="page-header">
      <h1>Voting Posts — Management</h1>
      <p>Moderate community posts and polls · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div class="toolbar">
        <div class="filters">
          <button v-for="f in filters" :key="f" :class="{ on: active === f }" @click="active = f">{{ f }}</button>
        </div>
        <button class="refresh" @click="load" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">refresh</span> Refresh
        </button>
      </div>

      <div v-if="loading" class="state">Loading posts…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!filtered.length" class="state">No posts.</div>

      <div v-else class="tablewrap">
        <table>
          <thead>
            <tr>
              <th></th><th>Title / Question</th><th>Author</th><th>Date</th>
              <th>Poll</th><th>Status</th><th>Comments</th><th class="ac">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in filtered" :key="p._key" :class="{ busy: pending[p._key] }">
              <td><img v-if="p.downloadImageUrl" :src="p.downloadImageUrl" class="thumb"
                       @click="preview = p.downloadImageUrl"
                       @error="e => e.target.style.visibility='hidden'" /></td>
              <td>
                <div class="tt">{{ p.title || '(untitled)' }}</div>
                <div class="td" v-if="p.votingQuestion">❓ {{ p.votingQuestion }}</div>
                <div class="td" v-else>{{ (p.description || '').slice(0, 80) }}</div>
              </td>
              <td>{{ p.userName || '—' }}</td>
              <td class="nw">{{ p.date || '—' }}</td>
              <td>
                <button class="toggle" :class="{ on: p.votingEnabled }" @click="togglePoll(p)">
                  {{ p.votingEnabled ? 'Active' : 'Off' }}
                </button>
              </td>
              <td>
                <select :value="p.status || 'active'" @change="setStatus(p, $event.target.value)" class="sel">
                  <option>active</option><option>flagged</option>
                  <option>hidden</option><option>archived</option>
                </select>
              </td>
              <td class="cc">{{ commentCount(p) }}</td>
              <td class="ac">
                <button class="ico" title="View comments" :disabled="!commentCount(p)" @click="openComments(p)">
                  <span class="material-icons">forum</span>
                </button>
                <button class="ico" title="View image" :disabled="!p.downloadImageUrl"
                        @click="preview = p.downloadImageUrl">
                  <span class="material-icons">visibility</span>
                </button>
                <button class="ico danger" title="Delete post" @click="del(p)">
                  <span class="material-icons">delete</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="commentsFor" class="modal" @click.self="commentsFor = null">
      <div class="modal-box">
        <div class="modal-head">
          <strong>Comments — {{ commentsFor.title }}</strong>
          <button class="ico" @click="commentsFor = null"><span class="material-icons">close</span></button>
        </div>
        <div class="clist">
          <div v-for="(c, k) in (commentsFor.Comments || {})" :key="k" class="citem">
            <div class="ch">
              <b>{{ c.userName || c.user || 'anon' }}</b>
              <button class="ico danger sm" title="Delete comment" @click="delComment(commentsFor, k)">
                <span class="material-icons">delete</span>
              </button>
            </div>
            <p>{{ c.comment || c.text || c.message || JSON.stringify(c) }}</p>
          </div>
          <div v-if="!commentCount(commentsFor)" class="state">No comments.</div>
        </div>
      </div>
    </div>

    <div v-if="preview" class="lightbox" @click="preview = null"><img :src="preview" /></div>
    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type === 'err' ? 'error' : 'check_circle' }}</span>
      {{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { list, patch, remove } from '../services/db.js'
export default {
  name: 'VotingPosts',
  data() {
    return { rows: [], loading: true, error: '', active: 'All',
             pending: {}, preview: null, toast: null, commentsFor: null }
  },
  computed: {
    filters() {
      const s = new Set(['All'])
      this.rows.forEach(r => s.add(r.status || 'active'))
      return [...s]
    },
    filtered() {
      return this.active === 'All' ? this.rows
        : this.rows.filter(r => (r.status || 'active') === this.active)
    }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true; this.error = ''
      try {
        const r = await list('Voting Posts')
        this.rows = r.sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
      } catch (e) { this.error = e.message }
      finally { this.loading = false }
    },
    notify(msg, type = 'ok') {
      this.toast = { msg, type }; setTimeout(() => { this.toast = null }, 3000)
    },
    commentCount(p) {
      return p.Comments && typeof p.Comments === 'object' ? Object.keys(p.Comments).length : 0
    },
    openComments(p) { this.commentsFor = p },
    busy(k, v) { this.pending = { ...this.pending, [k]: v } },
    async togglePoll(p) {
      const next = !p.votingEnabled
      this.busy(p._key, true)
      try {
        await patch(`Voting Posts/${p._key}`, { votingEnabled: next })
        p.votingEnabled = next
        this.notify(`Poll ${next ? 'enabled' : 'disabled'}`)
      } catch (e) { this.notify(e.message, 'err') }
      finally { this.busy(p._key, false) }
    },
    async setStatus(p, status) {
      this.busy(p._key, true)
      try {
        await patch(`Voting Posts/${p._key}`, { status })
        p.status = status
        this.notify(`Post → ${status}`)
      } catch (e) { this.notify(e.message, 'err') }
      finally { this.busy(p._key, false) }
    },
    async del(p) {
      if (!confirm(`Delete post "${p.title || p._key}"? This cannot be undone.`)) return
      this.busy(p._key, true)
      try {
        await remove(`Voting Posts/${p._key}`)
        this.rows = this.rows.filter(r => r._key !== p._key)
        this.notify('Post deleted')
      } catch (e) { this.notify(e.message, 'err') }
    },
    async delComment(post, key) {
      if (!confirm('Delete this comment?')) return
      try {
        await remove(`Voting Posts/${post._key}/Comments/${key}`)
        delete post.Comments[key]
        this.notify('Comment deleted')
      } catch (e) { this.notify(e.message, 'err') }
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem;
  flex-wrap: wrap; margin-bottom: 1rem; }
.filters { display: flex; gap: .5rem; flex-wrap: wrap; }
.filters button { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08);
  color: var(--text-muted); padding: .35rem .85rem; border-radius: 999px; font-size: .8rem;
  cursor: pointer; text-transform: capitalize; }
.filters button.on { background: rgba(255,163,26,.15); color: #ffa31a; border-color: rgba(255,163,26,.3); }
.refresh { background: var(--bg-card); border: 1px solid rgba(255,255,255,.08); color: var(--text-muted);
  padding: .4rem .85rem; border-radius: 8px; font-size: .82rem; cursor: pointer; display: flex;
  align-items: center; gap: .35rem; }
.refresh .spin { animation: s 1s linear infinite; }
@keyframes s { to { transform: rotate(360deg); } }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; }
.tablewrap { overflow-x: auto; background: var(--bg-card); border: 1px solid rgba(255,255,255,.06);
  border-radius: 12px; }
table { width: 100%; border-collapse: collapse; font-size: .85rem; }
th { text-align: left; padding: .8rem 1rem; color: var(--text-muted); font-weight: 600;
  border-bottom: 1px solid rgba(255,255,255,.08); white-space: nowrap; }
td { padding: .7rem 1rem; border-bottom: 1px solid rgba(255,255,255,.04); vertical-align: middle; }
tr.busy { opacity: .5; pointer-events: none; }
.thumb { width: 42px; height: 42px; border-radius: 6px; object-fit: cover; cursor: pointer; }
.tt { font-weight: 600; } .td { color: var(--text-muted); font-size: .78rem; margin-top: .15rem; }
.nw { white-space: nowrap; } .cc { text-align: center; }
.sel { background: rgba(0,0,0,.25); border: 1px solid rgba(255,255,255,.1); border-radius: 6px;
  padding: .3rem .5rem; color: inherit; font-size: .8rem; text-transform: capitalize; cursor: pointer; }
.toggle { background: rgba(148,163,184,.15); color: #94a3b8; border: 0; border-radius: 999px;
  padding: .2rem .7rem; font-size: .76rem; cursor: pointer; }
.toggle.on { background: rgba(34,197,94,.18); color: #4ade80; }
.ac { text-align: right; white-space: nowrap; }
.ico { background: none; border: 0; color: var(--text-muted); cursor: pointer; padding: .25rem; }
.ico:hover:not(:disabled) { color: #fff; }
.ico:disabled { opacity: .3; cursor: not-allowed; }
.ico.danger:hover { color: #f87171; }
.ico.sm .material-icons { font-size: .95rem; }
.ico .material-icons { font-size: 1.15rem; }
.modal { position: fixed; inset: 0; background: rgba(0,0,0,.7); display: flex; align-items: center;
  justify-content: center; z-index: 999; padding: 2rem; }
.modal-box { background: var(--bg-card); border: 1px solid rgba(255,255,255,.1); border-radius: 12px;
  width: 100%; max-width: 520px; max-height: 80vh; display: flex; flex-direction: column; }
.modal-head { display: flex; justify-content: space-between; align-items: center;
  padding: 1rem 1.25rem; border-bottom: 1px solid rgba(255,255,255,.08); }
.clist { overflow-y: auto; padding: 1rem 1.25rem; display: flex; flex-direction: column; gap: .75rem; }
.citem { background: rgba(0,0,0,.2); border-radius: 8px; padding: .6rem .8rem; }
.ch { display: flex; justify-content: space-between; align-items: center; }
.citem p { margin: .25rem 0 0; font-size: .85rem; color: var(--text); }
.lightbox { position: fixed; inset: 0; background: rgba(0,0,0,.85); display: flex;
  align-items: center; justify-content: center; z-index: 1001; padding: 2rem; cursor: zoom-out; }
.lightbox img { max-width: 90vw; max-height: 90vh; border-radius: 8px; }
.toast { position: fixed; bottom: 1.5rem; right: 1.5rem; display: flex; align-items: center;
  gap: .5rem; padding: .7rem 1.1rem; border-radius: 8px; font-size: .87rem; z-index: 1002; }
.toast.ok { background: rgba(34,197,94,.18); color: #4ade80; border: 1px solid rgba(34,197,94,.3); }
.toast.err { background: rgba(248,113,113,.18); color: #f87171; border: 1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size: 1.05rem; }
</style>
