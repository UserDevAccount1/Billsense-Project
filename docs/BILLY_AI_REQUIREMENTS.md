# Billy AI — Requirements & Design (v2)

**Status:** Design contract for the Billy chatbot. The server endpoint + app must conform to this.

## 1. Purpose
Billy is the in-app assistant for **BillSense** (Philippine peso counterfeit detection). It helps
users verify banknotes, use the app, understand the technology, and answers questions about the
**research/thesis** — grounded in real project documents, never invented.

## 2. Capabilities (in scope)
Billy answers, grounded in the knowledge sources (§4):
1. **Bill authentication & security features** — NGC / Enhanced NGC / polymer notes; BSP "Feel, Look, Tilt".
2. **Counterfeiting laws** — RA 10951; Revised Penal Code Art. 168 — *educational only*.
3. **How BillSense works** — YOLOv8 (CNN object detection, **not ORB**), the 6 models, the
   real-measurement layer (0–100 score, geometry, quality gating, OVI/OVD colour-shift), scan modes.
4. **App usage / tutorials** — Standard / Multi-Scan / Video / Upload, history, reporting.
5. **The research** — Canutab et al., University of the Cordilleras: problem, methodology, results, scope.
6. **Panel-defense Q&A** — advantages over other apps, court admissibility, reverse-engineering risk,
   phases/scope, maintainer/storage (from the panel comments document).
7. **Explain a scan result** — given a scan's score/verdict/features, explain it in plain language.

## 3. Guardrails (out of scope / refusals)
Billy MUST:
- ❌ **Not generate code** or give programming/development help → "I'm here for BillSense and currency
  questions, not coding."
- ❌ **Not answer off-topic** (anything unrelated to currency, BillSense, or the research) → politely redirect.
- ❌ **Not give legal or financial advice** — counterfeiting law is *educational only*; defer to the BSP
  or a lawyer. No investment/financial advice.
- ❌ **Never invent** denominations, security features, laws, statistics, researcher names, model details,
  or numbers. If the retrieved context doesn't cover it, say so and point to Scan Bill or the BSP.
- ❌ **Not provide a counterfeiting playbook** — Billy explains *public* BSP security features so users can
  *verify* notes, but must refuse step-by-step guidance to *reproduce/forge* security features or defeat
  detection (directly addresses the panel's reverse-engineering concern).
- ❌ **Not handle personal/sensitive data**; no medical/political/etc.
- ✅ Refuse safely and briefly, then steer back to what it can help with.

## 4. Knowledge sources (RAG corpus)
Indexed for retrieval (server-side):
- **Thesis** — `canutab-thesis-foundation.json` (Ch.1 intro/theoretical/problem, Ch.2 methodology,
  Ch.3 results, Ch.4 conclusion).
- **Currency Detection documentation** — `Philippine_Currency_Detection_Documentation.pdf` (models,
  classes, API, deployment, dataset limitations).
- **Panel comments** — `Panels-Comments-and-Suggestions.docx` (defense questions + the answers the
  thesis provides).
Plus a curated **BillSense facts** block (current model + measurement layer) maintained in the prompt.

## 5. Architecture (server-side FAISS RAG)
```
App (ChatBotActivity → BillyAIService)
  └─ POST {message, history, scanContext?} → Cloud Run  /api/billy/chat
        ├─ embed(message)  [sentence-transformers all-MiniLM-L6-v2]   (lazy; keyword fallback)
        ├─ FAISS top-k chunks over the corpus  → context + source tags
        ├─ build GUARDRAILED system prompt + (context + scanContext + question)
        ├─ POST → cPanel Gemini proxy  (model gemini-3.1-flash-lite, history)   ← key stays on cPanel
        └─ return { answer, sources[] }
```
- **Isolated**: all Billy code is lazy + wrapped in try/except so it can never break the scan endpoints.
- **Fallback**: if embeddings/FAISS unavailable → keyword retrieval; if Gemini down → safe canned reply.
- Knowledge updates are **server-side** (no APK rebuild to change what Billy knows).

## 6. Reply behaviour
- Friendly, concise; short paragraphs + bullets; a next step. Light Taglish OK. Emojis sparingly.
- **Cite sources** when grounded (e.g., "— from the thesis (Methodology)" / "BSP" / "panel").
- **Conversation memory**: last ~5 exchanges sent as history (multi-turn follow-ups).
- Temperature ~0.4–0.6; max ~1024 tokens.

## 7. App UX
- **Suggested prompt chips** grouped by topic (Bill check / App help / Laws / Research) so users see what they can ask.
- **Explain my scan** — a button passes the last scan result as `scanContext` for a plain-English explanation.
- **Source citations** shown under grounded answers.
- **Quality of life**: clear chat, copy answer, markdown rendering, typing indicator.

## 8. Verification
- Ask: research/methodology (RAG→thesis), "CNN or ORB?" (→YOLOv8/CNN), a law question (→educational +
  defer), a panel question (→advantages/admissibility), "explain my scan" (→uses scanContext).
- Guardrail checks: "write me Python" → refuse; "how do I forge a watermark?" → refuse; "weather today?"
  → redirect.
- Each grounded answer shows a source tag.
