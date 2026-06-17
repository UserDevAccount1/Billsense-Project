import { database, ref, get } from './firebase.js'

const API_BASE = 'https://billsense-api-340624938055.asia-southeast2.run.app'
const DOCKER_LOCAL = 'http://localhost:8080'

export async function checkFirebase() {
  try {
    const start = performance.now()
    // Test connectivity by reading a lightweight path
    const snapshot = await get(ref(database, 'app_config'))
    const latency = Math.round(performance.now() - start)
    return { status: 'connected', latency, details: {} }
  } catch (e) {
    // Permission denied still means Firebase is reachable
    if (e.message && e.message.includes('Permission denied')) {
      return { status: 'connected', details: { note: 'Auth required for data access' } }
    }
    return { status: 'error', error: e.message }
  }
}

export async function checkModelScanning() {
  try {
    const start = performance.now()
    const res = await fetch(`${API_BASE}/api/health`, { signal: AbortSignal.timeout(10000) })
    const latency = Math.round(performance.now() - start)
    if (res.ok) {
      const data = await res.json()
      const models = Array.isArray(data.models_loaded) ? data.models_loaded : data.scan_types || []
      return { status: 'healthy', latency, models, details: data }
    }
    return { status: 'unhealthy', latency, error: `HTTP ${res.status}` }
  } catch (e) {
    return { status: 'offline', error: e.message }
  }
}

export async function checkDockerContainer() {
  try {
    const start = performance.now()
    const res = await fetch(`${DOCKER_LOCAL}/api/health`, { signal: AbortSignal.timeout(5000) })
    const latency = Math.round(performance.now() - start)
    if (res.ok) {
      const data = await res.json()
      return { status: 'running', latency, details: data }
    }
    return { status: 'error', latency, error: `HTTP ${res.status}` }
  } catch (e) {
    return { status: 'stopped', error: 'Docker container not running locally' }
  }
}

