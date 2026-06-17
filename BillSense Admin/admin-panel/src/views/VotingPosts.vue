<template>
  <div class="voting-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">how_to_vote</span> Voting Posts</h1>
        <p class="subtitle">Community voting posts from Firebase "Voting Posts" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">ballot</span>
        <div class="stat-info"><span class="stat-value">{{ allPosts.length }}</span><span class="stat-label">Total Posts</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">thumb_up</span>
        <div class="stat-info"><span class="stat-value">{{ totalVotes }}</span><span class="stat-label">Total Votes</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search posts..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Title</th><th>Author</th><th>Votes</th><th>Comments</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedPosts.length === 0"><td colspan="7" class="empty-state"><span class="material-icons">inbox</span><p>No voting posts found.</p></td></tr>
          <tr v-for="post in paginatedPosts" :key="post._id" @click="selectedPost = post" class="report-row">
            <td>{{ formatDate(post.date || post.timestamp || post.createdAt) }}</td>
            <td class="title-cell">{{ post.title || post.question || '-' }}</td>
            <td>{{ post.authorName || post.author || post.userName || '-' }}</td>
            <td>
              <div class="votes-cell">
                <span class="vote-up"><span class="material-icons" style="font-size:14px">thumb_up</span> {{ post.upvotes || post.votesUp || 0 }}</span>
                <span class="vote-down"><span class="material-icons" style="font-size:14px">thumb_down</span> {{ post.downvotes || post.votesDown || 0 }}</span>
              </div>
            </td>
            <td>{{ post.commentsCount || (post.comments ? Object.keys(post.comments).length : 0) }}</td>
            <td><span :class="['status-badge', post.status === 'flagged' ? 'flagged' : 'active']">{{ post.status || 'active' }}</span></td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="selectedPost = post" title="View"><span class="material-icons">visibility</span></button>
              <button class="icon-btn danger" @click="confirmDelete(post)" title="Delete"><span class="material-icons">delete</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button class="btn btn-sm btn-ghost" @click="currentPage = 1" :disabled="currentPage === 1"><span class="material-icons">first_page</span></button>
      <button class="btn btn-sm btn-ghost" @click="currentPage--" :disabled="currentPage === 1"><span class="material-icons">chevron_left</span></button>
      <span class="page-info">Page {{ currentPage }} of {{ totalPages }}</span>
      <button class="btn btn-sm btn-ghost" @click="currentPage++" :disabled="currentPage === totalPages"><span class="material-icons">chevron_right</span></button>
      <button class="btn btn-sm btn-ghost" @click="currentPage = totalPages" :disabled="currentPage === totalPages"><span class="material-icons">last_page</span></button>
    </div>

    <!-- Detail Modal -->
    <div v-if="selectedPost" class="modal-overlay" @click.self="selectedPost = null">
      <div class="preview-panel">
        <div class="preview-header"><h2><span class="material-icons">how_to_vote</span> Post Detail</h2><button class="icon-btn" @click="selectedPost = null"><span class="material-icons">close</span></button></div>
        <div class="preview-body">
          <h3 class="post-title">{{ selectedPost.title || selectedPost.question || '-' }}</h3>
          <p class="post-meta">By {{ selectedPost.authorName || selectedPost.author || 'Unknown' }} | {{ formatDate(selectedPost.date || selectedPost.timestamp) }}</p>
          <div class="post-body" v-if="selectedPost.description || selectedPost.body">{{ selectedPost.description || selectedPost.body }}</div>
          <div v-if="selectedPost.imageUrl" class="post-image"><img :src="selectedPost.imageUrl" alt="Post image" /></div>

          <div v-if="selectedPost.comments" class="comments-section">
            <h4>Comments ({{ Object.keys(selectedPost.comments).length }})</h4>
            <div v-for="(comment, cid) in selectedPost.comments" :key="cid" class="comment-item">
              <span class="comment-author">{{ comment.userName || comment.author || 'User' }}</span>
              <span class="comment-text">{{ comment.text || comment.message || comment.comment || '' }}</span>
              <span class="comment-date">{{ formatDate(comment.date || comment.timestamp) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="confirm-dialog">
        <h3>Delete Post</h3>
        <p>Are you sure you want to delete this voting post? This action cannot be undone.</p>
        <div class="confirm-actions">
          <button class="btn btn-ghost" @click="deleteTarget = null">Cancel</button>
          <button class="btn btn-danger" @click="deletePost">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue, remove } from '../services/firebase.js'
export default {
  name: 'VotingPosts',
  data() { return { allPosts: [], loading: true, searchQuery: '', currentPage: 1, perPage: 20, selectedPost: null, deleteTarget: null, unsubscribe: null } },
  computed: {
    totalVotes() { return this.allPosts.reduce((sum, p) => sum + (p.upvotes || p.votesUp || 0) + (p.downvotes || p.votesDown || 0), 0) },
    filteredPosts() {
      let posts = [...this.allPosts]
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); posts = posts.filter(p => (p.title || '').toLowerCase().includes(q) || (p.authorName || '').toLowerCase().includes(q)) }
      posts.sort((a, b) => new Date(b.date || b.timestamp || b.createdAt || 0) - new Date(a.date || a.timestamp || a.createdAt || 0)); return posts
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredPosts.length / this.perPage)) },
    paginatedPosts() { const s = (this.currentPage - 1) * this.perPage; return this.filteredPosts.slice(s, s + this.perPage) }
  },
  watch: { searchQuery() { this.currentPage = 1 } },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Voting Posts'), (snapshot) => {
        const posts = []
        if (snapshot.exists()) { for (const [id, post] of Object.entries(snapshot.val())) { if (typeof post === 'object' && post) posts.push({ ...post, _id: id }) } }
        this.allPosts = posts; this.loading = false
      })
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() },
    confirmDelete(post) { this.deleteTarget = post },
    async deletePost() {
      if (!this.deleteTarget) return
      try { await remove(ref(database, `Voting Posts/${this.deleteTarget._id}`)); this.deleteTarget = null } catch (e) { console.error('Delete failed:', e); alert('Failed to delete post') }
    }
  }
}
</script>

