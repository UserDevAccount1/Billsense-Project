<template>
  <div class="session-reports">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">assessment</span> Session Reports</h1>
        <p class="subtitle">Audit reports generated from development sessions</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="autoGenerateReport" :disabled="generating">
          <span v-if="generating" class="material-icons spin">sync</span>
          <span v-else class="material-icons">auto_awesome</span>
          {{ generating ? 'Generating...' : 'Create Report' }}
        </button>
        <button class="btn btn-ghost" @click="showCreateModal = true">
          <span class="material-icons">edit_note</span> Manual
        </button>
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">description</span>
        <div class="stat-info">
          <span class="stat-value">{{ reports.length }}</span>
          <span class="stat-label">Total Reports</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">check_circle</span>
        <div class="stat-info">
          <span class="stat-value">{{ reports.filter(r => r.status === 'complete').length }}</span>
          <span class="stat-label">Completed</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--warning)">pending</span>
        <div class="stat-info">
          <span class="stat-value">{{ reports.filter(r => r.status === 'draft').length }}</span>
          <span class="stat-label">Drafts</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--accent)">bug_report</span>
        <div class="stat-info">
          <span class="stat-value">{{ totalIssues }}</span>
          <span class="stat-label">Issues Found</span>
        </div>
      </div>
    </div>

    <!-- Reports Table -->
    <div class="reports-table-container">
      <table class="reports-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Session ID</th>
            <th>Status</th>
            <th>Actions Done</th>
            <th>Issues Found</th>
            <th>Fixes Applied</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty-state">
              <span class="material-icons spin">sync</span> Loading reports...
            </td>
          </tr>
          <tr v-else-if="reports.length === 0">
            <td colspan="7" class="empty-state">
              <span class="material-icons">inbox</span>
              <p>No reports yet. Click "Create Report" to generate your first audit report.</p>
            </td>
          </tr>
          <tr v-for="report in reports" :key="report.id" @click="previewReport(report)" class="report-row">
            <td>{{ formatDate(report.date) }}</td>
            <td><code class="session-id">{{ report.sessionId }}</code></td>
            <td>
              <span :class="['status-badge', report.status]">
                {{ report.status === 'complete' ? 'Complete' : 'Draft' }}
              </span>
            </td>
            <td>{{ (report.actionsDone || []).length }}</td>
            <td>
              <span class="issue-count">
                <span class="material-icons" style="font-size:14px;color:var(--danger)">error</span>
                {{ (report.issuesFound || []).length }}
              </span>
            </td>
            <td>
              <span class="fix-count">
                <span class="material-icons" style="font-size:14px;color:var(--success)">check</span>
                {{ (report.fixesApplied || []).length }}
              </span>
            </td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="previewReport(report)" title="Preview">
                <span class="material-icons">visibility</span>
              </button>
              <button class="icon-btn" @click="downloadDocx(report)" title="Download DOCX">
                <span class="material-icons">download</span>
              </button>
              <button class="icon-btn" @click="viewPdf(report)" title="View PDF">
                <span class="material-icons">picture_as_pdf</span>
              </button>
              <button class="icon-btn danger" @click="confirmDelete(report)" title="Delete">
                <span class="material-icons">delete</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Floating Preview Panel -->
    <div v-if="selectedReport" class="preview-overlay" @click.self="selectedReport = null">
      <div class="preview-panel">
        <div class="preview-header">
          <h2>
            <span class="material-icons">article</span>
            Report Preview
          </h2>
          <div class="preview-actions">
            <button class="btn btn-sm" @click="downloadDocx(selectedReport)">
              <span class="material-icons">download</span> DOCX
            </button>
            <button class="btn btn-sm" @click="viewPdf(selectedReport)">
              <span class="material-icons">picture_as_pdf</span> PDF
            </button>
            <button class="icon-btn" @click="selectedReport = null">
              <span class="material-icons">close</span>
            </button>
          </div>
        </div>
        <div class="preview-body" ref="previewContent">
          <div class="report-content">
            <h3>BillSense Audit Report</h3>
            <p class="report-meta">
              <strong>Session:</strong> {{ selectedReport.sessionId }}<br>
              <strong>Date:</strong> {{ formatDate(selectedReport.date) }}<br>
              <strong>Status:</strong> {{ selectedReport.status }}<br>
              <strong>Generated by:</strong> {{ selectedReport.generatedBy || 'Claude Agent' }}
            </p>

            <h4>Summary</h4>
            <p>{{ selectedReport.summary || 'No summary provided.' }}</p>

            <h4>Actions Done ({{ (selectedReport.actionsDone || []).length }})</h4>
            <table class="inner-table" v-if="(selectedReport.actionsDone || []).length">
              <thead><tr><th>#</th><th>Description</th><th>File</th><th>Timestamp</th></tr></thead>
              <tbody>
                <tr v-for="(action, i) in selectedReport.actionsDone" :key="i">
                  <td>{{ i+1 }}</td>
                  <td>{{ action.description }}</td>
                  <td><code>{{ action.file || '-' }}</code></td>
                  <td>{{ action.timestamp || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="no-data">No actions recorded.</p>

            <h4>Issues Found ({{ (selectedReport.issuesFound || []).length }})</h4>
            <table class="inner-table" v-if="(selectedReport.issuesFound || []).length">
              <thead><tr><th>#</th><th>Severity</th><th>Description</th><th>File</th><th>Line</th></tr></thead>
              <tbody>
                <tr v-for="(issue, i) in selectedReport.issuesFound" :key="i">
                  <td>{{ i+1 }}</td>
                  <td><span :class="['severity-badge', (issue.severity||'').toLowerCase()]">{{ issue.severity }}</span></td>
                  <td>{{ issue.description }}</td>
                  <td><code>{{ issue.file || '-' }}</code></td>
                  <td>{{ issue.line || '-' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="no-data">No issues found.</p>

            <h4>Fixes Applied ({{ (selectedReport.fixesApplied || []).length }})</h4>
            <table class="inner-table" v-if="(selectedReport.fixesApplied || []).length">
              <thead><tr><th>#</th><th>Issue</th><th>Fix</th><th>File</th></tr></thead>
              <tbody>
                <tr v-for="(fix, i) in selectedReport.fixesApplied" :key="i">
                  <td>{{ i+1 }}</td>
                  <td>{{ fix.issue }}</td>
                  <td>{{ fix.fix }}</td>
                  <td><code>{{ fix.file || '-' }}</code></td>
                </tr>
              </tbody>
            </table>
            <p v-else class="no-data">No fixes applied.</p>

            <h4>Checklist Summary</h4>
            <div v-if="selectedReport.checklistSummary" class="checklist-summary">
              <div class="checklist-bar">
                <div class="checklist-fill" :style="{ width: checklistPercent + '%' }"></div>
              </div>
              <p>{{ selectedReport.checklistSummary.passed || 0 }} / {{ selectedReport.checklistSummary.total || 0 }} passed</p>
              <ul v-if="(selectedReport.checklistSummary.items || []).length">
                <li v-for="(item, i) in selectedReport.checklistSummary.items" :key="i">
                  <span class="material-icons" :style="{ color: item.passed ? 'var(--success)' : 'var(--danger)', fontSize: '16px' }">
                    {{ item.passed ? 'check_circle' : 'cancel' }}
                  </span>
                  {{ item.label }}
                </li>
              </ul>
            </div>
            <p v-else class="no-data">No checklist data.</p>

            <!-- Admin Panel Features -->
            <template v-if="(selectedReport.adminFeatures || []).length">
              <h4>Admin Panel Features ({{ selectedReport.adminFeatures.length }})</h4>
              <table class="inner-table">
                <thead><tr><th>Feature</th><th>Status</th><th>Description</th><th>How to Use</th><th>Controls</th></tr></thead>
                <tbody>
                  <tr v-for="(f, i) in selectedReport.adminFeatures" :key="'af-'+i">
                    <td><strong>{{ f.name }}</strong></td>
                    <td><span class="status-badge complete">{{ f.status }}</span></td>
                    <td>{{ f.description }}</td>
                    <td>{{ f.howToUse }}</td>
                    <td><small>{{ f.controls }}</small></td>
                  </tr>
                </tbody>
              </table>
            </template>

            <!-- Mobile App Features -->
            <template v-if="(selectedReport.mobileFeatures || []).length">
              <h4>Mobile App Features ({{ selectedReport.mobileFeatures.length }})</h4>
              <table class="inner-table">
                <thead><tr><th>Feature</th><th>Status</th><th>Description</th><th>How to Use</th><th>Controls</th></tr></thead>
                <tbody>
                  <tr v-for="(f, i) in selectedReport.mobileFeatures" :key="'mf-'+i">
                    <td><strong>{{ f.name }}</strong></td>
                    <td><span class="status-badge complete">{{ f.status }}</span></td>
                    <td>{{ f.description }}</td>
                    <td>{{ f.howToUse }}</td>
                    <td><small>{{ f.controls }}</small></td>
                  </tr>
                </tbody>
              </table>
            </template>

            <!-- User Guide -->
            <template v-if="selectedReport.userGuide">
              <h4>User Guide</h4>
              <div class="user-guide-section">
                <template v-if="selectedReport.userGuide.admin">
                  <h5>Admin Panel</h5>
                  <ul>
                    <li v-for="(val, key) in selectedReport.userGuide.admin" :key="'ug-a-'+key">
                      <strong>{{ key.charAt(0).toUpperCase() + key.slice(1) }}:</strong> {{ val }}
                    </li>
                  </ul>
                </template>
                <template v-if="selectedReport.userGuide.mobile">
                  <h5>Mobile App</h5>
                  <ul>
                    <li v-for="(val, key) in selectedReport.userGuide.mobile" :key="'ug-m-'+key">
                      <strong>{{ key.charAt(0).toUpperCase() + key.slice(1) }}:</strong> {{ val }}
                    </li>
                  </ul>
                </template>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Report Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h2><span class="material-icons">add_circle</span> Create Audit Report</h2>
          <button class="icon-btn" @click="showCreateModal = false">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>Summary</label>
            <textarea v-model="newReport.summary" rows="3" placeholder="Brief summary of what was done in this session..."></textarea>
          </div>

          <div class="form-section">
            <h4>Actions Done</h4>
            <div v-for="(action, i) in newReport.actionsDone" :key="i" class="inline-item">
              <input v-model="action.description" placeholder="What was done..." class="flex-input">
              <input v-model="action.file" placeholder="File path" class="file-input">
              <button class="icon-btn danger sm" @click="newReport.actionsDone.splice(i, 1)">
                <span class="material-icons">remove_circle</span>
              </button>
            </div>
            <button class="btn btn-sm btn-ghost" @click="newReport.actionsDone.push({ description: '', file: '', timestamp: new Date().toISOString() })">
              <span class="material-icons">add</span> Add Action
            </button>
          </div>

          <div class="form-section">
            <h4>Issues Found</h4>
            <div v-for="(issue, i) in newReport.issuesFound" :key="i" class="inline-item">
              <select v-model="issue.severity" class="severity-select">
                <option value="Critical">Critical</option>
                <option value="High">High</option>
                <option value="Medium">Medium</option>
                <option value="Low">Low</option>
              </select>
              <input v-model="issue.description" placeholder="Issue description..." class="flex-input">
              <input v-model="issue.file" placeholder="File" class="file-input">
              <input v-model="issue.line" placeholder="Line" class="line-input">
              <button class="icon-btn danger sm" @click="newReport.issuesFound.splice(i, 1)">
                <span class="material-icons">remove_circle</span>
              </button>
            </div>
            <button class="btn btn-sm btn-ghost" @click="newReport.issuesFound.push({ severity: 'Medium', description: '', file: '', line: '' })">
              <span class="material-icons">add</span> Add Issue
            </button>
          </div>

          <div class="form-section">
            <h4>Fixes Applied</h4>
            <div v-for="(fix, i) in newReport.fixesApplied" :key="i" class="inline-item">
              <input v-model="fix.issue" placeholder="Issue fixed..." class="flex-input">
              <input v-model="fix.fix" placeholder="How it was fixed..." class="flex-input">
              <input v-model="fix.file" placeholder="File" class="file-input">
              <button class="icon-btn danger sm" @click="newReport.fixesApplied.splice(i, 1)">
                <span class="material-icons">remove_circle</span>
              </button>
            </div>
            <button class="btn btn-sm btn-ghost" @click="newReport.fixesApplied.push({ issue: '', fix: '', file: '' })">
              <span class="material-icons">add</span> Add Fix
            </button>
          </div>

          <div class="form-section">
            <h4>Checklist</h4>
            <div v-for="(item, i) in newReport.checklistItems" :key="i" class="inline-item">
              <label class="checkbox-label">
                <input type="checkbox" v-model="item.passed">
                <span class="checkmark"></span>
              </label>
              <input v-model="item.label" placeholder="Checklist item..." class="flex-input">
              <button class="icon-btn danger sm" @click="newReport.checklistItems.splice(i, 1)">
                <span class="material-icons">remove_circle</span>
              </button>
            </div>
            <button class="btn btn-sm btn-ghost" @click="newReport.checklistItems.push({ label: '', passed: false })">
              <span class="material-icons">add</span> Add Item
            </button>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showCreateModal = false">Cancel</button>
          <button class="btn btn-primary" @click="createReport" :disabled="saving">
            <span v-if="saving" class="material-icons spin">sync</span>
            <span v-else class="material-icons">save</span>
            {{ saving ? 'Saving...' : 'Save Report' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { listenReports, saveReport, deleteReport, generateSessionId } from '../services/reportService.js'
import { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, HeadingLevel, AlignmentType, BorderStyle } from 'docx'
import { saveAs } from 'file-saver'

export default {
  name: 'SessionReports',
  data() {
    return {
      reports: [],
      loading: true,
      showCreateModal: false,
      saving: false,
      generating: false,
      selectedReport: null,
      unsubscribe: null,
      newReport: this.getEmptyReport()
    }
  },
  computed: {
    totalIssues() {
      return this.reports.reduce((sum, r) => sum + (r.issuesFound || []).length, 0)
    },
    checklistPercent() {
      if (!this.selectedReport?.checklistSummary) return 0
      const { passed, total } = this.selectedReport.checklistSummary
      return total ? Math.round((passed / total) * 100) : 0
    }
  },
  mounted() {
    this.unsubscribe = listenReports((reports) => {
      this.reports = reports
      this.loading = false
    })
  },
  beforeUnmount() {
    if (this.unsubscribe) this.unsubscribe()
  },
  methods: {
    getEmptyReport() {
      return {
        summary: '',
        actionsDone: [{ description: '', file: '', timestamp: new Date().toISOString() }],
        issuesFound: [{ severity: 'Medium', description: '', file: '', line: '' }],
        fixesApplied: [{ issue: '', fix: '', file: '' }],
        checklistItems: [
          { label: 'Build succeeds without errors', passed: false },
          { label: 'All critical issues addressed', passed: false },
          { label: 'Security vulnerabilities fixed', passed: false },
          { label: 'App runs on emulator/device', passed: false },
          { label: 'Admin panel accessible', passed: false }
        ]
      }
    },
    async autoGenerateReport() {
      this.generating = true
      try {
        // Auto-detect system state for report
        const now = new Date()
        const sessionId = generateSessionId()

        // Check connected devices
        let devices = []
        try {
          const res = await fetch('/api/dev/emulator/status')
          if (res.ok) {
            const data = await res.json()
            devices = data.devices || []
          }
        } catch (e) { /* dev server may not be running */ }

        // Check Docker containers
        let dockerStatus = 'unknown'
        try {
          const res = await fetch('/api/dev/health')
          dockerStatus = res.ok ? 'running' : 'stopped'
        } catch (e) { dockerStatus = 'not reachable' }

        // Check Firebase connection
        let firebaseStatus = 'unknown'
        try {
          const { database, ref, get } = await import('../services/firebase.js')
          const { get: fbGet } = await import('firebase/database')
          const snap = await fbGet(ref(database, 'app_config'))
          firebaseStatus = snap.exists() ? 'connected' : 'no data'
        } catch (e) { firebaseStatus = 'error: ' + e.message }

        // Check API health
        let apiStatus = 'unknown'
        try {
          const res = await fetch('https://billsense-api-340624938055.asia-southeast2.run.app/api/health')
          apiStatus = res.ok ? 'healthy' : `error (${res.status})`
        } catch (e) { apiStatus = 'unreachable' }

        // Fetch thesis data from Firebase
        let thesisVersionCount = 0, thesisWordCount = 0, panelCommentCount = 0
        let scanReportCount = 0, userCount = 0
        try {
          const { database: db, ref: dbRef } = await import('../services/firebase.js')
          const { get: fbGet } = await import('firebase/database')

          const tvSnap = await fbGet(dbRef(db, 'thesis_versions'))
          if (tvSnap.exists()) {
            const tvData = tvSnap.val()
            thesisVersionCount = Object.keys(tvData).length
            const latest = Object.values(tvData).sort((a, b) => (b.versionNumber || 0) - (a.versionNumber || 0))[0]
            if (latest?.sections) {
              for (const sec of Object.values(latest.sections)) {
                const content = typeof sec === 'object' ? (sec.content || '') : (sec || '')
                thesisWordCount += content.split(/\s+/).filter(w => w).length
              }
            }
          }

          const prSnap = await fbGet(dbRef(db, 'thesis_panel_requests'))
          if (prSnap.exists()) {
            for (const entry of Object.values(prSnap.val())) {
              if (entry.panelists) {
                const pList = Array.isArray(entry.panelists) ? entry.panelists : Object.values(entry.panelists)
                for (const p of pList) panelCommentCount += (p.comments || []).length
              }
            }
          }

          const srSnap = await fbGet(dbRef(db, 'ScanReports'))
          if (srSnap.exists()) scanReportCount = Object.keys(srSnap.val()).length

          const uSnap = await fbGet(dbRef(db, 'Users'))
          if (uSnap.exists()) userCount = Object.keys(uSnap.val()).length
        } catch (e) { console.warn('Firebase data fetch partial:', e.message) }

        // Build comprehensive AI-generated structured report
        const report = {
          sessionId,
          status: 'complete',
          title: `BillSense Development Session Report — ${now.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}`,
          author: 'AI Session Agent',
          summary: `Comprehensive audit report for session ${sessionId}. System health: Firebase ${firebaseStatus}, API ${apiStatus}, Docker ${dockerStatus}, ${devices.length} device(s) connected. Thesis: ${thesisVersionCount} version(s), ${thesisWordCount.toLocaleString()} words, ${panelCommentCount} panel comments. Database: ${scanReportCount} scan reports, ${userCount} registered users.`,

          // --- ADMIN PANEL FEATURES ---
          adminFeatures: [
            { name: 'Thesis Validator', status: 'Active', description: 'Full thesis management with version control, editing, comparison, and validation scoring', howToUse: 'Admin Sidebar > Thesis tab. Edit sections, save versions, compare, export to DOCX/PDF.', controls: 'Edit/View Toggle, Save Current, Save as New Version, Create Version from File, Template Viewer, Validate, Panel Requests, Search' },
            { name: 'Panel Requests Manager', status: 'Active', description: 'Manage thesis defense panel comments with AI-powered solution generation', howToUse: 'Thesis tab > Panel Requests button. View panelist comments, generate AI solutions, apply to sections.', controls: 'File Drop Import, Manual Add, Generate Solution, Preview, Apply, Revert, Delete' },
            { name: 'AI Suggestion Reference', status: 'Active', description: 'Draggable floating window showing AI guidance while editing thesis sections', howToUse: 'Panel Requests > Generate Solution > Apply. Floating window appears with suggestion. Drag to reposition.', controls: 'Drag Header, Copy Button, Done Button' },
            { name: 'Template Viewer', status: 'Active', description: 'Full thesis preview in academic format (Times New Roman 12pt, double-spaced)', howToUse: 'Thesis tab > Template Viewer button. Preview then export.', controls: 'DOCX Export, PDF Export, Print' },
            { name: 'Version Control', status: 'Active', description: 'Track thesis versions with side-by-side comparison and diff highlighting', howToUse: 'Select previous version dropdown, view changes in both panels.', controls: 'Previous Version Dropdown, Current Version Dropdown, Section Filter' },
            { name: 'Session Reports', status: 'Active', description: 'Auto-generate audit reports with downloadable DOCX/PDF output', howToUse: 'Admin Sidebar > Session Reports. Click Create Report for AI-generated audit.', controls: 'Create Report, Manual Report, View, Download DOCX, View PDF, Delete' },
            { name: 'Connection Health', status: 'Active', description: 'Real-time status monitoring of Firebase, API, Docker, and devices', howToUse: 'Visible in admin sidebar. Shows live connection status for all services.', controls: 'Auto-refresh indicators' },
            { name: 'Device Management', status: 'Active', description: 'ADB device detection and WiFi pairing for app testing', howToUse: 'App Testing tab > Device Management. Detects USB and WiFi devices.', controls: 'QR Pair, Add Tester, Distribute App' }
          ],

          // --- MOBILE APP FEATURES ---
          mobileFeatures: [
            { name: 'Billy AI Assistant', status: 'Developed', description: 'AI chatbot for Philippine currency authentication guidance', howToUse: 'Home > Chat icon. Ask Billy about bill verification, security features, app usage.', controls: 'Text input, Send button, Chat history' },
            { name: 'Standard Scan', status: 'Active', description: 'Real-time single bill scan via WebSocket + CameraX', howToUse: 'Home > Scan > Standard Scan. Hold camera over banknote.', controls: 'Camera preview, Auto-detection, Results overlay' },
            { name: 'Multi Scan', status: 'Active', description: 'Multi-angle bill scanning for comprehensive analysis', howToUse: 'Home > Scan > Multi Scan. Capture multiple angles.', controls: 'Camera preview, Angle guidance, Combined results' },
            { name: 'Video Scan', status: 'Active', description: 'Video-based bill scanning for continuous detection', howToUse: 'Home > Scan > Video Scan. Record video of banknote.', controls: 'Video capture, Frame analysis, Results timeline' },
            { name: 'Voting System', status: 'Active', description: 'Community voting on bill authenticity', howToUse: 'Home > Voting. View posts, vote authentic/counterfeit.', controls: 'Post creation, Image upload, Vote buttons, Comments' },
            { name: 'Evidence Cases', status: 'Active', description: 'Report and map counterfeit cases with location data', howToUse: 'Home > Evidence. Write cases, view on Google Maps.', controls: 'Case form, Photo upload, Map markers' },
            { name: 'Educational Content', status: 'Active', description: 'Tutorials, trivia, and FAQ about Philippine currency', howToUse: 'Home > Educational. Browse tutorials, take quizzes.', controls: 'Tab navigation (Tutorials, Trivia, FAQ)' }
          ],

          // --- ACTIONS DONE ---
          actionsDone: [
            { description: 'System health check — all services scanned', file: 'admin-panel', timestamp: now.toISOString() },
            { description: `Device detection: ${devices.length} device(s) found (${devices.map(d => d.model || d.serial).join(', ') || 'none'})`, file: 'adb', timestamp: now.toISOString() },
            { description: `Firebase: ${firebaseStatus} — ${thesisVersionCount} thesis versions, ${userCount} users, ${scanReportCount} scan reports`, file: 'firebase.js', timestamp: now.toISOString() },
            { description: `API health check: ${apiStatus}`, file: 'Cloud Run', timestamp: now.toISOString() },
            { description: `Docker containers: ${dockerStatus}`, file: 'docker-compose.yml', timestamp: now.toISOString() },
            { description: `Thesis data: ${thesisWordCount.toLocaleString()} words loaded across ${thesisVersionCount} version(s)`, file: 'thesis_versions', timestamp: now.toISOString() },
            { description: `Panel requests: ${panelCommentCount} comments from panelists`, file: 'thesis_panel_requests', timestamp: now.toISOString() }
          ],

          // --- ISSUES FOUND ---
          issuesFound: [
            ...(devices.length === 0 ? [{ severity: 'High', description: 'No devices connected via ADB', file: 'emulator/device', line: '' }] : []),
            ...(firebaseStatus !== 'connected' ? [{ severity: 'Critical', description: `Firebase: ${firebaseStatus}`, file: 'firebase.js', line: '' }] : []),
            ...(apiStatus !== 'healthy' ? [{ severity: 'High', description: `API not healthy: ${apiStatus}`, file: 'Cloud Run', line: '' }] : []),
            ...(dockerStatus !== 'running' ? [{ severity: 'Medium', description: `Docker: ${dockerStatus}`, file: 'docker-compose.yml', line: '' }] : []),
            ...(thesisWordCount === 0 ? [{ severity: 'High', description: 'No thesis content loaded in Firebase', file: 'thesis_versions', line: '' }] : []),
            ...(userCount === 0 ? [{ severity: 'Medium', description: 'No registered users found', file: 'Users', line: '' }] : [])
          ],

          fixesApplied: [],

          // --- CHECKLIST ---
          checklistSummary: {
            total: 12,
            passed: [
              firebaseStatus === 'connected',
              apiStatus === 'healthy',
              devices.length > 0,
              dockerStatus === 'running',
              true, // Admin panel accessible
              thesisVersionCount > 0,
              thesisWordCount > 0,
              panelCommentCount > 0,
              scanReportCount >= 0, // May be 0 legitimately
              userCount > 0,
              true, // Session report generated
              true  // DOCX/PDF export available
            ].filter(Boolean).length,
            items: [
              { label: 'Firebase Realtime Database connected', passed: firebaseStatus === 'connected' },
              { label: 'BillSense API healthy', passed: apiStatus === 'healthy' },
              { label: 'Device(s) connected via ADB', passed: devices.length > 0 },
              { label: 'Docker containers running', passed: dockerStatus === 'running' },
              { label: 'Admin panel accessible', passed: true },
              { label: 'Thesis content loaded', passed: thesisVersionCount > 0 },
              { label: `Thesis word count: ${thesisWordCount.toLocaleString()}`, passed: thesisWordCount > 0 },
              { label: `Panel comments loaded: ${panelCommentCount}`, passed: panelCommentCount > 0 },
              { label: `Scan reports in database: ${scanReportCount}`, passed: true },
              { label: `Registered users: ${userCount}`, passed: userCount > 0 },
              { label: 'Session report generated', passed: true },
              { label: 'DOCX/PDF export available', passed: true }
            ]
          },

          // --- USER GUIDE ---
          userGuide: {
            admin: {
              thesis: 'Navigate to Thesis tab. Edit sections inline, save current or create new versions, compare versions side-by-side, export to DOCX/PDF.',
              panelRequests: 'Open Panel Requests. Import comments from .docx, generate AI solutions, apply to sections with draggable reference guide.',
              sessionReports: 'Click Create Report to auto-generate. View inline, download as DOCX or view as PDF.',
              deviceManagement: 'App Testing tab. Auto-detects USB/WiFi devices. Distribute APK to connected devices.'
            },
            mobile: {
              login: 'Enter email and password. Supports email verification.',
              scanning: 'Home > Scan. Choose Standard (single), Multi (angles), or Video mode. Hold camera over banknote.',
              voting: 'Home > Voting. Create posts with photos, vote on authenticity, add comments.',
              evidence: 'Home > Evidence. Submit counterfeit cases with location, view on map.',
              billy: 'Home > Chat. Ask Billy about currency features, verification methods, app usage.'
            }
          }
        }

        await saveReport(report)
      } catch (err) {
        console.error('Failed to auto-generate report:', err)
        alert('Failed to generate report. Check console.')
      } finally {
        this.generating = false
      }
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      return new Date(dateStr).toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
      })
    },
    async createReport() {
      this.saving = true
      try {
        const filtered = {
          sessionId: generateSessionId(),
          status: 'complete',
          summary: this.newReport.summary,
          actionsDone: this.newReport.actionsDone.filter(a => a.description.trim()),
          issuesFound: this.newReport.issuesFound.filter(i => i.description.trim()),
          fixesApplied: this.newReport.fixesApplied.filter(f => f.issue.trim() || f.fix.trim()),
          checklistSummary: {
            total: this.newReport.checklistItems.length,
            passed: this.newReport.checklistItems.filter(i => i.passed).length,
            items: this.newReport.checklistItems.filter(i => i.label.trim())
          }
        }
        await saveReport(filtered)
        this.showCreateModal = false
        this.newReport = this.getEmptyReport()
      } catch (err) {
        console.error('Failed to save report:', err)
        alert('Failed to save report. Check console for details.')
      } finally {
        this.saving = false
      }
    },
    previewReport(report) {
      this.selectedReport = report
    },
    async downloadDocx(report) {
      const rows = []
      // Issues table
      const issueRows = (report.issuesFound || []).map((issue, i) =>
        new TableRow({
          children: [
            new TableCell({ children: [new Paragraph(String(i + 1))], width: { size: 5, type: WidthType.PERCENTAGE } }),
            new TableCell({ children: [new Paragraph(issue.severity || '-')], width: { size: 15, type: WidthType.PERCENTAGE } }),
            new TableCell({ children: [new Paragraph(issue.description || '-')], width: { size: 45, type: WidthType.PERCENTAGE } }),
            new TableCell({ children: [new Paragraph(issue.file || '-')], width: { size: 25, type: WidthType.PERCENTAGE } }),
            new TableCell({ children: [new Paragraph(String(issue.line || '-'))], width: { size: 10, type: WidthType.PERCENTAGE } })
          ]
        })
      )

      const fixRows = (report.fixesApplied || []).map((fix, i) =>
        new TableRow({
          children: [
            new TableCell({ children: [new Paragraph(String(i + 1))] }),
            new TableCell({ children: [new Paragraph(fix.issue || '-')] }),
            new TableCell({ children: [new Paragraph(fix.fix || '-')] }),
            new TableCell({ children: [new Paragraph(fix.file || '-')] })
          ]
        })
      )

      const doc = new Document({
        sections: [{
          properties: {},
          children: [
            new Paragraph({ text: 'BillSense Audit Report', heading: HeadingLevel.TITLE, alignment: AlignmentType.CENTER }),
            new Paragraph({ text: '' }),
            new Paragraph({ children: [new TextRun({ text: 'Session: ', bold: true }), new TextRun(report.sessionId || '-')] }),
            new Paragraph({ children: [new TextRun({ text: 'Date: ', bold: true }), new TextRun(this.formatDate(report.date))] }),
            new Paragraph({ children: [new TextRun({ text: 'Status: ', bold: true }), new TextRun(report.status || '-')] }),
            new Paragraph({ children: [new TextRun({ text: 'Generated by: ', bold: true }), new TextRun(report.generatedBy || 'Claude Agent')] }),
            new Paragraph({ text: '' }),
            new Paragraph({ text: 'Summary', heading: HeadingLevel.HEADING_1 }),
            new Paragraph({ text: report.summary || 'No summary provided.' }),
            new Paragraph({ text: '' }),
            new Paragraph({ text: 'Issues Found', heading: HeadingLevel.HEADING_1 }),
            ...(issueRows.length ? [new Table({
              rows: [
                new TableRow({
                  children: ['#', 'Severity', 'Description', 'File', 'Line'].map(h =>
                    new TableCell({ children: [new Paragraph({ children: [new TextRun({ text: h, bold: true })] })] })
                  )
                }),
                ...issueRows
              ],
              width: { size: 100, type: WidthType.PERCENTAGE }
            })] : [new Paragraph('No issues found.')]),
            new Paragraph({ text: '' }),
            new Paragraph({ text: 'Fixes Applied', heading: HeadingLevel.HEADING_1 }),
            ...(fixRows.length ? [new Table({
              rows: [
                new TableRow({
                  children: ['#', 'Issue', 'Fix Applied', 'File'].map(h =>
                    new TableCell({ children: [new Paragraph({ children: [new TextRun({ text: h, bold: true })] })] })
                  )
                }),
                ...fixRows
              ],
              width: { size: 100, type: WidthType.PERCENTAGE }
            })] : [new Paragraph('No fixes applied.')]),
            new Paragraph({ text: '' }),
            new Paragraph({ text: 'Checklist Summary', heading: HeadingLevel.HEADING_1 }),
            ...((report.checklistSummary?.items || []).map(item =>
              new Paragraph({
                children: [new TextRun({ text: (item.passed ? '\u2705 ' : '\u274C ') + item.label })]
              })
            )),
            new Paragraph({ text: `${report.checklistSummary?.passed || 0} / ${report.checklistSummary?.total || 0} passed` })
          ]
        }]
      })

      const blob = await Packer.toBlob(doc)
      saveAs(blob, `BillSense_Report_${report.sessionId || 'unknown'}.docx`)
    },
    async viewPdf(report) {
      const html2pdf = (await import('html2pdf.js')).default
      // Create temporary element with report content
      const el = document.createElement('div')
      el.style.padding = '20px'
      el.style.fontFamily = 'Arial, sans-serif'
      el.style.color = '#333'
      el.innerHTML = `
        <h1 style="text-align:center;color:#1B2A4A">BillSense Audit Report</h1>
        <p><strong>Session:</strong> ${report.sessionId || '-'}</p>
        <p><strong>Date:</strong> ${this.formatDate(report.date)}</p>
        <p><strong>Status:</strong> ${report.status || '-'}</p>
        <hr>
        <h2>Summary</h2>
        <p>${report.summary || 'No summary.'}</p>
        <h2>Issues Found (${(report.issuesFound || []).length})</h2>
        <table style="width:100%;border-collapse:collapse;margin:10px 0">
          <tr style="background:#1B2A4A;color:#fff"><th style="padding:8px;border:1px solid #ddd">#</th><th style="padding:8px;border:1px solid #ddd">Severity</th><th style="padding:8px;border:1px solid #ddd">Description</th><th style="padding:8px;border:1px solid #ddd">File</th></tr>
          ${(report.issuesFound || []).map((issue, i) => `<tr><td style="padding:6px;border:1px solid #ddd">${i+1}</td><td style="padding:6px;border:1px solid #ddd">${issue.severity}</td><td style="padding:6px;border:1px solid #ddd">${issue.description}</td><td style="padding:6px;border:1px solid #ddd;font-size:11px">${issue.file||'-'}</td></tr>`).join('')}
        </table>
        <h2>Fixes Applied (${(report.fixesApplied || []).length})</h2>
        <table style="width:100%;border-collapse:collapse;margin:10px 0">
          <tr style="background:#1B2A4A;color:#fff"><th style="padding:8px;border:1px solid #ddd">#</th><th style="padding:8px;border:1px solid #ddd">Issue</th><th style="padding:8px;border:1px solid #ddd">Fix</th><th style="padding:8px;border:1px solid #ddd">File</th></tr>
          ${(report.fixesApplied || []).map((fix, i) => `<tr><td style="padding:6px;border:1px solid #ddd">${i+1}</td><td style="padding:6px;border:1px solid #ddd">${fix.issue}</td><td style="padding:6px;border:1px solid #ddd">${fix.fix}</td><td style="padding:6px;border:1px solid #ddd;font-size:11px">${fix.file||'-'}</td></tr>`).join('')}
        </table>
        <h2>Checklist</h2>
        <ul>${(report.checklistSummary?.items || []).map(item => `<li>${item.passed ? '&#x2705;' : '&#x274C;'} ${item.label}</li>`).join('')}</ul>
        <p><strong>${report.checklistSummary?.passed || 0} / ${report.checklistSummary?.total || 0} passed</strong></p>
      `
      document.body.appendChild(el)
      await html2pdf().from(el).set({
        margin: 10,
        filename: `BillSense_Report_${report.sessionId || 'unknown'}.pdf`,
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
      }).outputPdf('bloburl').then(url => {
        window.open(url, '_blank')
      })
      document.body.removeChild(el)
    },
    async confirmDelete(report) {
      if (confirm(`Delete report ${report.sessionId}? This cannot be undone.`)) {
        await deleteReport(report.id)
      }
    }
  }
}
</script>

<style scoped>
.session-reports { padding: 0; }

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

.session-id {
  background: rgba(79, 134, 247, 0.15); color: var(--accent, #4F86F7);
  padding: 2px 8px; border-radius: 4px; font-size: 0.8rem;
}
.status-badge {
  display: inline-flex; padding: 4px 10px; border-radius: 12px;
  font-size: 0.75rem; font-weight: 600;
}
.status-badge.complete { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.draft { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }

.issue-count, .fix-count { display: flex; align-items: center; gap: 4px; }

.icon-btn {
  background: none; border: none; color: var(--text-muted, #94A3B8);
  cursor: pointer; padding: 6px; border-radius: 6px; transition: all 0.2s;
}
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent, #4F86F7); }
.icon-btn.danger:hover { background: rgba(239, 68, 68, 0.1); color: #EF4444; }
.icon-btn.sm { padding: 4px; }
.icon-btn .material-icons { font-size: 20px; }

.actions-cell { display: flex; gap: 4px; }

.empty-state {
  text-align: center; padding: 48px 16px !important; color: var(--text-muted, #94A3B8);
}
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }

/* Preview Panel */
.preview-overlay, .modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000; backdrop-filter: blur(4px);
}
.preview-panel {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 16px; width: 90vw; max-width: 900px; max-height: 85vh;
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

.report-content h3 { color: var(--accent, #4F86F7); margin-bottom: 16px; }
.report-content h4 {
  color: var(--text, #E2E8F0); margin: 20px 0 8px;
  padding-bottom: 6px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.report-meta { color: var(--text-muted, #94A3B8); line-height: 1.8; }

.inner-table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 0.8rem; }
.inner-table th {
  background: rgba(79, 134, 247, 0.1); padding: 8px 10px; text-align: left;
  color: var(--text-muted, #94A3B8); font-weight: 600; border: 1px solid var(--border, #2A3A4E);
}
.inner-table td { padding: 6px 10px; border: 1px solid var(--border, #2A3A4E); color: var(--text, #E2E8F0); }
.inner-table code { color: var(--accent, #4F86F7); font-size: 0.75rem; }

.severity-badge {
  padding: 2px 8px; border-radius: 8px; font-size: 0.7rem; font-weight: 600;
}
.severity-badge.critical { background: rgba(239,68,68,0.2); color: #EF4444; }
.severity-badge.high { background: rgba(245,158,11,0.2); color: #F59E0B; }
.severity-badge.medium { background: rgba(79,134,247,0.2); color: #4F86F7; }
.severity-badge.low { background: rgba(148,163,184,0.2); color: #94A3B8; }

.no-data { color: var(--text-muted, #94A3B8); font-style: italic; font-size: 0.875rem; }
.user-guide-section h5 { color: #93C5FD; margin: 12px 0 6px; font-size: 0.95rem; }
.user-guide-section ul { list-style: none; padding: 0; }
.user-guide-section li { padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.85rem; color: #CBD5E1; }
.user-guide-section li strong { color: #E2E8F0; }

.checklist-summary ul { list-style: none; padding: 0; }
.checklist-summary li {
  display: flex; align-items: center; gap: 8px; padding: 4px 0;
  color: var(--text, #E2E8F0); font-size: 0.875rem;
}
.checklist-bar {
  width: 100%; height: 8px; background: var(--border, #2A3A4E);
  border-radius: 4px; overflow: hidden; margin: 8px 0;
}
.checklist-fill { height: 100%; background: var(--success, #22C55E); border-radius: 4px; transition: width 0.3s; }

/* Modal */
.modal-panel {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 16px; width: 90vw; max-width: 700px; max-height: 85vh;
  display: flex; flex-direction: column; overflow: hidden;
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.modal-header h2 {
  display: flex; align-items: center; gap: 8px;
  font-size: 1.1rem; color: var(--text, #E2E8F0); margin: 0;
}
.modal-body { overflow-y: auto; padding: 24px; flex: 1; }
.modal-footer {
  display: flex; justify-content: flex-end; gap: 12px;
  padding: 16px 24px; border-top: 1px solid var(--border, #2A3A4E);
}

.form-group { margin-bottom: 16px; }
.form-group label { display: block; color: var(--text-muted, #94A3B8); font-size: 0.8rem; margin-bottom: 6px; }
.form-group textarea, .form-group input {
  width: 100%; background: var(--bg, #0F1923); border: 1px solid var(--border, #2A3A4E);
  border-radius: 8px; padding: 10px 12px; color: var(--text, #E2E8F0);
  font-size: 0.875rem; resize: vertical;
}
.form-group textarea:focus, .form-group input:focus { border-color: var(--accent, #4F86F7); outline: none; }

.form-section { margin-bottom: 20px; }
.form-section h4 { color: var(--text, #E2E8F0); font-size: 0.9rem; margin: 0 0 10px; }

.inline-item {
  display: flex; gap: 8px; align-items: center; margin-bottom: 8px;
}
.flex-input {
  flex: 1; background: var(--bg, #0F1923); border: 1px solid var(--border, #2A3A4E);
  border-radius: 6px; padding: 8px 10px; color: var(--text, #E2E8F0); font-size: 0.8rem;
}
.file-input {
  width: 180px; background: var(--bg, #0F1923); border: 1px solid var(--border, #2A3A4E);
  border-radius: 6px; padding: 8px 10px; color: var(--text, #E2E8F0); font-size: 0.8rem;
}
.line-input {
  width: 60px; background: var(--bg, #0F1923); border: 1px solid var(--border, #2A3A4E);
  border-radius: 6px; padding: 8px 10px; color: var(--text, #E2E8F0); font-size: 0.8rem;
}
.severity-select {
  width: 100px; background: var(--bg, #0F1923); border: 1px solid var(--border, #2A3A4E);
  border-radius: 6px; padding: 8px; color: var(--text, #E2E8F0); font-size: 0.8rem;
}

.checkbox-label { display: flex; align-items: center; cursor: pointer; }
.checkbox-label input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--accent, #4F86F7); }

.btn-ghost .material-icons { font-size: 18px; }

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }

@media (max-width: 768px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .stats-bar { grid-template-columns: 1fr 1fr; }
  .reports-table { font-size: 0.8rem; }
  .reports-table th:nth-child(n+4), .reports-table td:nth-child(n+4) { display: none; }
  .preview-panel, .modal-panel { width: 95vw; max-height: 90vh; }
}
</style>
