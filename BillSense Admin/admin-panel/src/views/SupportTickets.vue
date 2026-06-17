<template>
  <div class="support-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">support_agent</span> Support Tickets</h1>
        <p class="subtitle">All support tickets across users from Firebase "Support" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">confirmation_number</span>
        <div class="stat-info"><span class="stat-value">{{ allTickets.length }}</span><span class="stat-label">Total Tickets</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--warning)">pending</span>
        <div class="stat-info"><span class="stat-value">{{ openCount }}</span><span class="stat-label">Open</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">check_circle</span>
        <div class="stat-info"><span class="stat-value">{{ resolvedCount }}</span><span class="stat-label">Resolved</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group">
        <label>Status</label>
        <select v-model="filterStatus" class="filter-select">
          <option value="all">All</option>
          <option value="open">Open</option>
          <option value="resolved">Resolved</option>
        </select>
      </div>
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search tickets..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Ticket ID</th><th>User</th><th>Subject</th><th>Status</th><th>Messages</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedTickets.length === 0"><td colspan="7" class="empty-state"><span class="material-icons">inbox</span><p>No support tickets found.</p></td></tr>
          <tr v-for="t in paginatedTickets" :key="t._id" @click="openTicket(t)" class="report-row">
            <td>{{ formatDate(t.date || t.createdAt || t.timestamp) }}</td>
            <td><code class="ticket-id">{{ t._id }}</code></td>
            <td>
              <div class="user-cell">
                <span class="user-name">{{ t._userName || 'Unknown' }}</span>
                <span class="user-email">{{ t._userEmail || '' }}</span>
              </div>
            </td>
            <td class="subject-cell">{{ t.subject || t.title || t.category || '-' }}</td>
            <td><span :class="['status-badge', (t.status || 'open').toLowerCase()]">{{ t.status || 'open' }}</span></td>
            <td>{{ t._messageCount }}</td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="openTicket(t)"><span class="material-icons">visibility</span></button>
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

    <!-- Ticket Detail / Chat Modal -->
    <div v-if="selectedTicket" class="modal-overlay" @click.self="selectedTicket = null">
      <div class="chat-panel">
        <div class="chat-header">
          <div>
            <h2><span class="material-icons">support_agent</span> {{ selectedTicket.subject || selectedTicket.title || 'Support Ticket' }}</h2>
            <p class="chat-meta">{{ selectedTicket._userName }} | <span :class="['status-badge', (selectedTicket.status || 'open').toLowerCase()]">{{ selectedTicket.status || 'open' }}</span></p>
          </div>
          <div class="chat-header-actions">
            <button v-if="(selectedTicket.status || 'open') !== 'resolved'" class="btn btn-sm btn-success" @click="resolveTicket">
              <span class="material-icons">check</span> Resolve
            </button>
            <button class="icon-btn" @click="selectedTicket = null"><span class="material-icons">close</span></button>
          </div>
        </div>
        <div class="chat-messages" ref="chatMessages">
          <div v-for="(msg, idx) in ticketMessages" :key="idx" :class="['chat-msg', msg.isAdmin ? 'admin' : 'user']">
            <div class="msg-bubble">
              <span class="msg-sender">{{ msg.sender || (msg.isAdmin ? 'Admin' : selectedTicket._userName) }}</span>
              <p class="msg-text">{{ msg.message || msg.text || msg.content || '' }}</p>
              <span class="msg-time">{{ formatDate(msg.date || msg.timestamp) }}</span>
            </div>
          </div>
          <div v-if="ticketMessages.length === 0" class="empty-chat">
            <span class="material-icons">chat_bubble_outline</span>
            <p>No messages in this ticket yet.</p>
          </div>
        </div>
        <div class="chat-input-area">
          <input v-model="replyText" class="chat-input" placeholder="Type a reply..." @keyup.enter="sendReply" />
          <button class="btn btn-primary btn-send" @click="sendReply" :disabled="!replyText.trim()">
            <span class="material-icons">send</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, get, onValue, push, update } from '../services/firebase.js'
