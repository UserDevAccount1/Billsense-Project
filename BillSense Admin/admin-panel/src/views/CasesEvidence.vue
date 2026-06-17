<template>
  <div class="cases-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">policy</span> Cases &amp; Evidence</h1>
        <p class="subtitle">Reported counterfeit cases from Firebase "Cases" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">folder_open</span>
        <div class="stat-info"><span class="stat-value">{{ allCases.length }}</span><span class="stat-label">Total Cases</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--warning)">pending</span>
        <div class="stat-info"><span class="stat-value">{{ openCases }}</span><span class="stat-label">Open</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">check_circle</span>
        <div class="stat-info"><span class="stat-value">{{ resolvedCases }}</span><span class="stat-label">Resolved</span></div>
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
          <input v-model="searchQuery" placeholder="Search cases..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Case ID</th><th>Reporter</th><th>Location</th><th>Description</th><th>Status</th><th>Evidence</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="8" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedCases.length === 0"><td colspan="8" class="empty-state"><span class="material-icons">inbox</span><p>No cases found.</p></td></tr>
          <tr v-for="c in paginatedCases" :key="c._id" @click="selectedCase = c" class="report-row">
            <td>{{ formatDate(c.date || c.timestamp || c.createdAt) }}</td>
            <td><code class="scan-id">{{ c._id }}</code></td>
            <td>{{ c.reporterName || c.userName || c.reporter || '-' }}</td>
            <td>{{ c.location || c.address || '-' }}</td>
            <td class="desc-cell">{{ c.description || c.details || '-' }}</td>
            <td><span :class="['status-badge', (c.status || 'open').toLowerCase()]">{{ c.status || 'open' }}</span></td>
            <td>{{ c.evidenceCount || (c.evidence ? Object.keys(c.evidence).length : 0) }}</td>
            <td class="actions-cell" @click.stop><button class="icon-btn" @click="selectedCase = c"><span class="material-icons">visibility</span></button></td>
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

    <div v-if="selectedCase" class="modal-overlay" @click.self="selectedCase = null">
      <div class="preview-panel">
        <div class="preview-header"><h2><span class="material-icons">policy</span> Case Detail</h2><button class="icon-btn" @click="selectedCase = null"><span class="material-icons">close</span></button></div>
        <div class="preview-body">
          <div class="detail-info">
            <h4>Case Information</h4>
            <div class="detail-row"><span class="detail-label">Case ID</span><span class="detail-value"><code>{{ selectedCase._id }}</code></span></div>
            <div class="detail-row"><span class="detail-label">Date</span><span class="detail-value">{{ formatDate(selectedCase.date || selectedCase.timestamp) }}</span></div>
            <div class="detail-row"><span class="detail-label">Reporter</span><span class="detail-value">{{ selectedCase.reporterName || selectedCase.userName || '-' }}</span></div>
            <div class="detail-row"><span class="detail-label">Location</span><span class="detail-value">{{ selectedCase.location || selectedCase.address || '-' }}</span></div>
            <div class="detail-row"><span class="detail-label">Status</span><span class="detail-value"><span :class="['status-badge', (selectedCase.status || 'open').toLowerCase()]">{{ selectedCase.status || 'open' }}</span></span></div>
          </div>
          <div v-if="selectedCase.description || selectedCase.details" class="case-description">
            <h4>Description</h4>
            <p>{{ selectedCase.description || selectedCase.details }}</p>
          </div>
          <div v-if="selectedCase.evidence" class="evidence-section">
            <h4>Evidence ({{ Object.keys(selectedCase.evidence).length }})</h4>
            <div class="evidence-grid">
              <div v-for="(ev, eid) in selectedCase.evidence" :key="eid" class="evidence-item">
                <img v-if="ev.imageUrl || ev.url" :src="ev.imageUrl || ev.url" class="evidence-img" alt="Evidence" />
                <p v-if="ev.description || ev.note">{{ ev.description || ev.note }}</p>
              </div>
            </div>
          </div>
          <div v-if="selectedCase.images && selectedCase.images.length" class="evidence-section">
            <h4>Images</h4>
            <div class="evidence-grid">
              <div v-for="(img, idx) in selectedCase.images" :key="idx" class="evidence-item">
                <img :src="img" class="evidence-img" alt="Case image" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue } from '../services/firebase.js'
