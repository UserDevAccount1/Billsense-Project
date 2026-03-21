/**
 * BillSense Dev Server — Local build/emulator control API
 * Zero external dependencies — uses Node.js built-in modules only
 *
 * Run:  node dev-server.mjs
 * Port: 3003
 *
 * Endpoints:
 *   GET  /api/dev/status              — emulator + build + env status
 *   POST /api/dev/build               — build APK (variant: user|admin, buildType: debug|release)
 *   GET  /api/dev/build/status        — current build progress
 *   POST /api/dev/pipeline            — full pipeline (emulator → build → install → launch)
 *   GET  /api/dev/pipeline/status     — current pipeline progress
 *   POST /api/dev/pipeline/cancel     — cancel running pipeline/build
 *   GET  /api/dev/apk/download        — download APK (?variant=user&buildType=debug)
 *   GET  /api/dev/apk/list            — list all built APKs
 *   POST /api/dev/distribute          — build + upload to Firebase App Distribution
 *   GET  /api/dev/distribute/status   — distribution pipeline progress
 *   POST /api/dev/distribute/cancel   — cancel running distribution
 */

import http from 'node:http'
import { spawn, execSync } from 'node:child_process'
import { createReadStream, statSync, existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const PORT = 3003
const ANDROID_HOME = path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk')
const ADB = path.join(ANDROID_HOME, 'platform-tools', 'adb.exe')
const EMULATOR_BIN = path.join(ANDROID_HOME, 'emulator', 'emulator.exe')
const AVD_NAME = 'Medium_Phone_API_36.1'
const PROJECT_ROOT = 'D:\\PROJECTS\\BillSense\\BillSense'
const JAVA_HOME = path.join(process.env.USERPROFILE || '', '.gradle', 'jdks', 'eclipse_adoptium-17-amd64-windows.2')
const APK_BASE = path.join(PROJECT_ROOT, 'app', 'build', 'outputs', 'apk')

// Flavor → Gradle task + applicationId mapping
const FLAVORS = {
  user:  { gradleName: 'User',  appId: 'com.app.billsense',   label: 'BillSense' },
  admin: { gradleName: 'Admin', appId: 'com.admin.billsense', label: 'BillSense Admin' }
}

const BUILD_TYPES = {
  debug:   { gradleName: 'Debug' },
  release: { gradleName: 'Release' }
}

// Firebase App Distribution config
const FIREBASE_APP_IDS = {
  user:  '1:340624938055:android:81d528ded5f924a23fcd62',   // com.app.billsense
  admin: '1:340624938055:android:ad1fd12436348f633fcd62'    // com.admin.billsense
}
const FIREBASE_PROJECT = 'bill-sense-aec6b'
const FIREBASE_GROUP = 'testers'

// Persistent device registry — tracks distribution history + ADB connections per tester
// Saved to JSON file so it survives server restarts
const REGISTRY_FILE = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), '.device-registry.json')

function loadDeviceRegistry() {
  try {
    if (existsSync(REGISTRY_FILE)) {
      return JSON.parse(readFileSync(REGISTRY_FILE, 'utf-8'))
    }
  } catch (err) { console.warn('Failed to load device registry:', err.message) }
  return {}
}

function saveDeviceRegistry() {
  try {
    writeFileSync(REGISTRY_FILE, JSON.stringify(deviceRegistry, null, 2), 'utf-8')
  } catch (err) { console.warn('Failed to save device registry:', err.message) }
}

let deviceRegistry = loadDeviceRegistry()
// Structure: { 'email@example.com': { email, addedAt, lastDistribution, distributions[], adbConnection: {ip, port, pairPort, serial, paired, connected} } }

function getDeviceEntry(email) {
  if (!deviceRegistry[email]) {
    deviceRegistry[email] = { email, addedAt: new Date().toISOString(), lastDistribution: null, distributions: [], adbConnection: null }
    saveDeviceRegistry()
  }
  return deviceRegistry[email]
}

function setTesterConnection(email, connectionInfo) {
  const entry = getDeviceEntry(email)
  entry.adbConnection = { ...entry.adbConnection, ...connectionInfo, updatedAt: new Date().toISOString() }
  saveDeviceRegistry()
  return entry.adbConnection
}

function getTesterConnection(email) {
  const entry = deviceRegistry[email]
  return entry?.adbConnection || null
}

// Check if a specific tester's device is connected via ADB
function isTesterDeviceConnected(email) {
  const conn = getTesterConnection(email)
  if (!conn || !conn.ip) return false
  const devices = getConnectedDevices()
  // Match by IP:port in serial (WiFi devices show as ip:port)
  return devices.some(d => d.serial.includes(conn.ip))
}

function recordDistribution(email, variant, buildType, releaseNotes) {
  const entry = getDeviceEntry(email)
  const record = { variant, buildType, at: new Date().toISOString(), releaseNotes }
  entry.distributions.push(record)
  entry.lastDistribution = record
  if (entry.distributions.length > 20) entry.distributions.shift()
  saveDeviceRegistry()
}

// Helper: run firebase CLI command and return output
function runFirebase(args, timeout = 30000) {
  return new Promise((resolve, reject) => {
    const proc = spawn('firebase', args, {
      cwd: PROJECT_ROOT,
      env: process.env,
      shell: true
    })
    let stdout = '', stderr = ''
    proc.stdout?.on('data', d => { stdout += d.toString() })
    proc.stderr?.on('data', d => { stderr += d.toString() })
    const timer = setTimeout(() => { proc.kill(); reject(new Error('Firebase CLI timeout')) }, timeout)
    proc.on('close', code => {
      clearTimeout(timer)
      if (code === 0) resolve(stdout + stderr)
      else reject(new Error(`Exit ${code}: ${(stderr || stdout).substring(0, 400)}`))
    })
    proc.on('error', reject)
  })
}

// Distribution pipeline state (separate from build pipeline)
let distPipeline = {
  running: false,
  step: '',
  stepIndex: 0,
  totalSteps: 4,
  progress: 0,
  logs: [],
  error: null,
  startedAt: null,
  variant: '',
  buildType: '',
  childProcess: null,
  result: null // { downloadUrl, releaseId, ... }
}

function distLog(msg) {
  const ts = new Date().toLocaleTimeString()
  const entry = `[${ts}] ${msg}`
  distPipeline.logs.push(entry)
  if (distPipeline.logs.length > 100) distPipeline.logs.shift()
  console.log(`[DIST] ${entry}`)
}

function setDistStep(index, name, progress) {
  distPipeline.stepIndex = index
  distPipeline.step = name
  distPipeline.progress = progress
  distLog(`Step ${index}/${distPipeline.totalSteps}: ${name}`)
}

