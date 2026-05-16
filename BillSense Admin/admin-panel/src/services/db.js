// Firebase RTDB read helper via the REST endpoint.
//
// The dashboard only READS data. The RTDB currently allows public read, so
// the REST endpoint works without auth and avoids SDK weight/edge-cases.
// Path names match the Android app's real schema (capitalised, spaces):
//   Users · Cases · "Voting Posts" · Detections · "Standard Scan" ·
//   "Multi Scan" · "Video Scan" · session_reports · ml_config ·
//   billy_analytics · Bills · Announcements · Notifications
//
// If rules are later locked to auth!=null, swap fetchJson() to use the
// Firebase SDK + an ID token. The view code below won't need changes.

const DB = 'https://bill-sense-aec6b-default-rtdb.firebaseio.com'

async function fetchJson(path, query = '') {
  const url = `${DB}/${path.split('/').map(encodeURIComponent).join('/')}.json` +
              (query ? `?${query}` : '')
  const ctrl = new AbortController()
  const t = setTimeout(() => ctrl.abort(), 15000)
  try {
    const r = await fetch(url, { signal: ctrl.signal })
    clearTimeout(t)
    if (!r.ok) throw new Error(`RTDB ${path}: HTTP ${r.status}`)
    return await r.json()
  } finally {
    clearTimeout(t)
  }
}

// Returns an array of { _key, ...record } from a collection object.
export async function list(path) {
  const obj = await fetchJson(path)
  if (!obj || typeof obj !== 'object') return []
  return Object.entries(obj).map(([_key, v]) =>
    (v && typeof v === 'object') ? { _key, ...v } : { _key, _value: v }
  )
}

// Raw value at a path.
export async function value(path) {
  return fetchJson(path)
}

function pathUrl(path) {
  return `${DB}/${path.split('/').map(encodeURIComponent).join('/')}.json`
}

// Merge fields into an existing record (RTDB PATCH).
export async function patch(path, partial) {
  const r = await fetch(pathUrl(path), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(partial),
    signal: AbortSignal.timeout(15000)
  })
  if (!r.ok) throw new Error(`PATCH ${path}: HTTP ${r.status}`)
  return r.json()
}

// Delete a record/subtree (RTDB DELETE).
export async function remove(path) {
  const r = await fetch(pathUrl(path), {
    method: 'DELETE',
    signal: AbortSignal.timeout(15000)
  })
  if (!r.ok) throw new Error(`DELETE ${path}: HTTP ${r.status}`)
  return true
}

// Count of children without pulling all data.
export async function count(path) {
  const shallow = await fetchJson(path, 'shallow=true')
  return shallow && typeof shallow === 'object' ? Object.keys(shallow).length : 0
}

// Best-effort relative time.
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
