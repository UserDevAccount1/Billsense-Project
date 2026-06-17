<template>
  <div class="scan-results">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">document_scanner</span> Scan Results</h1>
        <p class="subtitle">All scan results from all users across Standard, Multi, and Video scans</p>
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

    <!-- Stats Bar -->
    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">description</span>
        <div class="stat-info">
          <span class="stat-value">{{ allScans.length }}</span>
          <span class="stat-label">Total Scans</span>
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

    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="filter-group">
        <label>Scan Type</label>
        <select v-model="filterType" class="filter-select">
          <option value="all">All Types</option>
          <option value="standard">Standard</option>
          <option value="multi">Multi</option>
          <option value="video">Video</option>
        </select>
      </div>
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
          <input v-model="searchQuery" placeholder="Search by user name or email..." class="filter-input" />
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="reports-table-container">
      <table class="reports-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>User</th>
            <th>Scan Type</th>
            <th>Denomination</th>
            <th>Status</th>
            <th>Features</th>
            <th>Confidence</th>
            <th>Time</th>
            <th>Image</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10" class="empty-state">
              <span class="material-icons spin">sync</span> Loading scan results...
            </td>
          </tr>
          <tr v-else-if="paginatedScans.length === 0">
            <td colspan="10" class="empty-state">
              <span class="material-icons">inbox</span>
              <p>No scan results found.</p>
            </td>
          </tr>
          <tr v-for="scan in paginatedScans" :key="scan._id" @click="openDetail(scan)" class="report-row">
            <td>{{ formatDate(scan._date) }}</td>
            <td>
              <div class="user-cell">
                <span class="user-name">{{ scan._userName || 'Unknown' }}</span>
                <span class="user-email">{{ scan._userEmail || '' }}</span>
              </div>
            </td>
            <td>
              <span :class="['type-badge', scan._scanType]">{{ scan._scanTypeLabel }}</span>
            </td>
            <td>{{ scan.denomination || scan._denomination || '-' }}</td>
            <td>
              <span :class="['status-badge', scan._status === 'GENUINE' ? 'genuine' : 'counterfeit']">
                {{ scan._status || '-' }}
              </span>
            </td>
            <td>{{ scan.featureSummary || scan._featureSummary || '-' }}</td>
            <td>
              <span :class="['confidence-badge', (scan._confidence || '').toLowerCase()]">
                {{ scan._confidence || '-' }}
              </span>
            </td>
            <td>{{ scan.processingTime ? scan.processingTime.toFixed(2) + 's' : '-' }}</td>
            <td @click.stop>
              <img
                v-if="scan._imageUrl"
                :src="scan._imageUrl"
                class="scan-thumbnail"
                @click="openImagePreview(scan._imageUrl)"
                alt="Scan"
              />
              <span v-else class="no-image">-</span>
            </td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="openDetail(scan)" title="View Details">
                <span class="material-icons">visibility</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="totalPages > 1">
      <button class="btn btn-sm btn-ghost" @click="currentPage = 1" :disabled="currentPage === 1">
        <span class="material-icons">first_page</span>
      </button>
      <button class="btn btn-sm btn-ghost" @click="currentPage--" :disabled="currentPage === 1">
        <span class="material-icons">chevron_left</span>
      </button>
      <span class="page-info">Page {{ currentPage }} of {{ totalPages }}</span>
      <button class="btn btn-sm btn-ghost" @click="currentPage++" :disabled="currentPage === totalPages">
        <span class="material-icons">chevron_right</span>
      </button>
      <button class="btn btn-sm btn-ghost" @click="currentPage = totalPages" :disabled="currentPage === totalPages">
        <span class="material-icons">last_page</span>
      </button>
    </div>

    <!-- Detail Modal -->
    <div v-if="selectedScan" class="modal-overlay" @click.self="selectedScan = null">
      <div class="preview-panel">
        <div class="preview-header">
          <h2>
            <span class="material-icons">document_scanner</span>
            Scan Detail
          </h2>
          <div class="preview-actions">
            <button class="icon-btn" @click="selectedScan = null">
              <span class="material-icons">close</span>
            </button>
          </div>
        </div>
        <div class="preview-body">
          <div class="detail-grid">
            <!-- Left: info -->
            <div class="detail-info">
              <h4>Scan Information</h4>
              <div class="detail-row">
                <span class="detail-label">Date</span>
                <span class="detail-value">{{ formatDate(selectedScan._date) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">User</span>
                <span class="detail-value">{{ selectedScan._userName || 'Unknown' }} ({{ selectedScan._userEmail || '-' }})</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Scan Type</span>
                <span class="detail-value">
                  <span :class="['type-badge', selectedScan._scanType]">{{ selectedScan._scanTypeLabel }}</span>
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Denomination</span>
                <span class="detail-value">PHP {{ selectedScan.denomination || selectedScan._denomination || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Status</span>
                <span class="detail-value">
                  <span :class="['status-badge', selectedScan._status === 'GENUINE' ? 'genuine' : 'counterfeit']">
                    {{ selectedScan._status }}
                  </span>
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Confidence</span>
                <span class="detail-value">
                  <span :class="['confidence-badge', (selectedScan._confidence || '').toLowerCase()]">
                    {{ selectedScan._confidence || '-' }}
                  </span>
                </span>
              </div>
              <div class="detail-row" v-if="selectedScan.authenticity && selectedScan.authenticity.coveragePercentage !== undefined">
                <span class="detail-label">Coverage</span>
                <span class="detail-value">{{ selectedScan.authenticity.coveragePercentage.toFixed(1) }}%</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Features</span>
                <span class="detail-value">{{ selectedScan.featureSummary || selectedScan._featureSummary || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">Processing Time</span>
                <span class="detail-value">{{ selectedScan.processingTime ? selectedScan.processingTime.toFixed(2) + 's' : '-' }}</span>
              </div>
              <div class="detail-row" v-if="selectedScan.firebaseStatus">
                <span class="detail-label">Firebase Status</span>
                <span class="detail-value">{{ selectedScan.firebaseStatus }}</span>
              </div>

              <!-- Detected Features List -->
              <div v-if="selectedScan.detectedFeatures && selectedScan.detectedFeatures.length > 0" class="detail-section">
                <h4>Detected Features</h4>
                <div class="feature-list">
                  <div v-for="(feat, i) in selectedScan.detectedFeatures" :key="i" class="feature-item">
                    <span class="material-icons" style="font-size:16px;color:var(--success)">check_circle</span>
                    <span>{{ feat.name || feat.className || feat }}</span>
                    <span v-if="feat.confidence" class="feature-conf">{{ (feat.confidence * 100).toFixed(1) }}%</span>
                  </div>
                </div>
              </div>

              <!-- Quality Metrics -->
              <div v-if="selectedScan.qualityMetrics" class="detail-section">
                <h4>Quality Metrics</h4>
                <div v-for="(val, key) in selectedScan.qualityMetrics" :key="key" class="detail-row">
                  <span class="detail-label">{{ key }}</span>
                  <span class="detail-value">{{ typeof val === 'number' ? val.toFixed(2) : val }}</span>
                </div>
              </div>
            </div>

            <!-- Right: image -->
            <div class="detail-image-section" v-if="selectedScan._imageUrl">
              <h4>Annotated Image</h4>
              <img :src="selectedScan._imageUrl" class="detail-image" alt="Annotated scan" @click="openImagePreview(selectedScan._imageUrl)" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Image Preview Modal -->
    <div v-if="previewImage" class="modal-overlay" @click.self="previewImage = null">
      <div class="image-preview-container">
        <button class="icon-btn close-preview" @click="previewImage = null">
          <span class="material-icons">close</span>
        </button>
        <img :src="previewImage" class="preview-full-image" alt="Full preview" />
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, get } from '../services/firebase.js'
import { onValue } from 'firebase/database'

export default {
  name: 'ScanResults',
  data() {
    return {
      allScans: [],
      usersMap: {},
      loading: true,
      filterType: 'all',
      filterStatus: 'all',
      searchQuery: '',
      currentPage: 1,
      perPage: 20,
      selectedScan: null,
      previewImage: null,
      listeners: []
    }
  },
  computed: {
    genuineCount() {
      return this.allScans.filter(s => s._status === 'GENUINE').length
    },
    counterfeitCount() {
      return this.allScans.filter(s => s._status === 'COUNTERFEIT').length
    },
    avgProcessingTime() {
      const withTime = this.allScans.filter(s => s.processingTime)
      if (withTime.length === 0) return '0.00'
      const avg = withTime.reduce((sum, s) => sum + s.processingTime, 0) / withTime.length
      return avg.toFixed(2)
    },
    filteredScans() {
      let scans = [...this.allScans]

      if (this.filterType !== 'all') {
        scans = scans.filter(s => s._scanType === this.filterType)
      }

      if (this.filterStatus !== 'all') {
        const target = this.filterStatus.toUpperCase()
        scans = scans.filter(s => s._status === target)
      }

      if (this.searchQuery.trim()) {
        const q = this.searchQuery.toLowerCase()
        scans = scans.filter(s =>
          (s._userName || '').toLowerCase().includes(q) ||
          (s._userEmail || '').toLowerCase().includes(q)
        )
      }

      // Sort by date descending
      scans.sort((a, b) => new Date(b._date || 0) - new Date(a._date || 0))
      return scans
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.filteredScans.length / this.perPage))
    },
    paginatedScans() {
      const start = (this.currentPage - 1) * this.perPage
      return this.filteredScans.slice(start, start + this.perPage)
    }
  },
  watch: {
    filterType() { this.currentPage = 1 },
    filterStatus() { this.currentPage = 1 },
    searchQuery() { this.currentPage = 1 }
  },
  mounted() {
    this.loadData()
  },
  beforeUnmount() {
    this.listeners.forEach(unsub => {
      if (typeof unsub === 'function') unsub()
    })
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        // Load users first
        const usersSnap = await get(ref(database, 'Users'))
        if (usersSnap.exists()) {
          const usersData = usersSnap.val()
          for (const [uid, user] of Object.entries(usersData)) {
            this.usersMap[uid] = {
              name: user.name || user.fullName || 'Unknown',
              email: user.email || ''
            }
            // Also map by the internal id field if present
            if (user.id) {
              this.usersMap[user.id] = this.usersMap[uid]
            }
          }
        }

        // Load all scan types in parallel
        const [standardSnap, multiSnap, videoSnap, detectionsSnap] = await Promise.all([
          get(ref(database, 'Standard Scan')),
          get(ref(database, 'Multi Scan')),
          get(ref(database, 'Video Scan')),
          get(ref(database, 'Detections'))
        ])

        const scans = []

        // Parse Standard Scans
        if (standardSnap.exists()) {
          const data = standardSnap.val()
          for (const [userId, userScans] of Object.entries(data)) {
            if (typeof userScans === 'object' && userScans !== null) {
              for (const [scanId, scan] of Object.entries(userScans)) {
                if (typeof scan === 'object' && scan !== null) {
                  scans.push(this.normalizeScan(scan, userId, scanId, 'standard'))
                }
              }
            }
          }
        }

        // Parse Multi Scans
        if (multiSnap.exists()) {
          const data = multiSnap.val()
          for (const [userId, userScans] of Object.entries(data)) {
            if (typeof userScans === 'object' && userScans !== null) {
              for (const [scanId, scan] of Object.entries(userScans)) {
                if (typeof scan === 'object' && scan !== null) {
                  scans.push(this.normalizeScan(scan, userId, scanId, 'multi'))
                }
              }
            }
          }
        }

        // Parse Video Scans
        if (videoSnap.exists()) {
          const data = videoSnap.val()
          for (const [userId, userScans] of Object.entries(data)) {
            if (typeof userScans === 'object' && userScans !== null) {
              for (const [scanId, scan] of Object.entries(userScans)) {
                if (typeof scan === 'object' && scan !== null) {
                  scans.push(this.normalizeScan(scan, userId, scanId, 'video'))
                }
              }
            }
          }
        }

        // Parse Detections
        if (detectionsSnap.exists()) {
          const data = detectionsSnap.val()
          for (const [detId, det] of Object.entries(data)) {
            if (typeof det === 'object' && det !== null) {
              scans.push(this.normalizeScan(det, det.userId || 'unknown', detId, 'standard'))
            }
          }
        }

        this.allScans = scans
      } catch (err) {
        console.error('Failed to load scan data:', err)
      } finally {
        this.loading = false
      }
    },
    normalizeScan(scan, userId, scanId, type) {
      const user = this.usersMap[userId] || this.usersMap[scan.userId] || {}
      const status = this.extractStatus(scan)
      const confidence = this.extractConfidence(scan)
      const imageUrl = scan.annotatedImageUrl || scan.imageUrl || scan.image_url || null
      const date = scan.date || scan.timestamp || scan.createdAt || null
      const denomination = scan.denomination || scan.bill_denomination || null
      const featureSummary = scan.featureSummary || (scan.detectedFeaturesCount !== undefined ? `${scan.detectedFeaturesCount} detected` : null)

      return {
        ...scan,
        _id: scanId,
        _userId: userId,
        _userName: user.name || 'Unknown',
        _userEmail: user.email || '',
        _scanType: type,
        _scanTypeLabel: type === 'standard' ? 'Standard' : type === 'multi' ? 'Multi' : 'Video',
        _status: status,
        _confidence: confidence,
        _imageUrl: imageUrl,
        _date: date,
        _denomination: denomination,
        _featureSummary: featureSummary
      }
    },
    extractStatus(scan) {
      if (scan.authenticity && scan.authenticity.status) return scan.authenticity.status
      if (scan.authenticity && typeof scan.authenticity.genuine === 'boolean') return scan.authenticity.genuine ? 'GENUINE' : 'COUNTERFEIT'
      if (scan.status) return scan.status.toUpperCase()
      if (scan.result) return scan.result.toUpperCase()
      return 'UNKNOWN'
    },
    extractConfidence(scan) {
      if (scan.authenticity && scan.authenticity.confidence) return scan.authenticity.confidence
      if (scan.confidence) return scan.confidence
      return null
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      const d = new Date(dateStr)
      if (isNaN(d.getTime())) return '-'
      return d.toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
      })
    },
    openDetail(scan) {
      this.selectedScan = scan
    },
    openImagePreview(url) {
      this.previewImage = url
    },
    async refreshData() {
      await this.loadData()
    },
    exportCsv() {
      const headers = ['Date', 'User Name', 'User Email', 'Scan Type', 'Denomination', 'Status', 'Features', 'Confidence', 'Processing Time (s)', 'Image URL']
      const rows = this.filteredScans.map(s => [
        s._date || '',
        s._userName || '',
        s._userEmail || '',
        s._scanTypeLabel || '',
        s.denomination || s._denomination || '',
        s._status || '',
        s.featureSummary || s._featureSummary || '',
        s._confidence || '',
        s.processingTime || '',
        s._imageUrl || ''
      ])

      const csvContent = [
        headers.join(','),
        ...rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
      ].join('\n')

      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `BillSense_ScanResults_${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
      URL.revokeObjectURL(url)
    }
  }
}
</script>

<style scoped>
.scan-results { padding: 0; }

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px; flex-wrap: wrap; gap: 16px;
}
.header-left h1 {
  display: flex; align-items: center; gap: 10px;
  font-size: 1.5rem; color: var(--text, #E2E8F0); margin: 0;
}
.header-icon { color: var(--accent, #4F86F7); font-size: 28px; }
.subtitle { color: var(--text-muted, #94A3B8); margin: 4px 0 0; font-size: 0.875rem; }

.header-actions { display: flex; gap: 8px; }

.btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px;
  border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem;
  font-weight: 600; transition: all 0.2s;
}
.btn-primary { background: var(--accent, #4F86F7); color: #fff; }
.btn-primary:hover { background: #3A6FD8; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost { background: transparent; color: var(--text-muted, #94A3B8); border: 1px solid var(--border, #2A3A4E); }
.btn-ghost:hover { border-color: var(--accent, #4F86F7); color: var(--text, #E2E8F0); }
.btn-sm { padding: 6px 12px; font-size: 0.8rem; }

.stats-bar {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px; margin-bottom: 24px;
}
.stat-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 12px; padding: 16px 20px;
}
.stat-icon { font-size: 32px; color: var(--accent, #4F86F7); }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--text, #E2E8F0); display: block; }
.stat-label { font-size: 0.75rem; color: var(--text-muted, #94A3B8); }

/* Filter Bar */
.filter-bar {
  display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; align-items: flex-end;
}
.filter-group { display: flex; flex-direction: column; gap: 4px; }
.filter-group label { font-size: 0.75rem; color: var(--text-muted, #94A3B8); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.filter-select {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 8px; padding: 10px 14px; color: var(--text, #E2E8F0);
  font-size: 0.875rem; min-width: 150px; cursor: pointer;
}
.filter-select:focus { border-color: var(--accent, #4F86F7); outline: none; }
.search-group { flex: 1; min-width: 250px; }
.search-input-wrapper { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted, #94A3B8); font-size: 20px; }
.filter-input {
  width: 100%; background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 8px; padding: 10px 14px 10px 40px; color: var(--text, #E2E8F0);
  font-size: 0.875rem;
}
.filter-input:focus { border-color: var(--accent, #4F86F7); outline: none; }

/* Table */
.reports-table-container {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 12px; overflow: hidden;
}
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th {
  padding: 12px 16px; text-align: left; font-size: 0.75rem;
  font-weight: 600; color: var(--text-muted, #94A3B8); text-transform: uppercase;
  letter-spacing: 0.5px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.reports-table td {
  padding: 12px 16px; font-size: 0.875rem; color: var(--text, #E2E8F0);
  border-bottom: 1px solid var(--border, #2A3A4E);
}
.report-row { cursor: pointer; transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }

.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-name { font-weight: 600; font-size: 0.875rem; }
.user-email { font-size: 0.75rem; color: var(--text-muted, #94A3B8); }

.type-badge {
  display: inline-flex; padding: 4px 10px; border-radius: 12px;
  font-size: 0.75rem; font-weight: 600;
}
.type-badge.standard { background: rgba(79, 134, 247, 0.15); color: #4F86F7; }
.type-badge.multi { background: rgba(168, 85, 247, 0.15); color: #A855F7; }
.type-badge.video { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }

.status-badge {
  display: inline-flex; padding: 4px 10px; border-radius: 12px;
  font-size: 0.75rem; font-weight: 600;
}
.status-badge.genuine { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.counterfeit { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.confidence-badge {
  display: inline-flex; padding: 2px 8px; border-radius: 8px;
  font-size: 0.7rem; font-weight: 600;
}
.confidence-badge.high { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.confidence-badge.medium { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.confidence-badge.low { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.scan-thumbnail {
  width: 48px; height: 36px; object-fit: cover; border-radius: 6px;
  border: 1px solid var(--border, #2A3A4E); cursor: pointer;
  transition: transform 0.2s;
}
.scan-thumbnail:hover { transform: scale(1.1); }
.no-image { color: var(--text-muted, #94A3B8); font-size: 0.8rem; }

.icon-btn {
  background: none; border: none; color: var(--text-muted, #94A3B8);
  cursor: pointer; padding: 6px; border-radius: 6px; transition: all 0.2s;
}
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent, #4F86F7); }
.icon-btn .material-icons { font-size: 20px; }

.actions-cell { display: flex; gap: 4px; }

.empty-state {
  text-align: center; padding: 48px 16px !important; color: var(--text-muted, #94A3B8);
}
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }

/* Pagination */
.pagination {
  display: flex; justify-content: center; align-items: center; gap: 8px;
  margin-top: 20px; padding: 16px 0;
}
.page-info { color: var(--text-muted, #94A3B8); font-size: 0.875rem; }

/* Detail Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000; backdrop-filter: blur(4px);
}
.preview-panel {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 16px; width: 90vw; max-width: 950px; max-height: 85vh;
  display: flex; flex-direction: column; overflow: hidden;
}
.preview-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.preview-header h2 {
  display: flex; align-items: center; gap: 8px;
  font-size: 1.1rem; color: var(--text, #E2E8F0); margin: 0;
}
.preview-actions { display: flex; gap: 8px; align-items: center; }
.preview-body { overflow-y: auto; padding: 24px; flex: 1; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.detail-info h4 {
  color: var(--accent, #4F86F7); margin: 0 0 16px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.detail-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-bottom: 1px solid rgba(42, 58, 78, 0.5);
}
.detail-label { color: var(--text-muted, #94A3B8); font-size: 0.85rem; }
.detail-value { color: var(--text, #E2E8F0); font-size: 0.85rem; font-weight: 500; }

.detail-section { margin-top: 20px; }
.detail-section h4 {
  color: var(--accent, #4F86F7); margin: 0 0 12px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border, #2A3A4E);
}

.feature-list { display: flex; flex-direction: column; gap: 6px; }
.feature-item {
  display: flex; align-items: center; gap: 8px; padding: 6px 10px;
  background: rgba(34, 197, 94, 0.05); border-radius: 6px; font-size: 0.85rem;
  color: var(--text, #E2E8F0);
}
.feature-conf { margin-left: auto; color: var(--text-muted, #94A3B8); font-size: 0.75rem; }

.detail-image-section h4 {
  color: var(--accent, #4F86F7); margin: 0 0 12px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.detail-image {
  width: 100%; border-radius: 10px; border: 1px solid var(--border, #2A3A4E);
  cursor: pointer;
}

/* Image Preview */
.image-preview-container {
  position: relative; max-width: 90vw; max-height: 90vh;
}
.close-preview {
  position: absolute; top: 8px; right: 8px; z-index: 10;
  background: rgba(0,0,0,0.6) !important; border-radius: 50%;
}
.close-preview .material-icons { color: #fff; }
.preview-full-image {
  max-width: 90vw; max-height: 85vh; border-radius: 12px;
  border: 1px solid var(--border, #2A3A4E);
}

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }

@media (max-width: 768px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .stats-bar { grid-template-columns: 1fr 1fr; }
  .filter-bar { flex-direction: column; }
  .reports-table { font-size: 0.8rem; }
  .reports-table th:nth-child(n+6), .reports-table td:nth-child(n+6) { display: none; }
  .preview-panel { width: 95vw; max-height: 90vh; }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
