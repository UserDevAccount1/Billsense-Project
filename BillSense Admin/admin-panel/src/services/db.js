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
