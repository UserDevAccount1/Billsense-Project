<template>
  <div>
    <div class="page-header">
      <h1>APK Management</h1>
      <p>Build history, version tracking, and deployment management for BillSense APKs</p>
    </div>

    <div v-if="isRemoteSite" class="local-only-banner">
      <span class="material-icons">desktop_windows</span>
      <div>
        <strong>Building is local — distributing works here</strong>
        <p><em>Building</em> APKs needs Gradle/ADB on your machine via the dev-server
        (<code>localhost:3003</code>), so the build/emulator buttons are disabled on the live site.
        But <strong>published releases below are installable from here</strong> — the
        <span class="material-icons" style="font-size:.9em;vertical-align:middle">download</span>
        button opens the Firebase App Distribution install page, which works on any device.
        To build new APKs, open <code>http://localhost:3001</code> with <code>node dev-server.mjs</code>,
        or use the cloud <code>Fire APK</code> GitHub Actions workflow.</p>
      </div>
    </div>

    <div class="dashboard-content">
      <!-- Top Controls -->
      <div class="apk-controls">
        <div class="search-box">
          <span class="material-icons">search</span>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search by version, variant, or description..."
            @input="currentPage = 1"
          />
          <button v-if="searchQuery" class="clear-btn" @click="searchQuery = ''; currentPage = 1">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="filter-group">
          <select v-model="variantFilter" @change="currentPage = 1">
            <option value="all">All Variants</option>
            <option value="main">Main App</option>
            <option value="admin">Admin Panel</option>
          </select>
          <select v-model="statusFilter" @change="currentPage = 1">
            <option value="all">All Status</option>
            <option value="released">Released</option>
            <option value="debug">Debug</option>
            <option value="testing">Testing</option>
          </select>
          <button class="apk-btn primary" @click="showNewBuildModal = true; checkDevServer()">
            <span class="material-icons">add_circle</span>
            Log New Build
          </button>
        </div>
      </div>

      <!-- Stats Row -->
      <div class="apk-stats">
        <div class="apk-stat-card">
          <span class="material-icons">android</span>
          <div>
            <span class="stat-value">{{ apkBuilds.length }}</span>
            <span class="stat-label">Total Builds</span>
          </div>
        </div>
        <div class="apk-stat-card">
          <span class="material-icons">phone_android</span>
          <div>
            <span class="stat-value">{{ apkBuilds.filter(b => b.variant === 'main').length }}</span>
            <span class="stat-label">Main App</span>
          </div>
        </div>
        <div class="apk-stat-card">
          <span class="material-icons">admin_panel_settings</span>
          <div>
            <span class="stat-value">{{ apkBuilds.filter(b => b.variant === 'admin').length }}</span>
            <span class="stat-label">Admin App</span>
          </div>
        </div>
        <div class="apk-stat-card latest">
          <span class="material-icons">new_releases</span>
          <div>
            <span class="stat-value">{{ latestVersion }}</span>
            <span class="stat-label">Latest Version</span>
          </div>
        </div>
      </div>

      <!-- APK Table -->
      <div class="table-wrapper">
        <table class="apk-table">
          <thead>
            <tr>
              <th @click="sortBy('version')" class="sortable">
                Version
                <span class="material-icons sort-icon">{{ getSortIcon('version') }}</span>
              </th>
              <th @click="sortBy('versionCode')" class="sortable">
                Code
                <span class="material-icons sort-icon">{{ getSortIcon('versionCode') }}</span>
              </th>
              <th>Variant</th>
              <th>Build Type</th>
              <th @click="sortBy('buildDate')" class="sortable">
                Build Date
                <span class="material-icons sort-icon">{{ getSortIcon('buildDate') }}</span>
              </th>
              <th>Size</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="build in paginatedBuilds" :key="build.id" :class="{ 'row-latest': build.isLatest }">
              <td class="version-cell">
                <span class="version-tag">v{{ build.version }}</span>
                <span v-if="build.isLatest" class="latest-badge">LATEST</span>
              </td>
              <td class="code-cell">{{ build.versionCode }}</td>
              <td>
                <span class="variant-badge" :class="build.variant">
                  <span class="material-icons">{{ build.variant === 'main' ? 'phone_android' : 'admin_panel_settings' }}</span>
                  {{ build.variant === 'main' ? 'Main' : 'Admin' }}
                </span>
              </td>
              <td>
                <span class="build-type-badge" :class="build.buildType">{{ build.buildType }}</span>
              </td>
              <td class="date-cell">{{ formatDate(build.buildDate) }}</td>
              <td class="size-cell">{{ build.size }}</td>
              <td>
                <span class="status-pill" :class="build.status">{{ build.status }}</span>
              </td>
              <td class="actions-cell">
                <button class="icon-btn details" @click="openDetails(build)" title="View Details">
                  <span class="material-icons">info</span>
                </button>
                <button
                  class="icon-btn emulator"
                  @click="launchEmulator(build)"
                  :disabled="pipelineRunning"
                  :title="pipelineRunning ? 'Pipeline running...' : 'Build & Run in Emulator'"
                >
                  <span class="material-icons">{{ pipelineRunning && pipelineBuildId === build.id ? 'sync' : 'play_circle' }}</span>
                </button>
                <button class="icon-btn download" @click="downloadApk(build)" title="Download APK">
                  <span class="material-icons">download</span>
                </button>
                <button class="icon-btn delete" @click="confirmDelete(build)" title="Delete Build">
                  <span class="material-icons">delete</span>
                </button>
              </td>
            </tr>
            <tr v-if="filteredBuilds.length === 0">
              <td colspan="8" class="empty-row">
                <span class="material-icons">inbox</span>
                <span>No builds found matching your search</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="pagination" v-if="totalPages > 1">
        <button class="page-btn" :disabled="currentPage === 1" @click="currentPage = 1">
          <span class="material-icons">first_page</span>
        </button>
        <button class="page-btn" :disabled="currentPage === 1" @click="currentPage--">
          <span class="material-icons">chevron_left</span>
        </button>
        <span class="page-info">Page {{ currentPage }} of {{ totalPages }} ({{ filteredBuilds.length }} builds)</span>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="currentPage++">
          <span class="material-icons">chevron_right</span>
        </button>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="currentPage = totalPages">
          <span class="material-icons">last_page</span>
        </button>
        <select v-model.number="pageSize" @change="currentPage = 1" class="page-size-select">
          <option :value="5">5 / page</option>
          <option :value="10">10 / page</option>
          <option :value="25">25 / page</option>
        </select>
      </div>
    </div>

    <!-- Details Modal -->
    <div class="modal-overlay" v-if="selectedBuild" @click.self="selectedBuild = null">
      <div class="modal-card">
        <div class="modal-header">
          <div>
            <h2>v{{ selectedBuild.version }} <span class="variant-badge" :class="selectedBuild.variant">{{ selectedBuild.variant === 'main' ? 'Main App' : 'Admin App' }}</span></h2>
            <p>Build #{{ selectedBuild.versionCode }} &mdash; {{ formatDate(selectedBuild.buildDate) }}</p>
          </div>
          <button class="close-btn" @click="selectedBuild = null">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="modal-body">
          <div class="detail-section">
            <h3><span class="material-icons">info</span> Build Information</h3>
            <div class="detail-grid">
              <div class="detail-item"><span class="label">Version</span><span class="value">{{ selectedBuild.version }}</span></div>
              <div class="detail-item"><span class="label">Version Code</span><span class="value">{{ selectedBuild.versionCode }}</span></div>
              <div class="detail-item"><span class="label">Build Type</span><span class="value"><span class="build-type-badge" :class="selectedBuild.buildType">{{ selectedBuild.buildType }}</span></span></div>
              <div class="detail-item"><span class="label">Status</span><span class="value"><span class="status-pill" :class="selectedBuild.status">{{ selectedBuild.status }}</span></span></div>
              <div class="detail-item"><span class="label">APK Size</span><span class="value">{{ selectedBuild.size }}</span></div>
              <div class="detail-item"><span class="label">Min SDK</span><span class="value">{{ selectedBuild.minSdk }}</span></div>
              <div class="detail-item"><span class="label">Target SDK</span><span class="value">{{ selectedBuild.targetSdk }}</span></div>
              <div class="detail-item"><span class="label">Package</span><span class="value mono">{{ selectedBuild.packageName }}</span></div>
            </div>
          </div>
          <div class="detail-section">
            <h3><span class="material-icons">description</span> Release Notes</h3>
            <p class="release-notes">{{ selectedBuild.description }}</p>
          </div>
          <div class="detail-section" v-if="selectedBuild.changes && selectedBuild.changes.length">
            <h3><span class="material-icons">checklist</span> Changes in this Build</h3>
            <ul class="changes-list">
              <li v-for="(change, i) in selectedBuild.changes" :key="i">
                <span class="change-type" :class="change.type">{{ change.type }}</span>
                {{ change.text }}
              </li>
            </ul>
          </div>
          <div class="detail-section" v-if="selectedBuild.dependencies">
            <h3><span class="material-icons">link</span> Key Dependencies</h3>
            <div class="dep-chips">
              <span class="dep-chip" v-for="dep in selectedBuild.dependencies" :key="dep">{{ dep }}</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="apk-btn secondary" @click="selectedBuild = null">Close</button>
          <button class="apk-btn primary" @click="launchEmulator(selectedBuild); selectedBuild = null" :disabled="pipelineRunning">
            <span class="material-icons">play_circle</span> Run in Emulator
          </button>
        </div>
      </div>
    </div>

    <!-- New Build Modal -->
    <div class="modal-overlay" v-if="showNewBuildModal" @click.self="showNewBuildModal = false">
      <div class="modal-card compact">
        <div class="modal-header">
          <h2><span class="material-icons" style="color: var(--accent);">build_circle</span> Build New APK</h2>
          <button class="close-btn" @click="showNewBuildModal = false">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="modal-body">
          <!-- Environment Status -->
          <div class="env-status" v-if="envStatus">
            <div class="env-item" :class="{ ok: envStatus.javaHome }">
              <span class="material-icons">{{ envStatus.javaHome ? 'check_circle' : 'error' }}</span>
              Java 17 (Temurin)
            </div>
            <div class="env-item" :class="{ ok: envStatus.androidSdk }">
              <span class="material-icons">{{ envStatus.androidSdk ? 'check_circle' : 'error' }}</span>
              Android SDK 35
            </div>
            <div class="env-item" :class="{ ok: envStatus.gradlew }">
              <span class="material-icons">{{ envStatus.gradlew ? 'check_circle' : 'error' }}</span>
              Gradle 8.13
            </div>
            <div class="env-item" :class="{ ok: devServerOnline }">
              <span class="material-icons">{{ devServerOnline ? 'check_circle' : 'error' }}</span>
              Dev Server
            </div>
          </div>

          <div class="form-row" style="margin-top: 16px;">
            <div class="form-group">
              <label>APK Variant</label>
              <select v-model="newBuild.variant">
                <option value="user">Main App (com.app.billsense)</option>
                <option value="admin">Admin App (com.admin.billsense)</option>
              </select>
            </div>
            <div class="form-group">
              <label>Build Type</label>
              <select v-model="newBuild.buildType">
                <option value="debug">Debug</option>
                <option value="release">Release</option>
              </select>
            </div>
          </div>
          <div class="build-info-box">
            <div><strong>Gradle Task:</strong> <code>assemble{{ newBuild.variant === 'user' ? 'User' : 'Admin' }}{{ newBuild.buildType === 'debug' ? 'Debug' : 'Release' }}</code></div>
            <div><strong>Package:</strong> <code>{{ newBuild.variant === 'user' ? 'com.app.billsense' : 'com.admin.billsense' }}</code></div>
            <div><strong>Output:</strong> <code>app-{{ newBuild.variant }}-{{ newBuild.buildType }}.apk</code></div>
          </div>
          <div class="form-group">
            <label>Description / Release Notes (optional)</label>
            <textarea v-model="newBuild.description" rows="3" placeholder="What's new in this build..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="apk-btn secondary" @click="showNewBuildModal = false">Cancel</button>
          <button class="apk-btn primary" @click="triggerBuild" :disabled="pipelineRunning || !devServerOnline">
            <span class="material-icons">{{ pipelineRunning ? 'sync' : 'rocket_launch' }}</span>
            {{ pipelineRunning ? 'Building...' : 'Build APK' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div class="modal-overlay" v-if="deleteTarget" @click.self="deleteTarget = null">
      <div class="modal-card compact">
        <div class="modal-header">
          <h2><span class="material-icons" style="color: var(--danger);">warning</span> Delete Build</h2>
          <button class="close-btn" @click="deleteTarget = null">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="modal-body">
          <p style="font-size: 14px; color: var(--text); line-height: 1.6;">
            Are you sure you want to delete <strong>v{{ deleteTarget.version }}</strong>
            ({{ deleteTarget.variant === 'main' ? 'Main App' : 'Admin App' }}, {{ deleteTarget.buildType }})? This action cannot be undone.
          </p>
        </div>
        <div class="modal-footer">
          <button class="apk-btn secondary" @click="deleteTarget = null">Cancel</button>
          <button class="apk-btn danger" @click="deleteBuild">
            <span class="material-icons">delete</span> Delete
          </button>
        </div>
      </div>
    </div>

    <!-- Pipeline Progress Panel -->
    <div class="pipeline-panel" v-if="pipelineRunning || pipelineResult">
      <div class="pipeline-header">
        <div class="pipeline-title">
          <span class="material-icons" :class="pipelineIconClass">{{ pipelineIcon }}</span>
          <div>
            <strong>{{ pipelineStep || 'Initializing...' }}</strong>
            <span class="pipeline-elapsed" v-if="pipelineElapsed">{{ pipelineElapsed }}s</span>
          </div>
        </div>
        <button class="icon-btn" @click="cancelOrDismissPipeline" :title="pipelineRunning ? 'Cancel' : 'Dismiss'">
          <span class="material-icons">{{ pipelineRunning ? 'stop_circle' : 'close' }}</span>
        </button>
      </div>
      <div class="pipeline-progress-bar">
        <div class="pipeline-progress-fill" :style="{ width: pipelineProgress + '%' }" :class="pipelineBarClass"></div>
      </div>
      <div class="pipeline-steps">
        <div v-for="s in pipelineSteps" :key="s.index" class="pipeline-step-item" :class="stepClass(s.index)">
          <span class="material-icons">{{ stepIcon(s.index) }}</span>
          {{ s.label }}
        </div>
      </div>
      <div class="pipeline-logs" v-if="pipelineLogs.length">
        <div v-for="(l, i) in pipelineLogs.slice(-6)" :key="i" class="log-line">{{ l }}</div>
      </div>
    </div>

    <!-- Toast (non-pipeline) -->
    <div class="emulator-toast" v-if="toastMsg" :class="toastType">
      <span class="material-icons">{{ toastType === 'success' ? 'check_circle' : toastType === 'error' ? 'error' : 'info' }}</span>
      {{ toastMsg }}
    </div>
  </div>
</template>

<script>
// Dev server URL resolution:
// - Port 3003: running directly on dev server
// - Port 3000 (Docker Nginx): proxied via /api/dev/ → host.docker.internal:3003
// - Any other port (Vite dev 5173, 3001, etc): direct cross-origin to localhost:3003
const DEV_SERVER = (() => {
  const port = window.location.port
  // Direct dev server, Docker Nginx proxy, or Vite dev proxy — all proxy /api/dev/
  if (port === '3003' || port === '3000' || port === '3001') return ''
  return 'http://localhost:3003'
})()

import { value } from '../services/db.js'

export default {
  name: 'ApkManagement',
  data() {
    return {
      searchQuery: '',
      variantFilter: 'all',
      statusFilter: 'all',
      currentPage: 1,
      pageSize: 10,
      sortField: 'buildDate',
      sortDirection: 'desc',
      selectedBuild: null,
      showNewBuildModal: false,
      newBuild: { variant: 'user', buildType: 'debug', description: '' },
      envStatus: null,
      deleteTarget: null,
      // Pipeline state
      pipelineRunning: false,
      pipelineBuildId: null,
      pipelineStep: '',
      pipelineStepIndex: 0,
      pipelineProgress: 0,
      pipelineLogs: [],
      pipelineError: null,
      pipelineElapsed: 0,
      pipelineResult: null,
      pollTimer: null,
      devServerOnline: false,
      // Toast
      toastMsg: '',
      toastType: 'info',
      toastTimer: null,
      // Pipeline step definitions
      pipelineSteps: [
        { index: 1, label: 'Start Emulator' },
        { index: 2, label: 'Build APK' },
        { index: 3, label: 'Install APK' },
        { index: 4, label: 'Launch App' },
        { index: 5, label: 'Done' }
      ],
      apkBuilds: [
        {
          id: 100, version: '1.4.1', versionCode: 6, variant: 'main', buildType: 'debug', status: 'testing',
          buildDate: '2026-06-17T16:10:00', size: '55.4 MB', minSdk: 24, targetSdk: 35,
          packageName: 'com.app.billsense', isLatest: true,
          distribution: 'Firebase App Distribution',
          downloadUrl: 'https://appdistribution.firebase.google.com/testerapps/1:340624938055:android:81d528ded5f924a23fcd62/releases/361qlb728f82o',
          description: 'Latest test build distributed to the "testers" group via Firebase App Distribution. Billy now knows the BillSense research (Joy Canutab, University of the Cordilleras); on-device TFLite offline scanning reconciled; live Cloud Run backend verified. Install from the link on any device signed in as a tester.',
          changes: [
            { type: 'feat', text: 'Billy AI: research knowledge (researcher, university, methodology, SDG 16.4)' },
            { type: 'feat', text: 'On-device TFLite offline scanning (counterfeit + security models)' },
            { type: 'feat', text: 'Distributed via Firebase App Distribution (cloud, installable on live)' }
          ],
          dependencies: ['Firebase App Distribution', 'TFLite 2.14.0', 'CameraX 1.4.2']
        },
        {
          id: 1, version: '1.4.0', versionCode: 5, variant: 'main', buildType: 'debug', status: 'testing',
          buildDate: '2026-03-16T12:00:00', size: '35.8 MB', minSdk: 24, targetSdk: 35,
          packageName: 'com.app.billsense', isLatest: false,
          description: 'Firebase ML models deployed to cloud. simple_model (12.3MB) and uv_model (10.9MB INT8) now available for on-device download. Hybrid cloud inference with 6 YOLOv8 models on Cloud Run + 2 TFLite models on Firebase ML. WebSocket real-time scanning, CameraX integration, and Docker local dev support.',
          changes: [
            { type: 'feat', text: 'simple_model TFLite deployed to Firebase ML (counterfeit detection, 7 classes)' },
            { type: 'feat', text: 'uv_model TFLite deployed to Firebase ML (security features, INT8 quantized)' },
            { type: 'feat', text: 'HybridInferenceManager — on-device + cloud fallback strategy' },
            { type: 'feat', text: 'Standard, Multi, Video scan modes with WebSocket real-time detection' },
            { type: 'feat', text: 'BuildConfig split: debug (Docker local) / release (Cloud Run)' },
            { type: 'fix', text: 'Connection Health — Firebase ML checks Storage metadata' },
            { type: 'perf', text: 'uv_model INT8 quantization: 42.7MB to 10.9MB' }
          ],
          dependencies: ['Firebase ML 24.2.3', 'TFLite 2.14.0', 'CameraX 1.4.2', 'OkHttp 4.12.0', 'Retrofit 2.9.0', 'Firebase DB 21.0.0']
        },
        {
          id: 2, version: '1.1.0', versionCode: 2, variant: 'admin', buildType: 'debug', status: 'testing',
          buildDate: '2026-03-16T14:00:00', size: '19.8 MB', minSdk: 24, targetSdk: 35,
          packageName: 'com.admin.billsense', isLatest: true,
          description: 'Admin panel app with web admin (Vue 3 Docker) integration. Includes dashboard, scan reports, user management, Connection Health monitoring, GitNexus code intelligence, APK Management, and App Testing pages.',
          changes: [
            { type: 'feat', text: 'Web admin panel (Vue 3 + Nginx Docker) on port 3000' },
            { type: 'feat', text: 'Connection Health page with 4 model pipeline checks' },
            { type: 'feat', text: 'GitNexus code intelligence integration' },
            { type: 'feat', text: 'APK Management page with version tracking' },
            { type: 'feat', text: 'App Testing page with feature validation' },
            { type: 'feat', text: 'Case management and voting posts moderation' }
          ],
          dependencies: ['Firebase DB 21.0.0', 'Glide 4.16.0', 'Material 1.12.0']
        }
      ]
    }
  },
  computed: {
    latestVersion() {
      const latest = this.apkBuilds.find(b => b.isLatest && b.variant === 'main')
      return latest ? `v${latest.version}` : '-'
    },
    filteredBuilds() {
      let builds = [...this.apkBuilds]
      if (this.searchQuery) {
        const q = this.searchQuery.toLowerCase()
        builds = builds.filter(b =>
          b.version.includes(q) ||
          b.variant.includes(q) ||
          b.description.toLowerCase().includes(q) ||
          b.status.includes(q) ||
          (b.changes && b.changes.some(c => c.text.toLowerCase().includes(q)))
        )
      }
      if (this.variantFilter !== 'all') builds = builds.filter(b => b.variant === this.variantFilter)
      if (this.statusFilter !== 'all') builds = builds.filter(b => b.status === this.statusFilter)

      builds.sort((a, b) => {
        let aVal = a[this.sortField], bVal = b[this.sortField]
        if (this.sortField === 'buildDate') { aVal = new Date(aVal); bVal = new Date(bVal) }
        if (aVal < bVal) return this.sortDirection === 'asc' ? -1 : 1
        if (aVal > bVal) return this.sortDirection === 'asc' ? 1 : -1
        return 0
      })
      return builds
    },
    totalPages() {
      return Math.ceil(this.filteredBuilds.length / this.pageSize)
    },
    paginatedBuilds() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.filteredBuilds.slice(start, start + this.pageSize)
    },
    pipelineIcon() {
      if (this.pipelineError) return 'error'
      if (this.pipelineProgress >= 100) return 'check_circle'
      return 'sync'
    },
    pipelineIconClass() {
      if (this.pipelineError) return 'error'
      if (this.pipelineProgress >= 100) return 'success'
      return 'spinning'
    },
    pipelineBarClass() {
      if (this.pipelineError) return 'error'
      if (this.pipelineProgress >= 100) return 'success'
      return ''
    },
    isRemoteSite() {
      const h = window.location.hostname
      return h !== 'localhost' && h !== '127.0.0.1'
    }
  },
  mounted() {
    // Load any dynamically-published releases from RTDB (works on the live
    // site). Falls back silently to the committed list if the apk_releases
    // root isn't enabled on the proxy yet.
    this.loadReleases()
    // Live site can't reach the developer's local dev-server — skip the
    // check that would only produce "Dev server offline" noise.
    if (this.isRemoteSite) return
    this.checkDevServer()
  },
  beforeUnmount() {
    this.stopPolling()
  },
  methods: {
    showToast(msg, type = 'info', duration = 3000) {
      this.toastMsg = msg
      this.toastType = type
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toastMsg = '' }, duration)
    },
    // Pull published releases from RTDB `apk_releases` (if the proxy allows
    // that root). Each record may carry a `downloadUrl` that works on the live
    // site. Merged ahead of the committed records, de-duplicated by version+variant.
    async loadReleases() {
      try {
        const data = await value('apk_releases')
        if (!data || typeof data !== 'object') return
        const rows = Object.entries(data).map(([k, v]) => ({
          id: v.id || k,
          version: v.version || '?', versionCode: v.versionCode || 0,
          variant: v.variant || 'main', buildType: v.buildType || 'debug',
          status: v.status || 'testing', buildDate: v.buildDate || new Date().toISOString(),
          size: v.size || '-', minSdk: v.minSdk || 24, targetSdk: v.targetSdk || 35,
          packageName: v.packageName || (v.variant === 'admin' ? 'com.admin.billsense' : 'com.app.billsense'),
          isLatest: false, distribution: v.distribution || '', downloadUrl: v.downloadUrl || '',
          description: v.description || '', changes: v.changes || [], dependencies: v.dependencies || []
        }))
        if (!rows.length) return
        const seen = new Set(rows.map(r => `${r.version}|${r.variant}`))
        const merged = [...rows, ...this.apkBuilds.filter(b => !seen.has(`${b.version}|${b.variant}`))]
        // Recompute latest per variant by buildDate
        ;['main', 'admin'].forEach(variant => {
          const ofV = merged.filter(b => b.variant === variant)
          ofV.forEach(b => (b.isLatest = false))
          const newest = ofV.sort((a, b) => new Date(b.buildDate) - new Date(a.buildDate))[0]
          if (newest) newest.isLatest = true
        })
        this.apkBuilds = merged
      } catch { /* apk_releases not enabled yet — keep committed list */ }
    },
    async checkDevServer() {
      try {
        const res = await fetch(`${DEV_SERVER}/api/dev/status`, { signal: AbortSignal.timeout(3000) })
        if (res.ok) {
          this.devServerOnline = true
          const data = await res.json()
          // Populate environment status for the build modal
          if (data.environment) {
            this.envStatus = {
              javaHome: data.environment.javaHome?.ok || false,
              androidSdk: data.environment.androidSdk?.ok || false,
              gradlew: data.environment.gradlew?.ok || false,
              emulator: data.environment.emulator?.ok || false
            }
          }
          // If pipeline was already running (page refresh), resume polling
          if (data.pipeline.running) {
            this.pipelineRunning = true
            this.pipelineStep = data.pipeline.step
            this.pipelineStepIndex = data.pipeline.stepIndex
            this.pipelineProgress = data.pipeline.progress
            // Set correct pipeline steps based on mode
            if (data.pipeline.mode === 'build') {
              this.pipelineSteps = [
                { index: 1, label: 'Build APK' },
                { index: 2, label: 'Done' }
              ]
            }
            this.startPolling()
          }
        }
      } catch {
        this.devServerOnline = false
        this.envStatus = null
      }
    },
    sortBy(field) {
      if (this.sortField === field) {
        this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc'
      } else {
        this.sortField = field
        this.sortDirection = 'desc'
      }
    },
    getSortIcon(field) {
      if (this.sortField !== field) return 'unfold_more'
      return this.sortDirection === 'asc' ? 'arrow_upward' : 'arrow_downward'
    },
    formatDate(dateStr) {
      return new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    },
    openDetails(build) {
      this.selectedBuild = build
    },

    // ===== Emulator Pipeline =====
    async launchEmulator(build) {
      if (this.pipelineRunning) return

      // Check if dev server is online
      if (!this.devServerOnline) {
        await this.checkDevServer()
        if (!this.devServerOnline) {
          this.showToast('Dev server offline. Run: node admin-panel/dev-server.mjs', 'error', 6000)
          // Copy the command to start dev server
          try {
            await navigator.clipboard.writeText('cd D:\\PROJECTS\\BillSense\\admin-panel && node dev-server.mjs')
          } catch (e) { /* ignore */ }
          return
        }
      }

      // Map table variant names to flavor names
      const variantMap = { main: 'user', admin: 'admin' }
      const variant = variantMap[build.variant] || build.variant
      this._buildMode = 'pipeline'

      this.pipelineRunning = true
      this.pipelineBuildId = build.id
      this.pipelineStep = 'Starting pipeline...'
      this.pipelineStepIndex = 0
      this.pipelineProgress = 0
      this.pipelineLogs = []
      this.pipelineError = null
      this.pipelineElapsed = 0
      this.pipelineResult = null
      this.pipelineSteps = [
        { index: 1, label: 'Start Emulator' },
        { index: 2, label: 'Build APK' },
        { index: 3, label: 'Install APK' },
        { index: 4, label: 'Launch App' },
        { index: 5, label: 'Done' }
      ]

      try {
        const res = await fetch(`${DEV_SERVER}/api/dev/pipeline`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ variant })
        })

        if (res.ok) {
          this.startPolling()
        } else {
          const data = await res.json()
          this.pipelineError = data.error || 'Failed to start pipeline'
          this.pipelineStep = this.pipelineError
          this.pipelineRunning = false
          this.pipelineResult = 'error'
          this.showToast(this.pipelineError, 'error', 6000)
        }
      } catch (e) {
        this.pipelineError = 'Cannot connect to dev server. Start it: node admin-panel/dev-server.mjs'
        this.pipelineStep = this.pipelineError
        this.pipelineRunning = false
        this.pipelineResult = 'error'
        this.devServerOnline = false
        this.showToast(this.pipelineError, 'error', 8000)
      }
    },

    startPolling() {
      this.stopPolling()
      this.pollTimer = setInterval(() => this.pollPipelineStatus(), 1500)
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    async pollPipelineStatus() {
      try {
        // Use the right status endpoint based on pipeline mode
        const endpoint = this._buildMode === 'build' ? '/api/dev/build/status' : '/api/dev/pipeline/status'
        const res = await fetch(`${DEV_SERVER}${endpoint}`, { signal: AbortSignal.timeout(3000) })
        if (!res.ok) return

        const data = await res.json()
        this.pipelineStep = data.step
        this.pipelineStepIndex = data.stepIndex
        this.pipelineProgress = data.progress
        this.pipelineLogs = data.logs || []
        this.pipelineElapsed = data.elapsed
        this.pipelineError = data.error

        if (!data.running) {
          this.stopPolling()
          this.pipelineRunning = false
          if (data.error) {
            this.pipelineResult = 'error'
          } else if (data.progress >= 100) {
            this.pipelineResult = 'success'
            const successMsg = this._buildMode === 'build' ? 'APK build complete!' : 'App launched on emulator!'
            this.showToast(successMsg, 'success', 5000)

            // Auto-add completed build to the table
            if (data.result) {
              this.addBuildToTable(data.result)
            }
          }
          this.pipelineBuildId = null
          this._buildMode = null
        }
      } catch {
        // Server might be busy, keep polling
      }
    },

    async cancelOrDismissPipeline() {
      if (this.pipelineRunning) {
        try {
          await fetch(`${DEV_SERVER}/api/dev/pipeline/cancel`, { method: 'POST' })
        } catch { /* ignore */ }
        this.stopPolling()
        this.pipelineRunning = false
        this.pipelineError = 'Cancelled'
        this.pipelineStep = 'Cancelled by user'
        this.pipelineResult = 'error'
        this.pipelineBuildId = null
      } else {
        // Dismiss completed panel
        this.pipelineResult = null
        this.pipelineStep = ''
        this.pipelineProgress = 0
        this.pipelineError = null
      }
    },

    stepClass(index) {
      if (this.pipelineError && index === this.pipelineStepIndex) return 'error'
      if (index < this.pipelineStepIndex) return 'done'
      if (index === this.pipelineStepIndex) return 'active'
      return 'pending'
    },

    stepIcon(index) {
      if (this.pipelineError && index === this.pipelineStepIndex) return 'error'
      if (index < this.pipelineStepIndex) return 'check_circle'
      if (index === this.pipelineStepIndex && this.pipelineRunning) return 'sync'
      return 'radio_button_unchecked'
    },

    // ===== Download APK =====
    async downloadApk(build) {
      // Published releases carry a real URL (Firebase App Distribution / Storage)
      // that works from any browser — including the live site.
      if (build.downloadUrl) {
        window.open(build.downloadUrl, '_blank', 'noopener')
        this.showToast('Opening install page…', 'success')
        return
      }
      if (this.devServerOnline) {
        // Map display variant to flavor variant
        const variant = build.variant === 'main' ? 'user' : build.variant
        const buildType = build.buildType || 'debug'
        try {
          const link = document.createElement('a')
          link.href = `${DEV_SERVER}/api/dev/apk/download?variant=${variant}&buildType=${buildType}`
          link.download = `billsense-${variant}-v${build.version}-${buildType}.apk`
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          this.showToast('Downloading APK...', 'success')
        } catch (e) {
          this.showToast('Download failed', 'error')
        }
      } else {
        this.showToast('Dev server offline — start it to enable APK downloads', 'error', 4000)
      }
    },

    // ===== Delete =====
    confirmDelete(build) {
      this.deleteTarget = build
    },
    deleteBuild() {
      if (!this.deleteTarget) return
      this.apkBuilds = this.apkBuilds.filter(b => b.id !== this.deleteTarget.id)
      const sameVariant = this.apkBuilds.filter(b => b.variant === this.deleteTarget.variant)
      if (sameVariant.length > 0 && !sameVariant.some(b => b.isLatest)) {
        sameVariant.sort((a, b) => new Date(b.buildDate) - new Date(a.buildDate))
        sameVariant[0].isLatest = true
      }
      this.showToast(`Deleted v${this.deleteTarget.version} (${this.deleteTarget.variant})`, 'success')
      this.deleteTarget = null
    },

    // ===== New Build =====
    async triggerBuild() {
      if (this.pipelineRunning) return

      // Refresh dev server status
      if (!this.devServerOnline) {
        await this.checkDevServer()
        if (!this.devServerOnline) {
          this.showToast('Dev server offline. Run: node admin-panel/dev-server.mjs', 'error', 6000)
          return
        }
      }

      const { variant, buildType } = this.newBuild
      this._buildMode = 'build'
      this._buildDescription = this.newBuild.description || ''

      // Set build-only pipeline steps (2 steps)
      this.pipelineSteps = [
        { index: 1, label: 'Build APK' },
        { index: 2, label: 'Done' }
      ]

      this.pipelineRunning = true
      this.pipelineBuildId = null
      this.pipelineStep = `Building ${variant === 'user' ? 'Main App' : 'Admin App'} (${buildType})...`
      this.pipelineStepIndex = 0
      this.pipelineProgress = 0
      this.pipelineLogs = []
      this.pipelineError = null
      this.pipelineElapsed = 0
      this.pipelineResult = null

      this.showNewBuildModal = false

      try {
        const res = await fetch(`${DEV_SERVER}/api/dev/build`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ variant, buildType })
        })

        if (res.ok) {
          this.startPolling()
        } else {
          const data = await res.json()
          this.pipelineError = data.error || 'Failed to start build'
          this.pipelineStep = this.pipelineError
          this.pipelineRunning = false
          this.pipelineResult = 'error'
          this.showToast(this.pipelineError, 'error', 6000)
        }
      } catch (e) {
        this.pipelineError = 'Cannot connect to dev server. Start it: node admin-panel/dev-server.mjs'
        this.pipelineStep = this.pipelineError
        this.pipelineRunning = false
        this.pipelineResult = 'error'
        this.devServerOnline = false
        this.showToast(this.pipelineError, 'error', 8000)
      }
    },

    addBuildToTable(result) {
      // Map flavor variant to display variant
      const displayVariant = result.variant === 'user' ? 'main' : result.variant
      const maxId = Math.max(...this.apkBuilds.map(b => b.id), 0)
      const sameVariantBuilds = this.apkBuilds.filter(b => b.variant === displayVariant)
      const maxCode = sameVariantBuilds.length > 0 ? Math.max(...sameVariantBuilds.map(b => b.versionCode)) : 0

      // Unmark previous latest for this variant
      this.apkBuilds.forEach(b => { if (b.variant === displayVariant) b.isLatest = false })

      // Get version from existing builds or from result
      const prevBuild = sameVariantBuilds.sort((a, b) => new Date(b.buildDate) - new Date(a.buildDate))[0]
      const version = prevBuild ? prevBuild.version : (displayVariant === 'main' ? '1.4.0' : '1.1.0')

      this.apkBuilds.push({
        id: maxId + 1,
        version,
        versionCode: maxCode + 1,
        variant: displayVariant,
        buildType: result.buildType || 'debug',
        status: result.buildType === 'release' ? 'released' : 'debug',
        buildDate: new Date().toISOString(),
        size: result.size || '-',
        minSdk: 24,
        targetSdk: 35,
        packageName: result.applicationId || (displayVariant === 'main' ? 'com.app.billsense' : 'com.admin.billsense'),
        isLatest: true,
        description: this._buildDescription || `Build via admin panel (${result.gradleTask || 'gradle'}) — ${result.buildTime || 0}s`,
        changes: [],
        dependencies: []
      })

      this._buildDescription = ''
    }
  }
}
</script>

