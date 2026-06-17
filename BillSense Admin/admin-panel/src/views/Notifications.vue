<template>
  <div class="notif-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">notifications</span> Notifications</h1>
        <p class="subtitle">Manage push notifications sent to BillSense users</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="showSendForm = true">
          <span class="material-icons">send</span> Send Notification
        </button>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">mark_email_read</span>
        <div class="stat-info"><span class="stat-value">{{ allNotifications.length }}</span><span class="stat-label">Total Sent</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search notifications..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Title</th><th>Message</th><th>Target</th><th>Type</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="5" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedNotifications.length === 0"><td colspan="5" class="empty-state"><span class="material-icons">inbox</span><p>No notifications found.</p></td></tr>
          <tr v-for="n in paginatedNotifications" :key="n._id" class="report-row">
            <td>{{ formatDate(n.date || n.sentAt || n.timestamp) }}</td>
            <td class="title-cell">{{ n.title || '-' }}</td>
            <td class="msg-cell">{{ n.message || n.body || '-' }}</td>
            <td>{{ n.target || n.targetUser || 'All Users' }}</td>
            <td><span :class="['type-badge', n.type || 'general']">{{ n.type || 'general' }}</span></td>
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

    <!-- Send Notification Form -->
    <div v-if="showSendForm" class="modal-overlay" @click.self="showSendForm = false">
      <div class="form-panel">
        <div class="form-header">
          <h2><span class="material-icons">send</span> Send Notification</h2>
          <button class="icon-btn" @click="showSendForm = false"><span class="material-icons">close</span></button>
        </div>
        <div class="form-body">
          <div class="form-group">
            <label>Title</label>
            <input v-model="sendForm.title" class="form-input" placeholder="Notification title..." />
          </div>
          <div class="form-group">
            <label>Message</label>
            <textarea v-model="sendForm.message" class="form-textarea" placeholder="Notification message..." rows="4"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Target</label>
              <select v-model="sendForm.target" class="form-input">
                <option value="all">All Users</option>
                <option value="specific">Specific User</option>
              </select>
            </div>
            <div class="form-group">
              <label>Type</label>
              <select v-model="sendForm.type" class="form-input">
                <option value="general">General</option>
                <option value="alert">Alert</option>
                <option value="update">Update</option>
              </select>
            </div>
          </div>
          <div class="form-group" v-if="sendForm.target === 'specific'">
            <label>User Email or ID</label>
            <input v-model="sendForm.targetUser" class="form-input" placeholder="user@email.com or user ID..." />
          </div>
          <div class="form-actions">
            <button class="btn btn-ghost" @click="showSendForm = false">Cancel</button>
            <button class="btn btn-primary" @click="sendNotification" :disabled="sending">
              <span class="material-icons" :class="{ spin: sending }">{{ sending ? 'sync' : 'send' }}</span>
              {{ sending ? 'Sending...' : 'Send' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue, push } from '../services/firebase.js'
export default {
  name: 'Notifications',
  data() {
    return {
      allNotifications: [], loading: true, searchQuery: '', currentPage: 1, perPage: 20,
      showSendForm: false, sending: false, unsubscribe: null,
      sendForm: { title: '', message: '', target: 'all', type: 'general', targetUser: '' }
    }
  },
  computed: {
    filteredNotifications() {
      let notifs = [...this.allNotifications]
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); notifs = notifs.filter(n => (n.title || '').toLowerCase().includes(q) || (n.message || '').toLowerCase().includes(q)) }
      notifs.sort((a, b) => new Date(b.date || b.sentAt || 0) - new Date(a.date || a.sentAt || 0)); return notifs
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredNotifications.length / this.perPage)) },
    paginatedNotifications() { const s = (this.currentPage - 1) * this.perPage; return this.filteredNotifications.slice(s, s + this.perPage) }
  },
  watch: { searchQuery() { this.currentPage = 1 } },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Notifications'), (snapshot) => {
        const items = []
        if (snapshot.exists()) {
          for (const [id, item] of Object.entries(snapshot.val())) {
            if (typeof item === 'object' && item) items.push({ ...item, _id: id })
          }
        }
        this.allNotifications = items; this.loading = false
      })
    },
    async sendNotification() {
      if (!this.sendForm.title.trim() || !this.sendForm.message.trim()) return alert('Title and message are required')
      this.sending = true
      try {
        const data = {
          title: this.sendForm.title,
          message: this.sendForm.message,
          type: this.sendForm.type,
          target: this.sendForm.target === 'all' ? 'All Users' : this.sendForm.targetUser,
          sentAt: new Date().toISOString(),
          date: new Date().toISOString()
        }
        await push(ref(database, 'Notifications'), data)
        this.showSendForm = false
        this.sendForm = { title: '', message: '', target: 'all', type: 'general', targetUser: '' }
      } catch (e) { console.error(e); alert('Failed to send notification') }
      finally { this.sending = false }
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.notif-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.header-left h1 { display: flex; align-items: center; gap: 10px; font-size: 1.5rem; color: var(--text); margin: 0; }
.header-icon { color: var(--accent); font-size: 28px; }
.subtitle { color: var(--text-muted); margin: 4px 0 0; font-size: 0.875rem; }
.header-actions { display: flex; gap: 8px; }
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; }
.btn-primary { background: var(--accent); color: #fff; } .btn-primary:disabled { opacity: 0.6; }
.btn-ghost { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-ghost:hover { border-color: var(--accent); color: var(--text); }
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
.report-row { transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }
.title-cell { font-weight: 600; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-muted); }
.type-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.type-badge.general { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }
.type-badge.alert { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.type-badge.update { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.empty-state { text-align: center; padding: 48px 16px !important; color: var(--text-muted); }
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 8px; margin-top: 20px; padding: 16px 0; }
.page-info { color: var(--text-muted); font-size: 0.875rem; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(4px); }
.form-panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; width: 90vw; max-width: 550px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; }
.form-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid var(--border); }
.form-header h2 { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; color: var(--text); margin: 0; }
.form-body { overflow-y: auto; padding: 24px; flex: 1; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 0.8rem; color: var(--text-muted); font-weight: 600; text-transform: uppercase; margin-bottom: 6px; }
.form-input, .form-textarea { width: 100%; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; font-family: inherit; }
.form-input:focus, .form-textarea:focus { border-color: var(--accent); outline: none; }
.form-textarea { resize: vertical; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