export default {
  name: 'CasesEvidence',
  data() { return { allCases: [], loading: true, filterStatus: 'all', searchQuery: '', currentPage: 1, perPage: 20, selectedCase: null, unsubscribe: null } },
  computed: {
    openCases() { return this.allCases.filter(c => !c.status || c.status.toLowerCase() === 'open').length },
    resolvedCases() { return this.allCases.filter(c => (c.status || '').toLowerCase() === 'resolved').length },
    filteredCases() {
      let cases = [...this.allCases]
      if (this.filterStatus !== 'all') cases = cases.filter(c => (c.status || 'open').toLowerCase() === this.filterStatus)
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); cases = cases.filter(c => (c.reporterName || '').toLowerCase().includes(q) || (c.location || '').toLowerCase().includes(q) || (c.description || '').toLowerCase().includes(q)) }
      cases.sort((a, b) => new Date(b.date || b.timestamp || 0) - new Date(a.date || a.timestamp || 0)); return cases
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredCases.length / this.perPage)) },
    paginatedCases() { const s = (this.currentPage - 1) * this.perPage; return this.filteredCases.slice(s, s + this.perPage) }
  },
  watch: { filterStatus() { this.currentPage = 1 }, searchQuery() { this.currentPage = 1 } },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Cases'), (snapshot) => {
        const cases = []
        if (snapshot.exists()) {
          const data = snapshot.val()
          // Cases might be nested under userId
          for (const [key, val] of Object.entries(data)) {
            if (typeof val === 'object' && val) {
              if (val.date || val.timestamp || val.description || val.location) {
                cases.push({ ...val, _id: key })
              } else {
                // Nested under userId
                for (const [caseId, caseData] of Object.entries(val)) {
                  if (typeof caseData === 'object' && caseData) cases.push({ ...caseData, _id: caseId, _userId: key })
                }
              }
            }
          }
        }
        this.allCases = cases; this.loading = false
      })
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.cases-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.header-left h1 { display: flex; align-items: center; gap: 10px; font-size: 1.5rem; color: var(--text); margin: 0; }
.header-icon { color: var(--accent); font-size: 28px; }
.subtitle { color: var(--text-muted); margin: 4px 0 0; font-size: 0.875rem; }
.header-actions { display: flex; gap: 8px; }
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem; font-weight: 600; transition: all 0.2s; }
.btn-ghost { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-ghost:hover { border-color: var(--accent); color: var(--text); }
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
.desc-cell { max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scan-id { background: rgba(79, 134, 247, 0.15); color: var(--accent); padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; }
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
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(4px); }
.preview-panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; width: 90vw; max-width: 800px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; }
.preview-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid var(--border); }
.preview-header h2 { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; color: var(--text); margin: 0; }
.preview-body { overflow-y: auto; padding: 24px; flex: 1; }
.detail-info h4, .case-description h4, .evidence-section h4 { color: var(--accent); margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid rgba(42,58,78,0.5); }
.detail-label { color: var(--text-muted); font-size: 0.85rem; }
.detail-value { color: var(--text); font-size: 0.85rem; font-weight: 500; }
.case-description { margin-top: 20px; }
.case-description p { color: var(--text); line-height: 1.6; padding: 12px; background: var(--bg); border-radius: 8px; }
.evidence-section { margin-top: 20px; }
.evidence-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.evidence-item { background: var(--bg); border-radius: 8px; padding: 8px; }
.evidence-img { width: 100%; border-radius: 6px; }
.evidence-item p { margin: 8px 0 0; font-size: 0.8rem; color: var(--text-muted); }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
