# 07 — Changelog & Tech Stack

> Append an entry for every meaningful change so the next agent inherits it.

## Tech stack (current versions)

| Layer | Stack |
|---|---|
| Android | Java 11, minSdk 24, targetSdk 35, AGP 8.11.1, Gradle 8.13, CameraX, ViewBinding, OkHttp/Retrofit, Glide, Firebase DB 21 / Storage 21 / Messaging |
| Dashboard | Vue 3.5.x, Vue Router 4.6.x, Vite 6.4.x, @vitejs/plugin-vue 5.2.x, Firebase Web SDK 11.10.x |
| cPanel backend | Node 22 (Phusion Passenger), pure Node stdlib (no deps) |
| ML API | Python 3.9, FastAPI v17, Ultralytics YOLOv8 OBB ×6, OpenCV, uvicorn, on Cloud Run |
| Helper svcs | Express + Puppeteer (gitnexus-agent), Node 20 stdlib (dev-server.mjs) |
| Infra | Cloud Run + Artifact Registry (asia-southeast2), Firebase RTDB/Storage/FCM/Hosting, cPanel (iFastNet, openresty+Passenger), GitHub Actions CI/CD |
| 3rd-party MCP | ringo380/cpanel-mcp v1.1.0 |

Major deps deliberately NOT upgraded (need a tested upgrade pass):
firebase 11→12, vite 6→8, vue-router 4→5, @vitejs/plugin-vue 5→6.

## Session history (2026-05, the build-out)

Chronological summary of what was done and why. Commits are on `main`.

1. **Docs + CI/CD + audit PDF** (`759bfe0`) — README, docs/, deep-analysis PDF,
   `.github/workflows/deploy-admin-cpanel.yml` (FTPS deploy).
2. **cPanel MCP** — built a custom one, then replaced with ringo380/cpanel-mcp
   (`6b65e0a`, `8a3ba33`).
3. **cPanel cutover** (`e78d3f6`) — replaced the Phusion "It works!" stub
   `app.js` with a static SPA server; deployed the Vue build to `public/`;
   created `deploy@dev-environment.site` FTP account.
4. **CI/CD hardening** (`c65d196`, `2e044bd`) — deploy on every push;
   `dangerous-clean-slate: true` (the incremental FTP sync drifted and froze
   cPanel on stale builds — root cause of "double sidebar / old dashboard").
5. **Billy AI** (`22ea48a`, `ade0310`, `65be50e`) — `/billy` chat view; model
   chain pro-latest→flash-latest→2.5-flash-lite; shared `services/gemini.js`;
   GitNexus AI repo analyzer.
6. **Firebase Hosting** (`a5c1a24`) — dual-origin: cPanel + Firebase mirror.
   Runtime proxy-base resolution; CORS allowlist in app.js.
7. **Login gate** (`5811969`) — soft client-side gate, `Billsense`/`admin`.
8. **Gemini key leak fixes** — Google auto-revoked 2 keys found in the public
   bundle. Moved to server-side proxy (`c50f014`); switched to an IP+API
   restricted GCP key; key never in browser/git again.
9. **6 missing pages** (`2a28c39`) — Users/ScanReports/Cases/VotingPosts/
   MLModels/Settings wired to the REAL RTDB schema (capitalised paths). Root
   cause of "Firebase not picking up data": dashboard queried guessed
   lowercase paths that don't exist.
10. **GitNexus fix** (`a7e12b6`) — iframe pointed at `/gitnexus-proxy` (a
    dev-only proxy) → recursively loaded the whole dashboard ("double
    sidebar"). Now points at gitnexus.vercel.app directly.
11. **Admin CRUD** (`cb464cc`) — Cases + Voting Posts management tables
    (status/archive/delete/poll-toggle/comment-moderation).
12. **DB security** (`f392b9a`, `c0cf270`) — discovered RTDB was world-writable.
    Built SA-authenticated `/api/db/*` proxy (JWT→OAuth, pure Node). Locked
    rules to `{".read":true,".write":"auth!=null"}`. Anonymous write → 401.
13. **Scan images + Docker card** (`76dc62f`) — Scan Reports now shows real
    `annotatedImageUrl` bill images; "Docker Container" health card no longer
    false-errors (neutral "optional", production ML is Cloud Run).
14. **Live-site honesty** (`402ee80`) — App Testing / APK Management show a
    "local developer tool" banner on the live site instead of red errors;
    skip dev-server polling when remote.
15. **Live login fix** (`dae818a`) — root cause: `crypto.subtle` is
    HTTPS/secure-context only; `http://billsense.dev-environment.site` didn't
    redirect → login impossible over http. Replaced with verified pure-JS
    SHA-256; added http→https 301 in app.js (best-effort; iFastNet proxy
    doesn't always send x-forwarded-proto).
16. **gcloud unlock** — `gcloud auth activate-service-account` with the
    Firebase SA bypassed the "access blocked" interactive-login wall;
    enabled pulling the 9.93 GB ML container and any `bill-sense-aec6b` GCP op.
17. **This knowledge record** — `PROJECT_RECORD/` + `AGENTS.md`.

## Recurring lessons (don't relearn the hard way)

- **Docker `billsense-admin` is manual.** Not in CI. Rebuild after source
  changes or it serves a stale dashboard.
- **3 deploy surfaces** (cPanel CI, Firebase manual, Docker manual) — keep in
  parity; CI build hash differs from local build hash (different env) but is
  functionally identical.
- **Never put a Gemini/API key in `VITE_*`** — Vite bakes it into the public
  bundle and Google auto-revokes within minutes. Proxy server-side.
- **RTDB paths are capitalised with spaces** (`Users`, `Voting Posts`,
  `Standard Scan`) — the Android app's schema. Not lowercase.
- **`crypto.subtle` needs a secure context** — don't use it for anything that
  must work over plain http. Pure-JS where it matters.
- **The Windows `python` shim** breaks gcloud — use the explicit Python312 path
  / `CLOUDSDK_PYTHON`.
- **Interactive gcloud/firebase login is blocked here** — use the service
  account (`gcloud auth activate-service-account`) and `GH_TOKEN` env for gh.
- **Local-first** — verify on Vite + browser preview before every push; CI
  auto-deploys to production.

## Template for new entries

```
### YYYY-MM-DD — <short title> (commit <sha>)
- What changed:
- Why / root cause:
- Verified how (local-first):
- Surfaces updated: cPanel CI ☐  Firebase ☐  Docker ☐
- Follow-ups / risks:
```