<style scoped>
.local-only-banner {
  display: flex; gap: 1rem; align-items: flex-start;
  background: rgba(59,130,246,.10); border: 1px solid rgba(59,130,246,.3);
  border-radius: 12px; padding: 1rem 1.25rem; margin-bottom: 1.25rem;
}
.local-only-banner .material-icons { color: #60a5fa; font-size: 1.6rem; flex-shrink: 0; }
.local-only-banner strong { color: #93c5fd; }
.local-only-banner p { margin: .3rem 0 0; font-size: .85rem; color: var(--text-muted); line-height: 1.55; }
.local-only-banner code { background: rgba(0,0,0,.3); padding: .1rem .35rem; border-radius: 4px; font-size: .85em; }

/* Controls */
.apk-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 280px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 14px;
}

.search-box .material-icons { color: var(--text-muted); font-size: 20px; }

.search-box input {
  flex: 1;
  background: none;
  border: none;
  color: var(--text);
  font-size: 14px;
  outline: none;
}

.search-box input::placeholder { color: var(--text-muted); }

.clear-btn {
  background: none; border: none; cursor: pointer; padding: 2px;
  color: var(--text-muted);
}
.clear-btn:hover { color: var(--text); }

.filter-group {
  display: flex;
  gap: 10px;
  align-items: center;
}

.filter-group select {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  outline: none;
}

.apk-btn {
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
.apk-btn .material-icons { font-size: 18px; }
.apk-btn.primary { background: var(--accent); color: #fff; }
.apk-btn.primary:hover { filter: brightness(1.15); }
.apk-btn.secondary { background: var(--bg-card); color: var(--text); border: 1px solid var(--border); }
.apk-btn.secondary:hover { background: var(--bg-card-hover); }
.apk-btn.danger { background: #EF4444; color: #fff; }
.apk-btn.danger:hover { filter: brightness(1.15); }

/* Stats */
.apk-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.apk-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  border-left: 4px solid var(--accent);
}
.apk-stat-card.latest { border-left-color: var(--success); }
.apk-stat-card .material-icons { font-size: 28px; color: var(--accent); }
.apk-stat-card.latest .material-icons { color: var(--success); }
.stat-value { display: block; font-size: 22px; font-weight: 700; color: var(--text); }
.stat-label { font-size: 12px; color: var(--text-muted); }

/* Table */
.table-wrapper {
  overflow-x: auto;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg-card);
}

.apk-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.apk-table thead th {
  background: rgba(139, 92, 246, 0.08);
  padding: 12px 14px;
  text-align: left;
  font-weight: 700;
  color: var(--text);
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
  user-select: none;
}

.apk-table thead th.sortable { cursor: pointer; }
.apk-table thead th.sortable:hover { color: var(--accent); }

.sort-icon { font-size: 16px; vertical-align: middle; margin-left: 2px; }

.apk-table tbody td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
}

.apk-table tbody tr:hover { background: rgba(139, 92, 246, 0.04); }
.apk-table tbody tr.row-latest { background: rgba(34, 197, 94, 0.04); }

.version-tag {
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  color: var(--accent);
}

.latest-badge {
  display: inline-block;
  font-size: 9px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--success);
  color: #fff;
  margin-left: 6px;
  vertical-align: middle;
}

