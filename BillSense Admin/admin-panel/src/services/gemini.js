// Shared Gemini client with auto-rotating model alias + free-tier fallback chain.
// Used by Billy chat (/billy) and GitNexus repo analysis.
//
// Why: gemini-pro-latest is Google's auto-rotating "newest Pro" alias — today it
// resolves to Gemini 3.1 Pro Preview, tomorrow it might be 3.2 / 4.0 without any
// code change here. But the free-tier Pro daily quota is tiny (~50 requests), so
// we walk the chain on 429/503 instead of dying.

const KEY = import.meta.env.VITE_GEMINI_API_KEY || ''

export const MODEL_CHAIN = [
  'gemini-pro-latest',     // newest Pro — best answers, smallest free quota
  'gemini-flash-latest',   // newest Flash — currently Gemini 3 Flash Preview
  'gemini-2.5-flash-lite'  // bulletproof free tier
]

const API = (model) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${KEY}`

export const hasGeminiKey = () => !!KEY

/**
 * Call Gemini, walking MODEL_CHAIN on 429/503 errors.
 *
 * @param {object} opts
 * @param {string} opts.systemPrompt   - System instruction
 * @param {Array}  opts.history        - [{role: 'user'|'model', text: '...'}]
 * @param {string} [opts.userMessage]  - Convenience: appends a user turn to history
 * @param {object} [opts.generationConfig] - { temperature, maxOutputTokens, ... }
 * @param {number} [opts.timeoutMs=45000]
 * @returns {Promise<{text, model, version, latencyMs, fallback, attempts}>}
 */
export async function chat(opts) {
  if (!KEY) throw new Error('VITE_GEMINI_API_KEY is not configured at build time.')

  const history = (opts.history || []).map(m => ({
    role: m.role === 'assistant' ? 'model' : m.role,
    parts: [{ text: m.text }]
  }))
  if (opts.userMessage) {
    history.push({ role: 'user', parts: [{ text: opts.userMessage }] })
  }

  const payload = {
    systemInstruction: opts.systemPrompt
      ? { parts: [{ text: opts.systemPrompt }] }
      : undefined,
    contents: history,
    generationConfig: opts.generationConfig || { temperature: 0.7, maxOutputTokens: 1024 }
  }
  const timeoutMs = opts.timeoutMs ?? 45000
  const started = performance.now()
  const attempts = []

  for (let i = 0; i < MODEL_CHAIN.length; i++) {
    const model = MODEL_CHAIN[i]
    try {
      const ctrl = new AbortController()
      const t = setTimeout(() => ctrl.abort(), timeoutMs)
      const res = await fetch(API(model), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: ctrl.signal
      })
      clearTimeout(t)

      if (!res.ok) {
        const body = await res.text()
        attempts.push({ model, status: res.status, error: body.slice(0, 200) })
        if ((res.status === 429 || res.status === 503) && i < MODEL_CHAIN.length - 1) continue
        throw new Error(`HTTP ${res.status}: ${body.slice(0, 300)}`)
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
