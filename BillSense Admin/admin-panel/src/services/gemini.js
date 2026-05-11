// Shared Gemini client.
//
// The browser never sees a Gemini API key. Requests go to the same-origin
// server-side proxy at /api/gemini/chat, which holds the key (read from a
// file on the cPanel host) and forwards to Google's Generative Language API.
//
// We still walk a Pro -> Flash -> Flash-Lite chain on 429/503 because Pro
// has a tight free-tier and even paid tier can return overloaded errors.

export const MODEL_CHAIN = [
  'gemini-pro-latest',     // newest Pro — best, smallest free quota
  'gemini-flash-latest',   // newest Flash — Gemini 3 Flash Preview today
  'gemini-2.5-flash-lite'  // bulletproof fallback
]

const PROXY = '/api/gemini/chat'

let cachedKeyState = null
export async function hasGeminiKey() {
  if (cachedKeyState !== null) return cachedKeyState
  try {
    const r = await fetch('/api/gemini/health', { signal: AbortSignal.timeout(5000) })
    if (!r.ok) return (cachedKeyState = false)
    const j = await r.json()
    return (cachedKeyState = !!j.keyConfigured)
  } catch {
    return (cachedKeyState = false)
  }
}

/**
 * Call the server-side proxy, walking MODEL_CHAIN on 429/503.
 *
 * @param {object} opts
 * @param {string} opts.systemPrompt
 * @param {Array}  opts.history             [{role:'user'|'assistant', text}]
 * @param {string} [opts.userMessage]
 * @param {object} [opts.generationConfig]
 * @param {number} [opts.timeoutMs=60000]
 * @returns {Promise<{text, model, version, latencyMs, fallback, attempts}>}
 */
export async function chat(opts) {
  const started = performance.now()
  const attempts = []
  const history = (opts.history || []).map(m => ({
    role: m.role === 'assistant' ? 'model' : m.role,
    text: m.text
  }))
  const timeoutMs = opts.timeoutMs ?? 60000

  for (let i = 0; i < MODEL_CHAIN.length; i++) {
    const model = MODEL_CHAIN[i]
    try {
      const ctrl = new AbortController()
      const t = setTimeout(() => ctrl.abort(), timeoutMs)
      const res = await fetch(PROXY, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model,
          systemPrompt: opts.systemPrompt,
          history,
          userMessage: opts.userMessage,
          generationConfig: opts.generationConfig || { temperature: 0.7, maxOutputTokens: 1024 }
        }),
        signal: ctrl.signal
      })
      clearTimeout(t)

      // The proxy mirrors Google's status code via X-Upstream-Status. If
      // the proxy itself can't reach Google, it returns 502/504.
      const upstream = parseInt(res.headers.get('x-upstream-status') || res.status, 10)

      if (!res.ok) {
        const body = await res.text()
        attempts.push({ model, status: res.status, upstream, error: body.slice(0, 200) })
        if ((upstream === 429 || upstream === 503 || res.status === 503) &&
            i < MODEL_CHAIN.length - 1) continue
        throw new Error(`HTTP ${res.status}${upstream !== res.status ? ` (upstream ${upstream})` : ''}: ${body.slice(0, 300)}`)
      }

      const data = await res.json()
      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text
        || '(empty response from Gemini)'
      return {
        text,
        model,
        version: data.modelVersion || '',
        latencyMs: Math.round(performance.now() - started),
        fallback: i > 0,
        attempts
      }
    } catch (e) {
      attempts.push({ model, error: e.message })
      if (i >= MODEL_CHAIN.length - 1) {
        const err = new Error('All Gemini tiers failed')
        err.attempts = attempts
        throw err
      }
    }
  }
}
