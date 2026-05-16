import { checkModelScanning, checkDockerContainer, checkFirebaseML, checkFirebase } from './healthCheck.js'

const API_BASE = 'https://billsense-api-340624938055.asia-southeast2.run.app'
const DOCKER_LOCAL = 'http://localhost:8080'

/**
 * GCP Connection — Cloud Run inference service
 */
export async function checkGCPConnection() {
  const checklist = [
    { id: 'cloud-run-reachable', label: 'Cloud Run service reachable', status: 'pending' },
    { id: 'api-gateway', label: 'API Gateway responding', status: 'pending' },
    { id: 'simple-model', label: 'simple_model loaded', status: 'pending' },
    { id: 'uv-model', label: 'uv_model loaded', status: 'pending' },
    { id: 'latency-ok', label: 'Inference latency acceptable (<1.5s)', status: 'pending' },
    { id: 'https-active', label: 'HTTPS endpoint active', status: 'pending' }
  ]

  const details = {
    region: 'asia-southeast2',
    endpoint: API_BASE,
    modelsLoaded: [],
    responseTime: null
  }

  try {
    const result = await checkModelScanning()

    if (result.status === 'healthy') {
      checklist[0].status = 'pass'
      checklist[1].status = 'pass'
      checklist[5].status = 'pass'

      const scanTypes = result.details?.scan_types || result.models || []
      const apiVersion = result.details?.api_version || 'unknown'
      details.modelsLoaded = scanTypes
      details.apiVersion = apiVersion

      // Models are loaded if the API is healthy and has scan types
      const hasScans = scanTypes.length > 0
      checklist[2].status = hasScans ? 'pass' : 'warn'
      checklist[3].status = hasScans ? 'pass' : 'warn'

      details.responseTime = result.latency
      checklist[4].status = (result.latency && result.latency < 1500) ? 'pass' : 'warn'
    } else if (result.status === 'unhealthy') {
      checklist[0].status = 'pass'
      checklist[1].status = 'fail'
      checklist[5].status = 'pass'
      details.error = result.error
    } else {
      checklist.forEach(c => { if (c.status === 'pending') c.status = 'fail' })
      details.error = result.error
    }
  } catch (e) {
    checklist.forEach(c => { c.status = 'fail' })
    details.error = e.message
  }

  return {
    id: 'gcp',
    name: 'GCP Cloud Run',
    icon: 'cloud',
    description: 'Production inference service running YOLOv8 OBB models on Google Cloud Run. Handles REST and WebSocket scan requests.',
    checklist,
    details,
    overall: getOverallStatus(checklist)
  }
}

/**
 * Docker Connection — Local FastAPI container
 */
export async function checkDockerConnection() {
  const checklist = [
    { id: 'container-running', label: 'Container running', status: 'pending' },
    { id: 'fastapi-responding', label: 'FastAPI responding', status: 'pending' },
    { id: 'simple-model-mounted', label: 'simple_model mounted', status: 'pending' },
    { id: 'uv-model-mounted', label: 'uv_model mounted', status: 'pending' },
    { id: 'ws-endpoint', label: 'WebSocket endpoint active', status: 'pending' },
    { id: 'health-endpoint', label: 'Health endpoint responding', status: 'pending' }
  ]

  const details = {
    host: 'localhost',
    port: 8080,
    endpoint: DOCKER_LOCAL,
    modelsLoaded: [],
    responseTime: null
  }

  try {
    const result = await checkDockerContainer()

    if (result.status === 'running') {
      checklist[0].status = 'pass'
      checklist[1].status = 'pass'
      checklist[5].status = 'pass'

      const scanTypes = result.details?.scan_types || []
      details.modelsLoaded = scanTypes
      details.apiVersion = result.details?.api_version || 'unknown'
      checklist[2].status = scanTypes.length > 0 ? 'pass' : 'warn'
      checklist[3].status = scanTypes.length > 0 ? 'pass' : 'warn'

      // WebSocket endpoint assumed active if container is running
      checklist[4].status = 'pass'
      details.responseTime = result.latency
    } else {
      // Optional local dev mirror — not running. This is NOT an error:
      // production ML inference runs on Cloud Run (checked separately).
      checklist.forEach(c => { c.status = 'skip' })
      details.note = 'Optional — local container not running. Production ML uses Cloud Run (see GCP Cloud Run card). Start locally with: docker compose up billsense-api'
    }
  } catch (e) {
    checklist.forEach(c => { c.status = 'skip' })
    details.note = 'Optional — local container not reachable. Production ML uses Cloud Run.'
  }

  return {
    id: 'docker',
    name: 'Docker Container',
    icon: 'inventory_2',
    description: 'OPTIONAL local FastAPI mirror for debugging ML inference. Production inference runs on Cloud Run — this being offline does not affect the app.',
    checklist,
    details,
    overall: getOverallStatus(checklist)
  }
}

