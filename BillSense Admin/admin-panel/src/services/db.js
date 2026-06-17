// Firebase RTDB access — routed through the authenticated cPanel proxy.
//
// The browser NEVER talks to RTDB directly. All reads/writes go to
// /api/db/* on the cPanel Node app, which authenticates with a Firebase
// service account (server-only key). This lets the RTDB security rules
// deny all public access while the dashboard keeps working.
//
// Same exported API as before (list/value/count/patch/remove/timeAgo)
// so the views are unchanged.

const CPANEL_PROXY_ORIGIN = 'https://billsense.dev-environment.site'

function proxyBase() {
  if (typeof window === 'undefined') return CPANEL_PROXY_ORIGIN
  return window.location.hostname === 'billsense.dev-environment.site'
    ? ''                       // same-origin on cPanel
    : CPANEL_PROXY_ORIGIN      // cross-origin from Firebase / localhost
}

async function dbCall(op, pathOrBody) {
  const body = typeof pathOrBody === 'string' ? { path: pathOrBody } : pathOrBody
  const ctrl = new AbortController()
  const t = setTimeout(() => ctrl.abort(), 20000)
  try {
    const r = await fetch(`${proxyBase()}/api/db/${op}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: ctrl.signal
    })
    const j = await r.json().catch(() => ({}))
    if (!r.ok || j.ok === false) {
      throw new Error(j.error || `db/${op}: HTTP ${r.status}`)
    }
    return j.data
  } finally {
    clearTimeout(t)
  }
}

let _saState = null
export async function dbReady() {
  if (_saState !== null) return _saState
  try {
    const r = await fetch(`${proxyBase()}/api/db/health`, { signal: AbortSignal.timeout(6000) })
    if (!r.ok) return (_saState = false)
    const j = await r.json()
    return (_saState = !!j.saConfigured)
  } catch {
    return (_saState = false)
  }
}

// Array of { _key, ...record } from a collection object.
export async function list(path) {
  const obj = await dbCall('get', path)
  if (!obj || typeof obj !== 'object') return []
  return Object.entries(obj).map(([_key, v]) =>
    (v && typeof v === 'object') ? { _key, ...v } : { _key, _value: v }
  )
}

export async function value(path) {
  return dbCall('get', path)
}

export async function count(path) {
  const obj = await dbCall('get', path)
  return obj && typeof obj === 'object' ? Object.keys(obj).length : 0
}

export async function patch(path, partial) {
  return dbCall('patch', { path, data: partial })
}

export async function remove(path) {
  await dbCall('delete', { path })
  return true
}

// Update specific fields on an existing node (RTDB PATCH = merge).
export async function update(path, fields) {
  return dbCall('patch', { path, data: fields })
}

// Firebase-style push id: 20-char, chronologically ordered, collision-resistant.
// Lets us create children with the same key shape the mobile app generates,
// using only the proxy's PATCH op (no server-side push needed).
const PUSH_CHARS = '-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz'
let _lastPushTime = 0
const _lastRand = []
export function pushId() {
  let now = Date.now()
  const dup = now === _lastPushTime
  _lastPushTime = now
  const ts = new Array(8)
  for (let i = 7; i >= 0; i--) { ts[i] = PUSH_CHARS.charAt(now % 64); now = Math.floor(now / 64) }
  let id = ts.join('')
  if (!dup) { for (let i = 0; i < 12; i++) _lastRand[i] = Math.floor(Math.random() * 64) }
  else { let i = 11; for (; i >= 0 && _lastRand[i] === 63; i--) _lastRand[i] = 0; _lastRand[i]++ }
  for (let i = 0; i < 12; i++) id += PUSH_CHARS.charAt(_lastRand[i])
  return id
}

export function timeAgo(input) {
  if (!input) return ''
  let ms
  if (typeof input === 'number') ms = input < 1e12 ? input * 1000 : input
  else { const d = new Date(input); ms = isNaN(d) ? null : d.getTime() }
  if (!ms) return String(input)
  const diff = Date.now() - ms
  const s = Math.floor(diff / 1000)
  if (s < 60) return `${s}s ago`
  const m = Math.floor(s / 60); if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60); if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24); if (d < 30) return `${d}d ago`
  return new Date(ms).toLocaleDateString()
}