// Build + distribute APK via Firebase App Distribution
async function runDistribute(variant, buildType, targetEmails = null, skipBuild = false) {
  distPipeline.running = true
  distPipeline.error = null
  distPipeline.startedAt = Date.now()
  distPipeline.variant = variant
  distPipeline.buildType = buildType
  distPipeline.logs = []
  distPipeline.totalSteps = 4
  distPipeline.result = null
  distPipeline.childProcess = null

  const flavor = FLAVORS[variant]
  const bt = BUILD_TYPES[buildType]
  if (!flavor || !bt) {
    distPipeline.error = `Invalid variant "${variant}" or buildType "${buildType}"`
    distPipeline.running = false
    return
  }

  const firebaseAppId = FIREBASE_APP_IDS[variant]
  if (!firebaseAppId) {
    distPipeline.error = `No Firebase App ID configured for variant "${variant}"`
    distPipeline.running = false
    return
  }

  const gradleTask = `assemble${flavor.gradleName}${bt.gradleName}`
  const apkPath = getApkPath(variant, buildType)

  let apkInfo = null

  try {
    // Step 1: Build APK (or skip if already built)
    if (skipBuild && existsSync(apkPath)) {
      apkInfo = getApkInfo(variant, buildType)
      setDistStep(1, `Using existing APK — ${apkInfo.filename} (${apkInfo.size})`, 40)
      distLog(`Skipping build — APK exists: ${apkInfo.filename} (${apkInfo.size})`)
    } else {
      setDistStep(1, `Building ${flavor.label} (${buildType})...`, 5)
      distLog(`Gradle task: ${gradleTask}`)

      await new Promise((resolve, reject) => {
        const proc = spawn('.\\gradlew.bat', [gradleTask, '--console=plain'], {
          cwd: PROJECT_ROOT,
          env: { ...process.env, JAVA_HOME, PATH: `${JAVA_HOME}\\bin;${process.env.PATH}` },
          shell: true
        })
        distPipeline.childProcess = proc
        let stderr = ''
        proc.stdout?.on('data', d => {
          const line = d.toString().trim()
          if (line.includes('Task :') || line.includes('BUILD')) {
            distLog(line.substring(0, 120))
          }
          distPipeline.progress = Math.min(40, distPipeline.progress + 1)
        })
        proc.stderr?.on('data', d => { stderr += d.toString() })
        proc.on('close', code => {
          if (code === 0) resolve()
          else reject(new Error(`Build failed (exit ${code}): ${stderr.substring(0, 300)}`))
        })
        proc.on('error', reject)
      })

      if (!existsSync(apkPath)) throw new Error(`APK not found at ${apkPath} after build`)
      apkInfo = getApkInfo(variant, buildType)
      setDistStep(1, `Build complete — ${apkInfo.filename} (${apkInfo.size})`, 40)
      distLog(`APK ready: ${apkInfo.filename} (${apkInfo.size})`)
    }

    // Step 2: Upload to Firebase App Distribution
    setDistStep(2, 'Uploading to Firebase App Distribution...', 45)
    const releaseNotes = `${flavor.label} ${buildType} — Built from admin panel at ${new Date().toLocaleString()}`
    distLog(`Firebase App ID: ${firebaseAppId}`)
    distLog(`Release notes: ${releaseNotes}`)

    const distOutput = await new Promise((resolve, reject) => {
      // Sanitize release notes for shell safety (remove special chars)
      const safeNotes = releaseNotes.replace(/[^a-zA-Z0-9 .,\-_()]/g, '')
      const args = [
        'appdistribution:distribute', `"${apkPath}"`,
        '--app', firebaseAppId,
        '--release-notes', `"${safeNotes}"`,
        ...(targetEmails && targetEmails.length > 0
          ? ['--testers', targetEmails.join(',')]
          : ['--groups', FIREBASE_GROUP]),
        '--project', FIREBASE_PROJECT
      ]
      distLog(`Running: firebase ${args.join(' ')}`)
      const proc = spawn('firebase', args, {
        cwd: PROJECT_ROOT,
        env: process.env,
        shell: true
      })
      distPipeline.childProcess = proc
      let stdout = '', stderr = ''
      proc.stdout?.on('data', d => {
        const line = d.toString()
        stdout += line
        const trimmed = line.trim()
        if (trimmed) distLog(trimmed.substring(0, 150))
        // Increment progress during upload
        if (distPipeline.progress < 75) distPipeline.progress += 2
      })
      proc.stderr?.on('data', d => {
        const line = d.toString()
        stderr += line
        // Firebase CLI writes progress to stderr
        const trimmed = line.trim()
        if (trimmed && !trimmed.includes('DeprecationWarning')) {
          distLog(trimmed.substring(0, 150))
        }
        if (distPipeline.progress < 75) distPipeline.progress += 2
      })
      proc.on('close', code => {
        if (code === 0) resolve(stdout + stderr)
        else reject(new Error(`Firebase distribute failed (exit ${code}): ${(stderr || stdout).substring(0, 400)}`))
      })
      proc.on('error', reject)
    })

    setDistStep(2, 'Upload complete', 78)

    // Step 3: Notify testers
    setDistStep(3, `Notifying testers in group "${FIREBASE_GROUP}"...`, 82)
    distLog('Testers notified via Firebase App Distribution — install prompt sent to registered devices')

    // Parse useful URLs from distribution output
    const consoleMatch = distOutput.match(/https:\/\/console\.firebase\.google\.com[^\s]+/)
    const testerMatch = distOutput.match(/https:\/\/appdistribution\.firebase\.google\.com[^\s]+/)
    const downloadMatch = distOutput.match(/https:\/\/firebaseappdistribution\.googleapis\.com[^\s]+/)
    const consoleUrl = consoleMatch ? consoleMatch[0] : `https://console.firebase.google.com/project/${FIREBASE_PROJECT}/appdistribution/app/android:${firebaseAppId}/releases`
    const testerUrl = testerMatch ? testerMatch[0] : null
    const downloadUrl = downloadMatch ? downloadMatch[0] : null
    if (consoleUrl) distLog(`Console: ${consoleUrl}`)
    if (testerUrl) distLog(`Tester link: ${testerUrl}`)
    setDistStep(3, 'Testers notified — install prompts sent', 90)

    // Step 4: Done
    distPipeline.result = {
      variant,
      buildType,
      apk: apkInfo,
      firebaseAppId,
      group: FIREBASE_GROUP,
      consoleUrl,
      testerUrl,
      downloadUrl,
      releaseNotes,
      distributedAt: new Date().toISOString(),
      buildTime: Math.round((Date.now() - distPipeline.startedAt) / 1000)
    }
    // Record distribution for each targeted tester
    const targets = targetEmails || []
    if (targets.length > 0) {
      targets.forEach(email => recordDistribution(email, variant, buildType, releaseNotes))
    } else {
      // Distributed to whole group — record for all known testers
      Object.keys(deviceRegistry).forEach(email => recordDistribution(email, variant, buildType, releaseNotes))
    }

    setDistStep(4, 'Distribution complete!', 100)
    distLog(`Distributed in ${distPipeline.result.buildTime}s`)
    await sleep(1000)

  } catch (err) {
    distPipeline.error = err.message
    distPipeline.step = `Error: ${err.message.substring(0, 150)}`
    distLog(`ERROR: ${err.message}`)
  } finally {
    distPipeline.running = false
    distPipeline.childProcess = null
  }
}

function getApkPath(variant, buildType) {
  // Gradle outputs: app/build/outputs/apk/{variant}/{buildType}/app-{variant}-{buildType}.apk
  return path.join(APK_BASE, variant, buildType, `app-${variant}-${buildType}.apk`)
}

function getApkInfo(variant, buildType) {
  const apkPath = getApkPath(variant, buildType)
  if (!existsSync(apkPath)) return null
  const stat = statSync(apkPath)
  return {
    variant,
    buildType,
    path: apkPath,
    size: `${(stat.size / 1024 / 1024).toFixed(1)} MB`,
    sizeBytes: stat.size,
    modified: stat.mtime.toISOString(),
    filename: path.basename(apkPath)
  }
}

