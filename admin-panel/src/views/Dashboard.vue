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

      <!-- Quick Stats (placeholder) -->
      <div class="section-header" style="margin-top: 32px;">
        <h2>Quick Stats</h2>
      </div>

      <div class="stats-grid">
        <div class="stat-card">
          <span class="material-icons stat-icon">qr_code_scanner</span>
          <div class="stat-value">—</div>
          <div class="stat-label">Total Scans</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">people</span>
          <div class="stat-value">—</div>
          <div class="stat-label">Active Users</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">warning</span>
          <div class="stat-value">—</div>
          <div class="stat-label">Counterfeit Detected</div>
        </div>
        <div class="stat-card">
          <span class="material-icons stat-icon">speed</span>
          <div class="stat-value">—</div>
          <div class="stat-label">Avg Latency</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { runModelConnectionChecks } from '../services/modelConnectionCheck.js'

export default {
  name: 'Dashboard',
  data() {
    return {
      connections: [
        { id: 'gcp', name: 'GCP Cloud Run', icon: 'cloud', overall: 'checking' },
        { id: 'docker', name: 'Docker Container', icon: 'inventory_2', overall: 'checking' },
        { id: 'mobile', name: 'Mobile App', icon: 'phone_android', overall: 'checking' },
        { id: 'firebase-ml', name: 'Firebase ML', icon: 'model_training', overall: 'checking' }
      ]
    }
  },
  methods: {
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
    }
  },
  mounted() {
    this.loadConnectionStatus()
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
