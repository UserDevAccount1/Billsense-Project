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

// Pure-JS SHA-256. We deliberately do NOT use crypto.subtle: it only
// exists in a "secure context" (HTTPS or localhost). The cPanel domain
// can be opened over plain http://, where crypto.subtle is undefined and
// login would throw and be impossible. This implementation works in any
// context, producing the identical digest.
function sha256Hex(ascii) {
  function rightRotate(v, a) { return (v >>> a) | (v << (32 - a)) }
  const mathPow = Math.pow
  const maxWord = mathPow(2, 32)
  let result = ''
  const words = []
  const asciiBitLength = ascii.length * 8

  let hash = sha256Hex.h = sha256Hex.h || []
  const k = sha256Hex.k = sha256Hex.k || []
  let primeCounter = k.length
  const isComposite = {}
  for (let candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (let i = 0; i < 313; i += candidate) isComposite[i] = candidate
      hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0
      k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0
    }
  }

  ascii += '\x80'
  while (ascii.length % 64 - 56) ascii += '\x00'
  for (let i = 0; i < ascii.length; i++) {
    const j = ascii.charCodeAt(i)
    if (j >> 8) return ''        // ASCII only (our creds are ASCII)
    words[i >> 2] |= j << ((3 - i) % 4) * 8
  }
  words[words.length] = (asciiBitLength / maxWord) | 0
  words[words.length] = asciiBitLength

  for (let j = 0; j < words.length;) {
    const w = words.slice(j, j += 16)
    const oldHash = hash
    hash = hash.slice(0, 8)
    for (let i = 0; i < 64; i++) {
      const w15 = w[i - 15], w2 = w[i - 2]
      const a = hash[0], e = hash[4]
      const temp1 = hash[7] +
        (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)) +
        ((e & hash[5]) ^ ((~e) & hash[6])) +
        k[i] +
        (w[i] = (i < 16) ? w[i] : (
          w[i - 16] +
          (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) +
          w[i - 7] +
          (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))
        ) | 0)
      const temp2 = (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) +
        ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]))
      hash = [(temp1 + temp2) | 0].concat(hash)
      hash[4] = (hash[4] + temp1) | 0
    }
    for (let i = 0; i < 8; i++) hash[i] = (hash[i] + oldHash[i]) | 0
  }

  for (let i = 0; i < 8; i++) {
    for (let j = 3; j + 1; j--) {
      const b = (hash[i] >> (j * 8)) & 255
      result += ((b < 16) ? 0 : '') + b.toString(16)
    }
  }
  return result
}

export async function login(username, password) {
  const hash = sha256Hex(`${username}:${password}`)
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