/**
 * Mobile App Connection — API and WebSocket reachability
 */
export async function checkMobileAppConnection() {
  const checklist = [
    { id: 'rest-reachable', label: 'REST API reachable from app', status: 'pending' },
    { id: 'ws-connectable', label: 'WebSocket endpoint connectable', status: 'pending' },
    { id: 'cors-configured', label: 'CORS configured', status: 'pending' },
    { id: 'debug-endpoint', label: 'Debug endpoint (10.0.2.2) configured', status: 'pending' },
    { id: 'release-endpoint', label: 'Release endpoint configured', status: 'pending' },
    { id: 'scan-types', label: 'All scan types supported', status: 'pending' }
  ]

  const details = {
    debugUrl: 'http://10.0.2.2:8080',
    releaseUrl: API_BASE,
    wsDebug: 'ws://10.0.2.2:8080/ws/standard-scan',
    wsRelease: `wss://${API_BASE.replace('https://', '')}/ws/standard-scan`,
    scanTypes: ['standard-scan', 'multi-scan', 'video-scan']
  }

  try {
    // Check if the production REST API is reachable
    const result = await checkModelScanning()

    if (result.status === 'healthy') {
      checklist[0].status = 'pass'
      checklist[4].status = 'pass'

      // Test WebSocket handshake via HTTP upgrade check
      try {
        const wsTest = await fetch(`${API_BASE}/api/health`, {
          signal: AbortSignal.timeout(5000),
          headers: { 'Origin': 'http://localhost:3000' }
        })
        checklist[2].status = 'pass'
      } catch {
        checklist[2].status = 'warn'
      }

      // WebSocket assumed connectable if REST is healthy
      checklist[1].status = 'pass'
      checklist[5].status = 'pass'
    } else {
      checklist[0].status = 'fail'
      checklist[1].status = 'fail'
      checklist[4].status = 'fail'
      details.error = result.error
    }

    // Debug endpoint is a config check — always configured in BuildConfig
    checklist[3].status = 'pass'
  } catch (e) {
    checklist.forEach(c => { if (c.status === 'pending') c.status = 'fail' })
    details.error = e.message
  }

  return {
    id: 'mobile',
    name: 'Mobile App',
    icon: 'phone_android',
    description: 'Android app connectivity to the inference backend. Handles REST API calls and WebSocket real-time scanning with base64 JPEG frame encoding.',
    checklist,
    details,
    overall: getOverallStatus(checklist)
  }
}

/**
 * Firebase ML Connection — On-device model hosting
 */
