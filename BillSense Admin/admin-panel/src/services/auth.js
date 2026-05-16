// Lightweight client-side auth gate for the BillSense admin dashboard.
//
// IMPORTANT: this is a SOFT gate. The check runs in the browser, so a
// determined user can read the bundle or bypass the router. It keeps casual
// visitors out — it is not a security boundary. For real protection, move
// auth server-side (cPanel proxy session, or Firebase Auth).
//
// Credentials are NOT stored in plaintext. We store sha256("user:pass") and
// compare against the hash of what the user types.

const CRED_HASH = '4f62caf7d2c1ca3626c9db8771ace186235c151f72f029c6a7f214ada560b4ec' // Billsense:admin
const SESSION_KEY = 'billsense_auth'
const SESSION_TTL_MS = 1000 * 60 * 60 * 8 // 8 hours

async function sha256Hex(str) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(str))
  return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join('')
}

export async function login(username, password) {
  const hash = await sha256Hex(`${username}:${password}`)
  if (hash !== CRED_HASH) return false
  const token = { ok: true, ts: Date.now() }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(token))
  return true
}

export function isAuthenticated() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return false
    const { ok, ts } = JSON.parse(raw)
    if (!ok) return false
    if (Date.now() - ts > SESSION_TTL_MS) {
      sessionStorage.removeItem(SESSION_KEY)
      return false
    }
    return true
  } catch {
    return false
  }
}

export function logout() {
  sessionStorage.removeItem(SESSION_KEY)
}