function listAllApks() {
  const apks = []
  for (const v of Object.keys(FLAVORS)) {
    for (const bt of Object.keys(BUILD_TYPES)) {
      const info = getApkInfo(v, bt)
      if (info) apks.push(info)
    }
  }
  return apks
}

// Pipeline state (shared for both build-only and full pipeline)
let pipeline = {
  running: false,
  mode: '', // 'build' or 'pipeline'
  step: '',
  stepIndex: 0,
  totalSteps: 5,
  progress: 0,
  logs: [],
  error: null,
  startedAt: null,
  variant: 'user',
  buildType: 'debug',
  childProcess: null,
  result: null // build result info
}

function resetPipeline() {
  pipeline.running = false
  pipeline.step = ''
  pipeline.stepIndex = 0
  pipeline.progress = 0
  pipeline.logs = []
  pipeline.error = null
  pipeline.startedAt = null
  pipeline.childProcess = null
  pipeline.result = null
}

function log(msg) {
  const ts = new Date().toLocaleTimeString()
  const entry = `[${ts}] ${msg}`
  pipeline.logs.push(entry)
  if (pipeline.logs.length > 100) pipeline.logs.shift()
  console.log(entry)
}

function setStep(index, name, progress) {
  pipeline.stepIndex = index
  pipeline.step = name
  pipeline.progress = progress
  log(`Step ${index}/${pipeline.totalSteps}: ${name}`)
}

function isEmulatorRunning() {
  try {
    const output = execSync(`"${ADB}" devices`, { encoding: 'utf-8', timeout: 5000 })
    return output.includes('emulator')
  } catch { return false }
}

function isBootComplete() {
  try {
    const output = execSync(`"${ADB}" shell getprop sys.boot_completed`, { encoding: 'utf-8', timeout: 5000 })
    return output.trim() === '1'
  } catch { return false }
}

// Check if ANY device (emulator or physical) is connected via ADB
function isAnyDeviceConnected() {
  try {
    const output = execSync(`"${ADB}" devices`, { encoding: 'utf-8', timeout: 5000 })
    const lines = output.split('\n').filter(l => l.includes('\tdevice'))
    return lines.length > 0
  } catch { return false }
}

// Get list of connected ADB devices with type info
function getConnectedDevices() {
  try {
    const output = execSync(`"${ADB}" devices -l`, { encoding: 'utf-8', timeout: 5000 })
    // Match lines with "device" status (tab or spaces between serial and "device" keyword)
    // Windows ADB may use spaces instead of tab, and adds \r line endings
    const lines = output.split('\n')
      .map(l => l.replace(/\r/g, '').trim())
      .filter(l => l && !l.startsWith('List') && /\bdevice\b/.test(l) && !l.includes('unauthorized') && !l.includes('offline'))
    return lines.map(line => {
      const parts = line.split(/\s+/)
      const serial = parts[0]
      const isEmulator = serial.startsWith('emulator-')
      const model = (line.match(/model:(\S+)/) || [])[1] || (isEmulator ? 'Emulator' : 'Unknown')
      const device = (line.match(/device:(\S+)/) || [])[1] || ''
      return { serial, isEmulator, model: model.replace(/_/g, ' '), device }
    })
  } catch { return [] }
}

function runCmd(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    const proc = spawn(cmd, args, {
      cwd: opts.cwd || PROJECT_ROOT,
      env: { ...process.env, JAVA_HOME, PATH: `${JAVA_HOME}\\bin;${process.env.PATH}` },
      shell: true
    })
    let stdout = '', stderr = ''
    proc.stdout?.on('data', d => {
      const line = d.toString()
      stdout += line
      if (line.includes('BUILD') || line.includes('Task :') || line.includes('Installing') || line.includes('Starting')) {
        log(line.trim().substring(0, 120))
      }
    })
    proc.stderr?.on('data', d => { stderr += d.toString() })
    proc.on('close', code => {
      if (code === 0) resolve(stdout)
      else reject(new Error(`Exit code ${code}: ${stderr.substring(0, 300)}`))
    })
    proc.on('error', reject)
    if (opts.trackProcess) pipeline.childProcess = proc
  })
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

// Build-only: just build the APK
async function runBuild(variant, buildType) {
  pipeline.running = true
  pipeline.mode = 'build'
  pipeline.error = null
  pipeline.startedAt = Date.now()
  pipeline.variant = variant
  pipeline.buildType = buildType
  pipeline.logs = []
  pipeline.totalSteps = 2
  pipeline.result = null

  const flavor = FLAVORS[variant]
  const bt = BUILD_TYPES[buildType]
  if (!flavor || !bt) {
    pipeline.error = `Invalid variant "${variant}" or buildType "${buildType}"`
    pipeline.running = false
    return
  }

  const gradleTask = `assemble${flavor.gradleName}${bt.gradleName}`

  try {
    setStep(1, `Building ${flavor.label} (${buildType})...`, 10)
    log(`Gradle task: ${gradleTask}`)

    await runCmd('.\\gradlew.bat', [gradleTask, '--console=plain'], { trackProcess: true })

    setStep(1, 'Build complete', 90)

    // Get APK info
    const apkInfo = getApkInfo(variant, buildType)
    if (apkInfo) {
      pipeline.result = {
        ...apkInfo,
        gradleTask,
        applicationId: flavor.appId,
        label: flavor.label,
        buildTime: Math.round((Date.now() - pipeline.startedAt) / 1000)
      }
      log(`APK: ${apkInfo.filename} (${apkInfo.size})`)
    }

    setStep(2, 'Build successful!', 100)
    log(`Build finished in ${Math.round((Date.now() - pipeline.startedAt) / 1000)}s`)
    await sleep(1000)

  } catch (err) {
    pipeline.error = err.message
    pipeline.step = `Build failed: ${err.message.substring(0, 100)}`
    log(`ERROR: ${err.message}`)
  } finally {
    pipeline.running = false
    pipeline.childProcess = null
  }
}