.code-cell { font-family: monospace; color: var(--text-muted); }
.date-cell { white-space: nowrap; }
.size-cell { white-space: nowrap; color: var(--text-muted); }

.variant-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
}
.variant-badge .material-icons { font-size: 14px; }
.variant-badge.main { background: rgba(34, 197, 94, 0.12); color: #22C55E; }
.variant-badge.admin { background: rgba(139, 92, 246, 0.12); color: #8B5CF6; }

.build-type-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}
.build-type-badge.debug { background: rgba(255, 163, 26, 0.15); color: #FFA31A; }
.build-type-badge.release { background: rgba(34, 197, 94, 0.15); color: #22C55E; }

.status-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 20px;
  text-transform: uppercase;
}
.status-pill.released { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-pill.debug { background: rgba(255, 163, 26, 0.15); color: #FFA31A; }
.status-pill.testing { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }

.icon-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.2s;
}
.icon-btn:hover { color: var(--accent); border-color: var(--accent); }
.icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.icon-btn .material-icons { font-size: 18px; }
.icon-btn.delete:hover { color: #EF4444; border-color: #EF4444; }
.icon-btn.download:hover { color: #3B82F6; border-color: #3B82F6; }
.icon-btn.emulator:hover { color: #22C55E; border-color: #22C55E; }
.icon-btn.emulator .material-icons { animation: none; }
.icon-btn.emulator:disabled .material-icons { animation: spin 1s linear infinite; }

.empty-row {
  text-align: center;
  padding: 40px !important;
  color: var(--text-muted);
}
.empty-row .material-icons { font-size: 32px; display: block; margin-bottom: 8px; }

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
}

.page-btn {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--text);
  transition: all 0.2s;
}
.page-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-btn .material-icons { font-size: 20px; }

.page-info { font-size: 13px; color: var(--text-muted); padding: 0 8px; }

.page-size-select {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 4px 8px;
  font-size: 12px;
  margin-left: 8px;
  cursor: pointer;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  width: 680px;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}
.modal-card.compact { width: 480px; }

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--border);
}
.modal-header h2 {
  font-size: 20px;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 10px;
}
.modal-header p { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
}
.close-btn:hover { color: var(--text); background: rgba(255,255,255,0.05); }

.modal-body { padding: 20px 24px; }

.detail-section { margin-bottom: 20px; }
.detail-section h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 12px;
}
.detail-section h3 .material-icons { font-size: 18px; }

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.detail-item .label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
.detail-item .value { font-size: 14px; color: var(--text); font-weight: 500; }
.detail-item .value.mono { font-family: monospace; font-size: 12px; }

.release-notes {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
  padding: 12px 16px;
  background: rgba(139, 92, 246, 0.04);
  border-radius: 8px;
  border-left: 3px solid var(--accent);
}

.changes-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.changes-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--text);
}

