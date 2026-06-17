<template>
  <div class="agents-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">smart_toy</span> Agents</h1>
        <p class="subtitle">Manage dispatch agents from Firebase "Agents" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="openForm()">
          <span class="material-icons">add</span> Add Agent
        </button>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">groups</span>
        <div class="stat-info"><span class="stat-value">{{ allAgents.length }}</span><span class="stat-label">Total Agents</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">circle</span>
        <div class="stat-info"><span class="stat-value">{{ activeCount }}</span><span class="stat-label">Active</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--text-muted)">circle</span>
        <div class="stat-info"><span class="stat-value">{{ inactiveCount }}</span><span class="stat-label">Inactive</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search agents..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Area/Region</th><th>Status</th><th>Assigned Cases</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="filteredAgents.length === 0"><td colspan="7" class="empty-state"><span class="material-icons">inbox</span><p>No agents found.</p></td></tr>
          <tr v-for="agent in filteredAgents" :key="agent._id" class="report-row">
            <td class="agent-name">{{ agent.name || '-' }}</td>
            <td>{{ agent.email || '-' }}</td>
            <td>{{ agent.phone || '-' }}</td>
            <td>{{ agent.area || agent.region || '-' }}</td>
            <td><span :class="['status-badge', (agent.status || 'active').toLowerCase()]">{{ agent.status || 'active' }}</span></td>
            <td>{{ agent.assignedCases || 0 }}</td>
            <td class="actions-cell">
              <button class="icon-btn" @click="openForm(agent)" title="Edit"><span class="material-icons">edit</span></button>
              <button class="icon-btn danger" @click="confirmDelete(agent)" title="Delete"><span class="material-icons">delete</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add/Edit Form -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="form-panel">
        <div class="form-header">
          <h2><span class="material-icons">{{ editingId ? 'edit' : 'person_add' }}</span> {{ editingId ? 'Edit' : 'Add' }} Agent</h2>
          <button class="icon-btn" @click="showForm = false"><span class="material-icons">close</span></button>
        </div>
        <div class="form-body">
          <div class="form-group">
            <label>Name</label>
            <input v-model="form.name" class="form-input" placeholder="Agent name..." />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Email</label>
              <input v-model="form.email" class="form-input" type="email" placeholder="Email..." />
            </div>
            <div class="form-group">
              <label>Phone</label>
              <input v-model="form.phone" class="form-input" placeholder="Phone..." />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Area/Region</label>
              <input v-model="form.area" class="form-input" placeholder="Coverage area..." />
            </div>
            <div class="form-group">
              <label>Status</label>
              <select v-model="form.status" class="form-input">
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
              </select>
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn-ghost" @click="showForm = false">Cancel</button>
            <button class="btn btn-primary" @click="saveAgent" :disabled="saving">
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
        <h3>Delete Agent</h3>
        <p>Are you sure you want to remove agent "{{ deleteTarget.name }}"?</p>
        <div class="confirm-actions">
          <button class="btn btn-ghost" @click="deleteTarget = null">Cancel</button>
          <button class="btn btn-danger" @click="deleteAgent">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue, push, update, remove } from '../services/firebase.js'
export default {
  name: 'Agents',
  data() {
    return {
      allAgents: [], loading: true, searchQuery: '', showForm: false, editingId: null, saving: false, deleteTarget: null, unsubscribe: null,
      form: { name: '', email: '', phone: '', area: '', status: 'active' }
    }
  },
  computed: {
    activeCount() { return this.allAgents.filter(a => (a.status || 'active') === 'active').length },
    inactiveCount() { return this.allAgents.filter(a => a.status === 'inactive').length },
    filteredAgents() {
      let agents = [...this.allAgents]
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); agents = agents.filter(a => (a.name || '').toLowerCase().includes(q) || (a.email || '').toLowerCase().includes(q) || (a.area || '').toLowerCase().includes(q)) }
      agents.sort((a, b) => (a.name || '').localeCompare(b.name || '')); return agents
    }
  },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Agents'), (snapshot) => {
        const agents = []
        if (snapshot.exists()) { for (const [id, agent] of Object.entries(snapshot.val())) { if (typeof agent === 'object' && agent) agents.push({ ...agent, _id: id }) } }
        this.allAgents = agents; this.loading = false
      })
    },
    openForm(agent = null) {
      if (agent) {
        this.editingId = agent._id
        this.form = { name: agent.name || '', email: agent.email || '', phone: agent.phone || '', area: agent.area || agent.region || '', status: agent.status || 'active' }
      } else {
        this.editingId = null
        this.form = { name: '', email: '', phone: '', area: '', status: 'active' }
      }
      this.showForm = true
    },
    async saveAgent() {
      if (!this.form.name.trim()) return alert('Name is required')
      this.saving = true
      try {
        const data = { ...this.form, updatedAt: new Date().toISOString() }
        if (this.editingId) {
          await update(ref(database, `Agents/${this.editingId}`), data)
        } else {
          data.createdAt = new Date().toISOString()
          await push(ref(database, 'Agents'), data)
        }
        this.showForm = false
      } catch (e) { console.error(e); alert('Failed to save agent') }
      finally { this.saving = false }
    },
    confirmDelete(agent) { this.deleteTarget = agent },
    async deleteAgent() {
      if (!this.deleteTarget) return
      try { await remove(ref(database, `Agents/${this.deleteTarget._id}`)); this.deleteTarget = null } catch (e) { console.error(e); alert('Failed to delete') }
    },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.agents-page { padding: 0; }
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
.agent-name { font-weight: 600; }
.status-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.status-badge.active { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.inactive { background: rgba(148, 163, 184, 0.15); color: #94A3B8; }
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
.form-input { width: 100%; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; font-family: inherit; }
.form-input:focus { border-color: var(--accent); outline: none; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 400px; width: 90vw; }
.confirm-dialog h3 { color: var(--text); margin: 0 0 12px; }
.confirm-dialog p { color: var(--text-muted); margin: 0 0 20px; font-size: 0.9rem; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