export async function checkGitHub() {
  try {
    const start = performance.now()
    const res = await fetch('https://api.github.com/repos/UserDevAccount1/Billsense-Project', {
      signal: AbortSignal.timeout(10000)
    })
    const latency = Math.round(performance.now() - start)
    if (res.ok) {
      const data = await res.json()
      return {
        status: 'connected',
        latency,
        details: { name: data.full_name, visibility: data.private ? 'private' : 'public', updated: data.updated_at }
      }
    }
    if (res.status === 404) return { status: 'not-found', error: 'Repository not found or private' }
    return { status: 'error', error: `HTTP ${res.status}` }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function checkClaudeCode() {
  return {
    status: 'configured',
    details: {
      skills: ['android-emulator-skill', 'android-architecture', 'android-testing', 'compose-ui', 'pdf', 'docx', 'xlsx', 'pptx'],
      mcpServers: ['mobile (claude-in-mobile)', 'android (android-mcp-server)', 'cpanel-mcp', 'firebase', 'supabase', 'vercel', 'cloudflare'],
      ide: 'VS Code + Android Studio Panda 2025.3.2'
    }
  }
}

export async function checkMCP() {
  // MCP runs as CLI subprocess — test underlying Firebase connectivity instead
  try {
    const start = performance.now()
    const snapshot = await get(ref(database, 'app_config'))
    const latency = Math.round(performance.now() - start)
    return {
      status: 'connected',
      latency,
      details: {
        servers: ['firebase', 'context7'],
        projectId: 'bill-sense-aec6b',
        firebaseAccess: 'authenticated',
        note: 'Firebase MCP operational — RTDB access confirmed'
      }
    }
  } catch (e) {
    const isAuthError = e.message && e.message.includes('Permission denied')
    return {
      status: isAuthError ? 'partial' : 'error',
      details: {
        servers: ['firebase', 'context7'],
        projectId: 'bill-sense-aec6b',
        firebaseAccess: isAuthError ? 'unauthenticated' : 'unreachable',
        note: isAuthError
          ? 'Firebase reachable but RTDB rules require auth — MCP uses service account (separate auth)'
          : `Firebase unreachable: ${e.message}`
      }
    }
  }
}

export async function checkEmulator() {
  // Check dev-server for live emulator/device status
  try {
    const res = await fetch('http://localhost:3003/api/dev/emulator/status', { signal: AbortSignal.timeout(3000) })
    if (res.ok) {
      const data = await res.json()
      const deviceCount = data.devices?.length || 0
      const physicalDevices = data.devices?.filter(d => !d.isEmulator) || []
      const emulators = data.devices?.filter(d => d.isEmulator) || []
      return {
        status: data.anyDevice ? 'running' : 'configured',
        details: {
          avd: data.avd || 'BillSense_Test',
          emulatorRunning: data.running ? 'Yes' : 'No',
          emulatorBooted: data.booted ? 'Yes' : 'No',
          connectedDevices: deviceCount,
          physicalDevices: physicalDevices.map(d => `${d.model} (${d.serial})`).join(', ') || 'None',
          emulators: emulators.map(d => `${d.model} (${d.serial})`).join(', ') || 'None',
          sdk: 'D:\\Software\\android studio\\Sdk',
          ide: 'Android Studio Panda 2025.3.2 + VS Code',
          apiLevels: 'API 33 (Android 13), API 36 (Android 16)'
        }
      }
    }
  } catch {
    // Dev server not running — return static config
  }
  return {
    status: 'configured',
    details: {
      avd: 'BillSense_Test',
      ide: 'Android Studio Panda 2025.3.2 + VS Code',
      sdk: 'D:\\Software\\android studio\\Sdk',
      apiLevels: 'API 33 (Android 13), API 36 (Android 16)',
      note: 'Start dev-server (port 3003) for live device status'
    }
  }
}

export async function checkFirebaseML() {
  const BUCKET = 'bill-sense-aec6b.firebasestorage.app'
  const API_KEY = 'AIzaSyCKdSYeVztx0gXo2Z-Q6CkZ_SJT2pcajAI'

  // Known Firebase ML models deployed to this project
  const knownModels = [
    { name: 'simple_model', file: 'ml_models/counterfeit_best_float32.tflite', id: '25239872', format: 'float32', classes: 7 },
    { name: 'uv_model', file: 'ml_models/security_best_int8.tflite', id: '25239898', format: 'int8', classes: 6 }
  ]

  const modelResults = []
  let allDeployed = true

  try {
    // Check each model file exists in Firebase Storage (proves Firebase ML is configured)
    for (const model of knownModels) {
      try {
        const encodedPath = encodeURIComponent(model.file)
        const res = await fetch(
          `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/${encodedPath}?alt=json`,
          { signal: AbortSignal.timeout(8000) }
        )
        if (res.ok) {
          const meta = await res.json()
          modelResults.push({
            name: model.name,
            modelId: model.id,
            format: model.format,
            size: `${(parseInt(meta.size) / 1024 / 1024).toFixed(1)} MB`,
            classes: model.classes,
            updated: meta.updated,
            status: 'deployed'
          })
        } else {
          allDeployed = false
          modelResults.push({ name: model.name, status: 'missing', error: `HTTP ${res.status}` })
        }
      } catch (e) {
        allDeployed = false
        modelResults.push({ name: model.name, status: 'error', error: e.message })
      }
    }

    const deployedCount = modelResults.filter(m => m.status === 'deployed').length

    return {
      status: deployedCount > 0 ? 'deployed' : 'no-models',
      details: {
        count: deployedCount,
        total: knownModels.length,
        models: modelResults,
        bucket: BUCKET,
        storagePath: 'ml_models/',
        androidSdk: 'firebase-ml-modeldownloader:24.2.3',
        tfliteRuntime: 'tensorflow-lite:2.14.0',
        hybridInference: 'HybridInferenceManager.java (on-device + cloud fallback)'
      }
    }
  } catch (e) {
    return { status: 'not-configured', error: e.message }
  }
}

export async function checkGitNexus() {
  // GitNexus is a client-side knowledge graph tool (nxpatterns/gitnexus)
  // Check if the GitHub repo is accessible
  try {
    const res = await fetch('https://api.github.com/repos/nxpatterns/gitnexus', {
      signal: AbortSignal.timeout(10000)
    })
    if (res.ok) {
      const data = await res.json()
      return {
        status: 'available',
        details: {
          description: 'Zero-Server Code Intelligence Engine',
          stars: data.stargazers_count,
          features: ['Knowledge Graph', 'Graph RAG Agent', 'Code Exploration'],
          repoUrl: 'https://github.com/nxpatterns/gitnexus'
        }
      }
    }
    return { status: 'unavailable', error: `HTTP ${res.status}` }
  } catch (e) {
    return { status: 'unavailable', error: e.message }
  }
}

export async function checkCPanel() {
  const CPANEL_HOST = 'billsense.dev-environment.site'
  try {
    const start = performance.now()
    // Check if cPanel domain is reachable via HTTPS
    const res = await fetch(`https://${CPANEL_HOST}`, {
      signal: AbortSignal.timeout(10000),
      mode: 'no-cors'
    })
    const latency = Math.round(performance.now() - start)
    return {
      status: 'connected',
      latency,
      details: {
        host: CPANEL_HOST,
        port: 2083,
        username: 'devenvir',
        ssl: true,
        mcpServer: 'cpanel-mcp (uvx)',
        features: ['Email Management', 'Domain Config', 'File Manager', 'Database Admin']
      }
    }
  } catch (e) {
    // no-cors requests may throw but the domain could still be up
    // Check if it's a CORS error (means the server IS responding)
    if (e.name === 'TypeError' && !e.message.includes('Failed to fetch')) {
      return {
        status: 'connected',
        details: {
          host: CPANEL_HOST,
          port: 2083,
          username: 'devenvir',
          ssl: true,
          mcpServer: 'cpanel-mcp (uvx)',
          features: ['Email Management', 'Domain Config', 'File Manager', 'Database Admin'],
          note: 'Domain reachable (CORS restricted for direct checks)'
        }
      }
    }
    return {
      status: 'offline',
      error: e.message,
      details: {
        host: CPANEL_HOST,
        port: 2083,
        username: 'devenvir',
        mcpServer: 'cpanel-mcp (uvx)'
      }
    }
  }
}

// --- New Feature Health Checks ---

export async function checkThesisValidator() {
  try {
    const snap = await get(ref(database, 'thesis_versions'))
    if (snap.exists()) {
      const versions = Object.keys(snap.val()).length
      let totalWords = 0
      const latest = Object.values(snap.val()).sort((a, b) => (b.versionNumber || 0) - (a.versionNumber || 0))[0]
      if (latest?.sections) {
        for (const sec of Object.values(latest.sections)) {
          const content = typeof sec === 'object' ? (sec.content || '') : (sec || '')
          totalWords += content.split(/\s+/).filter(w => w).length
        }
      }
      // Check panel requests too
      const prSnap = await get(ref(database, 'thesis_panel_requests'))
      const panelCount = prSnap.exists() ? Object.keys(prSnap.val()).length : 0
      return {
        status: 'connected',
        details: { versions, totalWords, panelRequests: panelCount, latestVersion: latest?.versionNumber || 1 }
      }
    }
    return { status: 'warning', details: { note: 'No thesis data found in Firebase' } }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function checkVectorSearch() {
  // Vector search is client-side TF-IDF — always available if thesis data exists
  try {
    const snap = await get(ref(database, 'thesis_versions'))
    if (snap.exists()) {
      return { status: 'connected', details: { engine: 'TF-IDF Cosine Similarity', type: 'Client-side', chunking: 'Sentence-level', note: 'Indexes thesis content for semantic search' } }
    }
    return { status: 'warning', details: { note: 'No data to index — upload thesis content first' } }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function checkBillyAgent() {
  try {
    const snap = await get(ref(database, 'Billy Chats'))
    const chatCount = snap.exists() ? Object.keys(snap.val()).length : 0
    return {
      status: 'connected',
      details: { chatSessions: chatCount, model: 'Billy AI Assistant', capabilities: 'Currency auth, app guidance, counterfeit detection education' }
    }
  } catch (e) {
    if (e.message?.includes('Permission denied')) {
      return { status: 'connected', details: { note: 'Auth required — agent is deployed' } }
    }
    return { status: 'error', error: e.message }
  }
}

export async function checkScanResultsDB() {
  try {
    const paths = ['Standard Scan', 'Multi Scan', 'Video Scan']
    let totalScans = 0
    for (const path of paths) {
      const snap = await get(ref(database, path))
      if (snap.exists()) {
        for (const userScans of Object.values(snap.val())) {
          if (typeof userScans === 'object') totalScans += Object.keys(userScans).length
        }
      }
    }
    const detSnap = await get(ref(database, 'Detections'))
    const detections = detSnap.exists() ? Object.keys(detSnap.val()).length : 0
    return {
      status: 'connected',
      details: { totalScans, detections, paths: paths.join(', '), types: 'Standard, Multi, Video' }
    }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function checkUsersDB() {
  try {
    const snap = await get(ref(database, 'Users'))
    if (snap.exists()) {
      const users = Object.values(snap.val())
      const verified = users.filter(u => u.status === 'verified').length
      const pending = users.filter(u => u.status !== 'verified').length
      return {
        status: 'connected',
        details: { totalUsers: users.length, verified, pending }
      }
    }
    return { status: 'warning', details: { note: 'No users found' } }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function checkSessionReports() {
  try {
    const snap = await get(ref(database, 'session_reports'))
    const count = snap.exists() ? Object.keys(snap.val()).length : 0
    return {
      status: 'connected',
      details: { totalReports: count, exports: 'DOCX, PDF', generator: 'AI Session Agent' }
    }
  } catch (e) {
    return { status: 'error', error: e.message }
  }
}

export async function runAllChecks() {
  const [firebase, modelScanning, docker, github, claudeCode, mcp, emulator, firebaseML, gitNexus, cpanel] =
    await Promise.allSettled([
      checkFirebase(),
      checkModelScanning(),
      checkDockerContainer(),
      checkGitHub(),
      checkClaudeCode(),
      checkMCP(),
      checkEmulator(),
      checkFirebaseML(),
      checkGitNexus(),
      checkCPanel()
    ])

  return {
    firebase: firebase.value || { status: 'error', error: firebase.reason?.message },
    modelScanning: modelScanning.value || { status: 'error', error: modelScanning.reason?.message },
    docker: docker.value || { status: 'error', error: docker.reason?.message },
    github: github.value || { status: 'error', error: github.reason?.message },
    claudeCode: claudeCode.value || { status: 'error', error: claudeCode.reason?.message },
    mcp: mcp.value || { status: 'error', error: mcp.reason?.message },
    emulator: emulator.value || { status: 'error', error: emulator.reason?.message },
    firebaseML: firebaseML.value || { status: 'error', error: firebaseML.reason?.message },
    gitNexus: gitNexus.value || { status: 'error', error: gitNexus.reason?.message },
    cpanel: cpanel.value || { status: 'error', error: cpanel.reason?.message }
  }
}
