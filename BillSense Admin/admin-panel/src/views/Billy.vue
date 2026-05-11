<template>
  <div class="billy-view">
    <header class="billy-header">
      <div class="title-block">
        <span class="material-icons brand-icon">smart_toy</span>
        <div>
          <h1>Billy</h1>
          <p class="subtitle">AI assistant for Philippine peso authentication</p>
        </div>
      </div>
      <div class="status" :class="statusClass">
        <span class="material-icons">{{ statusIcon }}</span>
        <span>{{ statusLabel }}</span>
      </div>
    </header>

    <div ref="chatScroll" class="chat-scroll">
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div class="bubble">
          <div class="meta">
            <span class="material-icons">{{ m.role === 'user' ? 'person' : 'smart_toy' }}</span>
            <span>{{ m.role === 'user' ? 'You' : 'Billy' }}</span>
            <span v-if="m.latency" class="latency">{{ m.latency }}ms</span>
          </div>
          <div class="text" v-html="renderMarkdown(m.text)"></div>
        </div>
      </div>
      <div v-if="loading" class="msg assistant">
        <div class="bubble typing">
          <div class="meta">
            <span class="material-icons">smart_toy</span>
            <span>Billy</span>
          </div>
          <div class="text"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
        </div>
      </div>
    </div>

    <div class="suggestions" v-if="messages.length <= 1 && !loading">
      <button v-for="s in suggestions" :key="s" @click="send(s)">{{ s }}</button>
    </div>

    <form class="composer" @submit.prevent="onSubmit">
      <input
        v-model="draft"
        :disabled="loading || !hasKey"
        :placeholder="hasKey ? 'Ask Billy about peso bills, security features, or how to use BillSense...' : 'VITE_GEMINI_API_KEY not configured — set it in GitHub secrets'"
        @keydown.enter.exact.prevent="onSubmit"
        @keydown.enter.shift.exact.stop
        autofocus
      />
      <button type="submit" :disabled="!canSend">
        <span class="material-icons">{{ loading ? 'hourglass_top' : 'send' }}</span>
      </button>
    </form>

    <footer class="footer-note">
      <span class="material-icons">info</span>
      <span>
        Billy runs on Gemini ({{ model }}). The API key is baked into this browser bundle,
        so this works for the BillSense demo but is not appropriate for public production.
        For real production, proxy requests through a server.
      </span>
    </footer>
  </div>
</template>

<script>
const GEMINI_KEY = import.meta.env.VITE_GEMINI_API_KEY || ''
const MODEL = 'gemini-2.5-flash'
const API = (model, key) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`

const SYSTEM_PROMPT = `You are Billy, the AI assistant for BillSense — a Philippine peso counterfeit-detection
app. Your job:

1) Help users tell real Philippine peso bills from counterfeit ones. Talk about specific security
   features: watermark, security thread (windowed/embedded), serial number, see-through register,
   concealed value, optically variable ink (OVI), optically variable device (OVD), tactile marks,
   and the enhanced value panel (EVP) on 500 and 1000-peso bills.
2) Reference the New Generation Currency (NGC) Series specifically — that's what BillSense detects.
3) Explain how to use the BillSense Android app's tools: Scan Bill (single bill in real time),
   Multi-Scan (multiple bills in one image), Video Scan, Compare Bill, and the Step-by-Step
   Detection Guide.
4) Cite Philippine counterfeit laws when relevant (RPC Art. 168 — counterfeiting, BSP Circulars).
5) If asked a non-currency question, redirect politely to currency topics.

Style: friendly, concise, use bullet points and short paragraphs. Use emojis sparingly — 💵 🔍 ✅
are fine, don't overdo it. Always close advice with a clear next-step.

