<template>
  <div>
    <div class="page-header">
      <h1>Connection Health</h1>
      <p>All infrastructure and service connection statuses</p>
    </div>

    <div class="dashboard-content">
      <!-- Pipeline Visualization -->
      <div class="pipeline-bar">
        <div class="pipeline-node">
          <span class="material-icons">phone_android</span>
          <span>Mobile App</span>
        </div>
        <span class="pipeline-arrow material-icons">arrow_forward</span>
        <div class="pipeline-node">
          <span class="material-icons">cloud</span>
          <span>GCP / Docker</span>
        </div>
        <span class="pipeline-arrow material-icons">arrow_forward</span>
        <div class="pipeline-node">
          <span class="material-icons">model_training</span>
          <span>Firebase ML</span>
        </div>
        <span class="pipeline-arrow material-icons">arrow_forward</span>
        <div class="pipeline-node">
          <span class="material-icons">storage</span>
          <span>Storage</span>
        </div>
      </div>

      <!-- Refresh bar -->
      <div class="refresh-bar">
        <div class="summary-bar">
          <div class="summary-item healthy">
            <span class="summary-count">{{ healthyCount }}</span>
            <span>Healthy</span>
          </div>
          <div class="summary-item warning">
            <span class="summary-count">{{ warningCount }}</span>
            <span>Warning</span>
          </div>
          <div class="summary-item error">
            <span class="summary-count">{{ errorCount }}</span>
            <span>Error</span>
          </div>
        </div>
        <div>
          <span class="last-check" v-if="lastCheck">Last check: {{ lastCheck }}</span>
          <button class="refresh-btn" @click="refresh" :disabled="loading">
            <span class="material-icons" :class="{ spinning: loading }">refresh</span>
            {{ loading ? 'Checking...' : 'Refresh All' }}
          </button>
        </div>
      </div>

      <!-- Model Pipeline Section -->
      <div class="section-label">
        <span class="material-icons">psychology</span>
        Model Pipeline
      </div>
      <div class="conn-grid">
        <div
          v-for="conn in pipelineConnections"
          :key="conn.id"
          class="health-card"
          :class="'status-' + conn.overall"
        >
          <div class="card-header-row">
            <div class="card-icon" :class="conn.id">
              <span class="material-icons">{{ conn.icon }}</span>
            </div>
            <span class="status-badge" :class="conn.overall">
              <span class="status-dot"></span>
              {{ conn.overall }}
            </span>
          </div>
          <div class="card-title">{{ conn.name }}</div>
          <div class="card-subtitle">{{ conn.description }}</div>

          <div class="conn-checklist">
            <div v-for="item in conn.checklist" :key="item.id" class="checklist-item">
              <span class="check-dot" :class="item.status"></span>
              <span class="check-label">{{ item.label }}</span>
            </div>
          </div>

          <button class="details-toggle" @click="toggleDetails(conn.id)">
            <span class="material-icons">{{ expandedCards[conn.id] ? 'expand_less' : 'expand_more' }}</span>
            {{ expandedCards[conn.id] ? 'Hide Details' : 'Show Details' }}
          </button>
          <div class="card-details" v-if="expandedCards[conn.id]">
            <div class="detail-row" v-for="(value, key) in getDisplayDetails(conn.details)" :key="key">
              <span class="detail-label">{{ formatKey(key) }}</span>
              <span class="detail-value" :style="key === 'error' ? 'color: var(--danger)' : ''">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Dev Tools & Services Section -->
      <div class="section-label" style="margin-top: 32px;">
        <span class="material-icons">build</span>
        Dev Tools & Services
      </div>
      <div class="conn-grid">
        <div
          v-for="svc in devToolConnections"
          :key="svc.id"
          class="health-card"
          :class="'status-' + svc.status"
        >
          <div class="card-header-row">
            <div class="card-icon" :class="svc.id">
              <span class="material-icons">{{ svc.icon }}</span>
            </div>
            <span class="status-badge" :class="svc.status">
              <span class="status-dot"></span>
              {{ svc.status }}
            </span>
          </div>
          <div class="card-title">{{ svc.name }}</div>
          <div class="card-subtitle">{{ svc.subtitle }}</div>
          <div class="card-details">
            <div class="detail-row" v-for="(value, key) in getDisplayDetails(svc.details)" :key="key">
              <span class="detail-label">{{ formatKey(key) }}</span>
              <span class="detail-value" :style="key === 'error' || key === 'note' ? 'color: var(--warning)' : ''">{{ formatValue(value) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { runModelConnectionChecks } from '../services/modelConnectionCheck.js'
import {
  checkFirebase, checkGitHub, checkClaudeCode, checkMCP,
  checkEmulator, checkGitNexus
} from '../services/healthCheck.js'

const defaultConn = {
  id: '', name: '', icon: 'pending', description: '',
  checklist: [], details: {}, overall: 'checking'
}

const defaultSvc = {
  id: '', name: '', icon: 'pending', subtitle: '',
  status: 'checking', details: {}
}

export default {
  name: 'ConnectionHealth',
  data() {
    return {
      loading: false,
      lastCheck: null,
      expandedCards: {},
      connections: {
        gcp: { ...defaultConn, id: 'gcp', name: 'GCP Cloud Run', icon: 'cloud' },
        docker: { ...defaultConn, id: 'docker', name: 'Docker Container', icon: 'inventory_2' },
        mobile: { ...defaultConn, id: 'mobile', name: 'Mobile App', icon: 'phone_android' },
        firebaseML: { ...defaultConn, id: 'firebase-ml', name: 'Firebase ML', icon: 'model_training' }
      },
      devTools: {
        firebase: { ...defaultSvc, id: 'firebase', name: 'Firebase', icon: 'local_fire_department', subtitle: 'Realtime Database, Storage, Messaging' },
        github: { ...defaultSvc, id: 'github', name: 'GitHub Repository', icon: 'code', subtitle: 'UserDevAccount1/Billsense-Project' },
        mcp: { ...defaultSvc, id: 'mcp', name: 'MCP Servers', icon: 'hub', subtitle: 'Model Context Protocol connections' },
        claudeCode: { ...defaultSvc, id: 'claude', name: 'Claude Code', icon: 'smart_toy', subtitle: 'AI coding assistant + skills' },
        emulator: { ...defaultSvc, id: 'emulator', name: 'Android Emulator', icon: 'phone_android', subtitle: 'VS Code tasks + Android Studio AVD' },
        gitNexus: { ...defaultSvc, id: 'gitnexus', name: 'GitNexus', icon: 'account_tree', subtitle: 'Zero-Server Code Intelligence Engine' }
      }
    }
  },
  computed: {
    pipelineConnections() {
      return [this.connections.gcp, this.connections.docker, this.connections.mobile, this.connections.firebaseML]
    },
    devToolConnections() {
      return Object.values(this.devTools)
    },
    allStatuses() {
      const pipeline = this.pipelineConnections.map(c => c.overall)
      const devStatuses = this.devToolConnections.map(s => {
        if (['healthy', 'connected', 'running', 'configured', 'deployed', 'available'].includes(s.status)) return 'healthy'
        if (['warning', 'no-models', 'not-found', 'stopped', 'partial'].includes(s.status)) return 'warning'
        if (['error', 'offline', 'disconnected', 'not-configured', 'unavailable'].includes(s.status)) return 'error'
        return 'checking'
      })
      return [...pipeline, ...devStatuses]
    },
    healthyCount() {
      return this.allStatuses.filter(s => s === 'healthy').length
    },
    warningCount() {
      return this.allStatuses.filter(s => s === 'warning').length
    },
    errorCount() {
      return this.allStatuses.filter(s => s === 'error').length
    }
  },
  methods: {
    async refresh() {
      this.loading = true
      try {
        // Run pipeline and dev tool checks in parallel
        const [pipelineResults, firebase, github, mcp, claudeCode, emulator, gitNexus] =
          await Promise.allSettled([
            runModelConnectionChecks(),
            checkFirebase(),
            checkGitHub(),
            checkMCP(),
            checkClaudeCode(),
            checkEmulator(),
            checkGitNexus()
          ])

        // Update pipeline connections
        if (pipelineResults.status === 'fulfilled' && pipelineResults.value) {
          this.connections.gcp = pipelineResults.value.gcp
          this.connections.docker = pipelineResults.value.docker
          this.connections.mobile = pipelineResults.value.mobile
          this.connections.firebaseML = pipelineResults.value.firebaseML
        } else {
          const err = pipelineResults.reason?.message || 'Pipeline check failed'
          for (const key of ['gcp', 'docker', 'mobile', 'firebaseML']) {
            this.connections[key].overall = 'error'
            this.connections[key].details = { error: err }
            this.connections[key].checklist = this.connections[key].checklist.map(c => ({ ...c, status: 'fail' }))
          }
        }

        // Update dev tool statuses
        const updateSvc = (key, result) => {
          if (result.value) {
            this.devTools[key].status = result.value.status
            this.devTools[key].details = result.value.details || {}
            if (result.value.latency) this.devTools[key].details.latency = result.value.latency + 'ms'
            if (result.value.error) this.devTools[key].details.error = result.value.error
          } else {
            this.devTools[key].status = 'error'
            this.devTools[key].details = { error: result.reason?.message || 'Check failed' }
          }
        }

        updateSvc('firebase', firebase)
        updateSvc('github', github)
        updateSvc('mcp', mcp)
        updateSvc('claudeCode', claudeCode)
        updateSvc('emulator', emulator)
        updateSvc('gitNexus', gitNexus)

        this.lastCheck = new Date().toLocaleTimeString()
      } catch (e) {
        console.error('Connection check failed:', e)
      } finally {
        this.loading = false
      }
    },
    toggleDetails(id) {
      this.expandedCards = { ...this.expandedCards, [id]: !this.expandedCards[id] }
    },
    getDisplayDetails(details) {
      if (!details) return {}
      const filtered = {}
      for (const [key, value] of Object.entries(details)) {
        if (value !== null && value !== undefined && value !== '') {
          filtered[key] = value
        }
      }
      return filtered
    },
    formatKey(key) {
      return key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())
    },
    formatValue(value) {
      if (Array.isArray(value)) return value.join(', ') || 'None'
      if (typeof value === 'number') return String(value)
      return String(value)
    }
  },
  mounted() {
    this.refresh()
  }
}
</script>

<style scoped>
/* Section labels */
.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-label .material-icons {
  font-size: 20px;
  color: var(--accent);
}

/* Pipeline visualization */
.pipeline-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.pipeline-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: var(--primary-light);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.pipeline-node .material-icons {
  font-size: 20px;
  color: var(--accent);
}

.pipeline-arrow {
  font-size: 20px;
  color: var(--text-muted);
}

/* 2x2 grid */
.conn-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-top: 12px;
}

