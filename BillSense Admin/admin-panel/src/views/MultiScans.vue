<template>
  <div class="scan-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">burst_mode</span> Multi Scans</h1>
        <p class="subtitle">Multi-bill scan results from Firebase "Multi Scan" path</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="exportCsv" :disabled="filteredScans.length === 0">
          <span class="material-icons">download</span> Export CSV
        </button>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">description</span>
        <div class="stat-info"><span class="stat-value">{{ allScans.length }}</span><span class="stat-label">Total Multi Scans</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">verified</span>
        <div class="stat-info"><span class="stat-value">{{ genuineCount }}</span><span class="stat-label">Genuine</span></div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--danger)">dangerous</span>
        <div class="stat-info"><span class="stat-value">{{ counterfeitCount }}</span><span class="stat-label">Counterfeit</span></div>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-group">
        <label>Status</label>
        <select v-model="filterStatus" class="filter-select">
          <option value="all">All Status</option>
          <option value="genuine">Genuine</option>
          <option value="counterfeit">Counterfeit</option>
        </select>
      </div>
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead>
          <tr>
            <th>Date</th><th>User</th><th>Scan ID</th><th>Denomination</th><th>Status</th><th>Confidence</th><th>Image</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="8" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td></tr>
          <tr v-else-if="paginatedScans.length === 0"><td colspan="8" class="empty-state"><span class="material-icons">inbox</span><p>No multi scans found.</p></td></tr>
          <tr v-for="scan in paginatedScans" :key="scan._id" @click="selectedScan = scan" class="report-row">
            <td>{{ formatDate(scan._date) }}</td>
            <td><div class="user-cell"><span class="user-name">{{ scan._userName }}</span><span class="user-email">{{ scan._userEmail }}</span></div></td>
            <td><code class="scan-id">{{ scan._id }}</code></td>
            <td>{{ scan.denomination || '-' }}</td>
            <td><span :class="['status-badge', scan._status === 'GENUINE' ? 'genuine' : 'counterfeit']">{{ scan._status }}</span></td>
            <td><span :class="['confidence-badge', (scan._confidence || '').toLowerCase()]">{{ scan._confidence || '-' }}</span></td>
            <td @click.stop>
              <img v-if="scan._imageUrl" :src="scan._imageUrl" class="scan-thumbnail" @click="previewImage = scan._imageUrl" />
              <span v-else class="no-image">-</span>
            </td>
            <td class="actions-cell" @click.stop><button class="icon-btn" @click="selectedScan = scan"><span class="material-icons">visibility</span></button></td>
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

    <div v-if="selectedScan" class="modal-overlay" @click.self="selectedScan = null">
      <div class="preview-panel">
        <div class="preview-header">
          <h2><span class="material-icons">burst_mode</span> Multi Scan Detail</h2>
          <button class="icon-btn" @click="selectedScan = null"><span class="material-icons">close</span></button>
        </div>
        <div class="preview-body">
          <div class="detail-grid">
            <div class="detail-info">
              <h4>Scan Information</h4>
              <div class="detail-row"><span class="detail-label">Date</span><span class="detail-value">{{ formatDate(selectedScan._date) }}</span></div>
              <div class="detail-row"><span class="detail-label">User</span><span class="detail-value">{{ selectedScan._userName }} ({{ selectedScan._userEmail }})</span></div>
              <div class="detail-row"><span class="detail-label">Denomination</span><span class="detail-value">PHP {{ selectedScan.denomination || '-' }}</span></div>
              <div class="detail-row"><span class="detail-label">Status</span><span class="detail-value">{{ selectedScan._status }}</span></div>
              <div class="detail-row"><span class="detail-label">Confidence</span><span class="detail-value">{{ selectedScan._confidence || '-' }}</span></div>
              <div class="detail-row"><span class="detail-label">Processing Time</span><span class="detail-value">{{ selectedScan.processingTime ? selectedScan.processingTime.toFixed(2) + 's' : '-' }}</span></div>
            </div>
            <div class="detail-image-section" v-if="selectedScan._imageUrl">
              <h4>Annotated Image</h4>
              <img :src="selectedScan._imageUrl" class="detail-image" @click="previewImage = selectedScan._imageUrl" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="previewImage" class="modal-overlay" @click.self="previewImage = null">
      <div class="image-preview-container">
        <button class="icon-btn close-preview" @click="previewImage = null"><span class="material-icons">close</span></button>
        <img :src="previewImage" class="preview-full-image" />
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, get, onValue } from '../services/firebase.js'
export default {
  name: 'MultiScans',
  data() {
    return { allScans: [], usersMap: {}, loading: true, filterStatus: 'all', searchQuery: '', currentPage: 1, perPage: 20, selectedScan: null, previewImage: null, unsubscribe: null }
  },
  computed: {
    genuineCount() { return this.allScans.filter(s => s._status === 'GENUINE').length },
    counterfeitCount() { return this.allScans.filter(s => s._status === 'COUNTERFEIT').length },
    filteredScans() {
      let scans = [...this.allScans]
      if (this.filterStatus !== 'all') scans = scans.filter(s => s._status === this.filterStatus.toUpperCase())
      if (this.searchQuery.trim()) { const q = this.searchQuery.toLowerCase(); scans = scans.filter(s => (s._userName || '').toLowerCase().includes(q) || (s._userEmail || '').toLowerCase().includes(q)) }
      scans.sort((a, b) => new Date(b._date || 0) - new Date(a._date || 0))
      return scans
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredScans.length / this.perPage)) },
    paginatedScans() { const s = (this.currentPage - 1) * this.perPage; return this.filteredScans.slice(s, s + this.perPage) }
  },
  watch: { filterStatus() { this.currentPage = 1 }, searchQuery() { this.currentPage = 1 } },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const usersSnap = await get(ref(database, 'Users'))
        if (usersSnap.exists()) { for (const [uid, user] of Object.entries(usersSnap.val())) { this.usersMap[uid] = { name: user.name || user.fullName || 'Unknown', email: user.email || '' } } }
        this.unsubscribe = onValue(ref(database, 'Multi Scan'), (snapshot) => {
          const scans = []
          if (snapshot.exists()) {
            for (const [userId, userScans] of Object.entries(snapshot.val())) {
              if (typeof userScans === 'object' && userScans) {
                for (const [scanId, scan] of Object.entries(userScans)) {
                  if (typeof scan === 'object' && scan) scans.push(this.normalize(scan, userId, scanId))
                }
              }
            }
          }
          this.allScans = scans; this.loading = false
        })
      } catch (e) { console.error(e); this.loading = false }
    },
    normalize(scan, userId, scanId) {
      const user = this.usersMap[userId] || {}
      const status = scan.authenticity?.status || (scan.authenticity?.genuine === true ? 'GENUINE' : scan.authenticity?.genuine === false ? 'COUNTERFEIT' : (scan.status || 'UNKNOWN').toUpperCase())
      return { ...scan, _id: scanId, _userId: userId, _userName: user.name || 'Unknown', _userEmail: user.email || '', _status: status, _confidence: scan.authenticity?.confidence || scan.confidence || null, _imageUrl: scan.annotatedImageUrl || scan.imageUrl || null, _date: scan.date || scan.timestamp || null }
    },
    formatDate(d) { if (!d) return '-'; const dt = new Date(d); return isNaN(dt) ? '-' : dt.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) },
    refreshData() { if (this.unsubscribe) this.unsubscribe(); this.loadData() },
    exportCsv() {
      const rows = this.filteredScans.map(s => [s._date || '', s._userName, s._userEmail, s._id, s.denomination || '', s._status, s._confidence || ''])
      const csv = [['Date','User','Email','ID','Denomination','Status','Confidence'].join(','), ...rows.map(r => r.map(c => `"${String(c).replace(/"/g,'""')}"`).join(','))].join('\n')
      const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); link.download = `MultiScans_${new Date().toISOString().slice(0,10)}.csv`; link.click()
    }
  }
}
</script>

