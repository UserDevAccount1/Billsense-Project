<template>
  <div>
    <div class="page-header">
      <h1>Dashboard</h1>
      <p>BillSense admin overview</p>
    </div>

    <div class="dashboard-content">
      <!-- Model Connection Summary -->
      <div class="section-header">
        <h2>Model Pipeline Status</h2>
        <router-link to="/connection-health" class="view-link">
          View Connection Health
          <span class="material-icons">arrow_forward</span>
        </router-link>
      </div>

      <div class="conn-summary-grid">
        <router-link
          v-for="conn in connections"
          :key="conn.id"
          to="/connection-health"
          class="conn-summary-card"
          :class="'status-' + conn.overall"
        >
          <div class="card-icon" :class="conn.id">
            <span class="material-icons">{{ conn.icon }}</span>
          </div>
          <div class="conn-summary-info">
            <div class="card-title">{{ conn.name }}</div>
            <span class="status-badge" :class="conn.overall">
              <span class="status-dot"></span>
              {{ conn.overall }}
            </span>
          </div>
        </router-link>
      </div>

      <!-- Quick Stats (live from RTDB) -->
      <div class="section-header" style="margin-top: 32px;">
        <h2>Quick Stats</h2>
        <button class="view-link refresh-stats" @click="loadStats" :disabled="statsLoading">
          <span class="material-icons" :class="{ spin: statsLoading }">refresh</span> Refresh
        </button>
      </div>

      <div class="stats-grid">
        <div class="stat-card">
          <span class="material-icons stat-icon">qr_code_scanner</span>
          <div class="stat-value">{{ statsLoading ? '…' : fmt(stats.totalScans) }}</div>
          <div class="stat-label">Total Scans</div>
          <div class="stat-sub">{{ stats.byType }}</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">people</span>
          <div class="stat-value">{{ statsLoading ? '…' : fmt(stats.users) }}</div>
          <div class="stat-label">Registered Users</div>
          <div class="stat-sub">{{ stats.scanningUsers != null ? stats.scanningUsers + ' have scanned' : '' }}</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">warning</span>
          <div class="stat-value">{{ statsLoading ? '…' : fmt(stats.counterfeit) }}</div>
          <div class="stat-label">Counterfeit Detected</div>
          <div class="stat-sub">{{ stats.counterfeitRate != null ? stats.counterfeitRate + '% of scans' : '' }}</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">speed</span>
          <div class="stat-value">{{ statsLoading ? '…' : (stats.avgLatency != null ? stats.avgLatency + 's' : '—') }}</div>
          <div class="stat-label">Avg Scan Time</div>
          <div class="stat-sub">{{ stats.genuine != null ? stats.genuine + ' genuine' : '' }}</div>
        </div>
      </div>
      <div v-if="statsError" class="stats-err"><span class="material-icons">error</span> {{ statsError }}</div>
    </div>
  </div>
</template>

<script>
import { runModelConnectionChecks } from '../services/modelConnectionCheck.js'
import { value, count } from '../services/db.js'

const SCAN_COLLECTIONS = ['Standard Scan', 'Multi Scan', 'Video Scan']

