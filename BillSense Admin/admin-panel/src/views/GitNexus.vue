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
              {{ statusLabel }}
            </span>
            <span class="nexus-repo-name">{{ repoFullName }}</span>
            <span class="rag-badge" v-if="ragEnabled">
              <span class="material-icons">smart_toy</span>
              RAG Active
            </span>
          </div>
          <div class="nexus-actions">
            <button class="nexus-btn clone-btn" @click="autoFillIframe" :disabled="fillStatus === 'filling'" title="Auto-fill repo URL + PAT inside the iframe and clone">
              <span class="material-icons">{{ fillStatus === 'done' ? 'check' : fillStatus === 'filling' ? 'hourglass_top' : 'auto_fix_high' }}</span>
              {{ fillStatus === 'done' ? 'Filled & Cloning' : fillStatus === 'filling' ? 'Filling...' : 'Auto Clone Repo' }}
            </button>
            <button class="nexus-btn" :class="ragEnabled ? 'rag-on' : 'secondary'" @click="toggleRag" :title="ragEnabled ? 'Disable RAG Agent' : 'Enable RAG Agent'">
              <span class="material-icons">{{ ragEnabled ? 'smart_toy' : 'psychology' }}</span>
              {{ ragEnabled ? 'RAG On' : 'Enable RAG' }}
            </button>
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

      <!-- AI Repo Analyzer (Gemini-powered) -->
      <div class="ai-analyzer-panel">
        <div class="ai-header">
          <span class="material-icons">auto_awesome</span>
          <strong>AI Repo Intelligence</strong>
          <span class="ai-status" :class="aiStatusClass">
            <span class="material-icons">{{ aiStatusIcon }}</span>
            {{ aiStatusLabel }}
          </span>
        </div>

        <div class="ai-input-row">
          <input
            type="text"
            class="ai-input"
            v-model="analyzeRepo"
            placeholder="owner/repo  (e.g. UserDevAccount1/Billsense-Project)"
            @keydown.enter.prevent="runAnalysis"
          />
          <button
            class="nexus-btn primary"
            @click="runAnalysis"
            :disabled="aiBusy || !aiKeyPresent"
            :title="aiKeyPresent ? 'Analyze repo with Gemini' : 'VITE_GEMINI_API_KEY not configured'"
          >
            <span class="material-icons" :class="{ spinning: aiBusy }">
              {{ aiBusy ? 'hourglass_top' : 'psychology' }}
            </span>
            {{ aiBusy ? 'Analyzing…' : 'Analyze with AI' }}
          </button>
        </div>

        <div v-if="aiAnalysis" class="ai-output">
          <div class="ai-meta">
            <span class="ai-model-tag" :class="{ fallback: aiAnalysis.fallback }">
              {{ aiAnalysis.version || aiAnalysis.model }}
            </span>
            <span class="ai-latency">{{ aiAnalysis.latencyMs }}ms</span>
            <span v-if="aiAnalysis.repoMeta" class="ai-repometa">
              ⭐ {{ aiAnalysis.repoMeta.stargazers_count }} ·
              {{ aiAnalysis.repoMeta.language || 'mixed' }} ·
              {{ aiAnalysis.repoMeta.open_issues_count }} open issues
            </span>
          </div>
          <div class="ai-text" v-html="renderAi(aiAnalysis.text)"></div>
        </div>

        <div v-if="aiError" class="ai-error">
          <span class="material-icons">error</span>
          <span>{{ aiError }}</span>
        </div>
      </div>

      <!-- Auto-Fill Config Panel -->
      <div class="autofill-panel">
        <div class="autofill-header">
          <span class="material-icons">auto_fix_high</span>
          <strong>Auto-Fill Configuration</strong>
          <span class="autofill-hint">Click "Auto Clone Repo" to populate these in the iframe below</span>
        </div>
        <div class="autofill-fields">
          <div class="autofill-field">
            <div class="field-label">
              <span class="material-icons">link</span>
              <span>GitHub URL</span>
            </div>
            <div class="field-value-row">
              <input type="text" class="field-input" :value="githubRepoUrl" readonly />
              <button class="copy-btn" @click="copyToClipboard(githubRepoUrl, 'url')" :title="copied === 'url' ? 'Copied!' : 'Copy URL'">
                <span class="material-icons">{{ copied === 'url' ? 'check' : 'content_copy' }}</span>
              </button>
            </div>
            <span class="field-status connected">
              <span class="material-icons">check_circle</span> Pre-configured
            </span>
          </div>

          <div class="autofill-field">
            <div class="field-label">
              <span class="material-icons">key</span>
              <span>GitHub PAT</span>
            </div>
            <div class="field-value-row">
              <input :type="showPat ? 'text' : 'password'" class="field-input" :value="githubPat" readonly />
              <button class="copy-btn" @click="showPat = !showPat" title="Toggle visibility">
                <span class="material-icons">{{ showPat ? 'visibility_off' : 'visibility' }}</span>
              </button>
              <button class="copy-btn" @click="copyToClipboard(githubPat, 'pat')" :title="copied === 'pat' ? 'Copied!' : 'Copy PAT'">
                <span class="material-icons">{{ copied === 'pat' ? 'check' : 'content_copy' }}</span>
              </button>
            </div>
            <span class="field-status" :class="githubPat ? 'connected' : 'idle'">
              <span class="material-icons">{{ githubPat ? 'check_circle' : 'warning' }}</span>
              {{ githubPat ? 'Configured — private repo access enabled' : 'Not set — public repos only' }}
            </span>
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

      <!-- Fill Status Toast -->
      <div class="fill-toast" v-if="fillMessage" :class="fillToastClass">
        <span class="material-icons">{{ fillToastIcon }}</span>
        {{ fillMessage }}
      </div>

      <!-- Agent Live View (replaces iframe when agent is active) -->
      <div class="agent-screenshot" v-if="agentScreenshot">
        <div class="screenshot-header">
          <span class="material-icons">smart_toy</span>
          <strong>Agent Live View</strong>
          <span class="screenshot-hint" v-if="agentStatus === 'parsing'">Cloning & parsing in progress...</span>
          <span class="screenshot-hint" v-else-if="agentStatus === 'complete'">Knowledge graph ready!</span>
          <span class="screenshot-hint" v-else>AI agent session active</span>
          <div style="margin-left:auto; display:flex; gap:6px">
            <button class="nexus-btn primary" @click="refreshLiveView" :disabled="liveRefreshing" title="Refresh screenshot">
              <span class="material-icons" :class="{ spinning: liveRefreshing }">refresh</span>
              {{ liveRefreshing ? 'Refreshing...' : 'Refresh' }}
            </button>
            <button class="nexus-btn secondary" @click="agentScreenshot = null; stopLiveRefresh()" title="Switch back to iframe">
              <span class="material-icons">close</span> Show Iframe
            </button>
          </div>
        </div>
        <img :src="agentScreenshot" alt="GitNexus Agent Live View" class="screenshot-img" />
      </div>

      <!-- Embedded GitNexus Web UI (shown when agent is not active) -->
      <div class="nexus-iframe-wrapper" v-show="!agentScreenshot">
        <div class="iframe-loading-overlay" v-if="iframeLoading">
          <span class="material-icons spinning">hourglass_empty</span>
          <span>Loading GitNexus for <strong>{{ repoFullName }}</strong>...</span>
        </div>
        <div class="iframe-error-overlay" v-if="iframeError">
          <span class="material-icons">cloud_off</span>
          <div class="error-msg">
            <strong>GitNexus UI could not be loaded</strong>
            <p>Try clicking "Reload" or use "Open Full UI" to access it directly.</p>
          </div>
          <button class="nexus-btn primary" @click="openExternal">
            <span class="material-icons">open_in_new</span>
            Open GitNexus Directly
          </button>
        </div>
        <iframe
          v-show="!iframeError"
          ref="nexusFrame"
          :src="currentUrl"
          title="GitNexus Knowledge Graph"
          allow="clipboard-read; clipboard-write"
          @load="onIframeLoad"
          @error="onIframeError"
        ></iframe>
      </div>

      <!-- Quick Actions -->
      <div class="quick-actions">
        <h3>Quick Actions</h3>
        <div class="action-grid">
          <div class="action-card" @click="autoFillIframe">
            <span class="material-icons">auto_fix_high</span>
            <div>
              <strong>Auto Clone & Visualize</strong>
              <span>Fill repo URL + PAT in iframe and clone</span>
            </div>
          </div>
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
          <div class="action-card" :class="{ active: ragEnabled }" @click="toggleRag">
            <span class="material-icons">smart_toy</span>
            <div>
              <strong>Graph RAG Agent</strong>
              <span>{{ ragEnabled ? 'RAG Active — OpenAI connected' : 'Click to enable RAG agent' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { chat, hasGeminiKey } from '../services/gemini.js'

const REPO = import.meta.env.VITE_GITNEXUS_REPO || 'UserDevAccount1/Billsense-Project'
const OPENAI_KEY = import.meta.env.VITE_OPENAI_API_KEY || ''
const GITHUB_PAT = import.meta.env.VITE_GITHUB_PAT || ''
// Use same-origin proxy so we can access the iframe DOM directly
const NEXUS_BASE = '/gitnexus-proxy'

export default {
  name: 'GitNexus',
  data() {
    return {
      repoFullName: REPO,
      githubRepoUrl: `https://github.com/${REPO}`,
      githubPat: GITHUB_PAT,
      openaiKey: OPENAI_KEY,
      iframeLoading: true,
      iframeError: false,
      repoStatus: 'loading',
      currentUrl: '',
      copied: null,
      showPat: false,
      fillStatus: 'idle',
      fillMessage: '',
      fillToastClass: '',
      fillToastIcon: '',
      agentScreenshot: null,
      agentStatus: '',
      liveRefreshing: false,
      liveRefreshInterval: null,
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
      ],
      ragEnabled: !!OPENAI_KEY,
      // AI Repo Analyzer state
      analyzeRepo: REPO,
      aiBusy: false,
      aiAnalysis: null,
      aiError: '',
      aiKeyPresent: hasGeminiKey()
    }
  },
  computed: {
    statusLabel() {
      const labels = { loading: 'Loading', connected: 'Connected', unavailable: 'Unavailable' }
      return labels[this.repoStatus] || this.repoStatus
    },
    aiStatusClass() {
      if (!this.aiKeyPresent) return 'err'
      if (this.aiAnalysis?.fallback) return 'warn'
      if (this.aiAnalysis) return 'ok'
      return 'idle'
    },
    aiStatusIcon() {
      if (!this.aiKeyPresent) return 'error'
      if (this.aiAnalysis?.fallback) return 'warning'
      if (this.aiAnalysis) return 'check_circle'
      return 'auto_awesome'
    },
    aiStatusLabel() {
      if (!this.aiKeyPresent) return 'No Gemini key'
      if (this.aiAnalysis) {
        const tag = this.aiAnalysis.fallback ? ' · fallback' : ''
        return `Connected · ${this.aiAnalysis.version || this.aiAnalysis.model}${tag}`
      }
      return 'Ready'
    }
  },
  methods: {
    async runAnalysis() {
      const repo = (this.analyzeRepo || '').trim()
      if (!repo || this.aiBusy) return
      if (!/^[^/\s]+\/[^/\s]+$/.test(repo)) {
        this.aiError = 'Use the "owner/repo" format, e.g. "facebook/react".'
        return
      }
      this.aiBusy = true
      this.aiError = ''
      this.aiAnalysis = null
      try {
        // 1) Fetch repo metadata from GitHub API
        const headers = { 'Accept': 'application/vnd.github+json' }
        if (this.githubPat) headers.Authorization = `Bearer ${this.githubPat}`
        const ghRes = await fetch(`https://api.github.com/repos/${repo}`, { headers })
        if (!ghRes.ok) throw new Error(`GitHub: HTTP ${ghRes.status} — repo not found or rate-limited`)
        const meta = await ghRes.json()

        // 2) Build a compact context payload for Gemini
        const ctx = {
          name: meta.full_name,
          description: meta.description,
          language: meta.language,
          languages_url: meta.languages_url,
          stars: meta.stargazers_count,
          forks: meta.forks_count,
          open_issues: meta.open_issues_count,
          size_kb: meta.size,
          topics: meta.topics || [],
          created_at: meta.created_at,
          updated_at: meta.updated_at,
          pushed_at: meta.pushed_at,
          default_branch: meta.default_branch,
          license: meta.license?.spdx_id,
          archived: meta.archived,
          homepage: meta.homepage
        }

        // 3) Pull README excerpt (best-effort, ignore errors)
        let readme = ''
        try {
          const rRes = await fetch(`https://api.github.com/repos/${repo}/readme`, {
            headers: { ...headers, Accept: 'application/vnd.github.raw' }
          })
          if (rRes.ok) readme = (await rRes.text()).slice(0, 4000)
        } catch (_) { /* ignore */ }

        // 4) Ask Gemini
        const systemPrompt = `You analyze GitHub repositories for engineers evaluating whether to use, fork, or contribute. Always produce these sections, in this order, using markdown headers:

## TL;DR
One paragraph, max 3 sentences.

## Stack
Bullet list of primary languages, frameworks, and notable tools.

## Health
Activity (recent commits? archived?), license, open-issue load, maintainer responsiveness signals.

## Risks
What could go wrong if someone adopts this — staleness, license traps, single-maintainer risk, security flags.

## Suggested next step
One concrete action — "clone for evaluation", "skip — better alternatives exist", "use as reference only", etc.

Be honest. If the data is sparse, say so. Never invent commit counts or specific issues.`

        const userMessage = `Repo metadata (JSON):\n\`\`\`json\n${JSON.stringify(ctx, null, 2)}\n\`\`\`\n\nREADME excerpt:\n\`\`\`\n${readme || '(no README found)'}\n\`\`\``

        const res = await chat({
          systemPrompt,
          userMessage,
          generationConfig: { temperature: 0.4, maxOutputTokens: 1200 }
        })
        this.aiAnalysis = { ...res, repoMeta: meta }
      } catch (e) {
        this.aiError = e.attempts
          ? `All Gemini tiers failed: ${e.attempts.map(a => a.model + ' (' + (a.status || 'err') + ')').join(', ')}`
          : e.message
      } finally {
        this.aiBusy = false
      }
    },

    renderAi(text) {
      // Tiny markdown — headings, bold, italic, code, lists, line breaks.
      const esc = s => s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))
      let out = esc(text)
      out = out.replace(/`([^`\n]+)`/g, '<code>$1</code>')
      out = out.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
      out = out.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      out = out.replace(/^##\s+(.+)$/gm, '<h4>$1</h4>')
      out = out.replace(/^#\s+(.+)$/gm, '<h3>$1</h3>')
      out = out.replace(/^- (.+)$/gm, '<li>$1</li>')
      out = out.replace(/(<li>.*<\/li>\n?)+/g, m => '<ul>' + m + '</ul>')
      out = out.replace(/\n{2,}/g, '</p><p>')
      out = '<p>' + out + '</p>'
      out = out.replace(/<p>(<h\d>)/g, '$1').replace(/(<\/h\d>)<\/p>/g, '$1')
      out = out.replace(/<p>(<ul>)/g, '$1').replace(/(<\/ul>)<\/p>/g, '$1')
      out = out.replace(/\n/g, '<br>')
      return out
    },

    buildNexusUrl() {
      // GitNexus supports ?repo= param for auto-loading
      let url = `${NEXUS_BASE}/?repo=https://github.com/${this.repoFullName}`
      if (GITHUB_PAT) url += '&token=' + encodeURIComponent(GITHUB_PAT)
      if (this.ragEnabled && OPENAI_KEY) url += '&openai_key=' + encodeURIComponent(OPENAI_KEY)
      return url
    },
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
      window.open(this.buildNexusUrl(), '_blank', 'noopener')
    },
    reloadIframe() {
      this.iframeLoading = true
      this.iframeError = false
      this.repoStatus = 'loading'
      this.fillStatus = 'idle'
      // Force reload by appending timestamp
      this.currentUrl = this.buildNexusUrl() + '&_t=' + Date.now()
    },
    toggleRag() {
      if (!OPENAI_KEY) {
        alert('OpenAI API key not configured. Set VITE_OPENAI_API_KEY in .env')
        return
      }
      this.ragEnabled = !this.ragEnabled
      this.reloadIframe()
    },
    copyToClipboard(text, field) {
      navigator.clipboard.writeText(text).then(() => {
        this.copied = field
        setTimeout(() => { this.copied = null }, 2000)
      }).catch(() => {
        const ta = document.createElement('textarea')
        ta.value = text
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        this.copied = field
        setTimeout(() => { this.copied = null }, 2000)
      })
    },

    showToast(msg, type) {
      this.fillMessage = msg
      this.fillToastClass = type
      this.fillToastIcon = type === 'success' ? 'check_circle' : type === 'error' ? 'error' : 'info'
      setTimeout(() => { this.fillMessage = '' }, 4000)
    },

    /**
     * Auto Clone Repo: Directly fills the same-origin iframe's DOM inputs
     * and clicks "Clone Repository". Works because GitNexus is proxied
     * through the same origin via Vite proxy (dev) or Nginx proxy (Docker).
     */
    async autoFillIframe() {
      this.fillStatus = 'filling'
      this.showToast('Auto-filling repo URL and PAT in the iframe...', 'info')

      const iframe = this.$refs.nexusFrame
      if (!iframe) {
        this.showToast('Iframe not found', 'error')
        this.fillStatus = 'idle'
        return
      }

      try {
        const doc = iframe.contentDocument || iframe.contentWindow.document

        // Step 1: Click the "GitHub URL" tab if not already active
        const allButtons = doc.querySelectorAll('button, [role="tab"]')
        for (const btn of allButtons) {
          const text = (btn.textContent || '').toLowerCase()
          if (text.includes('github') && text.includes('url')) {
            btn.click()
            await new Promise(r => setTimeout(r, 600))
            break
          }
        }

        // Step 2: Find input fields and fill using execCommand (simulates real typing)
        const inputs = doc.querySelectorAll('input')
        const visibleInputs = Array.from(inputs).filter(i =>
          i.type !== 'hidden' && i.type !== 'checkbox' && i.type !== 'radio' && i.offsetParent !== null
        )

        let repoFilled = false
        let patFilled = false

        // execCommand('insertText') triggers React state updates properly
        const realTypeFill = (input, value) => {
          input.focus()
          input.select()
          doc.execCommand('selectAll', false, null)
          doc.execCommand('delete', false, null)
          doc.execCommand('insertText', false, value)
        }

        // Try to match by placeholder first
        for (const input of visibleInputs) {
          const ph = (input.placeholder || '').toLowerCase()
          if (!repoFilled && (ph.includes('repo') || ph.includes('url') || ph.includes('owner') || ph.includes('github'))) {
            realTypeFill(input, `https://github.com/${this.repoFullName}`)
            repoFilled = true
          }
          if (!patFilled && GITHUB_PAT && (ph.includes('pat') || ph.includes('token') || ph.includes('private') || ph.includes('access'))) {
            realTypeFill(input, GITHUB_PAT)
            patFilled = true
          }
        }

        // Fallback: fill by position
        if (!repoFilled && visibleInputs.length >= 1) {
          realTypeFill(visibleInputs[0], `https://github.com/${this.repoFullName}`)
          repoFilled = true
        }
        if (!patFilled && GITHUB_PAT && visibleInputs.length >= 2) {
          realTypeFill(visibleInputs[1], GITHUB_PAT)
          patFilled = true
        }

        this.showToast(`Filled: repo=${repoFilled}, PAT=${patFilled}. Clicking Clone...`, 'info')

        // Step 3: Click "Clone Repository" button
        await new Promise(r => setTimeout(r, 400))
        const buttons = doc.querySelectorAll('button')
        let cloneClicked = false
        for (const btn of buttons) {
          const text = (btn.textContent || '').toLowerCase()
          if (text.includes('clone') && !btn.disabled) {
            btn.click()
            cloneClicked = true
            break
          }
        }

        if (cloneClicked) {
          this.fillStatus = 'done'
          this.showToast('Clone started! The knowledge graph will appear in the iframe below.', 'success')
        } else {
          this.fillStatus = 'done'
          this.showToast(`Fields filled (repo=${repoFilled}, PAT=${patFilled}). Clone button not found — it may need manual click.`, 'info')
        }

        setTimeout(() => { this.fillStatus = 'idle' }, 4000)

      } catch (error) {
        // Cross-origin fallback: if proxy isn't working, try the Puppeteer agent
        if (error.name === 'SecurityError' || error.message.includes('cross-origin') || error.message.includes('Blocked')) {
          console.warn('[GitNexus] Same-origin proxy not available, falling back to Puppeteer agent...')
          this.showToast('Proxy not available — falling back to AI Agent...', 'info')
          await this.autoFillViaAgent()
        } else {
          this.fillStatus = 'error'
          this.showToast(`Auto-fill error: ${error.message}`, 'error')
          setTimeout(() => { this.fillStatus = 'idle' }, 3000)
        }
      }
    },

    /**
     * Fallback: Uses the Puppeteer Agent microservice when same-origin proxy isn't available.
     */
    async autoFillViaAgent() {
      const agentUrl = this.getAgentUrl()
      try {
        const response = await fetch(`${agentUrl}/api/auto-clone`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            repo: `https://github.com/${this.repoFullName}`,
            pat: GITHUB_PAT || undefined,
            openaiKey: this.ragEnabled ? OPENAI_KEY : undefined
          })
        })
        const result = await response.json()
        if (result.success) {
          this.fillStatus = 'done'
          this.agentStatus = result.status
          this.showToast(`Agent done: ${result.message}`, 'success')
          if (result.screenshot) this.agentScreenshot = result.screenshot
          if (result.status !== 'complete') this.startLiveRefresh()
        } else {
          this.fillStatus = 'error'
          this.showToast(`Agent error: ${result.error}`, 'error')
        }
        setTimeout(() => { this.fillStatus = 'idle' }, 3000)
      } catch (err) {
        this.fillStatus = 'error'
        this.showToast('Neither proxy nor agent available. Start Docker containers.', 'error')
        setTimeout(() => { this.fillStatus = 'idle' }, 3000)
      }
    },

    getAgentUrl() {
      return window.location.hostname === 'localhost'
        ? 'http://localhost:3002'
        : `http://${window.location.hostname}:3002`
    },

    async refreshLiveView() {
      this.liveRefreshing = true
      try {
        const response = await fetch(`${this.getAgentUrl()}/api/live-view`)
        if (response.ok) {
          const result = await response.json()
          this.agentScreenshot = result.screenshot
          this.agentStatus = result.status
          // Stop auto-refresh once complete
          if (result.status === 'complete') {
            this.stopLiveRefresh()
          }
        }
      } catch (e) {
        // Agent might have closed the page
      }
      this.liveRefreshing = false
    },

    startLiveRefresh() {
      this.stopLiveRefresh()
      // Refresh every 5 seconds while parsing
      this.liveRefreshInterval = setInterval(() => {
        this.refreshLiveView()
      }, 5000)
    },

    stopLiveRefresh() {
      if (this.liveRefreshInterval) {
        clearInterval(this.liveRefreshInterval)
        this.liveRefreshInterval = null
      }
    }
  },
  beforeUnmount() {
    this.stopLiveRefresh()
  },
  mounted() {
    // Load with repo URL param — GitNexus auto-loads from ?repo=
    this.currentUrl = this.buildNexusUrl()

    // Check GitHub repo availability
    fetch(`https://api.github.com/repos/${this.repoFullName}`, { signal: AbortSignal.timeout(10000) })
      .then(res => {
        if (!res.ok) this.repoStatus = 'unavailable'
      })
      .catch(() => {
        this.repoStatus = 'unavailable'
      })

    // Fallback timeout
    setTimeout(() => {
      if (this.iframeLoading) {
        this.iframeLoading = false
        this.iframeError = true
        this.repoStatus = 'unavailable'
      }
    }, 20000)
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

.rag-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(16, 185, 129, 0.15);
  color: #10B981;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.rag-badge .material-icons { font-size: 14px; }

.nexus-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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

.nexus-btn .material-icons { font-size: 16px; }

.nexus-btn.primary { background: var(--accent); color: #fff; }
.nexus-btn.primary:hover { background: var(--accent-glow); }
.nexus-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; }

.nexus-btn.secondary {
  background: var(--primary-light);
  color: var(--text);
  border: 1px solid var(--border);
}
.nexus-btn.secondary:hover { background: var(--bg-card-hover); }

.nexus-btn.clone-btn {
  background: linear-gradient(135deg, #8B5CF6, #6D28D9);
  color: #fff;
  border: none;
}
.nexus-btn.clone-btn:hover {
  background: linear-gradient(135deg, #7C3AED, #5B21B6);
  box-shadow: 0 0 12px rgba(139, 92, 246, 0.4);
}
.nexus-btn.clone-btn:disabled {
  opacity: 0.7;
  cursor: wait;
}

.nexus-btn.rag-on {
  background: rgba(16, 185, 129, 0.15);
  color: #10B981;
  border: 1px solid rgba(16, 185, 129, 0.3);
}
.nexus-btn.rag-on:hover { background: rgba(16, 185, 129, 0.25); }

/* Feature chips */
.nexus-features { display: flex; flex-wrap: wrap; gap: 8px; }

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
.feature-chip .material-icons { font-size: 14px; color: var(--accent); }

/* Auto-Fill Panel */
.autofill-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}

.autofill-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.autofill-header .material-icons { font-size: 22px; color: #8B5CF6; }
.autofill-header strong { font-size: 15px; }
.autofill-hint { font-size: 12px; color: var(--text-muted); margin-left: auto; }

.autofill-fields { display: flex; flex-direction: column; gap: 14px; }

.autofill-field {
  display: grid;
  grid-template-columns: 160px 1fr auto;
  align-items: center;
  gap: 12px;
}

.field-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.field-label .material-icons { font-size: 18px; color: var(--accent); }

.field-value-row { display: flex; align-items: center; gap: 6px; }

.field-input {
  flex: 1;
  padding: 8px 12px;
  background: var(--primary-light);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text);
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  outline: none;
}
.field-input:focus { border-color: var(--accent); }

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  background: var(--primary-light);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.copy-btn:hover { background: var(--bg-card-hover); color: var(--accent); border-color: var(--accent); }
.copy-btn .material-icons { font-size: 16px; }

.field-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.field-status .material-icons { font-size: 14px; }
.field-status.connected { color: #10B981; }
.field-status.idle { color: var(--text-muted); }

/* Fill toast */
.fill-toast {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 13px;
  font-weight: 500;
  animation: slideIn 0.3s ease;
}
.fill-toast .material-icons { font-size: 18px; }
.fill-toast.success { background: rgba(16, 185, 129, 0.15); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); }
.fill-toast.error { background: rgba(239, 68, 68, 0.15); color: #EF4444; border: 1px solid rgba(239, 68, 68, 0.3); }
.fill-toast.info { background: rgba(59, 130, 246, 0.15); color: #3B82F6; border: 1px solid rgba(59, 130, 246, 0.3); }

@keyframes slideIn {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
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

.mcp-info { display: flex; align-items: center; gap: 12px; }
.mcp-icon { font-size: 24px; color: #8B5CF6; }
.mcp-info strong { display: block; font-size: 14px; margin-bottom: 2px; }
.mcp-detail { display: block; font-size: 12px; color: var(--text-muted); }
.mcp-tools { display: flex; flex-wrap: wrap; gap: 6px; }

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

/* Agent screenshot */
.agent-screenshot {
  background: var(--bg-card);
  border: 1px solid rgba(139, 92, 246, 0.4);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}

.screenshot-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.screenshot-header .material-icons {
  font-size: 20px;
  color: #8B5CF6;
}

.screenshot-header strong {
  font-size: 14px;
}

.screenshot-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.screenshot-img {
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--border);
}

/* Iframe wrapper */
.nexus-iframe-wrapper {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  height: 900px;
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

.iframe-loading-overlay .material-icons { font-size: 32px; color: var(--accent); }
.iframe-error-overlay .material-icons { font-size: 48px; color: var(--text-muted); }

.error-msg { text-align: center; }
.error-msg strong { display: block; font-size: 16px; color: var(--text); margin-bottom: 6px; }
.error-msg p { font-size: 13px; color: var(--text-muted); max-width: 400px; }

/* Quick actions */
.quick-actions h3 { font-size: 16px; font-weight: 600; margin-bottom: 12px; }

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
.action-card:hover { background: var(--bg-card-hover); transform: translateY(-1px); }
.action-card.active { border-color: #10B981; background: rgba(16, 185, 129, 0.08); }
.action-card.active .material-icons { color: #10B981; }
.action-card .material-icons { font-size: 24px; color: var(--accent); flex-shrink: 0; margin-top: 2px; }
.action-card strong { display: block; font-size: 13px; margin-bottom: 2px; }
.action-card span { font-size: 12px; color: var(--text-muted); }

/* Status colors */
.status-badge.loading { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }
.status-badge.unavailable { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.spinning { animation: spin 1s linear infinite; }

/* Responsive */
@media (max-width: 1200px) {
  .autofill-field { grid-template-columns: 140px 1fr auto; }
}

@media (max-width: 1024px) {
  .action-grid { grid-template-columns: repeat(2, 1fr); }
  .autofill-field { grid-template-columns: 1fr; gap: 6px; }
  .autofill-hint { display: none; }
}

@media (max-width: 768px) {
  .nexus-iframe-wrapper { height: 650px; }
  .mcp-bar { flex-direction: column; align-items: flex-start; }
  .autofill-header { flex-wrap: wrap; }
}

@media (max-width: 576px) {
  .action-grid { grid-template-columns: 1fr; }
  .nexus-iframe-wrapper { height: 500px; }
  .nexus-status-row { flex-direction: column; align-items: flex-start; }
  .nexus-actions { flex-wrap: wrap; }
}

/* AI Repo Analyzer */
.ai-analyzer-panel {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(99,102,241,0.18);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.ai-header {
  display: flex; align-items: center; gap: 0.5rem;
  margin-bottom: 0.75rem;
}
.ai-header .material-icons { color: #A5B4FC; }
.ai-header strong { flex: 1; }
.ai-status {
  display: flex; align-items: center; gap: 0.35rem;
  padding: 0.25rem 0.7rem; border-radius: 999px;
  font-size: 0.78rem;
}
.ai-status .material-icons { font-size: 0.95rem; }
.ai-status.idle { background: rgba(255,255,255,0.05); color: #94A3B8; }
.ai-status.ok   { background: rgba(34,197,94,0.12);  color: #4ADE80; }
.ai-status.warn { background: rgba(251,191,36,0.12); color: #FBBF24; }
.ai-status.err  { background: rgba(248,113,113,0.12); color: #F87171; }

.ai-input-row {
  display: flex; gap: 0.5rem; align-items: stretch;
}
.ai-input {
  flex: 1;
  background: rgba(0,0,0,0.25);
  border: 1px solid rgba(255,255,255,0.08);
  color: inherit;
  padding: 0.55rem 0.85rem;
  border-radius: 8px;
  font-size: 0.9rem;
  outline: none;
}
.ai-input:focus { border-color: rgba(99,102,241,0.5); }

.ai-output {
  margin-top: 0.85rem;
  padding: 0.85rem 1rem;
  background: rgba(0,0,0,0.2);
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.06);
}
.ai-meta {
  display: flex; gap: 0.5rem; align-items: center;
  font-size: 0.75rem; color: #94A3B8;
  margin-bottom: 0.6rem; flex-wrap: wrap;
}
.ai-model-tag {
  padding: 0.1rem 0.5rem; border-radius: 999px;
  background: rgba(99,102,241,0.18); color: #A5B4FC;
}
.ai-model-tag.fallback { background: rgba(251,191,36,0.15); color: #FBBF24; }
.ai-latency { opacity: 0.7; }
.ai-repometa { margin-left: auto; opacity: 0.8; }

.ai-text {
  font-size: 0.92rem;
  line-height: 1.6;
  color: #E2E8F0;
}
.ai-text :deep(h3), .ai-text :deep(h4) {
  margin: 0.9rem 0 0.35rem; font-size: 1rem;
  color: #FFA31A;
}
.ai-text :deep(ul) { margin: 0.25rem 0 0.5rem 1.2rem; padding: 0; }
.ai-text :deep(li) { margin: 0.15rem 0; }
.ai-text :deep(code) {
  background: rgba(0,0,0,0.3); padding: 0.1rem 0.4rem; border-radius: 4px; font-size: 0.85em;
}
.ai-text :deep(p) { margin: 0.5rem 0; }

.ai-error {
  margin-top: 0.75rem;
  display: flex; gap: 0.5rem; align-items: center;
  padding: 0.65rem 0.85rem;
  background: rgba(248,113,113,0.08);
  border: 1px solid rgba(248,113,113,0.25);
  border-radius: 8px;
  color: #F87171; font-size: 0.85rem;
}
.ai-error .material-icons { font-size: 1rem; }

.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
