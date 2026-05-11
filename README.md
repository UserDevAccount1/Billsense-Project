# BillSense

**Philippine peso counterfeit-detection platform.** Android client captures banknote imagery, a Cloud Run FastAPI service runs an ensemble of six YOLOv8 OBB models against it, and a Vue 3 admin dashboard provides operator visibility and tooling.

![status](https://img.shields.io/badge/status-active-success)
![platform](https://img.shields.io/badge/platform-Android%20%7C%20Web-blue)
![ml](https://img.shields.io/badge/ML-YOLOv8%20OBB-orange)
![backend](https://img.shields.io/badge/backend-FastAPI%20on%20Cloud%20Run-green)

---

## Table of contents

1. [What is BillSense](#what-is-billsense)
2. [Architecture](#architecture)
3. [Repository layout](#repository-layout)
4. [Components](#components)
5. [Local development](#local-development)
6. [Building](#building)
7. [ML models](#ml-models)
8. [Deployment](#deployment)
9. [API reference](#api-reference)
10. [Tech stack](#tech-stack)
11. [Documentation index](#documentation-index)

---

## What is BillSense

A counterfeit-banknote detection system for the Philippine peso. Three surfaces:

| Surface | Audience | Tech |
|---|---|---|
| **Android app** | Citizens, field officers | Java 11, CameraX, ViewBinding, OkHttp |
| **Cloud Run API** | Mobile + admin clients | FastAPI, YOLOv8, OpenCV, Firebase Admin |
| **Admin dashboard** | Operators / dev team | Vue 3, Vite 6, Firebase Web SDK |

The scan pipeline runs six specialised YOLOv8 OBB models in parallel:

1. **Denomination classifier** — bill value (20, 50, 100, 200, 500, 1000)
2. **Security-feature detector** — watermark, security thread, serial number, see-through mark, concealed value
3. **OVI** — optically variable ink
4. **OVD** — optically variable device foil
5. **EVP** — enhanced value panel (500 / 1000 variants)
6. **Counterfeit classifier** — direct fake/real verdict

Each detection is mapped to a numbered feature overlay (1–9) and persisted alongside the annotated image in Firebase.

---

## Architecture

```
┌────────────────────────┐       ┌────────────────────────┐
│   Android client       │       │   Admin dashboard      │
│   (Java, CameraX)      │       │   (Vue 3 / Vite)       │
└──────────┬─────────────┘       └──────────┬─────────────┘
           │ WSS / HTTPS                    │ HTTPS + Firebase Web SDK
           │                                │
           ▼                                ▼
┌──────────────────────────────────────────────────────────┐
│         FastAPI Cloud Run service (asia-southeast2)      │
│         /api/standard-scan, /api/multi-scan, ...         │
│         /ws/standard-scan, /ws/real-multi-scan, ...      │
│                                                          │
│  6× YOLOv8 OBB models (lazy-loaded, 4-worker pool)       │
└──────────┬───────────────────────────────────────────────┘
           │ admin SDK
           ▼
┌──────────────────────────────────────────────────────────┐
│            Firebase (bill-sense-aec6b)                   │
│  RTDB · Storage · FCM                                    │
└──────────────────────────────────────────────────────────┘
```

Dev-only services:
- **GitNexus agent** (port 3002, Docker) — Puppeteer-driven GitHub clone automation
- **Dev server** (port 3003, host Node) — local APK build, ADB control, Firebase App Distribution

Full diagram and data flow: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Repository layout

```
Billsense-Project/
├── BillSense/                        # Android app (Gradle project)
│   ├── app/src/main/java/com/app/billsense/
│   │   ├── activities/               # ~15 user-facing screens
│   │   ├── adapters/                 # RecyclerView adapters
│   │   ├── api/                      # Retrofit services + DTOs
│   │   ├── fcm/                      # Firebase Cloud Messaging
│   │   ├── fragments/                # Tab fragments
│   │   ├── model/                    # POJOs
│   │   ├── scan/                     # Scan engine, WebSocket manager
│   │   │   └── pojo/
│   │   │       ├── CurrencyApiService.java
│   │   │       └── RealTimeScanManager.java
│   │   └── utils/                    # Helpers
│   ├── app/google-services.json      # NOT COMMITTED
│   ├── gmailbackgroundlibrary/       # Local module
│   └── gradle/libs.versions.toml     # Version catalog
│
├── BillSense Admin/
│   └── admin-panel/                  # Vue 3 dashboard
│       ├── src/
│       │   ├── views/                # Dashboard, ConnectionHealth, GitNexus,
│       │   │                         # ApkManagement, AppTesting
│       │   ├── router/
│       │   └── services/             # firebase.js, healthCheck.js, ...
│       ├── dev-server.mjs            # Host-side Node API on :3003
│       ├── Dockerfile                # nginx-served prod image
│       └── nginx.conf
│
├── docker/                           # ML backend + helper services
│   ├── app/
│   │   ├── main.py                   # FastAPI service (~2.3k LOC)
│   │   └── firebase_config.py
│   ├── Dockerfile                    # Local API image
│   ├── Dockerfile.gcp                # GCP build variant
│   ├── docker-compose.yml            # Stack orchestration
│   ├── extract-models.bat            # Pull .pt files from Cloud Run image
│   ├── run-local.bat                 # Convenience wrapper
│   ├── gitnexus-agent/               # Puppeteer service on :3002
│   └── requirements.txt
│
├── .github/workflows/
│   ├── deploy-admin-cpanel.yml       # CI/CD: build + FTPS to cPanel
│   └── README.md                     # cPanel cutover guide
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ML_MODELS.md
│   └── DEVELOPMENT.md
│
├── pdf-output/                       # Audit report generator (regenerable)
├── CLAUDE.md                         # Claude Code project config
├── .gitignore
└── README.md                         # this file
```

---

## Components

### Android app — `BillSense/`

Java 11, `minSdk 24`, `targetSdk 35`, AGP 8.11.1, Gradle 8.13. ViewBinding only (no DataBinding, no findViewById). Two package roots:

- `com.app.billsense` — user-facing app
- `com.admin.billsense` — admin variant

Key screens: `MainActivity` / `DispatchActivity` (splash + routing), `LoginActivity` (Firebase Auth + FCM token registration), `HomeActivity` (tab shell), `DetectionActivity` (scan flow — CameraX preview, base64 JPEG @80% quality over WSS), `CompareBillActivity`, `EducationalContentActivity`, `EvidenceActivity`, `CasesActivity`, `ChatBotActivity`.

All scan activities run **landscape**. Real-time scanning auto-starts on WebSocket connect (no `START_SCAN` handshake).

### Cloud Run inference API — `docker/app/`

FastAPI app, single process, 4-worker thread pool, CORS open. Six YOLOv8 OBB models lazy-loaded on first request with a `DummyFirebaseClient` fallback when service-account credentials are absent. Custom numpy JSON encoder. Numbered-annotation system maps detections to overlay numbers 1–9.

Deployed to Cloud Run at `https://billsense-api-340624938055.asia-southeast2.run.app`.

### Admin dashboard — `BillSense Admin/admin-panel/`

Vue 3.5, Vite 6, Firebase Web SDK 11. Five views, polling-based health checks across all backends. Static SPA — `npm run build` emits `dist/` which is served by nginx (Docker) or cPanel/Apache (production).

| Route | View | Purpose |
|---|---|---|
| `/` | Dashboard | Connection-health tiles |
| `/connection-health` | ConnectionHealth | Deep service status: Firebase, GCP, Docker, Mobile, GitNexus, etc. |
| `/gitnexus` | GitNexus | Trigger Puppeteer auto-clone, manage AI keys |
| `/apk-management` | ApkManagement | Build / list / download APKs via dev-server |
| `/app-testing` | AppTesting | Scripted ADB checks against the live emulator |

Production URL: `https://billsense.dev-environment.site/`.

### Dev server — `BillSense Admin/admin-panel/dev-server.mjs`

Zero-dependency Node 20 HTTP server on port **3003**. Drives ADB, Gradle builds, APK distribution. Endpoints:

```
GET  /api/dev/status              emulator + build + env status
POST /api/dev/build               build APK (variant=user|admin, buildType=debug|release)
GET  /api/dev/build/status        build progress
POST /api/dev/pipeline             emulator → build → install → launch
GET  /api/dev/pipeline/status     pipeline progress
POST /api/dev/pipeline/cancel
GET  /api/dev/apk/download        ?variant=user&buildType=debug
GET  /api/dev/apk/list
POST /api/dev/distribute          build + upload to Firebase App Distribution
GET  /api/dev/distribute/status
POST /api/dev/distribute/cancel
```

### GitNexus agent — `docker/gitnexus-agent/`

Express + Puppeteer on port **3002**. Single endpoint `POST /api/auto-clone` accepts `{repo, pat, geminiKey}` and drives the GitNexus web UI via headless Chrome (`--no-sandbox` inside Docker). `GET /health` returns service status.

---

## Local development

### Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 17 (Microsoft / Adoptium) | Android build |
| Android SDK | platform 35, build-tools 35.0.1 | Android build |
| Node | 20 LTS | Admin panel + dev-server + gitnexus-agent |
| Docker | 24+ | Backend + admin Docker stack |
| Firebase CLI | 13+ | google-services.json + App Distribution |
| Python | 3.12 | Optional — running the backend locally |
| gcloud SDK | latest | Pulling the Cloud Run image locally |

Detailed setup: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

### Quick start

```powershell
# 1) Bring up admin + gitnexus
cd docker
docker compose up -d billsense-admin gitnexus-agent

# 2) Start the dev-server (host process — needs ADB on PATH)
cd "..\BillSense Admin\admin-panel"
node dev-server.mjs

# 3) Open the dashboard
start http://localhost:3000
```

### Configuration files you must provide locally

| File | Source | Notes |
|---|---|---|
| `BillSense/local.properties` | you | `MAPS_API_KEY=...`, `sdk.dir=...` |
| `BillSense/app/google-services.json` | Firebase console | `firebase apps:sdkconfig` |
| `BillSense/app/src/main/res/raw/service_account.json` | GCP IAM | service account with FCM perms |
| `BillSense Admin/admin-panel/.env` | you | `VITE_GITNEXUS_REPO`, `VITE_GEMINI_API_KEY`, `VITE_OPENAI_API_KEY`, `VITE_GITHUB_PAT` |
| `docker/app/serviceAccountKey.json` | GCP IAM | only needed if running the API locally |

All are listed in `.gitignore`. Do not commit any of them.

---

## Building

### Android app

```bash
cd BillSense

# Debug
./gradlew assembleDebug
./gradlew installDebug          # install on connected emulator/device

# Release (unsigned, then sign separately)
./gradlew assembleRelease
```

APKs land in `BillSense/app/build/outputs/apk/`.

The dashboard's APK Management view drives the same Gradle targets via dev-server on :3003 — useful for one-click rebuild + install + launch from the browser.

### Admin panel

```bash
cd "BillSense Admin/admin-panel"
npm ci

# Dev (Vite, hot reload)
npm run dev                     # http://localhost:5173

# Dev + dev-server in one go
npm run dev:all

# Production build
npm run build                   # emits dist/
```

### Backend image (local)

The Cloud Run image is private; pulling it requires `gcloud auth login`. Local building requires the model weights — see [ML models](#ml-models).

```bash
cd docker

# Option A — pull the prod image
gcloud auth configure-docker asia-southeast2-docker.pkg.dev
docker compose pull billsense-api

# Option B — build locally (needs models/ + serviceAccountKey.json)
extract-models.bat              # one-time: extract .pt files from Cloud Run image
docker compose build billsense-api
docker compose up -d billsense-api
```

---

## ML models

Six **YOLOv8 OBB (Oriented Bounding Box)** detectors run as an ensemble. Weights live inside the Cloud Run image at `/app/models/*.pt`. They are **not** version-controlled in this repo — extract them with `docker/extract-models.bat` if you need them locally.

| File | Detector | Notes |
|---|---|---|
| `denomination2.pt` | Bill value | 6 classes: 20, 50, 100, 200, 500, 1000 |
| `security_best.pt` | Security features | watermark, thread, serial, value, concealed value, see-through |
| `ovi.pt` | Optically variable ink | suspect / clean / absent |
| `ovd.pt` | Optically variable device | foil region detection |
| `evp.pt` | Enhanced value panel | 500/1000 variants + false-positive guard |
| `counterfeit_best.pt` | Direct counterfeit classifier | UV-thread, missing markers, anomalies |

Training pipeline, dataset notes, and inference internals: [`docs/ML_MODELS.md`](docs/ML_MODELS.md).

---

## Deployment

### Mobile

Android APKs are distributed via **Firebase App Distribution**. The dev-server's `/api/dev/distribute` endpoint wraps `firebase appdistribution:distribute`.

### Backend (Cloud Run)

Manual today:

```bash
cd docker
gcloud builds submit --tag asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest
gcloud run deploy billsense-api \
  --image asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest \
  --region asia-southeast2 \
  --platform managed \
  --allow-unauthenticated
```

CI/CD for the backend is on the roadmap (see [`pdf-output/BillSense_Deep_Analysis.pdf`](pdf-output/BillSense_Deep_Analysis.pdf) §9.2).

### Admin dashboard (cPanel)

Automated via GitHub Actions. On every push to `main` touching `BillSense Admin/admin-panel/`, the workflow [`.github/workflows/deploy-admin-cpanel.yml`](.github/workflows/deploy-admin-cpanel.yml) builds, injects `VITE_*` env, writes an SPA-aware `.htaccess`, and uploads `dist/` via FTPS using `SamKirkland/FTP-Deploy-Action@v4.3.5`.

Setup guide: [`.github/workflows/README.md`](.github/workflows/README.md).

---

## API reference

Base URLs:
- **HTTPS** — `https://billsense-api-340624938055.asia-southeast2.run.app`
- **WSS**   — `wss://billsense-api-340624938055.asia-southeast2.run.app`

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Liveness, model state, Firebase reachability |
| GET | `/api/real-time-status` | Active WebSocket connection metrics |
| POST | `/api/standard-scan` | One-shot single-image scan |
| POST | `/api/multi-scan` | Multi-bill batch processing |
| POST | `/api/video-scan` | Video file upload scan |
| WSS | `/ws/standard-scan` | Real-time single-bill stream |
| WSS | `/ws/real-multi-scan` | Real-time multi-bill stream |
| WSS | `/ws/real-video-scan` | Real-time video frame stream |

Frame payload over WebSocket: base64-encoded JPEG at 80% quality. Server replies with detection JSON shaped by the numpy-aware encoder.

A legacy API still lives at `https://ph-currency-fast-api-340624938055.asia-east1.run.app` (`/predict/{model_type}/image|video`). Plan to retire — see audit report.

---

## Tech stack

**Android.** Java 11 · CameraX · ViewBinding · OkHttp · Retrofit · Glide · Material Components · Firebase Database 21.0.0 · Firebase Storage 21.0.1 · Firebase Messaging · Play Services Maps · CountryCodePicker

**Admin web.** Vue 3.5 · Vite 6 · Vue Router 4 · Firebase Web SDK 11

**Backend.** Python 3.9 · FastAPI v17 · OpenCV · Ultralytics YOLOv8 (OBB) · Firebase Admin SDK · uvicorn

**Helper services.** Express 4 · Puppeteer (gitnexus-agent) · Node 20 stdlib (dev-server)

**Infra.** Google Cloud Run · Artifact Registry (asia-southeast2) · Firebase RTDB / Storage / FCM · cPanel + nginx (dashboard hosting) · GitHub Actions (CI/CD)

---

## Documentation index

| Doc | What's in it |
|---|---|
| [`README.md`](README.md) | this file — overview, build, deploy |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | full system diagram, data flows, sequence walkthroughs |
| [`docs/ML_MODELS.md`](docs/ML_MODELS.md) | model ensemble details, training playbook, dataset notes |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | local dev environment, troubleshooting, MCP setup |
| [`.github/workflows/README.md`](.github/workflows/README.md) | cPanel deploy cutover and secrets |
| [`pdf-output/BillSense_Deep_Analysis.pdf`](pdf-output/BillSense_Deep_Analysis.pdf) | 12-page technical audit (architecture, security, gaps, recommendations) |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code project configuration |

---

## License

Internal project — no public license declared yet. Treat as proprietary.

## Maintainers

- **GabFuture0426** ([@UserDevAccount1](https://github.com/UserDevAccount1))

For questions, open an issue at <https://github.com/UserDevAccount1/Billsense-Project/issues>.