.change-type {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  flex-shrink: 0;
  margin-top: 2px;
}
.change-type.feat { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.change-type.fix { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }
.change-type.perf { background: rgba(255, 163, 26, 0.15); color: #FFA31A; }
.change-type.dep { background: rgba(139, 92, 246, 0.15); color: #8B5CF6; }

.dep-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.dep-chip {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(139, 92, 246, 0.1);
  color: var(--accent);
  font-weight: 500;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}

/* Form */
.form-group { margin-bottom: 14px; }
.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.form-group input, .form-group select, .form-group textarea {
  width: 100%;
  background: var(--primary-light);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  padding: 10px 14px;
  font-size: 14px;
  outline: none;
  font-family: inherit;
}
.form-group input:focus, .form-group select:focus, .form-group textarea:focus {
  border-color: var(--accent);
}
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

/* Environment Status */
.env-status {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px;
  background: rgba(139, 92, 246, 0.04);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.env-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #EF4444;
}
.env-item .material-icons { font-size: 16px; }
.env-item.ok { color: #22C55E; }

/* Build Info Box */
.build-info-box {
  background: rgba(139, 92, 246, 0.06);
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text);
}
.build-info-box code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  background: rgba(139, 92, 246, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--accent);
}

/* Actions cell */
.actions-cell {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* Pipeline Progress Panel */
.pipeline-panel {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 420px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  z-index: 2000;
  animation: slideUp 0.3s ease;
  overflow: hidden;
}

.pipeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 10px;
}

.pipeline-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text);
}