// Full pipeline: emulator → build → install → launch
async function runPipeline(variant, buildType = 'debug') {
  pipeline.running = true
  pipeline.mode = 'pipeline'
  pipeline.error = null
  pipeline.startedAt = Date.now()
  pipeline.variant = variant
  pipeline.buildType = buildType
  pipeline.logs = []
  pipeline.totalSteps = 5
  pipeline.result = null

  const flavor = FLAVORS[variant]
  if (!flavor) { pipeline.error = `Unknown variant: ${variant}`; pipeline.running = false; return }

  const gradleTask = `assemble${flavor.gradleName}Debug`
  const installTask = `install${flavor.gradleName}Debug`
  const component = `${flavor.appId}/.activities.MainActivity`

  try {
    // Step 1: Emulator
    setStep(1, 'Checking emulator...', 5)
    if (isEmulatorRunning()) {
      log('Emulator already running')
      setStep(1, 'Emulator already running', 15)
    } else {
      setStep(1, 'Starting emulator...', 8)
      log(`Launching AVD: ${AVD_NAME}`)
      const emuProc = spawn(`"${EMULATOR_BIN}"`, ['-avd', AVD_NAME, '-gpu', 'host'], {
        shell: true, detached: true, stdio: 'ignore'
      })
      emuProc.unref()

      setStep(1, 'Waiting for emulator device...', 10)
      let deviceFound = false
      for (let i = 0; i < 30; i++) {
        await sleep(2000)
        if (isEmulatorRunning()) { deviceFound = true; break }
        pipeline.progress = 10 + Math.min(i, 10)
        log(`Waiting for device... (${(i + 1) * 2}s)`)
      }
      if (!deviceFound) throw new Error('Emulator device not found after 60s')

      setStep(1, 'Waiting for boot...', 15)
      let booted = false
      for (let i = 0; i < 60; i++) {
        await sleep(2000)
        if (isBootComplete()) { booted = true; break }
        pipeline.progress = 15 + Math.min(i / 4, 5)
        if (i % 5 === 0) log(`Still booting... (${(i + 1) * 2}s)`)
      }
      if (!booted) throw new Error('Emulator boot timeout after 120s')
      log('Emulator booted')
    }
    setStep(1, 'Emulator ready', 20)

    // Step 2: Build
    setStep(2, `Building ${flavor.label} debug APK...`, 25)
    log(`Gradle task: ${gradleTask}`)
    await runCmd('.\\gradlew.bat', [gradleTask, '--console=plain'], { trackProcess: true })
    setStep(2, 'Build complete', 55)

    // Step 3: Install
    setStep(3, 'Installing APK on emulator...', 60)
    await runCmd('.\\gradlew.bat', [installTask, '--console=plain'], { trackProcess: true })
    setStep(3, 'APK installed', 80)

    // Step 4: Launch
    setStep(4, `Launching ${flavor.label}...`, 85)
    await runCmd(`"${ADB}"`, ['shell', 'am', 'start', '-n', component])
    setStep(4, 'App launched', 95)
    log(`App launched: ${component}`)

    // Get APK info for result
    const apkInfo = getApkInfo(variant, 'debug')
    if (apkInfo) {
      pipeline.result = {
        ...apkInfo,
        gradleTask,
        applicationId: flavor.appId,
        label: flavor.label,
        buildTime: Math.round((Date.now() - pipeline.startedAt) / 1000)
      }
    }

    // Step 5: Done
    setStep(5, 'Pipeline complete!', 100)
    log(`Pipeline finished in ${Math.round((Date.now() - pipeline.startedAt) / 1000)}s`)
    await sleep(2000)

  } catch (err) {
    pipeline.error = err.message
    pipeline.step = `Error: ${err.message.substring(0, 100)}`
    log(`ERROR: ${err.message}`)
  } finally {
    pipeline.running = false
    pipeline.childProcess = null
  }
}

// CORS + JSON helpers
function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
}
function json(res, data, status = 200) {
  cors(res)
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(data))
}
function parseBody(req) {
  return new Promise(resolve => {
    let body = ''
    req.on('data', chunk => { body += chunk })
    req.on('end', () => { try { resolve(JSON.parse(body)) } catch { resolve({}) } })
  })
}

// Check build environment
function checkEnvironment() {
  const checks = {}
  checks.javaHome = existsSync(path.join(JAVA_HOME, 'bin', 'java.exe'))
  checks.androidSdk = existsSync(path.join(ANDROID_HOME, 'platform-tools', 'adb.exe'))
  checks.emulator = existsSync(EMULATOR_BIN)
  checks.gradlew = existsSync(path.join(PROJECT_ROOT, 'gradlew.bat'))
  checks.avd = (() => {
    try { const out = execSync(`"${EMULATOR_BIN}" -list-avds`, { encoding: 'utf-8', timeout: 5000 }); return out.trim().split('\n') } catch { return [] }
  })()
  return checks
}