export default {
  name: 'Dashboard',
  data() {
    return {
      connections: [
        { id: 'gcp', name: 'GCP Cloud Run', icon: 'cloud', overall: 'checking' },
        { id: 'docker', name: 'Docker Container', icon: 'inventory_2', overall: 'checking' },
        { id: 'mobile', name: 'Mobile App', icon: 'phone_android', overall: 'checking' },
        { id: 'firebase-ml', name: 'Firebase ML', icon: 'model_training', overall: 'checking' }
      ],
      statsLoading: true,
      statsError: '',
      stats: {
        totalScans: 0, users: 0, counterfeit: 0, genuine: 0,
        avgLatency: null, byType: '', counterfeitRate: null, scanningUsers: null
      }
    }
  },
  methods: {
    fmt(n) { return (n == null) ? '—' : Number(n).toLocaleString() },
    async loadConnectionStatus() {
      try {
        const results = await runModelConnectionChecks()
        this.connections[0].overall = results.gcp.overall
        this.connections[1].overall = results.docker.overall
        this.connections[2].overall = results.mobile.overall
        this.connections[3].overall = results.firebaseML.overall
      } catch (e) {
        console.error('Failed to load connection status:', e)
      }
    },
    // Aggregate scan analytics from RTDB. Scans are nested:
    // "<Scan Collection>/<userId>/<scanId>/{ authenticity:{status,isGenuine}, processingTime, ... }"
    async loadStats() {
      this.statsLoading = true; this.statsError = ''
      try {
        let total = 0, counterfeit = 0, genuine = 0
        const latencies = []
        const scanUserIds = new Set()
        const byType = {}
        const trees = await Promise.all(SCAN_COLLECTIONS.map(c => value(c).catch(() => null)))
        SCAN_COLLECTIONS.forEach((coll, idx) => {
          const data = trees[idx]
          if (!data || typeof data !== 'object') return
          let typeCount = 0
          for (const userId of Object.keys(data)) {
            const scans = data[userId]
            if (!scans || typeof scans !== 'object') continue
            for (const sid of Object.keys(scans)) {
              const rec = scans[sid]
              if (!rec || typeof rec !== 'object') continue
              if (!('authenticity' in rec) && !('denomination' in rec) && !('status' in rec)) continue
              total++; typeCount++; scanUserIds.add(userId)
              const a = rec.authenticity || {}
              const status = a.status || rec.status
              const fake = (status && String(status).toUpperCase() === 'COUNTERFEIT') ||
                           a.isGenuine === false || a.genuine === false
              if (fake) counterfeit++; else genuine++
              const pt = Number(rec.processingTime ?? rec.processing_time)
              if (!isNaN(pt) && pt > 0) latencies.push(pt)
            }
          }
          byType[coll] = typeCount
        })
        let users = 0
        try { users = await count('Users') } catch (e) { /* keep 0 */ }

        let avg = null
        if (latencies.length) {
          const mean = latencies.reduce((s, n) => s + n, 0) / latencies.length
          avg = Number((mean > 100 ? mean / 1000 : mean).toFixed(1)) // tolerate ms or s
        }
        this.stats = {
          totalScans: total,
          users,
          counterfeit,
          genuine,
          avgLatency: avg,
          byType: Object.entries(byType).filter(([, n]) => n).map(([k, n]) => `${k.replace(' Scan', '')}: ${n}`).join(' · '),
          counterfeitRate: total ? Number(((counterfeit / total) * 100).toFixed(0)) : null,
          scanningUsers: scanUserIds.size || null
        }
      } catch (e) {
        this.statsError = `Could not load stats: ${e.message}`
      } finally {
        this.statsLoading = false
      }
    }
  },
  mounted() {
    this.loadConnectionStatus()
    this.loadStats()
  }
}
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 18px;
  font-weight: 600;
}

.view-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}

.view-link:hover {
  color: var(--accent-glow);
}

.view-link .material-icons {
  font-size: 16px;
}

/* Connection summary cards */
.conn-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.conn-summary-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  text-decoration: none;
  color: var(--text);
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}

.conn-summary-card:hover {
  background: var(--bg-card-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.conn-summary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
}

.conn-summary-card.status-healthy::before { background: var(--success); }
.conn-summary-card.status-warning::before { background: var(--warning); }
.conn-summary-card.status-error::before { background: var(--danger); }
.conn-summary-card.status-checking::before { background: var(--info); animation: pulse 1.5s ease-in-out infinite; }

.conn-summary-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* Card icon colors */
.card-icon.gcp { background: rgba(79, 134, 247, 0.15); color: #4F86F7; }
.card-icon.docker { background: rgba(34, 197, 226, 0.15); color: #22C5E2; }
.card-icon.mobile { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.card-icon.firebase-ml { background: rgba(255, 107, 53, 0.15); color: #FF6B35; }

/* Stats grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 24px;
  text-align: center;
}

.stat-icon {
  font-size: 28px;
  color: var(--accent);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-muted);
}

.stat-sub {
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
  margin-top: 4px;
  min-height: 14px;
}

.refresh-stats {
  background: none;
  border: 0;
  cursor: pointer;
  font-family: inherit;
}
.refresh-stats:disabled { opacity: 0.5; cursor: default; }
.refresh-stats .spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.stats-err {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--danger, #f87171);
  font-size: 13px;
  margin-top: 12px;
}
.stats-err .material-icons { font-size: 16px; }

/* Responsive */
@media (max-width: 1024px) {
  .conn-summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .conn-summary-grid {
    grid-template-columns: 1fr;
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
