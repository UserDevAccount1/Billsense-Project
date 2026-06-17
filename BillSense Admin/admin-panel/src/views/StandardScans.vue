<template>
  <div class="scan-page">
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">center_focus_strong</span> Standard Scans</h1>
        <p class="subtitle">All standard single-image scan results from Firebase "Standard Scan" path</p>
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
        <div class="stat-info">
          <span class="stat-value">{{ allScans.length }}</span>
          <span class="stat-label">Total Standard Scans</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">verified</span>
        <div class="stat-info">
          <span class="stat-value">{{ genuineCount }}</span>
          <span class="stat-label">Genuine</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--danger)">dangerous</span>
        <div class="stat-info">
          <span class="stat-value">{{ counterfeitCount }}</span>
          <span class="stat-label">Counterfeit</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--warning)">timer</span>
        <div class="stat-info">
          <span class="stat-value">{{ avgProcessingTime }}s</span>
          <span class="stat-label">Avg Processing</span>
        </div>
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
      <div class="filter-group">
        <label>User</label>
        <select v-model="filterUser" class="filter-select">
          <option value="all">All Users</option>
          <option v-for="u in uniqueUsers" :key="u.id" :value="u.id">{{ u.name }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>From Date</label>
        <input type="date" v-model="filterDateFrom" class="filter-input" />
      </div>
      <div class="filter-group">
        <label>To Date</label>
        <input type="date" v-model="filterDateTo" class="filter-input" />
      </div>
      <div class="filter-group search-group">
        <label>Search</label>
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search by user, denomination..." class="filter-input search-field" />
        </div>
      </div>
    </div>

    <div class="reports-table-container">
      <table class="reports-table">
        <thead>
          <tr>
            <th @click="sortBy('_date')" class="sortable">Date <span class="material-icons sort-icon">unfold_more</span></th>
            <th @click="sortBy('_userName')" class="sortable">User <span class="material-icons sort-icon">unfold_more</span></th>
            <th>Scan ID</th>
            <th>Denomination</th>
            <th @click="sortBy('_status')" class="sortable">Status <span class="material-icons sort-icon">unfold_more</span></th>
            <th>Confidence</th>
            <th>Image</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="empty-state"><span class="material-icons spin">sync</span> Loading...</td>
          </tr>
          <tr v-else-if="paginatedScans.length === 0">
            <td colspan="8" class="empty-state"><span class="material-icons">inbox</span><p>No standard scans found.</p></td>
          </tr>
          <tr v-for="scan in paginatedScans" :key="scan._id" @click="openDetail(scan)" class="report-row">
            <td>{{ formatDate(scan._date) }}</td>
            <td>
              <div class="user-cell">
                <span class="user-name">{{ scan._userName || 'Unknown' }}</span>
                <span class="user-email">{{ scan._userEmail || '' }}</span>
              </div>
            </td>
            <td><code class="scan-id">{{ scan._id }}</code></td>
            <td>{{ scan.denomination || scan._denomination || '-' }}</td>
            <td>
              <span :class="['status-badge', scan._status === 'GENUINE' ? 'genuine' : 'counterfeit']">{{ scan._status || '-' }}</span>
            </td>
            <td>
              <span :class="['confidence-badge', (scan._confidence || '').toLowerCase()]">{{ scan._confidence || '-' }}</span>
            </td>
            <td @click.stop>
              <img v-if="scan._imageUrl" :src="scan._imageUrl" class="scan-thumbnail" @click="previewImage = scan._imageUrl" alt="Scan" />
              <span v-else class="no-image">-</span>
            </td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="openDetail(scan)" title="View Details"><span class="material-icons">visibility</span></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button class="btn btn-sm btn-ghost" @click="currentPage = 1" :disabled="currentPage === 1"><span class="material-icons">first_page</span></button>
      <button class="btn btn-sm btn-ghost" @click="currentPage--" :disabled="currentPage === 1"><span class="material-icons">chevron_left</span></button>
      <span class="page-info">Page {{ currentPage }} of {{ totalPages }} ({{ filteredScans.length }} results)</span>
      <button class="btn btn-sm btn-ghost" @click="currentPage++" :disabled="currentPage === totalPages"><span class="material-icons">chevron_right</span></button>
      <button class="btn btn-sm btn-ghost" @click="currentPage = totalPages" :disabled="currentPage === totalPages"><span class="material-icons">last_page</span></button>
    </div>

    <!-- Detail Modal -->
    <div v-if="selectedScan" class="modal-overlay" @click.self="selectedScan = null">
      <div class="preview-panel">
        <div class="preview-header">
          <h2><span class="material-icons">document_scanner</span> Standard Scan Detail</h2>
          <button class="icon-btn" @click="selectedScan = null"><span class="material-icons">close</span></button>
        </div>
        <div class="preview-body">
          <div class="detail-grid">
            <div class="detail-info">
              <h4>Scan Information</h4>
              <div class="detail-row" v-for="(val, label) in scanDetailRows" :key="label">
                <span class="detail-label">{{ label }}</span>
                <span class="detail-value" v-html="val"></span>
              </div>
              <div v-if="selectedScan.detectedFeatures && selectedScan.detectedFeatures.length" class="detail-section">
                <h4>Detected Features</h4>
                <div class="feature-list">
                  <div v-for="(feat, i) in selectedScan.detectedFeatures" :key="i" class="feature-item">
                    <span class="material-icons" style="font-size:16px;color:var(--success)">check_circle</span>
                    <span>{{ feat.name || feat.className || feat }}</span>
                    <span v-if="feat.confidence" class="feature-conf">{{ (feat.confidence * 100).toFixed(1) }}%</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="detail-image-section" v-if="selectedScan._imageUrl">
              <h4>Annotated Image</h4>
              <img :src="selectedScan._imageUrl" class="detail-image" @click="previewImage = selectedScan._imageUrl" alt="Scan" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Image Preview -->
    <div v-if="previewImage" class="modal-overlay" @click.self="previewImage = null">
      <div class="image-preview-container">
        <button class="icon-btn close-preview" @click="previewImage = null"><span class="material-icons">close</span></button>
        <img :src="previewImage" class="preview-full-image" alt="Preview" />
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, get, onValue } from '../services/firebase.js'