If unsure, say so. Never invent denominations, security features, or laws.`

export default {
  name: 'Billy',
  data() {
    return {
      messages: [
        {
          role: 'assistant',
          text:
            '👋 Hi! I\'m **Billy** — your AI assistant for BillSense.\n\n' +
            'Ask me about:\n' +
            '- Telling real peso bills from counterfeit ones\n' +
            '- Security features like watermarks, threads, OVI, OVD, EVP\n' +
            '- How to use the BillSense Android app\n' +
            '- Counterfeit prevention laws in the Philippines\n\n' +
            'What would you like to know?'
        }
      ],
      draft: '',
      loading: false,
      model: MODEL,
      suggestions: [
        'What security features should I check on a 1000-peso bill?',
        'How do I use the Scan Bill feature?',
        'What is the penalty for counterfeiting in the Philippines?',
        'Difference between OVI and OVD?'
      ]
    }
  },
  computed: {
    hasKey() { return !!GEMINI_KEY },
    canSend() { return this.draft.trim() && !this.loading && this.hasKey },
    statusClass() { return this.hasKey ? 'ok' : 'err' },
    statusIcon() { return this.hasKey ? 'check_circle' : 'error' },
    statusLabel() { return this.hasKey ? `Connected · ${this.model}` : 'No API key' }
  },
  methods: {
    renderMarkdown(text) {
      // Tiny safe-ish markdown renderer — bold, italic, code, line breaks, links.
      const esc = s => s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))
      let out = esc(text)
      out = out.replace(/`([^`\n]+)`/g, '<code>$1</code>')
      out = out.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
      out = out.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      out = out.replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
      out = out.replace(/^- (.+)$/gm, '• $1')
      out = out.replace(/\n/g, '<br>')
      return out
    },
    onSubmit() {
      const text = this.draft.trim()
      if (!text || this.loading || !this.hasKey) return
      this.send(text)
    },
    send(text) {
      this.draft = ''
      this.messages.push({ role: 'user', text })
      this.$nextTick(() => this.scrollBottom())
      this.callGemini(text)
    },
    scrollBottom() {
      const el = this.$refs.chatScroll
      if (el) el.scrollTop = el.scrollHeight
    },
    async callGemini(userText) {
      this.loading = true
      const started = performance.now()
      try {
        // Build chat history in Gemini's contents shape.
        const history = this.messages
          .filter(m => m.text)
          .map(m => ({
            role: m.role === 'assistant' ? 'model' : 'user',
            parts: [{ text: m.text }]
          }))

        const payload = {
          systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
          contents: history,
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 1024
          }
        }

        const ctrl = new AbortController()
        const t = setTimeout(() => ctrl.abort(), 45000)
        const res = await fetch(API(this.model, GEMINI_KEY), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
          signal: ctrl.signal
        })
        clearTimeout(t)

        if (!res.ok) {
          const err = await res.text()
          throw new Error(`HTTP ${res.status}: ${err.slice(0, 300)}`)
        }
        const data = await res.json()
        const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text
          || '(Billy returned an empty response — try rephrasing your question.)'
        const latency = Math.round(performance.now() - started)
        this.messages.push({ role: 'assistant', text: reply, latency })
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          text: `⚠️ I hit an error reaching Gemini: \`${e.message}\`.\n\n` +
                'Check the network tab or try again in a moment.'
        })
      } finally {
        this.loading = false
        this.$nextTick(() => this.scrollBottom())
      }
    }
  }
}
</script>

<style scoped>
.billy-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 1.5rem 2rem;
  gap: 1rem;
  max-width: 980px;
  margin: 0 auto;
  width: 100%;
}

.billy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.title-block { display: flex; align-items: center; gap: 0.75rem; }
.brand-icon {
  font-size: 2.2rem;
  color: #FFA31A;
  background: rgba(255,163,26,0.12);
  padding: 0.6rem;
  border-radius: 12px;
}
.title-block h1 { margin: 0; font-size: 1.4rem; }
.subtitle { margin: 0; font-size: 0.85rem; color: #94A3B8; }
.status {
  display: flex; align-items: center; gap: 0.4rem;
  padding: 0.4rem 0.85rem; border-radius: 999px;
  font-size: 0.85rem;
}
.status .material-icons { font-size: 1.05rem; }
.status.ok  { background: rgba(34,197,94,0.12);  color: #4ADE80; }
.status.err { background: rgba(248,113,113,0.12); color: #F87171; }

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.bubble {
  max-width: 720px;
  padding: 0.85rem 1rem;
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
}
.msg.user .bubble {
  background: rgba(99,102,241,0.12);
  border-color: rgba(99,102,241,0.25);
}
.meta {
  display: flex; align-items: center; gap: 0.4rem;
  font-size: 0.78rem; color: #94A3B8; margin-bottom: 0.35rem;
}
.meta .material-icons { font-size: 0.95rem; }
.latency { margin-left: auto; opacity: 0.7; }
.text { font-size: 0.95rem; line-height: 1.55; word-wrap: break-word; }
.text :deep(code) {
  background: rgba(0,0,0,0.3); padding: 0.1rem 0.4rem; border-radius: 4px;
  font-size: 0.85em;
}
.text :deep(a) { color: #93C5FD; }

.typing .text { display: flex; gap: 0.3rem; padding: 0.3rem 0; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: #64748B;
  animation: blink 1.4s infinite both;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.2 } 40% { opacity: 1 } }

.suggestions {
  display: flex; flex-wrap: wrap; gap: 0.5rem;
}
.suggestions button {
  background: rgba(255,255,255,0.04);
  color: #CBD5E1;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 999px;
  padding: 0.45rem 0.9rem;
  font-size: 0.85rem;
  cursor: pointer;
  transition: background 0.15s;
}
.suggestions button:hover { background: rgba(255,163,26,0.15); border-color: rgba(255,163,26,0.3); }

.composer {
  display: flex; gap: 0.5rem;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px;
  padding: 0.4rem;
}
.composer input {
  flex: 1;
  background: transparent;
  border: 0;
  color: inherit;
  font-size: 0.95rem;
  padding: 0.6rem;
  outline: none;
}
.composer input::placeholder { color: #64748B; }
.composer button {
  background: #FFA31A;
  color: #0F172A;
  border: 0;
  width: 44px; height: 44px;
  border-radius: 10px;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.15s, transform 0.1s;
}
.composer button:hover:not(:disabled) { background: #FFB347; }
.composer button:active:not(:disabled) { transform: scale(0.96); }
.composer button:disabled { background: rgba(255,163,26,0.3); cursor: not-allowed; }
.composer button .material-icons { font-size: 1.2rem; }

.footer-note {
  display: flex; gap: 0.5rem; align-items: flex-start;
  font-size: 0.78rem; color: #64748B;
  padding: 0.5rem 0.25rem;
}
.footer-note .material-icons { font-size: 1rem; flex-shrink: 0; margin-top: 1px; }
</style>