export default {
  name: 'SupportTickets',
  data() {
    return {
      allTickets: [], usersMap: {}, loading: true, filterStatus: 'all', searchQuery: '',
      currentPage: 1, perPage: 20, selectedTicket: null, replyText: '', unsubscribe: null
    }
  },
  computed: {
    openCount() { return this.allTickets.filter(t => !t.status || t.status.toLowerCase() === 'open').length },
    resolvedCount() { return this.allTickets.filter(t => (t.status || '').toLowerCase() === 'resolved').length },
    filteredTickets() {
      let tickets = [...this.allTickets]
      if (this.filterStatus !== 'all') tickets = tickets.filter(t => (t.status || 'open').toLowerCase() === this.filterStatus)
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); tickets = tickets.filter(t => (t._userName || '').toLowerCase().includes(q) || (t.subject || '').toLowerCase().includes(q) || (t.category || '').toLowerCase().includes(q)) }
      tickets.sort((a, b) => new Date(b.date || b.createdAt || 0) - new Date(a.date || a.createdAt || 0))
      return tickets
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredTickets.length / this.perPage)) },
    paginatedTickets() { const s = (this.currentPage - 1) * this.perPage; return this.filteredTickets.slice(s, s + this.perPage) },
    ticketMessages() {
      if (!this.selectedTicket) return []
      const msgs = []
      const ticket = this.selectedTicket
      // First message from the ticket itself
      if (ticket.message || ticket.description) {
        msgs.push({ message: ticket.message || ticket.description, sender: ticket._userName, date: ticket.date || ticket.createdAt, isAdmin: false })
      }
      // Messages/replies
      if (ticket.messages) {
        for (const [, msg] of Object.entries(ticket.messages)) {
          if (typeof msg === 'object') msgs.push(msg)
        }
      }
      if (ticket.replies) {
        for (const [, reply] of Object.entries(ticket.replies)) {
          if (typeof reply === 'object') msgs.push(reply)
        }
      }
      msgs.sort((a, b) => new Date(a.date || a.timestamp || 0) - new Date(b.date || b.timestamp || 0))
      return msgs
    }
  },
  watch: { filterStatus() { this.currentPage = 1 }, searchQuery() { this.currentPage = 1 } },
  mounted() {
    // Check URL for filter param
    const params = new URLSearchParams(window.location.search)
    if (params.get('filter') === 'resolved') this.filterStatus = 'resolved'
    this.loadData()
  },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const usersSnap = await get(ref(database, 'Users'))
        if (usersSnap.exists()) {
          for (const [uid, user] of Object.entries(usersSnap.val())) {
            this.usersMap[uid] = { name: user.name || user.fullName || 'Unknown', email: user.email || '' }
          }
        }
        this.unsubscribe = onValue(ref(database, 'Support'), (snapshot) => {
          const tickets = []
          if (snapshot.exists()) {
            const data = snapshot.val()
            for (const [userId, userTickets] of Object.entries(data)) {
              if (typeof userTickets === 'object' && userTickets) {
                // Check if it's a single ticket or nested
                if (userTickets.subject || userTickets.message || userTickets.category) {
                  tickets.push(this.normalizeTicket(userTickets, userId, userId))
                } else {
                  for (const [ticketId, ticket] of Object.entries(userTickets)) {
                    if (typeof ticket === 'object' && ticket) tickets.push(this.normalizeTicket(ticket, userId, ticketId))
                  }
                }
              }
            }
          }
          this.allTickets = tickets; this.loading = false
        })
      } catch (e) { console.error(e); this.loading = false }
    },
    normalizeTicket(ticket, userId, ticketId) {
      const user = this.usersMap[userId] || {}
      let messageCount = 0
      if (ticket.messages) messageCount += Object.keys(ticket.messages).length
      if (ticket.replies) messageCount += Object.keys(ticket.replies).length
      return { ...ticket, _id: ticketId, _userId: userId, _userName: user.name || ticket.userName || 'Unknown', _userEmail: user.email || ticket.userEmail || '', _messageCount: messageCount }
    },
    openTicket(t) {
      this.selectedTicket = t
      this.replyText = ''
      this.$nextTick(() => { if (this.$refs.chatMessages) this.$refs.chatMessages.scrollTop = this.$refs.chatMessages.scrollHeight })
    },
    async sendReply() {
      if (!this.replyText.trim() || !this.selectedTicket) return
      try {
        const reply = { message: this.replyText, sender: 'Admin', isAdmin: true, date: new Date().toISOString() }
        const ticketPath = `Support/${this.selectedTicket._userId}/${this.selectedTicket._id}`
        await push(ref(database, `${ticketPath}/replies`), reply)
        this.replyText = ''
        // Refresh the selected ticket data
        const snap = await get(ref(database, ticketPath))
        if (snap.exists()) {
          const updated = this.normalizeTicket(snap.val(), this.selectedTicket._userId, this.selectedTicket._id)
          this.selectedTicket = updated
        }
        this.$nextTick(() => { if (this.$refs.chatMessages) this.$refs.chatMessages.scrollTop = this.$refs.chatMessages.scrollHeight })
      } catch (e) { console.error(e); alert('Failed to send reply') }
    },
    async resolveTicket() {
      if (!this.selectedTicket) return
      try {
        const ticketPath = `Support/${this.selectedTicket._userId}/${this.selectedTicket._id}`
        await update(ref(database, ticketPath), { status: 'resolved' })
        this.selectedTicket = { ...this.selectedTicket, status: 'resolved' }
      } catch (e) { console.error(e); alert('Failed to resolve ticket') }
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.support-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.header-left h1 { display: flex; align-items: center; gap: 10px; font-size: 1.5rem; color: var(--text); margin: 0; }
.header-icon { color: var(--accent); font-size: 28px; }
.subtitle { color: var(--text-muted); margin: 4px 0 0; font-size: 0.875rem; }
.header-actions { display: flex; gap: 8px; }
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; }
.btn-primary { background: var(--accent); color: #fff; } .btn-primary:disabled { opacity: 0.6; }
.btn-ghost { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-ghost:hover { border-color: var(--accent); color: var(--text); }
.btn-success { background: var(--success); color: #fff; }
.btn-sm { padding: 6px 12px; font-size: 0.8rem; }
.stats-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 12px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 16px 20px; }
.stat-icon { font-size: 32px; color: var(--accent); }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--text); display: block; }
.stat-label { font-size: 0.75rem; color: var(--text-muted); }
.filter-bar { display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; align-items: flex-end; }
.filter-group { display: flex; flex-direction: column; gap: 4px; }
.filter-group label { font-size: 0.75rem; color: var(--text-muted); font-weight: 600; text-transform: uppercase; }
.filter-select, .filter-input { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; min-width: 140px; }
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
.ticket-id { background: rgba(79, 134, 247, 0.15); color: var(--accent); padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; }
.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-name { font-weight: 600; } .user-email { font-size: 0.75rem; color: var(--text-muted); }
.subject-cell { max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.status-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.status-badge.open { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.status-badge.resolved { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 6px; border-radius: 6px; }
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent); }
.actions-cell { display: flex; gap: 4px; }
.empty-state { text-align: center; padding: 48px 16px !important; color: var(--text-muted); }
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 8px; margin-top: 20px; padding: 16px 0; }
.page-info { color: var(--text-muted); font-size: 0.875rem; }
/* Chat Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(4px); }
.chat-panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; width: 90vw; max-width: 700px; height: 80vh; display: flex; flex-direction: column; overflow: hidden; }
.chat-header { display: flex; justify-content: space-between; align-items: flex-start; padding: 16px 24px; border-bottom: 1px solid var(--border); }
.chat-header h2 { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; color: var(--text); margin: 0; }
.chat-meta { color: var(--text-muted); font-size: 0.8rem; margin: 4px 0 0; }
.chat-header-actions { display: flex; gap: 8px; align-items: center; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px 24px; display: flex; flex-direction: column; gap: 12px; }
.chat-msg { display: flex; }
.chat-msg.user { justify-content: flex-start; }
.chat-msg.admin { justify-content: flex-end; }
.msg-bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px; }
.chat-msg.user .msg-bubble { background: var(--bg); border: 1px solid var(--border); border-bottom-left-radius: 4px; }
.chat-msg.admin .msg-bubble { background: rgba(79, 134, 247, 0.15); border: 1px solid rgba(79, 134, 247, 0.3); border-bottom-right-radius: 4px; }
.msg-sender { font-size: 0.75rem; font-weight: 600; color: var(--accent); display: block; margin-bottom: 4px; }
.msg-text { color: var(--text); font-size: 0.875rem; line-height: 1.5; margin: 0; }
.msg-time { font-size: 0.7rem; color: var(--text-muted); display: block; margin-top: 4px; text-align: right; }
.empty-chat { text-align: center; padding: 40px; color: var(--text-muted); }
.empty-chat .material-icons { font-size: 48px; margin-bottom: 8px; }
.chat-input-area { display: flex; gap: 8px; padding: 16px 24px; border-top: 1px solid var(--border); }
.chat-input { flex: 1; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; font-family: inherit; }
.chat-input:focus { border-color: var(--accent); outline: none; }
.btn-send { padding: 10px 16px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 768px) { .chat-panel { width: 95vw; height: 90vh; } .msg-bubble { max-width: 85%; } }
</style>