export default {
  name: 'StandardScans',
  data() {
    return {
      allScans: [],
      usersMap: {},
      loading: true,
      filterStatus: 'all',
      filterUser: 'all',
      filterDateFrom: '',
      filterDateTo: '',
      searchQuery: '',
      currentPage: 1,
      perPage: 20,
      selectedScan: null,
      previewImage: null,
      sortField: '_date',
      sortAsc: false,
      unsubscribe: null
    }
  },
  computed: {
    genuineCount() { return this.allScans.filter(s => s._status === 'GENUINE').length },
    counterfeitCount() { return this.allScans.filter(s => s._status === 'COUNTERFEIT').length },
    avgProcessingTime() {
      const withTime = this.allScans.filter(s => s.processingTime)
      if (!withTime.length) return '0.00'
      return (withTime.reduce((sum, s) => sum + s.processingTime, 0) / withTime.length).toFixed(2)
    },
    uniqueUsers() {
      const map = {}
      this.allScans.forEach(s => { if (s._userId && s._userName) map[s._userId] = { id: s._userId, name: s._userName } })
      return Object.values(map).sort((a, b) => a.name.localeCompare(b.name))
    },
    filteredScans() {
      let scans = [...this.allScans]
      if (this.filterStatus !== 'all') scans = scans.filter(s => s._status === this.filterStatus.toUpperCase())
      if (this.filterUser !== 'all') scans = scans.filter(s => s._userId === this.filterUser)
      if (this.filterDateFrom) scans = scans.filter(s => s._date && new Date(s._date) >= new Date(this.filterDateFrom))
      if (this.filterDateTo) scans = scans.filter(s => s._date && new Date(s._date) <= new Date(this.filterDateTo + 'T23:59:59'))
      if (this.searchQuery.trim()) {
        const q = this.searchQuery.toLowerCase()
        scans = scans.filter(s => (s._userName || '').toLowerCase().includes(q) || (s._userEmail || '').toLowerCase().includes(q) || (s.denomination || '').toString().includes(q))
      }
      scans.sort((a, b) => {
        const aVal = a[this.sortField] || ''
        const bVal = b[this.sortField] || ''
        const cmp = typeof aVal === 'string' ? aVal.localeCompare(bVal) : (aVal > bVal ? 1 : -1)
        return this.sortAsc ? cmp : -cmp
      })
      return scans
    },
    totalPages() { return Math.max(1, Math.ceil(this.filteredScans.length / this.perPage)) },
    paginatedScans() {
      const start = (this.currentPage - 1) * this.perPage
      return this.filteredScans.slice(start, start + this.perPage)
    },
    scanDetailRows() {
      const s = this.selectedScan
      if (!s) return {}
      return {
        'Date': this.formatDate(s._date),
        'User': `${s._userName || 'Unknown'} (${s._userEmail || '-'})`,
        'Scan ID': `<code>${s._id}</code>`,
        'Denomination': `PHP ${s.denomination || s._denomination || '-'}`,
        'Status': s._status || '-',
        'Confidence': s._confidence || '-',
        'Processing Time': s.processingTime ? s.processingTime.toFixed(2) + 's' : '-',
        'Features': s.featureSummary || s._featureSummary || '-'
      }
    }
  },
  watch: {
    filterStatus() { this.currentPage = 1 },
    filterUser() { this.currentPage = 1 },
    filterDateFrom() { this.currentPage = 1 },
    filterDateTo() { this.currentPage = 1 },
    searchQuery() { this.currentPage = 1 }
  },
  mounted() { this.loadData() },
  beforeUnmount() { if (this.unsubscribe) this.unsubscribe() },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const usersSnap = await get(ref(database, 'Users'))
        if (usersSnap.exists()) {
          const data = usersSnap.val()
          for (const [uid, user] of Object.entries(data)) {
            this.usersMap[uid] = { name: user.name || user.fullName || 'Unknown', email: user.email || '' }
          }
        }
        this.unsubscribe = onValue(ref(database, 'Standard Scan'), (snapshot) => {
          const scans = []
          if (snapshot.exists()) {
            const data = snapshot.val()
            for (const [userId, userScans] of Object.entries(data)) {
              if (typeof userScans === 'object' && userScans !== null) {
                for (const [scanId, scan] of Object.entries(userScans)) {
                  if (typeof scan === 'object' && scan !== null) scans.push(this.normalizeScan(scan, userId, scanId))
                }
              }
            }
          }
          this.allScans = scans
          this.loading = false
        })
      } catch (err) {
        console.error('Failed to load standard scans:', err)
        this.loading = false
      }
    },
    normalizeScan(scan, userId, scanId) {
      const user = this.usersMap[userId] || {}
      const status = scan.authenticity?.status || (scan.authenticity?.genuine === true ? 'GENUINE' : scan.authenticity?.genuine === false ? 'COUNTERFEIT' : (scan.status || scan.result || 'UNKNOWN').toUpperCase())
      const confidence = scan.authenticity?.confidence || scan.confidence || null
      return {
        ...scan, _id: scanId, _userId: userId,
        _userName: user.name || 'Unknown', _userEmail: user.email || '',
        _status: status, _confidence: confidence,
        _imageUrl: scan.annotatedImageUrl || scan.imageUrl || scan.image_url || null,
        _date: scan.date || scan.timestamp || scan.createdAt || null,
        _denomination: scan.denomination || scan.bill_denomination || null,
        _featureSummary: scan.featureSummary || (scan.detectedFeaturesCount !== undefined ? `${scan.detectedFeaturesCount} detected` : null)
      }
    },
    sortBy(field) {
      if (this.sortField === field) this.sortAsc = !this.sortAsc
      else { this.sortField = field; this.sortAsc = false }
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      const d = new Date(dateStr)
      return isNaN(d.getTime()) ? '-' : d.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    },
    openDetail(scan) { this.selectedScan = scan },
    async refreshData() {
      if (this.unsubscribe) this.unsubscribe()
      this.loadData()
    },
    exportCsv() {
      const headers = ['Date', 'User', 'Email', 'Scan ID', 'Denomination', 'Status', 'Confidence', 'Processing Time']
      const rows = this.filteredScans.map(s => [s._date || '', s._userName, s._userEmail, s._id, s.denomination || '', s._status, s._confidence || '', s.processingTime || ''])
      const csv = [headers.join(','), ...rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(','))].join('\n')
      const blob = new Blob([csv], { type: 'text/csv' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `StandardScans_${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
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
.btn-primary { background: var(--accent); color: #fff; }
.btn-primary:hover { filter: brightness(1.1); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
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
.filter-group label { font-size: 0.75rem; color: var(--text-muted); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.filter-select, .filter-input { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 0.875rem; min-width: 140px; }
.filter-select:focus, .filter-input:focus { border-color: var(--accent); outline: none; }
.search-group { flex: 1; min-width: 220px; }
.search-input-wrapper { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 20px; }
.search-field { padding-left: 40px !important; width: 100%; }
.reports-table-container { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; overflow-x: auto; }
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th { padding: 12px 16px; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid var(--border); white-space: nowrap; }
.reports-table td { padding: 12px 16px; font-size: 0.875rem; color: var(--text); border-bottom: 1px solid var(--border); }
.sortable { cursor: pointer; user-select: none; }
.sort-icon { font-size: 14px !important; vertical-align: middle; }
.report-row { cursor: pointer; transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }
.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-name { font-weight: 600; font-size: 0.875rem; }
.user-email { font-size: 0.75rem; color: var(--text-muted); }
.scan-id { background: rgba(79, 134, 247, 0.15); color: var(--accent); padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; }
.status-badge { display: inline-flex; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.status-badge.genuine { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.counterfeit { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.confidence-badge { display: inline-flex; padding: 2px 8px; border-radius: 8px; font-size: 0.7rem; font-weight: 600; }
.confidence-badge.high { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.confidence-badge.medium { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.confidence-badge.low { background: rgba(239, 68, 68, 0.15); color: #EF4444; }
.scan-thumbnail { width: 48px; height: 36px; object-fit: cover; border-radius: 6px; border: 1px solid var(--border); cursor: pointer; }
.no-image { color: var(--text-muted); font-size: 0.8rem; }
.icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 6px; border-radius: 6px; transition: all 0.2s; }
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent); }
.icon-btn .material-icons { font-size: 20px; }
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
.detail-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid rgba(42, 58, 78, 0.5); }
.detail-label { color: var(--text-muted); font-size: 0.85rem; }
.detail-value { color: var(--text); font-size: 0.85rem; font-weight: 500; }
.detail-section { margin-top: 20px; }
.detail-section h4 { color: var(--accent); margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.feature-list { display: flex; flex-direction: column; gap: 6px; }
.feature-item { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: rgba(34, 197, 94, 0.05); border-radius: 6px; font-size: 0.85rem; }
.feature-conf { margin-left: auto; color: var(--text-muted); font-size: 0.75rem; }
.detail-image-section h4 { color: var(--accent); margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.detail-image { width: 100%; border-radius: 10px; border: 1px solid var(--border); cursor: pointer; }
.image-preview-container { position: relative; max-width: 90vw; max-height: 90vh; }
.close-preview { position: absolute; top: 8px; right: 8px; z-index: 10; background: rgba(0,0,0,0.6) !important; border-radius: 50%; }
.close-preview .material-icons { color: #fff; }
.preview-full-image { max-width: 90vw; max-height: 85vh; border-radius: 12px; border: 1px solid var(--border); }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 768px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .filter-bar { flex-direction: column; }
  .detail-grid { grid-template-columns: 1fr; }
  .preview-panel { width: 95vw; }
}
</style>