<style scoped>
.scan-page { padding: 0; }
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
.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-name { font-weight: 600; } .user-email { font-size: 0.75rem; color: var(--text-muted); }
.scan-id { background: rgba(79, 134, 247, 0.15); color: var(--accent); padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; }
.status-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.status-badge.genuine { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.counterfeit { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.confidence-badge { display: inline-flex; padding: 2px 8px; border-radius: 8px; font-size: 0.7rem; font-weight: 600; }
.confidence-badge.high { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.confidence-badge.medium { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.confidence-badge.low { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.scan-thumbnail { width: 48px; height: 36px; object-fit: cover; border-radius: 6px; border: 1px solid var(--border); cursor: pointer; }
.no-image { color: var(--text-muted); }
.icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 6px; border-radius: 6px; transition: all 0.2s; }
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent); }
.actions-cell { display: flex; gap: 4px; }
.empty-state { text-align: center; padding: 48px 16px !important; color: var(--text-muted); }
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 8px; margin-top: 20px; padding: 16px 0; }
.page-info { color: var(--text-muted); font-size: 0.875rem; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(4px); }
.preview-panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; width: 90vw; max-width: 950px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; }
.preview-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid var(--border); }
.preview-header h2 { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; color: var(--text); margin: 0; }
.preview-body { overflow-y: auto; padding: 24px; flex: 1; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.detail-info h4 { color: var(--accent); margin: 0 0 16px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid rgba(42,58,78,0.5); }
.detail-label { color: var(--text-muted); font-size: 0.85rem; }
.detail-value { color: var(--text); font-size: 0.85rem; font-weight: 500; }
.detail-image-section h4 { color: var(--accent); margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.detail-image { width: 100%; border-radius: 10px; border: 1px solid var(--border); cursor: pointer; }
.image-preview-container { position: relative; }
.close-preview { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,0.6) !important; border-radius: 50%; }
.close-preview .material-icons { color: #fff; }
.preview-full-image { max-width: 90vw; max-height: 85vh; border-radius: 12px; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 768px) { .page-header { flex-direction: column; align-items: flex-start; } .filter-bar { flex-direction: column; } .detail-grid { grid-template-columns: 1fr; } }
</style>