<style scoped>
.voting-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.header-left h1 { display: flex; align-items: center; gap: 10px; font-size: 1.5rem; color: var(--text); margin: 0; }
.header-icon { color: var(--accent); font-size: 28px; }
.subtitle { color: var(--text-muted); margin: 4px 0 0; font-size: 0.875rem; }
.header-actions { display: flex; gap: 8px; }
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; }
.btn-ghost { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-ghost:hover { border-color: var(--accent); color: var(--text); }
.btn-danger { background: var(--danger); color: #fff; }
.btn-sm { padding: 6px 12px; font-size: 0.8rem; }
.stats-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 12px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 16px 20px; }
.stat-icon { font-size: 32px; color: var(--accent); }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--text); display: block; }
.stat-label { font-size: 0.75rem; color: var(--text-muted); }
.filter-bar { display: flex; gap: 16px; margin-bottom: 24px; }
.filter-group { display: flex; flex-direction: column; gap: 4px; }
.filter-group label { font-size: 0.75rem; color: var(--text-muted); font-weight: 600; text-transform: uppercase; }
.filter-input { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; }
.search-group { flex: 1; min-width: 220px; }
.search-input-wrapper { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 20px; }
.search-field { padding-left: 40px !important; width: 100%; }
.reports-table-container { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; overflow-x: auto; }
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th { padding: 12px 16px; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; border-bottom: 1px solid var(--border); }
.reports-table td { padding: 12px 16px; font-size: 0.875rem; color: var(--text); border-bottom: 1px solid var(--border); }
.report-row { cursor: pointer; transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }
.title-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.votes-cell { display: flex; gap: 12px; }
.vote-up { display: flex; align-items: center; gap: 4px; color: var(--success); font-size: 0.85rem; font-weight: 600; }
.vote-down { display: flex; align-items: center; gap: 4px; color: var(--danger); font-size: 0.85rem; font-weight: 600; }
.status-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.status-badge.active { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.flagged { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 6px; border-radius: 6px; }
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent); }
.icon-btn.danger:hover { background: rgba(239, 68, 68, 0.1); color: var(--danger); }
.actions-cell { display: flex; gap: 4px; }
.empty-state { text-align: center; padding: 48px 16px !important; color: var(--text-muted); }
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 8px; margin-top: 20px; padding: 16px 0; }
.page-info { color: var(--text-muted); font-size: 0.875rem; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(4px); }
.preview-panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; width: 90vw; max-width: 700px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; }
.preview-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid var(--border); }
.preview-header h2 { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; color: var(--text); margin: 0; }
.preview-body { overflow-y: auto; padding: 24px; flex: 1; }
.post-title { color: var(--text); margin: 0 0 8px; font-size: 1.2rem; }
.post-meta { color: var(--text-muted); font-size: 0.85rem; margin: 0 0 16px; }
.post-body { color: var(--text); line-height: 1.6; margin-bottom: 16px; padding: 16px; background: var(--bg); border-radius: 8px; }
.post-image img { width: 100%; border-radius: 8px; margin-bottom: 16px; }
.comments-section { margin-top: 20px; }
.comments-section h4 { color: var(--accent); margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.comment-item { padding: 10px; background: var(--bg); border-radius: 8px; margin-bottom: 8px; }
.comment-author { font-weight: 600; color: var(--accent); font-size: 0.85rem; display: block; margin-bottom: 4px; }
.comment-text { color: var(--text); font-size: 0.85rem; display: block; }
.comment-date { color: var(--text-muted); font-size: 0.75rem; display: block; margin-top: 4px; }
.confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 400px; width: 90vw; }
.confirm-dialog h3 { color: var(--text); margin: 0 0 12px; }
.confirm-dialog p { color: var(--text-muted); margin: 0 0 20px; font-size: 0.9rem; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
