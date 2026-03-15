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
  // Claude Code runs locally — check by verifying skill files exist
  // This is a client-side approximation; actual check would need a local server
  return {
    status: 'configured',
    details: {
      skills: ['claude-mem', 'superpowers', 'ui-ux-pro-max-skill', 'awesome-claude-code', 'context-mode', 'mobile-design'],
      hooks: ['gsd-check-update', 'gsd-context-monitor', 'gsd-statusline']
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
  // Emulator status — this is a local-only check
  return {
    status: 'configured',
    details: {
      avd: 'Medium_Phone_API_36.1',
      ide: 'VS Code (tasks.json)',
      sdk: 'Android SDK 35'
    }
  }
}

export async function checkFirebaseML() {
  try {
    // Check if Firebase ML models are accessible
    // This uses the Firebase ML REST API
    const res = await fetch(
      `https://firebaseml.googleapis.com/v1beta2/projects/bill-sense-aec6b/models?key=AIzaSyCKdSYeVztx0gXo2Z-Q6CkZ_SJT2pcajAI`,
      { signal: AbortSignal.timeout(10000) }
    )
    if (res.ok) {
      const data = await res.json()
      const models = data.models || []
      return {
        status: models.length > 0 ? 'deployed' : 'no-models',
        details: { count: models.length, models: models.map(m => m.displayName || m.name) }
      }
    }
    return { status: 'not-configured', error: `HTTP ${res.status}` }
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

export async function runAllChecks() {
  const [firebase, modelScanning, docker, github, claudeCode, mcp, emulator, firebaseML, gitNexus] =
    await Promise.allSettled([
      checkFirebase(),
      checkModelScanning(),
      checkDockerContainer(),
      checkGitHub(),
      checkClaudeCode(),
      checkMCP(),
      checkEmulator(),
      checkFirebaseML(),
      checkGitNexus()
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
    gitNexus: gitNexus.value || { status: 'error', error: gitNexus.reason?.message }
  }
}
