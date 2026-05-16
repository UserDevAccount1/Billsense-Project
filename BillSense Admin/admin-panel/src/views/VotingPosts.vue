<template>
  <div>
    <div class="page-header">
      <h1>Voting Posts</h1>
      <p>Community posts and polls · {{ rows.length }} total</p>
    </div>
    <div class="content">
      <div v-if="loading" class="state">Loading posts…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>
      <div v-else-if="!rows.length" class="state">No voting posts.</div>
      <div v-else class="plist">
        <div v-for="p in rows" :key="p._key" class="pcard">
          <img v-if="p.downloadImageUrl" :src="p.downloadImageUrl" class="pimg"
               @error="e => e.target.style.display='none'" />
          <div class="pbody">
            <div class="phead">
              <span class="ptitle">{{ p.title || '(untitled post)' }}</span>
              <span class="badge" :class="p.votingEnabled ? 'ok' : 'neutral'">
                {{ p.votingEnabled ? 'Poll active' : 'No poll' }}
              </span>
            </div>
            <p class="pdesc">{{ p.description || '—' }}</p>
            <div v-if="p.votingQuestion" class="pquestion">
              <span class="material-icons">how_to_vote</span> {{ p.votingQuestion }}
            </div>
            <div class="pmeta">
              <span v-if="p.userName"><span class="material-icons">person</span>{{ p.userName }}</span>
              <span v-if="p.date"><span class="material-icons">event</span>{{ p.date }} {{ p.time || '' }}</span>
              <span><span class="material-icons">comment</span>{{ commentCount(p) }} comments</span>
              <span v-if="p.status"><span class="material-icons">flag</span>{{ p.status }}</span>
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
  name: 'VotingPosts',
  data() { return { rows: [], loading: true, error: '' } },
  async mounted() {
    try {
      const r = await list('Voting Posts')
      this.rows = r.sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
    } catch (e) { this.error = e.message }
    finally { this.loading = false }
  },
  methods: {
    commentCount(p) {
      return p.Comments && typeof p.Comments === 'object' ? Object.keys(p.Comments).length : 0
    }
  }
}
</script>

<style scoped>
.content { padding: 1.5rem 2rem; }
.state { color: var(--text-muted); padding: 2rem; text-align: center; }
.state.err { color: #f87171; display: flex; gap: .5rem; justify-content: center; align-items: center; }
.plist { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 1rem; }
.pcard { background: var(--bg-card); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; overflow: hidden; }
.pimg { width: 100%; height: 180px; object-fit: cover; display: block; }
.pbody { padding: 1rem 1.15rem; }
.phead { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .5rem; }
.ptitle { font-weight: 600; }
.pdesc { margin: 0 0 .65rem; font-size: .85rem; color: var(--text-muted); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.pquestion { display: flex; align-items: center; gap: .4rem; font-size: .85rem; color: #ffa31a;
  background: rgba(255,163,26,.08); padding: .5rem .7rem; border-radius: 8px; margin-bottom: .65rem; }
.pquestion .material-icons { font-size: 1rem; }
.pmeta { display: flex; flex-wrap: wrap; gap: .85rem; font-size: .78rem; color: var(--text-muted); }
.pmeta span { display: flex; align-items: center; gap: .3rem; }
.pmeta .material-icons { font-size: .9rem; }
.badge { font-size: .68rem; padding: .12rem .5rem; border-radius: 999px; flex-shrink: 0; }
.badge.ok { background: rgba(34,197,94,.15); color: #4ade80; }
.badge.neutral { background: rgba(148,163,184,.15); color: #94a3b8; }
</style>