.pipeline-title strong {
  display: block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pipeline-title .material-icons {
  font-size: 22px;
}
.pipeline-title .material-icons.spinning {
  color: var(--accent);
  animation: spin 1s linear infinite;
}
.pipeline-title .material-icons.success { color: #22C55E; }
.pipeline-title .material-icons.error { color: #EF4444; }

.pipeline-elapsed {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 4px;
}

.pipeline-progress-bar {
  height: 4px;
  background: rgba(139, 92, 246, 0.1);
  margin: 0 16px;
  border-radius: 2px;
  overflow: hidden;
}

.pipeline-progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.5s ease;
}
.pipeline-progress-fill.success { background: #22C55E; }
.pipeline-progress-fill.error { background: #EF4444; }

.pipeline-steps {
  display: flex;
  gap: 4px;
  padding: 12px 16px 8px;
  flex-wrap: wrap;
}

.pipeline-step-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-muted);
  white-space: nowrap;
}
.pipeline-step-item .material-icons { font-size: 14px; }
.pipeline-step-item.done { color: #22C55E; }
.pipeline-step-item.done .material-icons { color: #22C55E; }
.pipeline-step-item.active { color: var(--accent); background: rgba(139, 92, 246, 0.1); font-weight: 600; }
.pipeline-step-item.active .material-icons { color: var(--accent); animation: spin 1s linear infinite; }
.pipeline-step-item.error { color: #EF4444; background: rgba(239, 68, 68, 0.1); }
.pipeline-step-item.error .material-icons { color: #EF4444; }

.pipeline-logs {
  padding: 8px 16px 14px;
  max-height: 100px;
  overflow-y: auto;
  border-top: 1px solid var(--border);
}

.log-line {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-muted);
  line-height: 1.6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Toast */
.emulator-toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  z-index: 2000;
  animation: slideUp 0.3s ease;
  box-shadow: 0 8px 30px rgba(0,0,0,0.3);
}
.emulator-toast.info { background: #1E3A5F; color: #93C5FD; border: 1px solid #3B82F6; }
.emulator-toast.success { background: #14532D; color: #86EFAC; border: 1px solid #22C55E; }
.emulator-toast.error { background: #7F1D1D; color: #FCA5A5; border: 1px solid #EF4444; }
.emulator-toast .material-icons { font-size: 20px; }

@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

/* Responsive */
@media (max-width: 1024px) {
  .apk-stats { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .apk-controls { flex-direction: column; }
  .filter-group { width: 100%; flex-wrap: wrap; }
  .apk-stats { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
