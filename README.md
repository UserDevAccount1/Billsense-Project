# BillSense

**AI-driven Philippine peso counterfeit-detection system** — an Android app that
authenticates New Generation Currency (NGC) & Enhanced NGC banknotes in real time
using YOLOv8 models, backed by a cloud ML API, a Vue admin panel, and Firebase.

> Research / thesis by **Joy Canutab** and co-researchers (Canutab et al.),
> **University of the Cordilleras**, Baguio City, Philippines. Aligned with
> UN Sustainable Development Goal 16 (Target 16.4 — reducing illicit financial flows).

## Live surfaces

| Surface | URL | Status |
|---|---|---|
| Admin panel (cPanel) | https://billsense.dev-environment.site | ✅ live |
| Admin panel (Firebase mirror) | https://bill-sense-aec6b.web.app | ✅ live |
| ML inference API (Cloud Run) | https://billsense-api-340624938055.asia-southeast2.run.app | ✅ live |
| Android app (test builds) | Firebase App Distribution → group `testers` | ✅ distributing |

Admin login (client-side gate): `Billsense` / `admin` (also `admin`/`admin123`,
`admin@neuralyx.dev`/`neuralyx2026`).

## Authenticity scoring — Real Measurement (v1.5.0 / API v17.5)

The cloud ML API (`docker/app/main.py`) goes beyond "feature detected / not detected"
to **measure** each scan and emit a calibrated **0–100 authenticity score**. Full design:
[docs/REAL_MEASUREMENT_DESIGN.md](docs/REAL_MEASUREMENT_DESIGN.md).

- **Calibrated score** = feature coverage + per-detection confidence + capture quality
  (geometry placement is a bonus, never a penalty on a genuine note).
- **Geometry** — each detected security feature is scored against a genuine reference
  layout (`docker/app/reference_geometry.json`; empirical medians + BSP cross-check, built
  on Colab GPU via `training/build_reference_geometry.py`).
- **Quality gating** — blurry/dark captures return `NEEDS_RESCAN` instead of a guess.
- **OVI/OVD colour-shift** (Multi-Scan) — measures the hue change of optically-variable
  ink/device across angle frames; a confirmed shift boosts the verdict.
- **Status tiers**: GENUINE · LIKELY GENUINE · NEEDS_RESCAN · COUNTERFEIT (forgery-only).
- The Android app shows the score as a colour-coded bar on all three scan screens, with
  per-feature "% placed" on the standard scan.

Denomination model retrained on a merged YOLOv8 + COCO PH-banknote dataset (mAP50 0.83);
pipeline in [`training/`](training/README.md). Still CNN (YOLOv8) — ORB is not used.

## Architecture

```
Android app (Java, minSdk 24)            Vue 3 admin panel (Vite)
  ├─ CameraX live scan                     ├─ Dashboard (live scan analytics)
  ├─ Cloud scan  ──► Cloud Run FastAPI     ├─ Billy AI  ──► /api/gemini proxy ──► Gemini
  │                  (6 YOLOv8 OBB models) ├─ Users / Cases / Voting / Scan Reports
  ├─ On-device TFLite (offline fallback)   ├─ Content CRUD (Trivia / Tutorials)
  └─ Firebase RTDB / Storage / FCM         ├─ APK Management (distribute via App Distribution)
                                           └─ all data ──► /api/db proxy (SA) ──► Firebase RTDB
```

- **ML API**: FastAPI + Ultralytics YOLOv8 OBB ×6 (denomination, security, OVI,
  OVD, EVP, counterfeit) on Google Cloud Run. Source in `docker/`.
- **On-device**: 2 TFLite models (counterfeit + security) bundled for offline scanning.
- **Backend proxy**: a Node app on cPanel exposes `/api/gemini/*` and `/api/db/*` so the
  browser never holds the Gemini key or talks to RTDB directly (service-account auth;
  RTDB rules deny anonymous writes).
- **Firebase project**: `bill-sense-aec6b` (Realtime DB, Storage, Messaging, App Distribution, Hosting).

## Repository layout

| Path | What |
|---|---|
| `BillSense/` | Android app (Gradle). Source under `app/src/main/java/com/app/billsense/` |
| `BillSense Admin/admin-panel/` | Vue 3 admin dashboard |
| `docker/` | FastAPI ML service (`app/main.py`, `requirements.txt`, Dockerfiles) |
| `tools/cpanel-mcp/` | cPanel deploy tooling + `app.js.production` (Node backend source of truth) |
| `.github/workflows/` | CI/CD: `deploy-admin-cpanel.yml` (admin → cPanel), `distribute-apk.yml` (cloud APK fire) |
| `PROJECT_RECORD/` | Living docs: setup, architecture, deployment, credentials, status, changelog |

The **Thesis Validator** admin page has its own detailed README on the
`thesis-validator` branch; see also `PROJECT_RECORD/`.

## Build & run

```bash
# Android (from BillSense/)
./gradlew assembleUserDebug assembleAdminDebug      # user + admin flavors

# Admin panel (from "BillSense Admin/admin-panel/")
npm ci && npm run dev        # local at http://localhost:3001
npm run build                # production dist/

# ML API (from docker/) — needs the 6 .pt weights staged in docker/models/
docker compose up -d billsense-api    # http://localhost:8080/api/health
```

## Deployment

- **Admin panel** → push to `main` auto-deploys to cPanel via GitHub Actions (FTPS).
  Firebase Hosting mirror is a manual `firebase deploy --only hosting`.
- **Node backend** (`app.js`) → manual cPanel UAPI deploy from `tools/cpanel-mcp/app.js.production`.
- **APK distribution** → Firebase App Distribution (group `testers`). Fire from the
  `Fire APK` GitHub Actions workflow, the local `dev-server.mjs`, or share the install
  link from the admin **APK Management** page.

## Recent updates (2026-06-17)

- Consolidated `main` from `thesis-validator`; reconciled on-device **TFLite offline scanning**.
- Restored the **Cloud Run ML API** (billing re-linked) and backed up all 6 model weights.
- **Content** page: full CRUD for Trivia & Tutorials (reflects in the mobile app).
- **Dashboard**: live scan analytics (total scans, counterfeit rate, avg scan time, users).
- **Billy AI**: research knowledge (researcher, university, methodology, SDG) on web + Android.
- **APK Management**: live release records + Download/Install + **Distribute/Share** (copy/email
  install link). Current build: **v1.4.2 (7)**.

See `PROJECT_RECORD/07-CHANGELOG.md` for the full history.

## Security notes

Gitignored / never committed: `BillSense/local.properties`, `app/google-services.json`,
`app/src/main/res/raw/service_account.json`, `tools/cpanel-mcp/*.local.*`, `Resources/`.
The Firebase **Web** SDK key is intentionally public (scoped by RTDB rules + authorized domains).
