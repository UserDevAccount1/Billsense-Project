<template>
  <div class="announcements-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">campaign</span> Announcements</h1>
        <p class="subtitle">Manage announcements shown on the mobile app Home screen</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="openForm()">
          <span class="material-icons">add</span> New Announcement
        </button>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">notifications_active</span>
        <div class="stat-info"><span class="stat-value">{{ allAnnouncements.length }}</span><span class="stat-label">Total</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">toggle_on</span>
        <div class="stat-info"><span class="stat-value">{{ activeCount }}</span><span class="stat-label">Active</span></div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Title</th><th>Message</th><th>Priority</th><th>Active</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="6" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="allAnnouncements.length === 0"><td colspan="6" class="empty-state"><span class="material-icons">inbox</span><p>No announcements yet.</p></td></tr>
          <tr v-for="a in sortedAnnouncements" :key="a._id" class="report-row">
            <td>{{ formatDate(a.date || a.createdAt) }}</td>
            <td class="title-cell">{{ a.title || '-' }}</td>
            <td class="msg-cell">{{ a.message || '-' }}</td>
            <td><span :class="['priority-badge', a.priority || 'info']">{{ a.priority || 'info' }}</span></td>
            <td>
              <button :class="['toggle-active', { on: a.active !== false }]" @click="toggleActive(a)">
                <span class="material-icons">{{ a.active !== false ? 'toggle_on' : 'toggle_off' }}</span>
              </button>
            </td>
            <td class="actions-cell">
              <button class="icon-btn" @click="openForm(a)" title="Edit"><span class="material-icons">edit</span></button>
              <button class="icon-btn danger" @click="confirmDelete(a)" title="Delete"><span class="material-icons">delete</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add/Edit Form Modal -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="form-panel">
        <div class="form-header">
          <h2><span class="material-icons">{{ editingId ? 'edit' : 'add' }}</span> {{ editingId ? 'Edit' : 'New' }} Announcement</h2>
          <button class="icon-btn" @click="showForm = false"><span class="material-icons">close</span></button>
        </div>
        <div class="form-body">
          <div class="form-group">
            <label>Title</label>
            <input v-model="form.title" class="form-input" placeholder="Announcement title..." />
          </div>
          <div class="form-group">
            <label>Message</label>
            <textarea v-model="form.message" class="form-textarea" placeholder="Announcement message..." rows="4"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Priority</label>
              <select v-model="form.priority" class="form-input">
                <option value="info">Info</option>
                <option value="warning">Warning</option>
                <option value="important">Important</option>
              </select>
            </div>
            <div class="form-group">
              <label>Active</label>
              <select v-model="form.active" class="form-input">
                <option :value="true">Yes</option>
                <option :value="false">No</option>
              </select>
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn-ghost" @click="showForm = false">Cancel</button>
            <button class="btn btn-primary" @click="saveAnnouncement" :disabled="saving">
              <span class="material-icons" :class="{ spin: saving }">{{ saving ? 'sync' : 'save' }}</span>
              {{ saving ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="confirm-dialog">
        <h3>Delete Announcement</h3>
        <p>Are you sure you want to delete "{{ deleteTarget.title }}"?</p>
        <div class="confirm-actions">
          <button class="btn btn-ghost" @click="deleteTarget = null">Cancel</button>
          <button class="btn btn-danger" @click="deleteAnnouncement">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue, set, push, remove, update } from '../services/firebase.js'
export default {
  name: 'Announcements',
  data() {
    return {
      allAnnouncements: [], loading: true, showForm: false, editingId: null, saving: false, deleteTarget: null, unsubscribe: null,
      form: { title: '', message: '', priority: 'info', active: true }
    }
  },
  computed: {
    activeCount() { return this.allAnnouncements.filter(a => a.active !== false).length },
    sortedAnnouncements() { return [...this.allAnnouncements].sort((a, b) => new Date(b.date || b.createdAt || 0) - new Date(a.date || a.createdAt || 0)) }
  },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Announcements'), (snapshot) => {
        const items = []
        if (snapshot.exists()) { for (const [id, item] of Object.entries(snapshot.val())) { if (typeof item === 'object' && item) items.push({ ...item, _id: id }) } }
        this.allAnnouncements = items; this.loading = false
      })
    },
    openForm(announcement = null) {
      if (announcement) {
        this.editingId = announcement._id
        this.form = { title: announcement.title || '', message: announcement.message || '', priority: announcement.priority || 'info', active: announcement.active !== false }
      } else {
        this.editingId = null
        this.form = { title: '', message: '', priority: 'info', active: true }
      }
      this.showForm = true
    },
    async saveAnnouncement() {
      if (!this.form.title.trim()) return alert('Title is required')
      this.saving = true
      try {
        const data = { ...this.form, date: new Date().toISOString() }
        if (this.editingId) {
          await update(ref(database, `Announcements/${this.editingId}`), data)
        } else {
          await push(ref(database, 'Announcements'), data)
        }
        this.showForm = false
      } catch (e) { console.error(e); alert('Failed to save') }
      finally { this.saving = false }
    },
    async toggleActive(a) {
      try { await update(ref(database, `Announcements/${a._id}`), { active: a.active === false }) } catch (e) { console.error(e) }
    },
    confirmDelete(a) { this.deleteTarget = a },
    async deleteAnnouncement() {
      if (!this.deleteTarget) return
      try { await remove(ref(database, `Announcements/${this.deleteTarget._id}`)); this.deleteTarget = null } catch (e) { console.error(e); alert('Failed to delete') }
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.announcements-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.header-left h1 { display: flex; align-items: center; gap: 10px; font-size: 1.5rem; color: var(--text); margin: 0; }
.header-icon { color: var(--accent); font-size: 28px; }
.subtitle { color: var(--text-muted); margin: 4px 0 0; font-size: 0.875rem; }
.header-actions { display: flex; gap: 8px; }
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; }
.btn-primary { background: var(--accent); color: #fff; } .btn-primary:disabled { opacity: 0.6; }
.btn-ghost { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-ghost:hover { border-color: var(--accent); color: var(--text); }
.btn-danger { background: var(--danger); color: #fff; }
.stats-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { display: flex; align-items: center; gap: 12px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 16px 20px; }
.stat-icon { font-size: 32px; color: var(--accent); }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--text); display: block; }
.stat-label { font-size: 0.75rem; color: var(--text-muted); }
.reports-table-container { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; overflow-x: auto; }
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th { padding: 12px 16px; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; border-bottom: 1px solid var(--border); }
.reports-table td { padding: 12px 16px; font-size: 0.875rem; color: var(--text); border-bottom: 1px solid var(--border); }
.report-row { transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }
.title-cell { font-weight: 600; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-muted); }
.priority-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.priority-badge.info { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }
.priority-badge.warning { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.priority-badge.important { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.toggle-active { background: none; border: none; cursor: pointer; padding: 4px; border-radius: 6px; }
.toggle-active .material-icons { font-size: 28px; color: var(--text-muted); }
.toggle-active.on .material-icons { color: var(--success); }
.icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 6px; border-radius: 6px; }
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent); }
.icon-btn.danger:hover { background: rgba(239, 68, 68, 0.1); color: var(--danger); }
.actions-cell { display: flex; gap: 4px; }
.empty-state { text-align: center; padding: 48px 16px !important; color: var(--text-muted); }
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }
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
.confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 400px; width: 90vw; }
.confirm-dialog h3 { color: var(--text); margin: 0 0 12px; }
.confirm-dialog p { color: var(--text-muted); margin: 0 0 20px; font-size: 0.9rem; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