// Server
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)

  if (req.method === 'OPTIONS') { cors(res); res.writeHead(204); return res.end() }

  // GET /api/dev/status
  if (req.method === 'GET' && url.pathname === '/api/dev/status') {
    const emulatorRunning = isEmulatorRunning()
    const booted = emulatorRunning ? isBootComplete() : false
    const env = checkEnvironment()
    const apks = listAllApks()

    return json(res, {
      emulator: { running: emulatorRunning, booted, avd: AVD_NAME },
      environment: {
        javaHome: { path: JAVA_HOME, ok: env.javaHome },
        androidSdk: { path: ANDROID_HOME, ok: env.androidSdk },
        emulator: { path: EMULATOR_BIN, ok: env.emulator },
        gradlew: { ok: env.gradlew },
        avds: env.avd
      },
      apks,
      pipeline: {
        running: pipeline.running,
        mode: pipeline.mode,
        step: pipeline.step,
        stepIndex: pipeline.stepIndex,
        totalSteps: pipeline.totalSteps,
        progress: pipeline.progress,
        error: pipeline.error,
        variant: pipeline.variant,
        buildType: pipeline.buildType,
        elapsed: pipeline.startedAt ? Math.round((Date.now() - pipeline.startedAt) / 1000) : 0
      },
      distribution: {
        running: distPipeline.running,
        step: distPipeline.step,
        stepIndex: distPipeline.stepIndex,
        totalSteps: distPipeline.totalSteps,
        progress: distPipeline.progress,
        error: distPipeline.error,
        variant: distPipeline.variant,
        buildType: distPipeline.buildType,
        elapsed: distPipeline.startedAt ? Math.round((Date.now() - distPipeline.startedAt) / 1000) : 0
      },
      flavors: Object.entries(FLAVORS).map(([k, v]) => ({ id: k, ...v })),
      server: { version: '3.1.0', uptime: Math.round(process.uptime()) }
    })
  }

  // POST /api/dev/build — build APK only (no emulator/install)
  if (req.method === 'POST' && url.pathname === '/api/dev/build') {
    if (pipeline.running) return json(res, { error: 'Build already running', step: pipeline.step }, 409)
    const body = await parseBody(req)
    const variant = body.variant || 'user'
    const buildType = body.buildType || 'debug'
    runBuild(variant, buildType)
    return json(res, { started: true, variant, buildType, task: `assemble${FLAVORS[variant]?.gradleName || 'User'}${BUILD_TYPES[buildType]?.gradleName || 'Debug'}` })
  }

  // GET /api/dev/build/status
  if (req.method === 'GET' && url.pathname === '/api/dev/build/status') {
    return json(res, {
      running: pipeline.running,
      mode: pipeline.mode,
      step: pipeline.step,
      stepIndex: pipeline.stepIndex,
      totalSteps: pipeline.totalSteps,
      progress: pipeline.progress,
      error: pipeline.error,
      variant: pipeline.variant,
      buildType: pipeline.buildType,
      result: pipeline.result,
      logs: pipeline.logs.slice(-20),
      elapsed: pipeline.startedAt ? Math.round((Date.now() - pipeline.startedAt) / 1000) : 0
    })
  }

  // GET /api/dev/pipeline/status (alias)
  if (req.method === 'GET' && url.pathname === '/api/dev/pipeline/status') {
    return json(res, {
      running: pipeline.running,
      mode: pipeline.mode,
      step: pipeline.step,
      stepIndex: pipeline.stepIndex,
      totalSteps: pipeline.totalSteps,
      progress: pipeline.progress,
      error: pipeline.error,
      variant: pipeline.variant,
      buildType: pipeline.buildType,
      result: pipeline.result,
      logs: pipeline.logs.slice(-20),
      elapsed: pipeline.startedAt ? Math.round((Date.now() - pipeline.startedAt) / 1000) : 0
    })
  }

  // POST /api/dev/pipeline — full pipeline
  if (req.method === 'POST' && url.pathname === '/api/dev/pipeline') {
    if (pipeline.running) return json(res, { error: 'Pipeline already running', step: pipeline.step }, 409)
    const body = await parseBody(req)
    const variant = body.variant || 'user'
    runPipeline(variant)
    return json(res, { started: true, variant })
  }

  // POST /api/dev/pipeline/cancel
  if (req.method === 'POST' && url.pathname === '/api/dev/pipeline/cancel') {
    if (pipeline.childProcess) pipeline.childProcess.kill('SIGTERM')
    pipeline.running = false
    pipeline.step = 'Cancelled'
    pipeline.error = 'Cancelled by user'
    log('Cancelled')
    return json(res, { cancelled: true })
  }

  // GET /api/dev/apk/download?variant=user&buildType=debug
  if (req.method === 'GET' && url.pathname === '/api/dev/apk/download') {
    cors(res)
    const variant = url.searchParams.get('variant') || 'user'
    const buildType = url.searchParams.get('buildType') || 'debug'
    const apkPath = getApkPath(variant, buildType)
    if (!existsSync(apkPath)) {
      res.writeHead(404, { 'Content-Type': 'application/json' })
      return res.end(JSON.stringify({ error: `No APK found at ${apkPath}. Build first.` }))
    }
    const stat = statSync(apkPath)
    const filename = `billsense-${variant}-${buildType}.apk`
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': `attachment; filename="${filename}"`,
      'Content-Length': stat.size
    })
    createReadStream(apkPath).pipe(res)
    return
  }

  // GET /api/dev/apk/list
  if (req.method === 'GET' && url.pathname === '/api/dev/apk/list') {
    return json(res, { apks: listAllApks() })
  }

  // GET /api/dev/distribute/testers — list Firebase App Distribution testers
  if (req.method === 'GET' && url.pathname === '/api/dev/distribute/testers') {
    try {
      const output = await runFirebase(['appdistribution:testers:list', '--project', FIREBASE_PROJECT])
      // Strip ANSI codes and parse emails from table output
      const stripped = output.replace(/\x1B\[[0-9;]*m/g, '')
      const emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g
      const emails = [...new Set(stripped.match(emailRegex) || [])]
      // Also try to extract group info per tester
      const testers = emails.map(email => {
        // Find the line containing this email to get group
        const line = stripped.split('\n').find(l => l.includes(email)) || ''
        const group = line.includes('testers') ? 'testers' : FIREBASE_GROUP
        return { email, group }
      })
      const allDevices = getConnectedDevices()
      const enrichedTesters = testers.map(t => {
        const reg = deviceRegistry[t.email] || {}
        const conn = reg.adbConnection || null
        // Check if this tester's device is currently connected
        let deviceConnected = false
        let deviceSerial = null
        if (conn && conn.ip) {
          const match = allDevices.find(d => d.serial.includes(conn.ip))
          if (match) { deviceConnected = true; deviceSerial = match.serial }
        }
        return {
          ...t,
          addedAt: reg.addedAt || null,
          lastDistribution: reg.lastDistribution || null,
          distributionCount: reg.distributions ? reg.distributions.length : 0,
          mainAppSent: reg.distributions ? reg.distributions.some(d => d.variant === 'user') : false,
          adminAppSent: reg.distributions ? reg.distributions.some(d => d.variant === 'admin') : false,
          lastMainDist: reg.distributions ? [...(reg.distributions || [])].reverse().find(d => d.variant === 'user') : null,
          lastAdminDist: reg.distributions ? [...(reg.distributions || [])].reverse().find(d => d.variant === 'admin') : null,
          adbConnection: conn,
          deviceConnected,
          deviceSerial,
        }
      })
      return json(res, { testers: enrichedTesters })
    } catch (err) {
      return json(res, { testers: [], error: err.message }, 500)
    }
  }

  // POST /api/dev/distribute/testers — add testers
  if (req.method === 'POST' && url.pathname === '/api/dev/distribute/testers') {
    const body = await parseBody(req)
    const emails = body.emails || []
    if (!emails.length) return json(res, { error: 'No emails provided' }, 400)
    try {
      await runFirebase([
        'appdistribution:testers:add', emails.join(','),
        '--group-alias', FIREBASE_GROUP,
        '--project', FIREBASE_PROJECT
      ])
      return json(res, { success: true, message: `Added ${emails.length} tester(s) to group "${FIREBASE_GROUP}"`, emails })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 300) }, 500)
    }
  }

  // DELETE /api/dev/distribute/testers — remove testers
  if (req.method === 'DELETE' && url.pathname === '/api/dev/distribute/testers') {
    const body = await parseBody(req)
    const emails = body.emails || []
    if (!emails.length) return json(res, { error: 'No emails provided' }, 400)
    try {
      await runFirebase([
        'appdistribution:testers:remove', emails.join(','),
        '--project', FIREBASE_PROJECT
      ])
      return json(res, { success: true, message: `Removed ${emails.length} tester(s)`, emails })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 300) }, 500)
    }
  }

  // POST /api/dev/distribute — build + upload to Firebase App Distribution
  if (req.method === 'POST' && url.pathname === '/api/dev/distribute') {
    if (distPipeline.running) return json(res, { error: 'Distribution already running', step: distPipeline.step }, 409)
    if (pipeline.running) return json(res, { error: 'Build pipeline is running. Wait for it to finish.', step: pipeline.step }, 409)
    const body = await parseBody(req)
    const variant = body.variant || 'user'
    const buildType = body.buildType || 'debug'
    const targetEmails = body.targetEmails || null // array of emails, or null for whole group
    const skipBuild = body.skipBuild || false
    runDistribute(variant, buildType, targetEmails, skipBuild)
    return json(res, { started: true, variant, buildType, targetEmails, firebaseAppId: FIREBASE_APP_IDS[variant] })
  }

  // GET /api/dev/distribute/status
  if (req.method === 'GET' && url.pathname === '/api/dev/distribute/status') {
    return json(res, {
      running: distPipeline.running,
      step: distPipeline.step,
      stepIndex: distPipeline.stepIndex,
      totalSteps: distPipeline.totalSteps,
      progress: distPipeline.progress,
      error: distPipeline.error,
      variant: distPipeline.variant,
      buildType: distPipeline.buildType,
      result: distPipeline.result,
      logs: distPipeline.logs.slice(-30),
      elapsed: distPipeline.startedAt ? Math.round((Date.now() - distPipeline.startedAt) / 1000) : 0
    })
  }

  // POST /api/dev/distribute/cancel
  if (req.method === 'POST' && url.pathname === '/api/dev/distribute/cancel') {
    if (distPipeline.childProcess) distPipeline.childProcess.kill('SIGTERM')
    distPipeline.running = false
    distPipeline.step = 'Cancelled'
    distPipeline.error = 'Cancelled by user'
    distLog('Cancelled')
    return json(res, { cancelled: true })
  }

  // GET /api/dev/distribute/releases — list recent releases per app
  if (req.method === 'GET' && url.pathname === '/api/dev/distribute/releases') {
    const results = {}
    for (const [variant, appId] of Object.entries(FIREBASE_APP_IDS)) {
      try {
        const output = await runFirebase([
          'appdistribution:releases:list',
          '--app', appId,
          '--project', FIREBASE_PROJECT
        ], 15000)
        // Parse release info from CLI output
        const stripped = output.replace(/\x1B\[[0-9;]*m/g, '')
        const lines = stripped.split('\n').filter(l => l.trim())
        results[variant] = { appId, rawOutput: lines.slice(0, 10) }
      } catch (err) {
        results[variant] = { appId, error: err.message.substring(0, 200) }
      }
    }
    return json(res, { releases: results })
  }

  // POST /api/dev/distribute/notify — re-send latest release notification to a tester
  if (req.method === 'POST' && url.pathname === '/api/dev/distribute/notify') {
    const body = await parseBody(req)
    const email = body.email
    const variant = body.variant || 'user'
    if (!email) return json(res, { error: 'No email provided' }, 400)

    // Distribute the latest built APK to just this tester
    const buildType = 'debug'
    const apkPath = getApkPath(variant, buildType)
    if (!existsSync(apkPath)) {
      return json(res, { error: `No ${variant} APK built yet. Build first.` }, 400)
    }

    try {
      const flavor = FLAVORS[variant]
      const firebaseAppId = FIREBASE_APP_IDS[variant]
      const safeNotes = `${flavor.label} - Sent to ${email} at ${new Date().toLocaleString()}`
      const output = await runFirebase([
        'appdistribution:distribute', `"${apkPath}"`,
        '--app', firebaseAppId,
        '--release-notes', `"${safeNotes}"`,
        '--testers', email,
        '--project', FIREBASE_PROJECT
      ], 120000)
      recordDistribution(email, variant, buildType, safeNotes)

      // Parse links from output
      const testerLink = (output.match(/https:\/\/appdistribution\.firebase\.google\.com[^\s]+/) || [])[0] || null
      const downloadLink = (output.match(/https:\/\/firebaseappdistribution\.googleapis\.com[^\s]+/) || [])[0] || null
      const isReupload = output.includes('re-uploaded already existing')

      return json(res, {
        success: true,
        message: isReupload
          ? `Release already exists — re-distributed to ${email}. If no email arrives, the tester must accept the initial invite first (check spam folder) and install the Firebase App Tester app.`
          : `New release distributed to ${email} for ${flavor.label}!`,
        isReupload,
        testerLink,
        downloadLink,
        hint: isReupload ? 'Firebase only sends email for NEW releases. To force a new email: rebuild the APK first (different binary), then distribute again.' : null
      })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 300) }, 500)
    }
  }

  // GET /api/dev/network/info — LAN IP + download URLs for QR codes
  if (req.method === 'GET' && url.pathname === '/api/dev/network/info') {
    // Get LAN IP addresses
    let lanIps = []
    try {
      const output = execSync('ipconfig', { encoding: 'utf-8', timeout: 5000 })
      const matches = output.match(/IPv4 Address[.\s]*:\s*([\d.]+)/g) || []
      lanIps = matches.map(m => m.match(/([\d.]+)$/)[1]).filter(ip => ip !== '127.0.0.1')
    } catch {}

    // Prefer 192.168.x.x addresses (typical home/office LAN)
    const preferredIp = lanIps.find(ip => ip.startsWith('192.168.')) || lanIps[0] || 'localhost'

    const apks = listAllApks()
    const downloadUrls = {}
    for (const apk of apks) {
      const key = `${apk.variant}-${apk.buildType}`
      downloadUrls[key] = {
        url: `http://${preferredIp}:${PORT}/api/dev/apk/download?variant=${apk.variant}&buildType=${apk.buildType}`,
        filename: apk.filename,
        size: apk.size,
        variant: apk.variant,
        buildType: apk.buildType
      }
    }

    return json(res, {
      lanIp: preferredIp,
      allIps: lanIps,
      port: PORT,
      baseUrl: `http://${preferredIp}:${PORT}`,
      downloadUrls
    })
  }

  // POST /api/dev/adb/connect — connect to device via ADB WiFi
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/connect') {
    const body = await parseBody(req)
    const ip = body.ip
    const port = body.port || 5555
    if (!ip) return json(res, { error: 'No IP provided' }, 400)

    try {
      const output = execSync(`"${ADB}" connect ${ip}:${port}`, { encoding: 'utf-8', timeout: 10000 })
      const connected = output.includes('connected')
      if (connected) {
        return json(res, { success: true, message: `Connected to ${ip}:${port}`, output: output.trim() })
      } else {
        return json(res, { success: false, error: output.trim() })
      }
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 200) }, 500)
    }
  }

  // POST /api/dev/adb/pair — pair with device for wireless debugging
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/pair') {
    const body = await parseBody(req)
    const ip = body.ip
    const port = body.port
    const code = body.code
    if (!ip || !port || !code) return json(res, { error: 'ip, port, and code required' }, 400)

    try {
      // ADB 34+: pass code as CLI argument; older: stdin fallback
      const result = await new Promise((resolve, reject) => {
        const proc = spawn(`"${ADB}"`, ['pair', `${ip}:${port}`, code], { shell: true })
        let stdout = '', stderr = ''
        proc.stdout?.on('data', d => {
          stdout += d.toString()
          if (d.toString().toLowerCase().includes('enter')) {
            try { proc.stdin?.write(code + '\n') } catch {}
          }
        })
        proc.stderr?.on('data', d => { stderr += d.toString() })
        const timer = setTimeout(() => { proc.kill(); reject(new Error('Pairing timeout (30s). Make sure the pairing dialog is open on the phone and the code hasn\'t expired.')) }, 30000)
        proc.on('close', c => { clearTimeout(timer); resolve({ code: c, output: (stdout + stderr).trim() }) })
        proc.on('error', err => { clearTimeout(timer); reject(err) })
      })
      const success = result.output.toLowerCase().includes('success') || result.code === 0
      return json(res, { success, message: result.output || (success ? 'Paired' : 'Failed') })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 400) }, 500)
    }
  }

  // POST /api/dev/adb/install — direct install APK via ADB to connected device
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/install') {
    const body = await parseBody(req)
    const variant = body.variant || 'user'
    const buildType = body.buildType || 'debug'
    const serial = body.serial || null // specific device serial, or null for default

    const apkPath = getApkPath(variant, buildType)
    if (!existsSync(apkPath)) {
      return json(res, { error: `No ${variant} ${buildType} APK built yet. Build first.` }, 400)
    }

    if (!isAnyDeviceConnected()) {
      return json(res, { error: 'No device connected. Connect via USB or ADB WiFi first.' }, 400)
    }

    try {
      const serialFlag = serial ? ['-s', serial] : []
      const args = [...serialFlag, 'install', '-r', apkPath]
      await runCmd(`"${ADB}"`, args)
      const flavor = FLAVORS[variant]
      return json(res, { success: true, message: `${flavor.label} (${buildType}) installed on device${serial ? ' ' + serial : ''}` })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 300) }, 500)
    }
  }

  // POST /api/dev/adb/disconnect — disconnect ADB WiFi device
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/disconnect') {
    const body = await parseBody(req)
    const target = body.target // 'ip:port' or 'all'
    try {
      if (target === 'all') {
        execSync(`"${ADB}" disconnect`, { encoding: 'utf-8', timeout: 5000 })
        return json(res, { success: true, message: 'Disconnected all WiFi devices' })
      } else if (target) {
        const output = execSync(`"${ADB}" disconnect ${target}`, { encoding: 'utf-8', timeout: 5000 })
        return json(res, { success: true, message: output.trim() || `Disconnected ${target}` })
      }
      return json(res, { error: 'No target specified' }, 400)
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 200) }, 500)
    }
  }

  // POST /api/dev/adb/tester-save — save ADB connection info for a specific tester
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-save') {
    const body = await parseBody(req)
    const { email, ip, port } = body
    if (!email) return json(res, { error: 'email required' }, 400)
    if (!ip) return json(res, { error: 'ip required' }, 400)
    const conn = setTesterConnection(email, { ip, port: port || 5555, paired: false, connected: false })
    return json(res, { success: true, connection: conn })
  }

  // POST /api/dev/adb/tester-pair — pair a specific tester's device
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-pair') {
    const body = await parseBody(req)
    const { email, ip, pairPort, pairCode } = body
    if (!email || !ip || !pairPort || !pairCode) return json(res, { error: 'email, ip, pairPort, pairCode required' }, 400)

    try {
      // ADB 34+ supports: adb pair ip:port CODE (as CLI argument)
      // Older ADB uses stdin prompt. Try CLI arg first, then stdin fallback.
      const result = await new Promise((resolve, reject) => {
        const proc = spawn(`"${ADB}"`, ['pair', `${ip}:${pairPort}`, pairCode], {
          shell: true
        })
        let stdout = '', stderr = ''
        proc.stdout?.on('data', d => {
          stdout += d.toString()
          // Fallback: if ADB still prompts for code via stdin, send it
          if (d.toString().toLowerCase().includes('enter')) {
            try { proc.stdin?.write(pairCode + '\n') } catch {}
          }
        })
        proc.stderr?.on('data', d => { stderr += d.toString() })
        const timer = setTimeout(() => {
          proc.kill()
          reject(new Error(`Pairing timeout (30s). Make sure:\n1. Wireless Debugging is ON on the phone\n2. The "Pair device with pairing code" dialog is OPEN on the phone\n3. Pair code hasn't expired (they last ~60s)\n4. Phone and PC are on the same WiFi network\n5. Port ${pairPort} is the PAIRING port (shown in the pairing dialog, NOT the main Wireless Debugging port)`))
        }, 30000)
        proc.on('close', code => {
          clearTimeout(timer)
          const output = stdout + stderr
          resolve({ code, output: output.trim() })
        })
        proc.on('error', err => { clearTimeout(timer); reject(err) })
      })

      const success = result.output.toLowerCase().includes('success') || result.code === 0
      if (success) {
        setTesterConnection(email, { ip, pairPort, paired: true })
      }
      return json(res, { success, message: result.output || (success ? 'Paired successfully' : 'Pairing failed') })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 400) }, 500)
    }
  }

  // POST /api/dev/adb/tester-connect — connect a specific tester's device via ADB WiFi
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-connect') {
    const body = await parseBody(req)
    const { email, ip, port } = body
    if (!email) return json(res, { error: 'email required' }, 400)

    // Use provided ip/port or fall back to saved connection
    const conn = getTesterConnection(email)
    const connectIp = ip || conn?.ip
    const connectPort = port || conn?.port || 5555
    if (!connectIp) return json(res, { error: 'No IP configured for this tester. Set connection details first.' }, 400)

    // Always save the IP/port when connecting
    setTesterConnection(email, { ip: connectIp, port: connectPort })

    try {
      const output = execSync(`"${ADB}" connect ${connectIp}:${connectPort}`, { encoding: 'utf-8', timeout: 15000 })
      const connected = output.includes('connected') && !output.includes('cannot')
      if (connected) {
        setTesterConnection(email, { connected: true })
        // Find the serial of the connected device
        const devices = getConnectedDevices()
        const match = devices.find(d => d.serial.includes(connectIp))
        const serial = match?.serial || `${connectIp}:${connectPort}`
        setTesterConnection(email, { serial })
        return json(res, { success: true, message: `Connected to ${connectIp}:${connectPort}`, output: output.trim() })
      } else {
        const hint = output.includes('refused') ? ' — Is Wireless Debugging enabled on the phone?' :
                     output.includes('timeout') ? ' — Phone may be on a different network or firewall is blocking.' :
                     output.includes('already') ? '' : ' — Make sure you paired first.'
        return json(res, { success: false, error: `${output.trim()}${hint}` })
      }
    } catch (err) {
      return json(res, { success: false, error: `Connection failed: ${err.message.substring(0, 200)}. Ensure Wireless Debugging is enabled and the port matches what's shown on the phone.` }, 500)
    }
  }

  // POST /api/dev/adb/tester-disconnect — disconnect a specific tester's device
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-disconnect') {
    const body = await parseBody(req)
    const { email } = body
    if (!email) return json(res, { error: 'email required' }, 400)
    const conn = getTesterConnection(email)
    if (!conn || !conn.ip) return json(res, { error: 'No connection saved for this tester' }, 400)

    try {
      const target = `${conn.ip}:${conn.port || 5555}`
      execSync(`"${ADB}" disconnect ${target}`, { encoding: 'utf-8', timeout: 5000 })
      setTesterConnection(email, { connected: false, serial: null })
      return json(res, { success: true, message: `Disconnected ${target}` })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 200) }, 500)
    }
  }

  // POST /api/dev/adb/tester-install — install APK to a specific tester's connected device
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-install') {
    const body = await parseBody(req)
    const { email, variant, buildType } = body
    if (!email) return json(res, { error: 'email required' }, 400)

    const v = variant || 'user'
    const bt = buildType || 'debug'
    const apkPath = getApkPath(v, bt)
    if (!existsSync(apkPath)) {
      return json(res, { error: `No ${v} ${bt} APK built yet. Build first.` }, 400)
    }

    // Find tester's device
    const conn = getTesterConnection(email)
    if (!conn || !conn.ip) {
      return json(res, { error: 'NO_DEVICE_CONFIGURED', detail: 'No device configured for this tester. Set up ADB WiFi connection first.' }, 400)
    }

    // Check if tester's device is actually connected
    const devices = getConnectedDevices()
    const match = devices.find(d => d.serial.includes(conn.ip))
    if (!match) {
      return json(res, { error: 'DEVICE_NOT_CONNECTED', detail: `Device ${conn.ip} is not connected. Reconnect via ADB WiFi.`, ip: conn.ip }, 400)
    }

    try {
      const args = ['-s', match.serial, 'install', '-r', apkPath]
      await runCmd(`"${ADB}"`, args)
      const flavor = FLAVORS[v]
      recordDistribution(email, v, bt, `Direct ADB install to ${match.serial}`)
      return json(res, { success: true, message: `${flavor.label} (${bt}) installed on ${match.model || match.serial}` })
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 300) }, 500)
    }
  }

  // POST /api/dev/adb/tester-install-all — install APK to ALL connected tester devices
  if (req.method === 'POST' && url.pathname === '/api/dev/adb/tester-install-all') {
    const body = await parseBody(req)
    const { variant, buildType } = body
    const v = variant || 'user'
    const bt = buildType || 'debug'
    const apkPath = getApkPath(v, bt)
    if (!existsSync(apkPath)) {
      return json(res, { error: `No ${v} ${bt} APK built yet. Build first.` }, 400)
    }

    const devices = getConnectedDevices()
    if (devices.length === 0) {
      return json(res, { error: 'No devices connected. Connect at least one device via ADB WiFi first.' }, 400)
    }

    const results = []
    const flavor = FLAVORS[v]

    // Install to every connected device
    for (const device of devices) {
      try {
        const args = ['-s', device.serial, 'install', '-r', apkPath]
        await runCmd(`"${ADB}"`, args)
        // Find which tester owns this device
        const testerEmail = Object.keys(deviceRegistry).find(email => {
          const conn = getTesterConnection(email)
          return conn?.ip && device.serial.includes(conn.ip)
        })
        if (testerEmail) {
          recordDistribution(testerEmail, v, bt, `Batch ADB install to ${device.serial}`)
        }
        results.push({ serial: device.serial, model: device.model, success: true, tester: testerEmail || null })
      } catch (err) {
        results.push({ serial: device.serial, model: device.model, success: false, error: err.message.substring(0, 200) })
      }
    }

    const successCount = results.filter(r => r.success).length
    return json(res, {
      success: successCount > 0,
      message: `${flavor.label} (${bt}) installed on ${successCount}/${results.length} device(s)`,
      results,
      successCount,
      totalDevices: results.length
    })
  }

  // POST /api/dev/emulator/action — emulator quick actions for App Testing
  if (req.method === 'POST' && url.pathname === '/api/dev/emulator/action') {
    const body = await parseBody(req)
    const action = body.action
    try {
      if (action === 'launch') {
        if (isEmulatorRunning()) {
          return json(res, { success: true, message: 'Emulator already running', alreadyRunning: true })
        }
        const emuProc = spawn(`"${EMULATOR_BIN}"`, ['-avd', AVD_NAME, '-gpu', 'host'], {
          shell: true, detached: true, stdio: 'ignore'
        })
        emuProc.unref()
        return json(res, { success: true, message: `Launching AVD ${AVD_NAME}...` })

      } else if (action === 'install-main') {
        if (!isEmulatorRunning()) return json(res, { success: false, error: 'Emulator not running' }, 400)
        const apkPath = getApkPath('user', 'debug')
        if (!existsSync(apkPath)) return json(res, { success: false, error: 'Main APK not built yet. Build first.' }, 400)
        await runCmd(`"${ADB}"`, ['install', '-r', apkPath])
        return json(res, { success: true, message: 'Main app installed on emulator' })

      } else if (action === 'install-admin') {
        if (!isEmulatorRunning()) return json(res, { success: false, error: 'Emulator not running' }, 400)
        const apkPath = getApkPath('admin', 'debug')
        if (!existsSync(apkPath)) return json(res, { success: false, error: 'Admin APK not built yet. Build first.' }, 400)
        await runCmd(`"${ADB}"`, ['install', '-r', apkPath])
        return json(res, { success: true, message: 'Admin app installed on emulator' })

      } else if (action === 'screenshot') {
        if (!isEmulatorRunning()) return json(res, { success: false, error: 'Emulator not running' }, 400)
        const ts = new Date().toISOString().replace(/[:.]/g, '-')
        const screenshotPath = path.join(PROJECT_ROOT, '..', 'screenshots', `screen-${ts}.png`)
        const dir = path.dirname(screenshotPath)
        if (!existsSync(dir)) { const { mkdirSync } = await import('node:fs'); mkdirSync(dir, { recursive: true }) }
        await runCmd(`"${ADB}"`, ['exec-out', 'screencap', '-p', '>', `"${screenshotPath}"`])
        return json(res, { success: true, message: `Screenshot saved: ${screenshotPath}`, path: screenshotPath })

      } else if (action === 'clear-data') {
        if (!isAnyDeviceConnected()) return json(res, { success: false, error: 'No device connected (emulator or USB)' }, 400)
        const pkg = body.packageName || 'com.app.billsense'
        await runCmd(`"${ADB}"`, ['shell', 'pm', 'clear', pkg])
        return json(res, { success: true, message: `App data cleared for ${pkg}` })

      } else if (action === 'logcat') {
        if (!isAnyDeviceConnected()) return json(res, { success: false, error: 'No device connected (emulator or USB)' }, 400)
        // Get recent logcat (last 50 lines)
        const output = execSync(`"${ADB}" logcat -d -t 50 *:W`, { encoding: 'utf-8', timeout: 5000 })
        return json(res, { success: true, message: 'Logcat retrieved', logs: output.split('\n').slice(-50) })

      } else if (action === 'devices') {
        // Return list of all connected ADB devices
        const devices = getConnectedDevices()
        return json(res, { success: true, message: `${devices.length} device(s) connected`, devices })

      } else {
        return json(res, { success: false, error: `Unknown action: ${action}` }, 400)
      }
    } catch (err) {
      return json(res, { success: false, error: err.message.substring(0, 200) }, 500)
    }
  }

  // GET /api/dev/emulator/status — quick emulator + device check
  if (req.method === 'GET' && url.pathname === '/api/dev/emulator/status') {
    const running = isEmulatorRunning()
    const booted = running ? isBootComplete() : false
    const devices = getConnectedDevices()
    const anyDevice = isAnyDeviceConnected()
    return json(res, { running, booted, avd: AVD_NAME, devices, anyDevice })
  }

  json(res, { error: 'Not found' }, 404)
})

