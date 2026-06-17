<template>
  <div class="detect-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">find_in_page</span> Detections</h1>
        <p class="subtitle">All detections from Firebase "Detections" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">sensors</span>
        <div class="stat-info"><span class="stat-value">{{ allDetections.length }}</span><span class="stat-label">Total Detections</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search detections..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead><tr><th>Date</th><th>Detection ID</th><th>Type</th><th>Confidence</th><th>Features Detected</th><th>Actions</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="6" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedDetections.length === 0"><td colspan="6" class="empty-state"><span class="material-icons">inbox</span><p>No detections found.</p></td></tr>
          <tr v-for="det in paginatedDetections" :key="det._id" @click="selectedDetection = det" class="report-row">
            <td>{{ formatDate(det.date || det.timestamp) }}</td>
            <td><code class="scan-id">{{ det._id }}</code></td>
            <td>{{ det.detectionType || det.type || '-' }}</td>
            <td><span :class="['confidence-badge', (det._confidence || '').toLowerCase()]">{{ det._confidence || '-' }}</span></td>
            <td>{{ det.featuresDetected || det.detectedFeaturesCount || '-' }}</td>
            <td class="actions-cell" @click.stop><button class="icon-btn" @click="selectedDetection = det"><span class="material-icons">visibility</span></button></td>
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

    <div v-if="selectedDetection" class="modal-overlay" @click.self="selectedDetection = null">
      <div class="preview-panel">
        <div class="preview-header"><h2><span class="material-icons">find_in_page</span> Detection Detail</h2><button class="icon-btn" @click="selectedDetection = null"><span class="material-icons">close</span></button></div>
        <div class="preview-body">
          <h4>Raw Data</h4>
          <pre class="raw-json">{{ JSON.stringify(selectedDetection, null, 2) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, onValue } from '../services/firebase.js'
export default {
  name: 'Detections',
  data() { return { allDetections: [], loading: true, searchQuery: '', currentPage: 1, perPage: 20, selectedDetection: null, unsubscribe: null } },
  computed: {
    filteredDetections() {
      let dets = [...this.allDetections]
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); dets = dets.filter(d => JSON.stringify(d).toLowerCase().includes(q)) }
      dets.sort((a, b) => new Date(b.date || b.timestamp || 0) - new Date(a.date || a.timestamp || 0)); return dets
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredDetections.length / this.perPage)) },
    paginatedDetections() { const s = (this.currentPage - 1) * this.perPage; return this.filteredDetections.slice(s, s + this.perPage) }
  },
  watch: { searchQuery() { this.currentPage = 1 } },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    loadData() {
      this.loading = true
      this.unsubscribe = onValue(ref(database, 'Detections'), (snapshot) => {
        const dets = []
        if (snapshot.exists()) {
          for (const [id, det] of Object.entries(snapshot.val())) {
            if (typeof det === 'object' && det) {
              dets.push({ ...det, _id: id, _confidence: det.authenticity?.confidence || det.confidence || null })
            }
          }
        }
        this.allDetections = dets; this.loading = false
      })
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() }
  }
}
</script>

<style scoped>
.detect-page { padding: 0; }
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
.scan-id { background: rgba(79, 134, 247, 0.15); color: var(--accent); padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; }
.confidence-badge { display: inline-flex; padding: 2px 8px; border-radius: 8px; font-size: 0.7rem; font-weight: 600; }
.confidence-badge.high { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.confidence-badge.medium { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.confidence-badge.low { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
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
.preview-body h4 { color: var(--accent); margin: 0 0 12px; }
.raw-json { background: var(--bg); border: 1px solid var(--border); border-radius: 8px; padding: 16px; font-size: 0.8rem; color: var(--text); overflow-x: auto; white-space: pre-wrap; word-break: break-all; max-height: 60vh; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
