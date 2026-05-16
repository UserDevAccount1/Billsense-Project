# 02 — Architecture & Connections

## The big picture

```
                         ┌───────────────────────────┐
  Android app  ───────►  │  Cloud Run FastAPI (ML)    │  bill-sense-aec6b
  (Java/CameraX)         │  6× YOLOv8 OBB models      │  asia-southeast2
                         │  billsense-api-340624938055│
                         │   .asia-southeast2.run.app │
                         └─────────────┬─────────────┘
                                       │ Firebase Admin
                                       ▼
                         ┌───────────────────────────┐
                         │  Firebase (bill-sense-aec6b)│
                         │  RTDB · Storage · FCM       │
                         └─────────────▲─────────────┘
                                       │ service-account (admin)
            ┌──────────────────────────┴───────────────────────┐
            │  cPanel Node app.js  (the Backend-for-Frontend)   │
            │  /home/devenvir/billsense.dev-environment.site/   │
            │                                                   │
            │  • serves ./public  (the Vue SPA, SPA fallback)   │
            │  • /api/gemini/*  → Google Gemini (key in         │
            │                     .gemini-key, server-only)     │
            │  • /api/db/*      → Firebase RTDB (auth via        │
            │                     .firebase-sa.json OAuth2 JWT)  │
            │  • CORS allow-list, http→https 301                 │
            │  Phusion Passenger, Node 22                        │
            └───────▲───────────────────────────▲───────────────┘
                    │ same-origin               │ cross-origin (CORS)
        ┌───────────┴─────────┐     ┌───────────┴───────────────┐
        │ cPanel static SPA   │     │ Firebase Hosting SPA      │
        │ billsense.dev-      │     │ bill-sense-aec6b.web.app  │
        │ environment.site    │     │ (static mirror, no proxy) │
        └─────────────────────┘     └───────────────────────────┘
```

## Components

| Component | Runtime | Endpoint | Notes |
|---|---|---|---|
| ML inference API | Cloud Run | `https://billsense-api-340624938055.asia-southeast2.run.app` | production ML; `/api/health`, `/api/standard-scan`, WS endpoints; models lazy-load (cold `models_loaded:false` is normal) |
| Legacy ML API | Cloud Run | `https://ph-currency-fast-api-340624938055.asia-east1.run.app` | older; candidate for retirement |
| cPanel Node app | Phusion Passenger, Node 22 | `https://billsense.dev-environment.site` | static SPA + `/api/gemini/*` + `/api/db/*`; source = `tools/cpanel-mcp/app.js.production`, lives on host as `app.js` |
| Firebase Hosting | Firebase | `https://bill-sense-aec6b.web.app` | static SPA mirror only — no proxy; calls the cPanel proxy cross-origin |
| Local ML mirror | Docker | `localhost:8080` | `billsense-api` image (9.93 GB) pulled from Artifact Registry; optional dev mirror, NOT production |
| dev-server | host Node | `localhost:3003` | ADB / Gradle / emulator control for App Testing + APK Management; local-only |
| gitnexus-agent | Docker | `localhost:3002` | Puppeteer GitHub-clone agent; local-only |
| admin (nginx) | Docker | `localhost:3000` | nginx-served prod image of the SPA; manual rebuild |

## How the dashboard talks to data + AI (critical)

The browser holds **no keys**. `src/services/gemini.js` and `src/services/db.js`
resolve a proxy base at runtime:

- served from `billsense.dev-environment.site` → relative `/api/...` (same-origin)
- served from anywhere else (Firebase, localhost) → absolute
  `https://billsense.dev-environment.site/api/...` (CORS-allowed)

### `/api/gemini/*` (Billy AI, GitNexus AI analyzer)
- `app.js` reads `.gemini-key` (server-only file) at startup.
- `POST /api/gemini/chat` body `{model, systemPrompt, history, userMessage}`.
- Model chain walked on 429/503: `gemini-pro-latest` → `gemini-flash-latest`
  → `gemini-2.5-flash-lite`. Free-tier Pro quota is tiny so it usually answers
  on Flash (`gemini-3-flash-preview`). Enable billing on the Gemini API project
  to get Pro consistently.
- `GET /api/gemini/health` → `{keyConfigured}`.
- Gemini key is a **GCP API key restricted to IPs `185.2.168.24,185.2.168.30`**
  (cPanel inbound + outbound) and the Generative Language API only — so even if
  leaked it's useless off-host.

### `/api/db/*` (all dashboard DB reads + writes)
- `app.js` reads `.firebase-sa.json` (Firebase service account, server-only).
- Mints a Google OAuth2 access token via RS256-signed JWT (pure Node `crypto`,
  no deps), cached ~1h.
- `POST /api/db/get|patch|delete` body `{path, data?}`.
- **Root allowlist** (defense in depth): `Users, Cases, "Voting Posts",
  session_reports, ml_config, Announcements, Bills, Detections, Notifications,
  "Standard Scan", "Multi Scan", "Video Scan", billy_analytics`.
- `GET /api/db/health` → `{saConfigured}`.
- Service-account access is **admin** — bypasses RTDB rules entirely, so the
  dashboard keeps full CRUD even though anonymous writes are denied.

## Firebase RTDB schema (real, from the Android app — capitalised, spaces)

| Path | Shape |
|---|---|
| `Users` | user records: name, email, phone, status, image, password(!) |
| `Cases` | counterfeit incident reports: title, description, status, image, lat/lng, isArchived |
| `Voting Posts` | community posts: title, description, votingQuestion, Comments{}, votingEnabled, status |
| `Standard Scan` / `Multi Scan` / `Video Scan` | nested `userId → scanId → record` with `annotatedImageUrl`, authenticity, denomination, confidence, detectedFeaturesCount, timestamp, processingTime |
| `session_reports` | dev session reports: title, summary, issuesFound[], fixesApplied[], checklist[], actionsDone[], features[], userGuide{} |
| `ml_config` | active_models {counterfeit, security} etc. |
| `Announcements`, `Bills`, `Detections`, `Notifications`, `billy_analytics` | misc |

> The original dashboard guessed lowercase paths (`users`, `scans`,
> `app_config`) that don't exist — fixed to the real schema. Don't reintroduce
> the guessed names.

## RTDB security rules (deployed)

```json
{ "rules": { ".read": true, ".write": "auth != null" } }
```

- Anonymous read: allowed (keeps the Android app's pre-login reads working;
  this was a deliberate user choice — see `07-CHANGELOG.md`).
- Anonymous write/delete: **DENIED** (closes the catastrophic data-wipe hole).
- Dashboard writes: via the SA proxy (admin, bypasses rules).
- Source: `BillSense Admin/database.rules.json`; deployed with
  `firebase deploy --only database`.
- **Residual risk:** records (incl. `Users.password`) are still publicly
  readable. Closing that needs full `auth != null` read rules + confirming the
  Android app authenticates before every read.

## Ports / surfaces matrix

| Port/URL | Service | Live-reachable? |
|---|---|---|
| `localhost:3001` | Vite dev | local only |
| `localhost:3000` | Docker admin (nginx) | local only |
| `localhost:3003` | dev-server (ADB/Gradle) | local only |
| `localhost:3002` | gitnexus-agent | local only |
| `localhost:8080` | local ML mirror | local only |
| `billsense.dev-environment.site` | cPanel SPA + proxy | ✅ production |
| `bill-sense-aec6b.web.app` | Firebase static mirror | ✅ production |
| `*.asia-southeast2.run.app` | Cloud Run ML | ✅ production |