server.listen(PORT, () => {
  const env = checkEnvironment()
  console.log(``)
  console.log(`  ========================================`)
  console.log(`  BillSense Dev Server v3.1`)
  console.log(`  http://localhost:${PORT}`)
  console.log(`  ========================================`)
  console.log(`  Environment:`)
  console.log(`    Java 17:     ${env.javaHome ? 'OK' : 'MISSING'} (${JAVA_HOME})`)
  console.log(`    Android SDK: ${env.androidSdk ? 'OK' : 'MISSING'} (${ANDROID_HOME})`)
  console.log(`    Emulator:    ${env.emulator ? 'OK' : 'MISSING'}`)
  console.log(`    Gradlew:     ${env.gradlew ? 'OK' : 'MISSING'}`)
  console.log(`    AVDs:        ${env.avd.length > 0 ? env.avd.join(', ') : 'none'}`)
  console.log(`  ----------------------------------------`)
  console.log(`  Flavors: user (com.app.billsense), admin (com.admin.billsense)`)
  console.log(`  Build Types: debug, release`)
  console.log(`  ----------------------------------------`)
  console.log(`  POST /api/dev/build            — build APK`)
  console.log(`  GET  /api/dev/build/status     — build progress`)
  console.log(`  POST /api/dev/pipeline         — emulator+build+install+launch`)
  console.log(`  GET  /api/dev/apk/list         — list built APKs`)
  console.log(`  GET  /api/dev/apk/download     — download APK`)
  console.log(`  POST /api/dev/distribute       — build + Firebase App Distribution`)
  console.log(`  GET  /api/dev/distribute/status — distribution progress`)
  console.log(`  POST /api/dev/distribute/notify — re-send notification`)
  console.log(`  GET  /api/dev/distribute/releases — list releases`)
  console.log(`  POST /api/dev/emulator/action  — emulator quick actions`)
  console.log(`  GET  /api/dev/emulator/status  — emulator status`)
  console.log(`  GET  /api/dev/network/info      — LAN IP + download URLs`)
  console.log(`  POST /api/dev/adb/connect       — ADB WiFi connect`)
  console.log(`  POST /api/dev/adb/pair          — ADB WiFi pair`)
  console.log(`  POST /api/dev/adb/install       — direct ADB install`)
  console.log(`  ========================================`)
  console.log(``)
})