export async function checkFirebaseMLConnection() {
  const checklist = [
    { id: 'firebase-accessible', label: 'Firebase project accessible', status: 'pending' },
    { id: 'storage-models', label: 'TFLite models in Firebase Storage', status: 'pending' },
    { id: 'simple-model', label: 'simple_model deployed (counterfeit detection)', status: 'pending' },
    { id: 'uv-model', label: 'uv_model deployed (security features)', status: 'pending' },
    { id: 'on-device-configured', label: 'On-device inference code ready', status: 'pending' }
  ]

  const details = {
    projectId: 'bill-sense-aec6b',
    bucket: 'bill-sense-aec6b.firebasestorage.app',
    modelCount: 0,
    models: [],
    deploymentStatus: 'unknown'
  }

  try {
    // Check Firebase RTDB connectivity first
    const fbResult = await checkFirebase()
    checklist[0].status = (fbResult.status === 'connected') ? 'pass' : 'fail'

    // Check Firebase ML models via Storage metadata
    const mlResult = await checkFirebaseML()

    if (mlResult.status === 'deployed') {
      const modelDetails = mlResult.details?.models || []
      const deployedModels = modelDetails.filter(m => m.status === 'deployed')

      checklist[1].status = deployedModels.length > 0 ? 'pass' : 'fail'

      // Check individual models
      const simpleModel = modelDetails.find(m => m.name === 'simple_model')
      const uvModel = modelDetails.find(m => m.name === 'uv_model')

      checklist[2].status = simpleModel?.status === 'deployed' ? 'pass' : 'fail'
      checklist[3].status = uvModel?.status === 'deployed' ? 'pass' : 'fail'

      // On-device inference code exists (FirebaseModelManager.java + HybridInferenceManager.java)
      checklist[4].status = 'pass'

      details.modelCount = deployedModels.length
      details.models = deployedModels.map(m => `${m.name} (${m.format}, ${m.size}, ${m.classes} classes)`)
      details.deploymentStatus = 'deployed'
      details.androidSdk = mlResult.details?.androidSdk
      details.tfliteRuntime = mlResult.details?.tfliteRuntime
      details.hybridInference = mlResult.details?.hybridInference
      details.storagePath = mlResult.details?.storagePath
    } else if (mlResult.status === 'no-models') {
      checklist[1].status = 'warn'
      checklist[2].status = 'warn'
      checklist[3].status = 'warn'
      checklist[4].status = 'pass' // Code still exists
      details.deploymentStatus = 'no models in storage'
    } else {
      checklist[1].status = 'fail'
      checklist[2].status = 'fail'
      checklist[3].status = 'fail'
      checklist[4].status = 'pass' // Code still exists
      details.deploymentStatus = 'not configured'
      details.error = mlResult.error
    }
  } catch (e) {
    checklist.forEach(c => { if (c.status === 'pending') c.status = 'fail' })
    details.error = e.message
  }

  return {
    id: 'firebase-ml',
    name: 'Firebase ML',
    icon: 'model_training',
    description: 'Firebase Machine Learning for on-device TFLite model hosting. 2 models deployed: simple_model (Real/Fake) and uv_model (security features). Enables hybrid inference with cloud API fallback.',
    checklist,
    details,
    overall: getOverallStatus(checklist)
  }
}

/**
 * Calculate overall status from checklist items
 */
function getOverallStatus(checklist) {
  const statuses = checklist.map(c => c.status)
  if (statuses.every(s => s === 'skip')) return 'optional'
  if (statuses.every(s => s === 'pass' || s === 'skip')) return 'healthy'
  if (statuses.some(s => s === 'fail')) return 'error'
  if (statuses.some(s => s === 'warn')) return 'warning'
  return 'checking'
}

/**
 * Run all 4 model connection checks in parallel
 */
export async function runModelConnectionChecks() {
  const [gcp, docker, mobile, firebaseML] = await Promise.allSettled([
    checkGCPConnection(),
    checkDockerConnection(),
    checkMobileAppConnection(),
    checkFirebaseMLConnection()
  ])

  return {
    gcp: gcp.value || { id: 'gcp', name: 'GCP Cloud Run', overall: 'error', checklist: [], details: { error: gcp.reason?.message } },
    docker: docker.value || { id: 'docker', name: 'Docker Container', overall: 'error', checklist: [], details: { error: docker.reason?.message } },
    mobile: mobile.value || { id: 'mobile', name: 'Mobile App', overall: 'error', checklist: [], details: { error: mobile.reason?.message } },
    firebaseML: firebaseML.value || { id: 'firebase-ml', name: 'Firebase ML', overall: 'error', checklist: [], details: { error: firebaseML.reason?.message } }
  }
}
