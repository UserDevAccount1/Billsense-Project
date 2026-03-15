<template>
  <div>
    <div class="page-header">
      <h1>GitNexus</h1>
      <p>Zero-Server Code Intelligence Engine — Knowledge Graph & RAG Agent</p>
    </div>

    <div class="dashboard-content">
      <!-- Control Bar -->
      <div class="nexus-controls">
        <div class="nexus-status-row">
          <div class="nexus-info">
            <span class="status-badge" :class="repoStatus">
              <span class="status-dot"></span>
              {{ repoStatus }}
            </span>
            <span class="nexus-repo-name" v-if="!iframeError">nxpatterns/gitnexus</span>
          </div>
          <div class="nexus-actions">
            <button class="nexus-btn secondary" @click="openExternal" title="Open in new tab">
              <span class="material-icons">open_in_new</span>
              Open Full UI
            </button>
            <button class="nexus-btn primary" @click="reloadIframe" :disabled="iframeLoading">
              <span class="material-icons" :class="{ spinning: iframeLoading }">refresh</span>
              {{ iframeLoading ? 'Loading...' : 'Reload' }}
            </button>
          </div>
        </div>

        <!-- Feature Cards -->
        <div class="nexus-features">
          <div class="feature-chip" v-for="feature in features" :key="feature.icon">
            <span class="material-icons">{{ feature.icon }}</span>
            <span>{{ feature.label }}</span>
          </div>
        </div>
      </div>

      <!-- MCP Integration Status -->
      <div class="mcp-bar">
        <div class="mcp-info">
          <span class="material-icons mcp-icon">hub</span>
          <div>
            <strong>MCP Integration</strong>
            <span class="mcp-detail">7 tools exposed via Model Context Protocol for Claude Code, Cursor, Windsurf</span>
          </div>
        </div>
        <div class="mcp-tools">
          <span class="tool-tag" v-for="tool in mcpTools" :key="tool">{{ tool }}</span>
        </div>
      </div>

      <!-- Embedded GitNexus Web UI -->
      <div class="nexus-iframe-wrapper">
        <div class="iframe-loading-overlay" v-if="iframeLoading">
          <span class="material-icons spinning">hourglass_empty</span>
          <span>Loading GitNexus Web UI...</span>
        </div>
        <div class="iframe-error-overlay" v-if="iframeError">
          <span class="material-icons">cloud_off</span>
          <div class="error-msg">
            <strong>GitNexus UI could not be embedded</strong>
            <p>The web app may block iframe embedding. Use the "Open Full UI" button to access it in a new tab.</p>
          </div>
          <button class="nexus-btn primary" @click="openExternal">
            <span class="material-icons">open_in_new</span>
            Open GitNexus
          </button>
        </div>
        <iframe
          v-show="!iframeError"
          ref="nexusFrame"
          :src="nexusUrl"
          title="GitNexus Knowledge Graph"
          sandbox="allow-scripts allow-same-origin allow-popups allow-forms allow-modals"
          @load="onIframeLoad"
          @error="onIframeError"
        ></iframe>
      </div>

      <!-- Quick Actions -->
      <div class="quick-actions">
        <h3>Quick Actions</h3>
        <div class="action-grid">
          <a :href="githubRepoUrl" target="_blank" rel="noopener" class="action-card">
            <span class="material-icons">code</span>
            <div>
              <strong>View BillSense Repo</strong>
              <span>Open GitHub repository</span>
            </div>
          </a>
          <a href="https://github.com/nxpatterns/gitnexus#readme" target="_blank" rel="noopener" class="action-card">
            <span class="material-icons">menu_book</span>
            <div>
              <strong>GitNexus Docs</strong>
              <span>README & usage guide</span>
            </div>
          </a>
          <div class="action-card" @click="openExternal">
            <span class="material-icons">account_tree</span>
            <div>
              <strong>Explore Knowledge Graph</strong>
              <span>Drop BillSense ZIP to visualize</span>
            </div>
          </div>
          <div class="action-card disabled">
            <span class="material-icons">smart_toy</span>
            <div>
              <strong>Graph RAG Agent</strong>
              <span>Query codebase via AI chat</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GitNexus',
  data() {
    return {
      nexusUrl: 'https://gitnexus.vercel.app',
      githubRepoUrl: 'https://github.com/UserDevAccount1/Billsense-Project',
      iframeLoading: true,
      iframeError: false,
      repoStatus: 'loading',
      features: [
        { icon: 'account_tree', label: 'Knowledge Graph' },
        { icon: 'psychology', label: 'Graph RAG Agent' },
        { icon: 'search', label: 'Code Exploration' },
        { icon: 'track_changes', label: 'Impact Analysis' },
        { icon: 'query_stats', label: 'Cypher Queries' },
        { icon: 'edit_note', label: 'Multi-File Rename' }
      ],
      mcpTools: [
        'explore', 'search', 'impact', 'debug', 'refactor', 'context', 'query'
      ]
    }
  },
  methods: {
    onIframeLoad() {
      this.iframeLoading = false
      this.repoStatus = 'connected'
    },
    onIframeError() {
      this.iframeLoading = false
      this.iframeError = true
      this.repoStatus = 'unavailable'
    },
    openExternal() {
      window.open(this.nexusUrl, '_blank', 'noopener')
    },
    reloadIframe() {
      this.iframeLoading = true
      this.iframeError = false
      this.repoStatus = 'loading'
      const iframe = this.$refs.nexusFrame
      if (iframe) {
        iframe.src = this.nexusUrl
      }
    }
  },
  mounted() {
    // Check GitHub repo availability
    fetch('https://api.github.com/repos/nxpatterns/gitnexus', { signal: AbortSignal.timeout(10000) })
      .then(res => {
        if (!res.ok) this.repoStatus = 'unavailable'
      })
      .catch(() => {
        this.repoStatus = 'unavailable'
      })

    // Fallback: if iframe doesn't fire load in 15s, show error state
    setTimeout(() => {
      if (this.iframeLoading) {
        this.iframeLoading = false
        this.iframeError = true
        this.repoStatus = 'unavailable'
      }
    }, 15000)
  }
}
</script>

