<template>
  <div class="edu-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">menu_book</span> Educational Content</h1>
        <p class="subtitle">Manage Tutorials, Trivia, and FAQ content</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="openForm()">
          <span class="material-icons">add</span> Add {{ activeTabLabel }}
        </button>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tab-bar">
      <button v-for="tab in tabs" :key="tab.key" :class="['tab-btn', { active: activeTab === tab.key }]" @click="activeTab = tab.key">
        <span class="material-icons">{{ tab.icon }}</span> {{ tab.label }} ({{ getCount(tab.key) }})
      </button>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Title</th><th>Content Preview</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="4" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="currentItems.length === 0"><td colspan="4" class="empty-state"><span class="material-icons">inbox</span><p>No {{ activeTabLabel.toLowerCase() }} found.</p></td></tr>
          <tr v-for="item in currentItems" :key="item._id" class="report-row">
            <td>{{ formatDate(item.date || item.createdAt || item.timestamp) }}</td>
            <td class="title-cell">{{ item.title || item.question || '-' }}</td>
            <td class="content-cell">{{ item.content || item.answer || item.body || item.description || '-' }}</td>
            <td class="actions-cell">
              <button class="icon-btn" @click="openForm(item)" title="Edit"><span class="material-icons">edit</span></button>
              <button class="icon-btn danger" @click="confirmDelete(item)" title="Delete"><span class="material-icons">delete</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add/Edit Form -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="form-panel">
        <div class="form-header">
          <h2><span class="material-icons">{{ editingId ? 'edit' : 'add' }}</span> {{ editingId ? 'Edit' : 'New' }} {{ activeTabLabel }}</h2>
          <button class="icon-btn" @click="showForm = false"><span class="material-icons">close</span></button>
        </div>
        <div class="form-body">
          <div class="form-group">
            <label>{{ activeTab === 'FAQ' ? 'Question' : 'Title' }}</label>
            <input v-model="form.title" class="form-input" :placeholder="activeTab === 'FAQ' ? 'Question...' : 'Title...'" />
          </div>
          <div class="form-group">
            <label>{{ activeTab === 'FAQ' ? 'Answer' : 'Content' }}</label>
            <textarea v-model="form.content" class="form-textarea" :placeholder="activeTab === 'FAQ' ? 'Answer...' : 'Content...'" rows="6"></textarea>
          </div>
          <div class="form-group" v-if="activeTab !== 'FAQ'">
            <label>Image URL (optional)</label>
            <input v-model="form.imageUrl" class="form-input" placeholder="https://..." />
          </div>
          <div class="form-actions">
            <button class="btn btn-ghost" @click="showForm = false">Cancel</button>
            <button class="btn btn-primary" @click="saveItem" :disabled="saving">
              <span class="material-icons" :class="{ spin: saving }">{{ saving ? 'sync' : 'save' }}</span> {{ saving ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="confirm-dialog">
        <h3>Delete {{ activeTabLabel }}</h3>
        <p>Are you sure you want to delete "{{ deleteTarget.title || deleteTarget.question }}"?</p>
        <div class="confirm-actions">
          <button class="btn btn-ghost" @click="deleteTarget = null">Cancel</button>
          <button class="btn btn-danger" @click="deleteItem">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue, push, update, remove } from '../services/firebase.js'
export default {
  name: 'EducationalContent',
  data() {
    return {
      activeTab: 'Tutorials',
      tabs: [
        { key: 'Tutorials', label: 'Tutorials', icon: 'school' },
        { key: 'Trivia', label: 'Trivia', icon: 'quiz' },
        { key: 'FAQ', label: 'FAQ', icon: 'help_outline' }
      ],
      data: { Tutorials: [], Trivia: [], FAQ: [] },
      loading: true, showForm: false, editingId: null, saving: false, deleteTarget: null,
      form: { title: '', content: '', imageUrl: '' },
      unsubscribers: []
    }
  },
  computed: {
    activeTabLabel() { return this.tabs.find(t => t.key === this.activeTab)?.label || this.activeTab },
    currentItems() {
      return [...(this.data[this.activeTab] || [])].sort((a, b) => new Date(b.date || b.createdAt || 0) - new Date(a.date || a.createdAt || 0))
    }
  },
  mounted() { this.loadData() },
  beforeUnmount() { this.unsubscribers.forEach(u => u()) },
  methods: {
    getCount(key) { return (this.data[key] || []).length },
    loadData() {
      this.loading = true
      this.tabs.forEach(tab => {
        const unsub = onValue(ref(database, tab.key), (snapshot) => {
          const items = []
          if (snapshot.exists()) {
            for (const [id, item] of Object.entries(snapshot.val())) {
              if (typeof item === 'object' && item) items.push({ ...item, _id: id })
            }
          }
          this.data[tab.key] = items
          this.loading = false
        })
        this.unsubscribers.push(unsub)
      })
    },
    openForm(item = null) {
      if (item) {
        this.editingId = item._id
        this.form = { title: item.title || item.question || '', content: item.content || item.answer || item.body || '', imageUrl: item.imageUrl || '' }
      } else {
        this.editingId = null
        this.form = { title: '', content: '', imageUrl: '' }
      }
      this.showForm = true
    },
    async saveItem() {
      if (!this.form.title.trim()) return alert('Title/Question is required')
      this.saving = true
      try {
        const isFaq = this.activeTab === 'FAQ'
        const data = isFaq
          ? { question: this.form.title, answer: this.form.content, date: new Date().toISOString() }
          : { title: this.form.title, content: this.form.content, imageUrl: this.form.imageUrl || null, date: new Date().toISOString() }
        if (this.editingId) {
          await update(ref(database, `${this.activeTab}/${this.editingId}`), data)
        } else {
          await push(ref(database, this.activeTab), data)
        }
        this.showForm = false
      } catch (e) { console.error(e); alert('Failed to save') }
      finally { this.saving = false }
    },
    confirmDelete(item) { this.deleteTarget = item },
    async deleteItem() {
      if (!this.deleteTarget) return
      try { await remove(ref(database, `${this.activeTab}/${this.deleteTarget._id}`)); this.deleteTarget = null } catch (e) { console.error(e); alert('Failed to delete') }
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) },
    refreshData() { this.unsubscribers.forEach(u => u()); this.unsubscribers = []; this.loadData() }
  }
}
</script>

<style scoped>
.edu-page { padding: 0; }
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
.tab-bar { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.tab-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; color: var(--text-muted); font-size: 0.875rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.tab-btn:hover { border-color: var(--accent); color: var(--text); }
.tab-btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }
.tab-btn .material-icons { font-size: 18px; }
.reports-table-container { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; overflow-x: auto; }
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th { padding: 12px 16px; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; border-bottom: 1px solid var(--border); }
.reports-table td { padding: 12px 16px; font-size: 0.875rem; color: var(--text); border-bottom: 1px solid var(--border); }
.report-row { transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }
.title-cell { font-weight: 600; max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.content-cell { max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-muted); }
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
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 400px; width: 90vw; }
.confirm-dialog h3 { color: var(--text); margin: 0 0 12px; }
.confirm-dialog p { color: var(--text-muted); margin: 0 0 20px; font-size: 0.9rem; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