/* Checklist */
.conn-checklist {
  margin: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.checklist-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text);
}

.check-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.check-dot.pass { background: var(--success); }
.check-dot.warn { background: var(--warning); }
.check-dot.fail { background: var(--danger); }
.check-dot.pending { background: var(--info); animation: pulse 1.5s ease-in-out infinite; }

/* Details toggle */
.details-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 0;
  margin-top: 4px;
}

.details-toggle:hover {
  color: var(--accent-glow);
}

.details-toggle .material-icons {
  font-size: 18px;
}

/* Card icon colors */
.card-icon.gcp { background: rgba(79, 134, 247, 0.15); color: #4F86F7; }
.card-icon.docker { background: rgba(34, 197, 226, 0.15); color: #22C5E2; }
.card-icon.mobile { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.card-icon.firebase-ml { background: rgba(255, 107, 53, 0.15); color: #FF6B35; }
.card-icon.firebase { background: rgba(255, 163, 26, 0.15); color: #FFA31A; }
.card-icon.github { background: rgba(255, 255, 255, 0.1); color: #fff; }
.card-icon.mcp { background: rgba(139, 92, 246, 0.15); color: #8B5CF6; }
.card-icon.claude { background: rgba(196, 130, 89, 0.15); color: #C48259; }
.card-icon.emulator { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.card-icon.gitnexus { background: rgba(99, 102, 241, 0.15); color: #6366F1; }

/* Responsive */
@media (max-width: 900px) {
  .conn-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 576px) {
  .pipeline-bar {
    padding: 14px;
    gap: 8px;
  }
  .pipeline-node {
    padding: 8px 12px;
    font-size: 12px;
  }
  .pipeline-arrow {
    font-size: 16px;
  }
}
</style>