<style scoped>
/* Controls bar */
.nexus-controls {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}

.nexus-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.nexus-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nexus-repo-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.nexus-actions {
  display: flex;
  gap: 8px;
}

.nexus-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.nexus-btn .material-icons {
  font-size: 16px;
}

.nexus-btn.primary {
  background: var(--accent);
  color: #fff;
}

.nexus-btn.primary:hover {
  background: var(--accent-glow);
}

.nexus-btn.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.nexus-btn.secondary {
  background: var(--primary-light);
  color: var(--text);
  border: 1px solid var(--border);
}

.nexus-btn.secondary:hover {
  background: var(--bg-card-hover);
}

/* Feature chips */
.nexus-features {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feature-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--primary-light);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
}

.feature-chip .material-icons {
  font-size: 14px;
  color: var(--accent);
}

/* MCP bar */
.mcp-bar {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.mcp-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mcp-icon {
  font-size: 24px;
  color: #8B5CF6;
}

.mcp-info strong {
  display: block;
  font-size: 14px;
  margin-bottom: 2px;
}

.mcp-detail {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
}

.mcp-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-tag {
  padding: 3px 10px;
  background: rgba(139, 92, 246, 0.15);
  color: #8B5CF6;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Iframe wrapper */
.nexus-iframe-wrapper {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  height: 600px;
  margin-bottom: 24px;
}

.nexus-iframe-wrapper iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #0d1117;
}

.iframe-loading-overlay,
.iframe-error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: var(--bg-card);
  color: var(--text-muted);
  font-size: 14px;
  z-index: 2;
}

.iframe-loading-overlay .material-icons {
  font-size: 32px;
  color: var(--accent);
}

.iframe-error-overlay .material-icons {
  font-size: 48px;
  color: var(--text-muted);
}

.error-msg {
  text-align: center;
}

.error-msg strong {
  display: block;
  font-size: 16px;
  color: var(--text);
  margin-bottom: 6px;
}

.error-msg p {
  font-size: 13px;
  color: var(--text-muted);
  max-width: 400px;
}

/* Quick actions */
.quick-actions h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.action-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-decoration: none;
  color: var(--text);
  cursor: pointer;
  transition: all 0.2s;
}

.action-card:hover {
  background: var(--bg-card-hover);
  transform: translateY(-1px);
}

.action-card.disabled {
  opacity: 0.5;
  cursor: default;
  pointer-events: none;
}

.action-card .material-icons {
  font-size: 24px;
  color: var(--accent);
  flex-shrink: 0;
  margin-top: 2px;
}

.action-card strong {
  display: block;
  font-size: 13px;
  margin-bottom: 2px;
}

.action-card span {
  font-size: 12px;
  color: var(--text-muted);
}

/* Status colors */
.status-badge.loading {
  background: rgba(59, 130, 246, 0.15);
  color: #3B82F6;
}

.status-badge.unavailable {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
}

/* Responsive */
@media (max-width: 1024px) {
  .action-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .nexus-iframe-wrapper {
    height: 450px;
  }
  .mcp-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 576px) {
  .action-grid {
    grid-template-columns: 1fr;
  }
  .nexus-iframe-wrapper {
    height: 350px;
  }
  .nexus-status-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
